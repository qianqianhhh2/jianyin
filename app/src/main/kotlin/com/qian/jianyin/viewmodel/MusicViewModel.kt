package com.qian.jianyin

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import java.io.File
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import com.qian.jianyin.bili.BiliApi
import com.qian.jianyin.bili.BiliWebLoginHelper
import com.qian.jianyin.bili.BiliAudioStreamInfo
import com.qian.jianyin.bili.SavedCookieAuthState
import com.qian.jianyin.netease.NeteaseSongSearchResult
import com.qian.jianyin.netease.api.NeteaseApiService
import com.qian.jianyin.playback.DesktopLyricService
import com.qian.jianyin.playback.BluetoothDisconnectReceiver
import com.qian.jianyin.ui.ThemeColorUtil
import com.qian.jianyin.DownloadSettingsStore

// 歌单队列项数据类
data class PlaylistQueueItem(
    val id: String,
    val name: String,
    val coverPic: String,
    val songs: List<Song>
)

// 分p选择对话框状态
data class MultiPageSelectionState(
    val show: Boolean = false,
    val song: Song? = null,
    val pages: List<Song> = emptyList()
)

// 进度条样式枚举
enum class ProgressBarStyle {
    DEFAULT,          // 默认样式
    ROUND,            // 圆条样式
    AUDIO             // 音频波形图样式
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    // 音频管理器
    private val audioManager: AudioManager by lazy {
        getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    private val mediaSessionManager = MediaSessionManager.getInstance(application)
    
    // --- 状态订阅 ---
    val searchResults = mutableStateListOf<Song>()
    val historyList = mutableStateListOf<Song>()    
    val searchHistory = mutableStateListOf<String>() 
    
    val playQueue = mutableStateListOf<Song>()      // 播放队列
    var currentQueueIndex = mutableIntStateOf(-1)   // 当前播放索引

    val isSearching = mutableStateOf(false)
    val currentSong = mutableStateOf<Song?>(null)
    val isPlaying = mutableStateOf(false)
    val isLoading = mutableStateOf(false)
    val isPlayerSheetVisible = mutableStateOf(false)

    // 进度与歌词
    val currentLrc = mutableStateListOf<LyricEntry>()
    val currentTranslatedLrc = mutableStateListOf<LyricEntry>()
    val currentLineIndex = mutableIntStateOf(0)
    var currentPosition = mutableLongStateOf(0L)
    var totalDuration = mutableLongStateOf(0L)

    // --- 播放模式 ---
    val playMode = mutableStateOf(PlaybackMode.SEQUENCE)
    
    // --- 进度条样式 ---
    val progressBarStyle = mutableStateOf(ProgressBarStyle.DEFAULT)
    
    // --- 播放速度 ---
    val playbackSpeed = mutableStateOf(1.0f)  // 当前播放速度，默认为1.0x

    // Toast 消息
    val toastMessage = mutableStateOf<String?>(null)

    val currentPlayingList = mutableStateListOf<Song>()   // 当前播放歌曲的来源列表
    val currentPlayingListIndex = mutableIntStateOf(-1)    // 当前歌曲在来源列表中的索引
    
    // 歌单队列（用于持续播放模式）
    val playlistQueue = mutableStateListOf<PlaylistQueueItem>()  // 歌单队列
    var currentPlaylistIndex = mutableIntStateOf(-1)              // 当前播放的歌单索引
    
    // 歌单更新触发器，用于通知 UI 刷新歌单列表
    val playlistUpdateTrigger = mutableIntStateOf(0)

        // 收藏状态
    val isCurrentSongFavorited = mutableStateOf(false)

    // 推荐搜索词
    val recommendedSearches = listOf("周杰伦", "陈奕迅", "林俊杰", "五月天", "邓紫棋", "告白气球", "十周年", "平凡之路")

    // B站相关状态
    val biliLoginState = mutableStateOf<BiliLoginState>(BiliLoginState.Unknown)
    private val biliApi: BiliApi by lazy { BiliApi.getInstance(application) }
    
    // 分p选择状态
    val multiPageSelectionState = mutableStateOf(MultiPageSelectionState())

    enum class BiliLoginState {
        Unknown,    // 未知状态
        NotLoggedIn, // 未登录
        LoggedIn,   // 已登录
        Expired     // 登录已过期
    }

    val player: ExoPlayer by lazy {
        BiliPlayerHelper.createPlayer(application, biliApi)
    }
    private val prefs = application.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private var progressJob: Job? = null
    private var fadeJob: Job? = null
    private var searchJob: Job? = null
    
    private var bluetoothDisconnectReceiver: BluetoothDisconnectReceiver? = null

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.qijieya.cn/")
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val api = retrofit.create(MetingApi::class.java)

    // 音频焦点变化监听器
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d("MusicVM", "音频焦点获取: 重新获得焦点")
                if (!player.isPlaying && currentSong.value != null) {
                    player.play()
                    isPlaying.value = true
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d("MusicVM", "音频焦点丢失: 永久失去焦点")
                player.pause()
                isPlaying.value = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d("MusicVM", "音频焦点丢失: 暂时失去焦点")
                player.pause()
                isPlaying.value = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d("MusicVM", "音频焦点丢失: 可以降低音量")
                player.volume = 0.1f
            }
        }
    }

    init {
        loadDataFromPrefs()
        
        // 加载保存的进度条样式
        loadProgressBarStyle()
        
        // 加载保存的播放速度
        loadPlaybackSpeed()
        
        // 恢复播放状态（仅在开启"离开后保留列表"时）
        restorePlaybackStateOnStartup()
        
        initializeMediaSession()
        
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying.value = isPlayingNow
                if (isPlayingNow) {
                    totalDuration.longValue = player.duration
                    startProgressUpdater()

                    mediaSessionManager.updatePlaybackState(true, player.currentPosition)
                } else {
                    progressJob?.cancel()

                    mediaSessionManager.updatePlaybackState(false, player.currentPosition)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                // 歌曲播放自然结束，触发切歌
                if (state == Player.STATE_ENDED) {
                    Log.d("MusicVM", "歌曲播放结束，自动下一首")
                    nextSong()
                }
                
                // 更新加载状态
                isLoading.value = state == Player.STATE_BUFFERING
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // 更新媒体会话的播放位置
                mediaSessionManager.updatePlaybackState(
                    isPlaying = player.isPlaying,
                    position = player.currentPosition
                )
            }

            override fun onEvents(player: Player, events: Player.Events) {
                // 当歌曲准备好时，更新总时长
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                    player.playbackState == Player.STATE_READY) {

                    val duration = player.duration
                    if (duration > 0 && currentSong.value != null) {
                        totalDuration.longValue = duration

                        currentSong.value?.let { song ->
                            val context = getApplication<Application>()
                            val localCoverPath = if (song.isLocal) null else CacheManager.getCachedCoverPath(context, song)
                            mediaSessionManager.updateMetadata(
                                title = song.name,
                                artist = song.artist,
                                album = "专辑",
                                duration = duration,
                                artworkUrl = localCoverPath ?: song.pic
                            )
                        }

                        Log.d("MusicVM", "歌曲已准备好，时长: $duration")
                    }
                }
            }
        })

        // 初始化B站登录状态监听
        initializeBiliLoginStateListener()
        
        // 注册蓝牙断开监听
        registerBluetoothDisconnectReceiver()
    }
    
    private fun registerBluetoothDisconnectReceiver() {
        bluetoothDisconnectReceiver = BluetoothDisconnectReceiver {
            if (player.isPlaying) {
                Log.d("MusicVM", "蓝牙设备断开，自动暂停播放")
                player.pause()
                isPlaying.value = false
                mediaSessionManager.updatePlaybackState(false, player.currentPosition)
            }
        }
        
        val intentFilter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                getApplication<Application>().registerReceiver(
                    bluetoothDisconnectReceiver,
                    intentFilter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                getApplication<Application>().registerReceiver(
                    bluetoothDisconnectReceiver,
                    intentFilter
                )
            }
            Log.d("MusicVM", "蓝牙断开监听已注册")
        } catch (e: Exception) {
            Log.e("MusicVM", "注册蓝牙断开监听失败", e)
        }
    }

    private fun initializeBiliLoginStateListener() {
        viewModelScope.launch {
            biliApi.authHealthFlow.collect { health ->
                val newState = when (health.state) {
                    SavedCookieAuthState.Missing -> BiliLoginState.NotLoggedIn
                    SavedCookieAuthState.Valid -> BiliLoginState.LoggedIn
                    SavedCookieAuthState.Expired, SavedCookieAuthState.Invalid -> {
                        if (health.loginCookieKeys.isNotEmpty()) {
                            BiliLoginState.Expired
                        } else {
                            BiliLoginState.NotLoggedIn
                        }
                    }
                }
                if (biliLoginState.value != newState) {
                    biliLoginState.value = newState
                    Log.d("MusicVM", "B站登录状态变更: $newState")
                    if (newState == BiliLoginState.Expired) {
                        clearBiliPlaylists()
                    }
                }
            }
        }

        viewModelScope.launch {
            validateBiliLogin()
        }
    }

    suspend fun validateBiliLogin(): Boolean = withContext(Dispatchers.IO) {
        try {
            val isValid = biliApi.validateLoginSession()
            val state = if (isValid == true) BiliLoginState.LoggedIn else BiliLoginState.NotLoggedIn
            biliLoginState.value = state
            state == BiliLoginState.LoggedIn
        } catch (e: Exception) {
            Log.e("MusicVM", "验证B站登录失败", e)
            biliLoginState.value = BiliLoginState.NotLoggedIn
            false
        }
    }

    private fun clearBiliPlaylists() {
        val context = getApplication<Application>()
        val playlists = PlaylistDataStore.getAll(context)
        val biliPlaylists = playlists.filter { it.id.startsWith("bili_") }
        biliPlaylists.forEach { playlist ->
            PlaylistDataStore.delete(context, playlist.id)
        }
        Log.d("MusicVM", "已清除${biliPlaylists.size}个过期的B站歌单")
    }

    suspend fun syncBiliPlaylists(): List<UserSyncedPlaylist>? = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val playlists = BiliPlaylistSyncManager.getUserPlaylists(context)
            if (playlists != null) {
                // 保存我的歌单到 PlaylistDataStore
                playlists.forEach { playlist ->
                    PlaylistDataStore.save(context, playlist)
                }
                // 触发歌单更新，通知 UI 刷新
                withContext(Dispatchers.Main) {
                    playlistUpdateTrigger.intValue++
                }
            }
            playlists
        } catch (e: Exception) {
            Log.e("MusicVM", "同步B站歌单失败", e)
            null
        }
    }

    suspend fun syncNeteaseUserPlaylists(): List<UserSyncedPlaylist>? = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val userId = NeteaseApiService.getCurrentUserId()
            val playlists = NeteaseApiService.getUserPlaylists(userId)
            if (playlists.isEmpty()) return@withContext null

            val syncedPlaylists = mutableListOf<UserSyncedPlaylist>()
            for (pl in playlists) {
                val songs = PlaylistSyncManager.fetchPlaylist(pl.id) ?: continue
                if (songs.isEmpty()) continue
                val playlist = UserSyncedPlaylist(
                    id = pl.id,
                    name = pl.name,
                    coverPic = pl.picUrl,
                    songs = songs
                )
                PlaylistDataStore.save(context, playlist)
                syncedPlaylists.add(playlist)
            }
            withContext(Dispatchers.Main) {
                playlistUpdateTrigger.intValue++
            }
            syncedPlaylists
        } catch (e: Exception) {
            Log.e("MusicVM", "同步网易云歌单失败", e)
            null
        }
    }

    /**
     * 初始化媒体会话
     */
    private fun initializeMediaSession() {
        mediaSessionManager.controlCallback = object : MediaSessionManager.MediaControlCallback {
            override fun onPlay() {
                Log.d("MusicVM", "从通知栏收到播放命令")
                if (!player.isPlaying) {
                    togglePlay()
                }
            }
            
            override fun onPause() {
                Log.d("MusicVM", "从通知栏收到暂停命令")
                if (player.isPlaying) {
                    togglePlay()
                }
            }
            
            override fun onNext() {
                Log.d("MusicVM", "从通知栏收到下一首命令")
                nextSong()
            }
            
            override fun onPrevious() {
                Log.d("MusicVM", "从通知栏收到上一首命令")
                previousSong()
            }
            
            override fun onStop() {
                Log.d("MusicVM", "从通知栏收到停止命令")
                player.pause()
                player.seekTo(0)
                isPlaying.value = false
                mediaSessionManager.hideNotification()
            }
            
            override fun onSeekTo(position: Long) {
                Log.d("MusicVM", "从通知栏收到跳转命令: $position")
                seekTo(position)
            }
        }
        
        mediaSessionManager.initialize()
    }

    // 切换模式方法
    fun togglePlayMode() {
        val nextMode = playMode.value.next()
        playMode.value = nextMode
        Log.d("MusicVM", "播放模式切换为: ${playMode.value}")
        
        // 如果切换到心动模式，自动初始化心动模式播放列表
        if (nextMode == PlaybackMode.HEARTBEAT) {
            initializeHeartbeatMode()
        }
    }
    
    /** 直接设置播放模式 */
    fun setPlayMode(mode: PlaybackMode) {
        if (playMode.value == mode) return
        playMode.value = mode
        Log.d("MusicVM", "播放模式设置为: $mode")
        if (mode == PlaybackMode.HEARTBEAT) {
            initializeHeartbeatMode()
        }
    }
    
    /**
     * 心动模式 - 从多个来源按比例随机获取歌曲
     * 比例：今日推荐(30%) : 个性化推荐(50%) : 用户曲库(20%)
     */
    private fun initializeHeartbeatMode() {
        Log.d("MusicVM", "初始化心动模式")
        viewModelScope.launch {
            try {
                // 获取今日推荐歌曲（热歌榜作为今日推荐的代表）
                val todayRecommendSongs = fetchTodayRecommendSongs()
                Log.d("MusicVM", "今日推荐歌曲数: ${todayRecommendSongs.size}")
                
                // 获取个性化推荐歌单的歌曲
                val personalizedSongs = fetchPersonalizedSongs()
                Log.d("MusicVM", "个性化推荐歌曲数: ${personalizedSongs.size}")
                
                // 获取用户曲库中的歌曲
                val librarySongs = fetchLibrarySongs()
                Log.d("MusicVM", "用户曲库歌曲数: ${librarySongs.size}")
                
                // 按比例随机选取歌曲（3:5:2）
                val totalCount = 50 // 总共选取50首歌曲
                val recommendCount = (totalCount * 0.3).toInt() // 15首
                val personalizedCount = (totalCount * 0.5).toInt() // 25首
                val libraryCount = totalCount - recommendCount - personalizedCount // 10首
                
                // 随机选取
                val selectedRecommend = todayRecommendSongs.shuffled().take(recommendCount)
                val selectedPersonalized = personalizedSongs.shuffled().take(personalizedCount)
                val selectedLibrary = librarySongs.shuffled().take(libraryCount)
                
                // 合并并打乱顺序
                val heartbeatQueue = (selectedRecommend + selectedPersonalized + selectedLibrary).shuffled()
                
                Log.d("MusicVM", "心动模式队列构建完成，总歌曲数: ${heartbeatQueue.size}")
                Log.d("MusicVM", "今日推荐选取: ${selectedRecommend.size}, 个性化推荐选取: ${selectedPersonalized.size}, 用户曲库选取: ${selectedLibrary.size}")
                
                // 更新播放队列
                if (heartbeatQueue.isNotEmpty()) {
                    playQueue.clear()
                    playQueue.addAll(heartbeatQueue)
                    currentQueueIndex.intValue = 0
                    
                    // 如果当前正在播放，继续播放新队列的第一首
                    if (isPlaying.value) {
                        val firstSong = heartbeatQueue[0]
                        if (firstSong.source == SongSource.NETEASE) {
                            val songWithUrl = fetchNeteaseSongUrl(firstSong)
                            if (songWithUrl.url.isNotBlank()) {
                                playQueue[0] = songWithUrl
                                startPlaying(songWithUrl, playQueue)
                            }
                        } else {
                            startPlaying(firstSong, playQueue)
                        }
                    }
                    
                    toastMessage.value = "心动模式已开启，共${heartbeatQueue.size}首歌曲"
                } else {
                    toastMessage.value = "暂无足够歌曲，无法开启心动模式"
                    // 切换回顺序播放模式
                    playMode.value = PlaybackMode.SEQUENCE
                }
            } catch (e: Exception) {
                Log.e("MusicVM", "初始化心动模式失败", e)
                toastMessage.value = "开启心动模式失败: ${e.message}"
                // 切换回顺序播放模式
                playMode.value = PlaybackMode.SEQUENCE
            }
        }
    }
    
    /**
     * 获取今日推荐歌曲（使用热歌榜作为今日推荐）
     */
    private suspend fun fetchTodayRecommendSongs(): List<Song> {
        return try {
            // 使用热歌榜作为今日推荐
            PlaylistSyncManager.fetchPlaylist("3778678") ?: emptyList()
        } catch (e: Exception) {
            Log.e("MusicVM", "获取今日推荐失败", e)
            emptyList()
        }
    }
    
    /**
     * 获取个性化推荐歌单的歌曲
     */
    private suspend fun fetchPersonalizedSongs(): List<Song> {
        val allSongs = mutableListOf<Song>()
        try {
            // 获取推荐歌单列表
            val playlists = NeteaseApiService.getRecommendedPlaylists(5) // 获取5个推荐歌单
            
            for (playlist in playlists) {
                try {
                    val songs = PlaylistSyncManager.fetchPlaylist(playlist.id)
                    if (songs != null && songs.isNotEmpty()) {
                        allSongs.addAll(songs)
                    }
                } catch (e: Exception) {
                    Log.e("MusicVM", "获取歌单 ${playlist.name} 失败", e)
                }
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "获取个性化推荐失败", e)
        }
        return allSongs.distinctBy { it.id } // 去重
    }
    
    /**
     * 获取用户曲库中的歌曲
     */
    private suspend fun fetchLibrarySongs(): List<Song> {
        val context = getApplication<Application>()
        val songs = mutableListOf<Song>()
        
        try {
            // 获取用户收藏的歌曲
            val favoritesPlaylist = PlaylistDataStore.getFavoritesPlaylist(context)
            songs.addAll(favoritesPlaylist.songs)
            
            // 获取用户所有歌单中的歌曲
            val playlists = PlaylistDataStore.getAll(context)
            for (playlist in playlists) {
                if (!playlist.id.startsWith("bili_") && playlist.id != "jianyin_favorites_playlist") { // 排除B站歌单和收藏歌单（已单独处理）
                    songs.addAll(playlist.songs)
                }
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "获取用户曲库失败", e)
        }
        
        return songs.distinctBy { it.id } // 去重
    }
    
    // 切换进度条样式
    fun setProgressBarStyle(style: ProgressBarStyle) {
        progressBarStyle.value = style
        saveProgressBarStyle(style)
        Log.d("MusicVM", "进度条样式切换为: ${style}")
    }

    // 保存进度条样式到SharedPreferences
    private fun saveProgressBarStyle(style: ProgressBarStyle) {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("progress_bar_style", style.name).apply()
    }

    // 从SharedPreferences加载进度条样式
    private fun loadProgressBarStyle() {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
        val savedStyle = sharedPreferences.getString("progress_bar_style", ProgressBarStyle.DEFAULT.name)
        val style = ProgressBarStyle.valueOf(savedStyle ?: ProgressBarStyle.DEFAULT.name)
        progressBarStyle.value = style
        Log.d("MusicVM", "加载进度条样式: ${style}")
    }
    
    // --- 播放速度控制 ---
    
    /**
     * 设置播放速度
     * @param speed 播放速度，范围 0.25f 到 4.0f（Media3限制）
     */
    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.25f, 4.0f)
        val roundedSpeed = (clampedSpeed * 10).toInt().toFloat() / 10.0f
        playbackSpeed.value = roundedSpeed
        player.setPlaybackSpeed(roundedSpeed)
        savePlaybackSpeed(roundedSpeed)
        Log.d("MusicVM", "设置播放速度: ${roundedSpeed}x")
    }
    
    /**
     * 获取当前播放速度
     */
    fun getPlaybackSpeed(): Float {
        return playbackSpeed.value
    }
    
    /**
     * 保存播放速度到SharedPreferences
     */
    private fun savePlaybackSpeed(speed: Float) {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putFloat("playback_speed", speed).apply()
    }
    
    /**
     * 从SharedPreferences加载播放速度
     */
    private fun loadPlaybackSpeed() {
        val sharedPreferences = getApplication<Application>().getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
        val savedSpeed = sharedPreferences.getFloat("playback_speed", 1.0f)
        playbackSpeed.value = savedSpeed
        player.setPlaybackSpeed(savedSpeed)
        Log.d("MusicVM", "加载播放速度: ${savedSpeed}x")
    }
    
        /**
     * 切换当前歌曲的收藏状态
     */
    fun toggleFavoriteCurrentSong() {
        val song = currentSong.value ?: return
        val context = getApplication<Application>()
        
        if (PlaylistDataStore.isSongInFavorites(context, song)) {
            // 如果已经在收藏中，则移除
            PlaylistDataStore.removeFromFavorites(context, song)
            isCurrentSongFavorited.value = false
            Log.d("MusicVM", "已从收藏移除: ${song.name}")
        } else {
            // 如果不在收藏中，则添加
            PlaylistDataStore.addToFavorites(context, song)
            isCurrentSongFavorited.value = true
            Log.d("MusicVM", "已添加到收藏: ${song.name}")
        }
    }
    
    /**
     * 检查当前歌曲是否在收藏中
     */
    private fun checkIfCurrentSongIsFavorited() {
        val song = currentSong.value ?: return
        val context = getApplication<Application>()
        isCurrentSongFavorited.value = PlaylistDataStore.isSongInFavorites(context, song)
    }


    fun executeSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            isSearching.value = true
            try {
                val results = NeteaseApiService.searchSongs(query)
                searchResults.clear()
                results.forEach { neteaseSong ->
                    searchResults.add(Song(
                        id = neteaseSong.id,
                        name = neteaseSong.name,
                        artist = neteaseSong.artist,
                        url = "",
                        pic = neteaseSong.picUrl,
                        source = SongSource.NETEASE
                    ))
                }
                if (!searchHistory.contains(query)) {
                    searchHistory.add(0, query)
                    saveSearchHistory()
                }
            } catch (e: Exception) {
                Log.e("MusicVM", "网易云搜索失败", e)
            } finally {
                isSearching.value = false
            }
        }
    }

    fun executeNeteaseSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            isSearching.value = true
            try {
                val results = NeteaseApiService.searchSongs(query)
                searchResults.clear()
                results.forEach { neteaseSong ->
                    searchResults.add(Song(
                        id = neteaseSong.id,
                        name = neteaseSong.name,
                        artist = neteaseSong.artist,
                        url = "",
                        pic = neteaseSong.picUrl,
                        source = SongSource.NETEASE
                    ))
                }
                if (!searchHistory.contains(query)) {
                    searchHistory.add(0, query)
                    saveSearchHistory()
                }
            } catch (e: Exception) {
                Log.e("MusicVM", "网易云搜索失败", e)
            } finally {
                isSearching.value = false
            }
        }
    }

    fun searchWithoutHistory(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            isSearching.value = true
            try {
                val results = NeteaseApiService.searchSongs(query)
                searchResults.clear()
                results.forEach { neteaseSong ->
                    searchResults.add(Song(
                        id = neteaseSong.id,
                        name = neteaseSong.name,
                        artist = neteaseSong.artist,
                        url = "",
                        pic = neteaseSong.picUrl,
                        source = SongSource.NETEASE
                    ))
                }
            } catch (e: Exception) {
                Log.e("MusicVM", "网易云搜索失败", e)
            } finally {
                isSearching.value = false
            }
        }
    }
    
    fun clearSearchResults() {
        searchJob?.cancel()
        searchResults.clear()
        isSearching.value = false
    }

    private suspend fun fetchNeteaseSongUrl(song: Song): Song {
        if (song.source != SongSource.NETEASE) {
            if (song.url.isNotBlank()) return song
            return song
        }

        return try {
            val context = getApplication<Application>()
            
            // 优先检查本地缓存
            val cachedMp3Path = CacheManager.getCachedMp3Path(context, song)
            if (cachedMp3Path != null) {
                Log.d("MusicVM", "fetchNeteaseSongUrl: 使用缓存文件: ${song.name}")
                return song.copy(url = "file://$cachedMp3Path")
            }
            
            // 从网络获取URL
            val qualityLevel = DownloadSettingsStore.getPlayQuality(context)
            Log.d("MusicVM", "fetchNeteaseSongUrl: 开始获取URL，songId=${song.id}, quality=$qualityLevel")
            val url = NeteaseApiService.getSongUrl(song.id, qualityLevel)
            Log.d("MusicVM", "fetchNeteaseSongUrl: 获取结果，url=${url ?: "null"}")
            if (url != null) {
                Log.d("MusicVM", "fetchNeteaseSongUrl: 成功获取URL: ${song.name}")
                song.copy(url = url)
            } else {
                Log.e("MusicVM", "fetchNeteaseSongUrl: 获取URL失败，返回null: ${song.name}")
                song
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "fetchNeteaseSongUrl: 获取网易云歌曲URL异常: ${song.id}", e)
            song
        }
    }

    /** 获取网易云歌词，返回 Pair(原词, 翻译词) */
    suspend fun fetchNeteaseLyric(song: Song): Pair<String?, String?> {
        if (song.source != SongSource.NETEASE) return null to null
        return try {
            val response = NeteaseApiService.getLyricRaw(song.id)
            val root = JSONObject(response)
            val yrc = root.optJSONObject("yrc")?.optString("lyric").orEmpty()
            val lrc = root.optJSONObject("lrc")?.optString("lyric").orEmpty()
            val preferred = yrc.ifBlank { lrc.ifBlank { null } }
            val translated = root.optJSONObject("ytlrc")?.optString("lyric")
                ?: root.optJSONObject("tlyric")?.optString("lyric")
                ?: ""
            preferred to translated.ifBlank { null }
        } catch (e: Exception) {
            Log.e("MusicVM", "获取网易云歌词失败: ${song.id}", e)
            null to null
        }
    }

    /** 获取歌词内容用于缓存（支持多种来源） */
    private suspend fun fetchLyricsForCache(song: Song): String? {
        return try {
            if (song.source == SongSource.NETEASE) {
                // 网易云歌曲，获取双语歌词
                val (neteaseLrc, neteaseTrans) = fetchNeteaseLyric(song)
                if (neteaseLrc != null && neteaseTrans != null) {
                    "$neteaseLrc\n[TRANSLATED]\n$neteaseTrans"
                } else {
                    neteaseLrc ?: ""
                }
            } else if (!song.lrc.isNullOrEmpty()) {
                // 其他来源，从网络获取歌词
                if (song.lrc.startsWith("http")) {
                    api.getLrcByUrl(song.lrc)
                } else {
                    api.getLrcById(id = song.id)
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "获取歌词用于缓存失败: ${song.name}", e)
            null
        }
    }

    fun playNeteaseSong(song: Song, newQueue: List<Song>? = null) {
        Log.d("MusicVM", "playNeteaseSong 被调用: ${song.name}")
        viewModelScope.launch {
            try {
                val songWithUrl = fetchNeteaseSongUrl(song)
                if (songWithUrl.url.isBlank()) {
                    Log.e("MusicVM", "获取网易云歌曲URL失败，歌曲: ${song.name}")
                    toastMessage.value = "无法获取播放链接，请先登录网易云"
                    return@launch
                }
                // 若未传入新队列，将已获取 URL 的歌曲回写到当前队列
                if (newQueue == null) {
                    val idx = playQueue.indexOfFirst { isSameSong(it, songWithUrl) }
                    if (idx != -1) {
                        playQueue[idx] = songWithUrl
                    }
                }
                playSong(songWithUrl, newQueue)
            } catch (e: Exception) {
                Log.e("MusicVM", "播放网易云歌曲失败", e)
                toastMessage.value = "播放失败: ${e.message}"
            }
        }
    }
    
    
    private fun isSameSong(a: Song, b: Song): Boolean {
        // 优先比较ID（最可靠的标识）
        if (a.id.isNotBlank() && b.id.isNotBlank() && a.id == b.id) {
            return true
        }
        // 如果ID不可用或不匹配，比较音乐文件的URL（通常是唯一的）
        if (a.url.isNotBlank() && b.url.isNotBlank() && a.url == b.url) {
            return true
        }
        return false
    }
    
    fun playSong(song: Song, newQueue: List<Song>? = null) {
        Log.d("MusicVM", "playSong 被调用: ${song.name}, 来源队列大小=${newQueue?.size ?: "无"}")
        
        if (newQueue != null && newQueue.isNotEmpty()) {
            playQueue.clear()
            playQueue.addAll(newQueue)
            
            val index = newQueue.indexOfFirst { isSameSong(it, song) }
            currentQueueIndex.intValue = if (index != -1) index else 0
            
            if (index != -1) {
                playQueue[index] = song
            }
            val songToPlay = playQueue[currentQueueIndex.intValue]
            Log.d("MusicVM", "列表播放模式。队列大小=${playQueue.size}, 目标索引=$index, 即将播放: ${songToPlay.name}, url=${songToPlay.url}")
            
            startPlaying(songToPlay, newQueue)
        } else {
            val isSongInQueue = playQueue.any { isSameSong(it, song) }
            
            if (playQueue.isEmpty() || !isSongInQueue) {
                // 队列为空 或 歌曲不在队列中 -> 创建新队列（单曲播放）
                playQueue.clear()
                playQueue.add(song)
                currentQueueIndex.intValue = 0
                Log.d("MusicVM", "单曲播放模式。创建新队列，播放: ${song.name}")
                
                startPlaying(song, listOf(song))
            } else {
                // 歌曲已在队列中 -> 定位并播放
                val index = playQueue.indexOfFirst { isSameSong(it, song) }
                if (index != -1) {
                    currentQueueIndex.intValue = index
                    val songToPlay = playQueue[currentQueueIndex.intValue]
                    Log.d("MusicVM", "从现有队列中定位。索引=$index, 播放: ${songToPlay.name}")
                    
                    startPlaying(songToPlay, playQueue)
                } else {
                    // 安全回退
                    Log.e("MusicVM", "错误：匹配逻辑不一致。将歌曲添加到队列末尾。")
                    playQueue.add(song)
                    currentQueueIndex.intValue = playQueue.size - 1
                    
                    startPlaying(song, playQueue)
                }
            }
        }
        // 打印当前队列状态（debug）
        printQueueStatus()
    }

    fun playExternalAudio(uri: android.net.Uri) {
        Log.d("MusicVM", "playExternalAudio: $uri")
        Log.d("MusicVM", "URI scheme: ${uri.scheme}")
        Log.d("MusicVM", "URI path: ${uri.path}")
        
        val song = Song(
            id = "external_${System.currentTimeMillis()}",
            name = uri.lastPathSegment ?: "外部音频",
            artist = "未知歌手",
            url = uri.toString(),
            isLocal = true
        )
        Log.d("MusicVM", "创建外部歌曲: ${song.name}, isLocal=${song.isLocal}, url=${song.url}")
        playSong(song, listOf(song))
    }
    
    private fun startPlaying(song: Song, sourceList: List<Song>? = null) {
        Log.d("MusicVM", "startPlaying: ${song.name}, sourceList大小=${sourceList?.size ?: "无"}")
        
        // 请求音频焦点
        if (audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        ) {
            Log.e("MusicVM", "无法获取音频焦点，播放中止")
            return
        }
        
        // 更新当前歌曲和索引状态
        currentSong.value = song
        currentLrc.clear()
        currentTranslatedLrc.clear()
        currentLineIndex.intValue = 0
        currentPosition.longValue = 0L
        totalDuration.longValue = 0L

        // 专辑封面取色（仅当主题源为"专辑封面"时生效）
        if (song.pic.isNotBlank() && DownloadSettingsStore.themeSourceFlow.value == 2) {
            viewModelScope.launch {
                val color = ThemeColorUtil.extractFromUrl(getApplication(), song.pic)
                if (color != null) {
                    DownloadSettingsStore.setCoverColor(color)
                    DownloadSettingsStore.setSeedColor(getApplication(), color)
                }
            }
        }

        // 记录来源列表和索引
        if (sourceList != null) {
            currentPlayingList.clear()
            currentPlayingList.addAll(sourceList)
            val indexInSource = sourceList.indexOfFirst { isSameSong(it, song) }
            currentPlayingListIndex.intValue = if (indexInSource != -1) indexInSource else 0
            Log.d("MusicVM", "已记录来源列表，大小=${currentPlayingList.size}, 歌曲索引=${currentPlayingListIndex.intValue}")
        } else {
            val indexInQueue = playQueue.indexOfFirst { isSameSong(it, song) }
            currentPlayingListIndex.intValue = if (indexInQueue != -1) indexInQueue else 0
            currentPlayingList.clear()
            currentPlayingList.addAll(playQueue)
        }
        
        // 历史记录处理（按 song.id 去重，避免因 data class equals 含 url 导致重复）
        historyList.removeAll { isSameSong(it, song) }
        historyList.add(song)
        if (historyList.size > 50) {
            historyList.removeAt(0)
        }
        saveHistory()
        
        // 保存播放状态（用于恢复）
        updatePlaybackStateOnPlay()
        
        // 记录播放次数
        val statsManager = MusicStatsManager(getApplication())
        val songKey = song.id.ifBlank { song.url }
        statsManager.recordPlay(songKey)

        // 记录用户统计：今日播放数、常听时段
        val userStats = UserStatsManager(getApplication())
        userStats.recordPlayToday()
        userStats.recordPlayHour()

        // 自动缓存逻辑：播放超过3次且未下载过则自动下载
        checkAutoCache(song)

        //配置并准备播放器
        try {
            val context = getApplication<Application>()
            var localSongPath: String? = null
            var localCoverPath: String? = null
            
            if (song.isLocal) {
                // 本地歌曲，直接使用歌曲的url作为路径
                localSongPath = song.url
                Log.d("MusicVM", "本地歌曲，使用URL作为路径: $localSongPath")
            } else {
                // 网络歌曲，优先检查cache目录
                val cachedMp3Path = CacheManager.getCachedMp3Path(context, song)
                val cachedCoverPath = CacheManager.getCachedCoverPath(context, song)
                
                if (cachedMp3Path != null) {
                    // 使用cache目录中的缓存文件
                    localSongPath = cachedMp3Path
                    localCoverPath = cachedCoverPath
                    Log.d("MusicVM", "使用缓存文件播放: $localSongPath")
                }
            }
            
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(song.name)
                .setArtist(song.artist)
                .setArtworkUri(if (localCoverPath != null) {
                    if (localCoverPath.startsWith("content://") || localCoverPath.startsWith("file://")) {
                        Uri.parse(localCoverPath)
                    } else {
                        Uri.fromFile(File(localCoverPath))
                    }
                } else Uri.parse(song.pic))
                .build()
            
            // 处理播放音质
            var finalUrl = song.url
            
            // 处理B站视频
            if (song.isBiliVideo) {
                viewModelScope.launch(Dispatchers.IO) {
                    // 首先检查本地是否有下载的文件
                    if (localSongPath != null) {
                        // 使用本地文件播放
                        withContext(Dispatchers.Main) {
                            val mediaUri = if (localSongPath.startsWith("content://") || localSongPath.startsWith("file://")) {
                                Uri.parse(localSongPath)
                            } else {
                                Uri.fromFile(File(localSongPath))
                            }
                            val mediaItem = MediaItem.Builder()
                                .setUri(mediaUri)
                                .setMediaMetadata(mediaMetadata)
                                .build()

                            player.setMediaItem(mediaItem)
                            player.prepare()
                            player.play()
                            applyFadeIn()
                        }
                        
                        // 更新播放状态
                        withContext(Dispatchers.Main) {
                            isPlaying.value = true
                            // 更新媒体会话
                            mediaSessionManager.updateMetadata(
                                title = song.name,
                                artist = song.artist,
                                album = "专辑",
                                duration = 0L, // 初始为0，播放器准备好后自动更新
                                artworkUrl = localCoverPath ?: song.pic
                            )
                            mediaSessionManager.updatePlaybackState(true, 0L)
                        }
                        
                        Log.d("MusicVM", "使用本地文件播放B站视频: ${song.name}")
                    } else {
                        // 从网络获取音频流
                        try {
                            val streamInfo = biliApi.getBestAudioStream(song.bvid, song.cid)
                            if (streamInfo != null && streamInfo.url.isNotEmpty()) {
                                finalUrl = streamInfo.url
                                // 重新创建mediaItem并播放 - 必须在主线程执行
                                withContext(Dispatchers.Main) {
                                    // Headers已通过BiliPlayerHelper添加
                                    val mediaItem = MediaItem.Builder()
                                        .setUri(Uri.parse(finalUrl))
                                        .setMediaMetadata(mediaMetadata)
                                        .build()

                                    player.setMediaItem(mediaItem)
                                    player.prepare()
                                    player.play()
                                    applyFadeIn()
                                }
                                
                                // 更新播放状态
                                withContext(Dispatchers.Main) {
                                    isPlaying.value = true
                                    // 更新媒体会话
                                    mediaSessionManager.updateMetadata(
                                        title = song.name,
                                        artist = song.artist,
                                        album = "专辑",
                                        duration = 0L, // 初始为0，播放器准备好后自动更新
                                        artworkUrl = localCoverPath ?: song.pic
                                    )
                                    mediaSessionManager.updatePlaybackState(true, 0L)
                                }
                                
                                Log.d("MusicVM", "B站视频音频流获取成功: ${song.name}")
                                
                                // 自动缓存：播放超过3次才缓存
                                val statsManager = MusicStatsManager(getApplication())
                                val playCount = statsManager.getPlayCountMap()[song.id.ifBlank { song.url }] ?: 0
                                if (!CacheManager.isCached(getApplication(), song) && playCount > 3) {
                                    viewModelScope.launch(Dispatchers.IO) {
                                        try {
                                            Log.d("MusicVM", "开始自动缓存B站视频: ${song.name}, 播放次数: $playCount")
                                            CacheManager.cacheSong(
                                                getApplication(),
                                                song,
                                                mp3Url = finalUrl,
                                                coverUrl = song.pic,
                                                lrcContent = null
                                            )
                                            Log.d("MusicVM", "自动缓存B站视频完成: ${song.name}")
                                        } catch (e: Exception) {
                                            Log.e("MusicVM", "自动缓存B站视频失败: ${song.name}", e)
                                        }
                                    }
                                }
                            } else {
                                Log.e("MusicVM", "B站视频音频流获取失败")
                            }
                        } catch (e: Exception) {
                            Log.e("MusicVM", "获取B站音频流错误", e)
                        }
                    }
                }
            }
            
            if (!song.isBiliVideo) {
                Log.d("MusicVM", "非B站视频，准备播放: ${song.name}")
                // 网易云歌曲已在 fetchNeteaseSongUrl 中按音质获取URL，无需额外处理
                finalUrl = if (!song.isLocal && localSongPath == null && song.source != SongSource.NETEASE) {
                    val playQuality = DownloadSettingsStore.getPlayQuality(context)
                    val qualityBitrate = DownloadSettingsStore.netEaseQualityToBitrate(playQuality)
                    if (qualityBitrate != 320000) {
                        Log.d("MusicVM", "非本地文件，添加音质参数: $playQuality ($qualityBitrate)")
                        if (song.url.contains("?")) {
                            "${song.url}&br=$qualityBitrate"
                        } else {
                            "${song.url}?br=$qualityBitrate"
                        }
                    } else {
                        song.url
                    }
                } else {
                    song.url
                }
                
                Log.d("MusicVM", "最终播放URL: $finalUrl")
                Log.d("MusicVM", "localSongPath: $localSongPath")
                
                val mediaUri = if (localSongPath != null) {
                    if (localSongPath.startsWith("content://") || localSongPath.startsWith("file://")) {
                        Log.d("MusicVM", "使用URI解析: $localSongPath")
                        Uri.parse(localSongPath)
                    } else {
                        Log.d("MusicVM", "使用文件路径: $localSongPath")
                        Uri.fromFile(File(localSongPath))
                    }
                } else {
                    Log.d("MusicVM", "使用finalUrl: $finalUrl")
                    Uri.parse(finalUrl)
                }
                
                Log.d("MusicVM", "mediaUri: $mediaUri")
                
                try {
                    val mediaItem = MediaItem.Builder()
                        .setUri(mediaUri)
                        .setMediaMetadata(mediaMetadata)
                        .build()

                    Log.d("MusicVM", "设置媒体项并准备播放")
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                    
                    isPlaying.value = true
                    applyFadeIn()
                    
                    Log.d("MusicVM", "播放成功启动: ${song.name}")
                } catch (e: Exception) {
                    Log.e("MusicVM", "播放失败: ${song.name}", e)
                }
                
                //更新媒体会话
                mediaSessionManager.updateMetadata(
                    title = song.name,
                    artist = song.artist,
                    album = "专辑",
                    duration = 0L, // 初始为0，播放器准备好后自动更新
                    artworkUrl = localCoverPath ?: song.pic
                )
                mediaSessionManager.updatePlaybackState(true, 0L)
                
                Log.d("MusicVM", "播放器已开始准备: ${song.name}")
                
                // 自动缓存：播放超过3次才缓存
                val statsManager = MusicStatsManager(getApplication())
                val playCount = statsManager.getPlayCountMap()[song.id.ifBlank { song.url }] ?: 0
                if (!song.isLocal && localSongPath == null && !CacheManager.isCached(getApplication(), song) && playCount > 3) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            Log.d("MusicVM", "开始自动缓存: ${song.name}, 播放次数: $playCount")
                            // 获取歌词内容（用于缓存）
                            val lrcContent = fetchLyricsForCache(song)
                            
                            CacheManager.cacheSong(
                                getApplication(),
                                song,
                                mp3Url = finalUrl,
                                coverUrl = song.pic,
                                lrcContent = lrcContent
                            )
                            Log.d("MusicVM", "自动缓存完成: ${song.name}")
                        } catch (e: Exception) {
                            Log.e("MusicVM", "自动缓存失败: ${song.name}", e)
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("MusicVM", "播放初始化错误", e)
            audioManager.abandonAudioFocus(audioFocusChangeListener)
            isPlaying.value = false
        }

        // 异步加载歌词
        viewModelScope.launch {
            try {
                var lrcContent = ""
                var translatedLrcContent: String? = null
                
                if (song.isLocal) {
                    // 本地歌曲，优先使用自定义歌词
                    val customLyrics = SongCustomDataStore.getLyrics(getApplication(), song.url)
                    if (customLyrics.isNotEmpty()) {
                        lrcContent = customLyrics
                    } else {
                        val lyricSource = DownloadSettingsStore.getLyricSource(getApplication())
                        if (lyricSource == 0) {
                            // 内嵌歌词
                            val localMusicManager = LocalMusicManager(getApplication())
                            val lyrics = localMusicManager.extractLyrics(song.url)
                            if (!lyrics.isNullOrEmpty()) {
                                lrcContent = lyrics
                            }
                        } else {
                            // 网络歌词
                            lrcContent = fetchNetworkLyrics(song.name, song.artist)
                        }
                    }
                } else {
                    // 网络歌曲，优先使用自定义歌词
                    val customLyrics = SongCustomDataStore.getLyrics(getApplication(), song.url)
                    if (customLyrics.isNotEmpty()) {
                        lrcContent = customLyrics
                    } else {
                        // 优先从cache目录读取缓存歌词
                        val (cachedLrc, cachedTrans) = CacheManager.readCachedLyricsBilingual(getApplication(), song)
                        if (cachedLrc != null) {
                            lrcContent = cachedLrc
                            translatedLrcContent = cachedTrans
                            Log.d("MusicVM", "使用缓存歌词: ${song.name}, 有翻译=${cachedTrans != null}")
                        } else if (song.source == SongSource.NETEASE) {
                            // 网易云歌曲，使用网易云歌词接口
                            val (neteaseLrc, neteaseTrans) = fetchNeteaseLyric(song)
                            lrcContent = neteaseLrc ?: ""
                            translatedLrcContent = neteaseTrans
                        } else {
                            // 从网络获取歌词
                            lrcContent = if (!song.lrc.isNullOrEmpty()) {
                                if (song.lrc.startsWith("http")) api.getLrcByUrl(song.lrc)
                                else api.getLrcById(id = song.id)
                            } else ""
                        }
                    }
                }
                
                currentLrc.clear()
                currentTranslatedLrc.clear()
                if (lrcContent.isNotEmpty()) {
                    currentLrc.addAll(parseLyricAuto(lrcContent))
                } else {
                    currentLrc.add(LyricEntry(0, 5000, "暂无歌词"))
                }
                if (!translatedLrcContent.isNullOrBlank()) {
                    currentTranslatedLrc.addAll(parseLyricAuto(translatedLrcContent))
                }
            } catch (e: Exception) {
                currentLrc.clear()
                currentTranslatedLrc.clear()
                currentLrc.add(LyricEntry(0, 5000, "暂无歌词"))
            }
        }
        
        // 应用自定义封面
        viewModelScope.launch {
            val customCover = SongCustomDataStore.getCover(getApplication(), song.url)
            if (customCover.isNotEmpty()) {
                val updatedSong = song.copy(pic = customCover)
                currentSong.value = updatedSong
                // 更新播放队列中的歌曲（使用 isSameSong 作为匹配逻辑）
                val index = playQueue.indexOfFirst { isSameSong(it, song) }
                if (index != -1) {
                    playQueue[index] = updatedSong
                }
                // 更新当前播放列表中的歌曲
                val playingIndex = currentPlayingList.indexOfFirst { isSameSong(it, song) }
                if (playingIndex != -1) {
                    currentPlayingList[playingIndex] = updatedSong
                }
                // 更新历史记录中的歌曲
                val historyIndex = historyList.indexOfFirst { isSameSong(it, song) }
                if (historyIndex != -1) {
                    historyList[historyIndex] = updatedSong
                }
                // 更新媒体会话的封面
                mediaSessionManager.updateMetadata(
                    title = updatedSong.name,
                    artist = updatedSong.artist,
                    album = "未知专辑",
                    duration = totalDuration.longValue,
                    artworkUrl = customCover
                )
            }
        }
        
        checkIfCurrentSongIsFavorited()
    }
    
    fun nextSong() {
        if (playQueue.isEmpty()) {
            Log.d("MusicVM", "队列为空，无法下一首")
            return
        }
        
        if (currentQueueIndex.intValue < 0 || currentQueueIndex.intValue >= playQueue.size) {
            Log.w("MusicVM", "当前索引无效，重置为0")
            currentQueueIndex.intValue = 0
        }
        
        val nextSong = when (playMode.value) {
            PlaybackMode.SINGLE -> {
                Log.d("MusicVM", "单曲循环模式，继续播放: ${playQueue[currentQueueIndex.intValue].name}")
                playQueue[currentQueueIndex.intValue]
            }
            PlaybackMode.RANDOM, PlaybackMode.HEARTBEAT -> {
                if (playQueue.size == 1) {
                    playQueue.first()
                } else {
                    var randomIndex: Int
                    do {
                        randomIndex = (0 until playQueue.size).random()
                    } while (randomIndex == currentQueueIndex.intValue)
                    
                    currentQueueIndex.intValue = randomIndex
                    Log.d("MusicVM", "${if (playMode.value == PlaybackMode.HEARTBEAT) "心动" else "随机"}播放模式，随机到: ${playQueue[randomIndex].name}")
                    playQueue[randomIndex]
                }
            }
            PlaybackMode.SEQUENCE, PlaybackMode.CONTINUOUS -> {
                val nextIndex = (currentQueueIndex.intValue + 1) % playQueue.size
                currentQueueIndex.intValue = nextIndex
                Log.d("MusicVM", "顺序播放模式，下一首索引: $nextIndex")
                playQueue[nextIndex]
            }
        }

        // 持续播放模式：当当前歌曲是歌单最后一首时，处理歌单切换
        if (playMode.value == PlaybackMode.CONTINUOUS && playlistQueue.isNotEmpty()) {
            val currentIndex = playQueue.indexOfFirst { isSameSong(it, currentSong.value ?: return@nextSong) }
            if (currentIndex == playQueue.size - 1) {
                // 当前歌曲是歌单最后一首，尝试切换到下一个歌单
                viewModelScope.launch {
                    handleContinuousModePlaylistSwitch()
                }
                // 如果切换成功，handleContinuousModePlaylistSwitch 已经调用了 startPlaying，直接返回
                return
            }
        }
        
        // 检查下一首是否是网易云歌曲且需要获取URL
        if (nextSong.source == SongSource.NETEASE) {
            Log.d("MusicVM", "nextSong: 网易云歌曲需要获取URL，歌曲: ${nextSong.name}")
            viewModelScope.launch {
                val songWithUrl = fetchNeteaseSongUrl(nextSong)
                if (songWithUrl.url.isBlank()) {
                    Log.e("MusicVM", "nextSong: 获取网易云歌曲URL失败，歌曲: ${nextSong.name}")
                    toastMessage.value = "无法获取播放链接，请先登录网易云"
                    return@launch
                }
                // 更新队列中的歌曲URL
                playQueue[currentQueueIndex.intValue] = songWithUrl
                applyFadeOut {
                    startPlaying(songWithUrl, playQueue)
                }
                Log.d("MusicVM", "下一首: ${songWithUrl.name}, 索引: $currentQueueIndex")
            }
        } else {
            applyFadeOut {
                startPlaying(nextSong, playQueue)
            }
            Log.d("MusicVM", "下一首: ${nextSong.name}, 索引: $currentQueueIndex")
        }
    }
    
    private suspend fun handleContinuousModePlaylistSwitch() {
        if (playlistQueue.isEmpty()) {
            Log.d("MusicVM", "持续播放模式：歌单队列为空，停止播放")
            player.pause()
            isPlaying.value = false
            return
        }
        
        val nextPlaylistIndex = currentPlaylistIndex.intValue + 1
        if (nextPlaylistIndex >= playlistQueue.size) {
            Log.d("MusicVM", "持续播放模式：所有歌单已播放完毕，停止播放")
            player.pause()
            isPlaying.value = false
            return
        }
        
        // 切换到下一个歌单
        val nextPlaylist = playlistQueue[nextPlaylistIndex]
        currentPlaylistIndex.intValue = nextPlaylistIndex
        playQueue.clear()
        playQueue.addAll(nextPlaylist.songs)
        currentQueueIndex.intValue = 0
        
        // 开始播放新歌单的第一首歌
        if (nextPlaylist.songs.isNotEmpty()) {
            val firstSong = nextPlaylist.songs[0]
            if (firstSong.source == SongSource.NETEASE) {
                val songWithUrl = fetchNeteaseSongUrl(firstSong)
                if (songWithUrl.url.isNotBlank()) {
                    playQueue[0] = songWithUrl
                    startPlaying(songWithUrl, playQueue)
                } else {
                    toastMessage.value = "无法获取播放链接，请先登录网易云"
                }
            } else {
                startPlaying(firstSong, nextPlaylist.songs)
            }
        }
        
        Log.d("MusicVM", "持续播放模式：切换到下一个歌单: ${nextPlaylist.name}")
    }

    // 设置歌词内容
    fun setLyrics(lrcContent: String) {
        viewModelScope.launch {
            currentLrc.clear()
            currentTranslatedLrc.clear()
            if (lrcContent.isNotEmpty()) {
                currentLrc.addAll(parseLyricAuto(lrcContent))
            } else {
                currentLrc.add(LyricEntry(0, 5000, "暂无歌词"))
            }
        }
    }

    // 设置封面
    fun setCover(coverUri: String) {
        viewModelScope.launch {
            currentSong.value?.let {song ->
                val updatedSong = song.copy(pic = coverUri)
                currentSong.value = updatedSong
                // 更新播放队列中的歌曲（使用 url 作为唯一标识）
                val index = playQueue.indexOfFirst { it.url == song.url }
                if (index != -1) {
                    playQueue[index] = updatedSong
                }
                // 更新当前播放列表中的歌曲
                val playingIndex = currentPlayingList.indexOfFirst { it.url == song.url }
                if (playingIndex != -1) {
                    currentPlayingList[playingIndex] = updatedSong
                }
                // 更新历史记录中的歌曲
                val historyIndex = historyList.indexOfFirst { it.url == song.url }
                if (historyIndex != -1) {
                    historyList[historyIndex] = updatedSong
                }
                // 更新媒体会话的封面
                mediaSessionManager.updateMetadata(
                    title = updatedSong.name,
                    artist = updatedSong.artist,
                    album = "未知专辑",
                    duration = totalDuration.longValue,
                    artworkUrl = coverUri
                )
                // 触发歌单更新，通知 UI 刷新
                playlistUpdateTrigger.intValue++
            }
        }
    }

    fun previousSong() {
        if (playQueue.isEmpty()) {
            Log.d("MusicVM", "队列为空，无法上一首")
            return
        }
        
        if (currentQueueIndex.intValue < 0 || currentQueueIndex.intValue >= playQueue.size) {
            Log.w("MusicVM", "当前索引无效，重置为最后一项")
            currentQueueIndex.intValue = playQueue.size - 1
        }
        
        val prevSong = when (playMode.value) {
            PlaybackMode.SINGLE -> {
                // 单曲循环：播放同一首
                Log.d("MusicVM", "单曲循环模式，继续播放: ${playQueue[currentQueueIndex.intValue].name}")
                playQueue[currentQueueIndex.intValue]
            }
            PlaybackMode.RANDOM, PlaybackMode.HEARTBEAT -> {
                // 随机播放 / 心动模式
                if (playQueue.size == 1) {
                    playQueue.first()
                } else {
                    var randomIndex: Int
                    do {
                        randomIndex = (0 until playQueue.size).random()
                    } while (randomIndex == currentQueueIndex.intValue) // 避免和当前歌曲相同
                    
                    currentQueueIndex.intValue = randomIndex
                    Log.d("MusicVM", "${if (playMode.value == PlaybackMode.HEARTBEAT) "心动" else "随机"}播放模式，随机到: ${playQueue[randomIndex].name}")
                    playQueue[randomIndex]
                }
            }
            PlaybackMode.SEQUENCE, PlaybackMode.CONTINUOUS -> {
                // 顺序播放：计算上一首索引
                val prevIndex = if (currentQueueIndex.intValue == 0) {
                    playQueue.size - 1  // 如果是第一首，跳转到最后一首
                } else {
                    currentQueueIndex.intValue - 1
                }
                currentQueueIndex.intValue = prevIndex
                Log.d("MusicVM", "顺序播放模式，上一首索引: $prevIndex")
                playQueue[prevIndex]
            }
        }
        
        if (prevSong.source == SongSource.NETEASE) {
            Log.d("MusicVM", "previousSong: 网易云歌曲需要获取URL，歌曲: ${prevSong.name}")
            viewModelScope.launch {
                val songWithUrl = fetchNeteaseSongUrl(prevSong)
                if (songWithUrl.url.isBlank()) {
                    Log.e("MusicVM", "previousSong: 获取网易云歌曲URL失败，歌曲: ${prevSong.name}")
                    toastMessage.value = "无法获取播放链接，请先登录网易云"
                    return@launch
                }
                // 更新队列中的歌曲URL
                playQueue[currentQueueIndex.intValue] = songWithUrl
                applyFadeOut {
                    startPlaying(songWithUrl, playQueue)
                }
                Log.d("MusicVM", "上一首: ${songWithUrl.name}, 索引: $currentQueueIndex")
            }
        } else {
            applyFadeOut {
                startPlaying(prevSong, playQueue)
            }
            Log.d("MusicVM", "上一首: ${prevSong.name}, 索引: $currentQueueIndex")
        }
    }
    
   // 播放控制
fun togglePlay() {
    if (player.isPlaying) {
        applyFadeOut {
            player.pause()
            isPlaying.value = false
            audioManager.abandonAudioFocus(audioFocusChangeListener)
            mediaSessionManager.updatePlaybackState(false, player.currentPosition)
        }
    } else {
        if (audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        ) {
            player.play()
            isPlaying.value = true
            applyFadeIn()
            mediaSessionManager.updatePlaybackState(true, player.currentPosition)
        }
    }
}


    fun seekTo(pos: Long) {
        val newPosition = if (pos < 0) 0L else if (pos > totalDuration.longValue) totalDuration.longValue else pos
        player.seekTo(newPosition)
        currentPosition.longValue = newPosition
        mediaSessionManager.updatePlaybackState(player.isPlaying, newPosition)
    }

    private fun applyFadeIn() {
        val context = getApplication<Application>()
        if (!DownloadSettingsStore.isFadeEnabled(context)) return
        
        fadeJob?.cancel()
        player.volume = 0f
        fadeJob = viewModelScope.launch {
            val steps = (FADE_DURATION_MS / FADE_STEP_MS).toInt()
            val volumeStep = 1f / steps
            repeat(steps) { step ->
                player.volume = (step + 1) * volumeStep
                delay(FADE_STEP_MS)
            }
            player.volume = 1f
        }
    }

    private fun applyFadeOut(onComplete: () -> Unit) {
        val context = getApplication<Application>()
        if (!DownloadSettingsStore.isFadeEnabled(context)) {
            onComplete()
            return
        }
        
        fadeJob?.cancel()
        fadeJob = viewModelScope.launch {
            val steps = (FADE_DURATION_MS / FADE_STEP_MS).toInt()
            val volumeStep = 1f / steps
            repeat(steps) { step ->
                player.volume = 1f - (step + 1) * volumeStep
                delay(FADE_STEP_MS)
            }
            player.volume = 0f
            onComplete()
        }
    }
    
    /**
     * 添加歌曲到播放队列末尾
     */
    fun addToQueue(song: Song) {
        if (!playQueue.any { it.id == song.id }) {
            playQueue.add(song)
            Log.d("MusicVM", "添加到队列: ${song.name}, 队列大小: ${playQueue.size}")
        } else {
            Log.d("MusicVM", "歌曲已在队列中: ${song.name}")
        }
    }
    
    /**
     * 添加歌曲到当前播放歌曲的下一首位置
     */
    fun addNextToQueue(song: Song) {
        Log.d("MusicVM", "addNextToQueue called: ${song.name}, id: ${song.id}, url: ${song.url}")
        if (playQueue.isEmpty()) {
            playQueue.add(song)
            currentQueueIndex.intValue = 0
            Log.d("MusicVM", "队列为空，添加歌曲作为第一首: ${song.name}")
            return
        }

        val insertIndex = (currentQueueIndex.intValue + 1).coerceIn(0, playQueue.size)
        val isDuplicate = playQueue.any { q ->
            (q.id.isNotBlank() && q.id == song.id) || (q.url.isNotBlank() && q.url == song.url)
        }
        if (!isDuplicate) {
            playQueue.add(insertIndex, song)
            Log.d("MusicVM", "添加到当前播放歌曲下一首: ${song.name}, 插入位置: $insertIndex, 队列大小: ${playQueue.size}")
        } else {
            Log.d("MusicVM", "歌曲已在队列中: ${song.name}")
        }
    }
    
    /**
     * 从播放队列移除指定歌曲
     * @param song 要移除的歌曲
     */
    fun removeFromQueue(song: Song) {
        val index = playQueue.indexOfFirst { isSameSong(it, song) }
        if (index == -1) {
            Log.d("MusicVM", "歌曲不在队列中: ${song.name}")
            return
        }
        
        // 如果移除的是当前播放的歌曲
        if (index == currentQueueIndex.intValue) {
            // 如果队列只剩一首歌，停止播放
            if (playQueue.size == 1) {
                playQueue.removeAt(index)
                currentQueueIndex.intValue = -1
                currentSong.value = null
                player.pause()
                isPlaying.value = false
                mediaSessionManager.hideNotification()
                audioManager.abandonAudioFocus(audioFocusChangeListener)
                Log.d("MusicVM", "移除当前播放歌曲（最后一首），停止播放: ${song.name}")
            } else {
                // 播放下一首（移除前先记录下一首）
                val nextIndex = if (index < playQueue.size - 1) index else 0
                playQueue.removeAt(index)
                // 调整索引
                currentQueueIndex.intValue = if (index < playQueue.size) index else 0
                // 播放新的当前索引歌曲
                if (playQueue.isNotEmpty()) {
                    startPlaying(playQueue[currentQueueIndex.intValue], playQueue)
                }
                Log.d("MusicVM", "移除当前播放歌曲，自动播放下一首: ${song.name}")
            }
        } else {
            // 移除非当前播放歌曲
            playQueue.removeAt(index)
            // 如果移除的歌曲在当前播放歌曲之前，需要调整当前索引
            if (index < currentQueueIndex.intValue) {
                currentQueueIndex.intValue = currentQueueIndex.intValue - 1
            }
            Log.d("MusicVM", "从队列移除: ${song.name}, 新队列大小: ${playQueue.size}")
        }
        
        savePlaybackState()
    }
    
    /**
     * 移动队列中的歌曲
     * @param fromIndex 起始索引
     * @param toIndex 目标索引
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || fromIndex >= playQueue.size || toIndex < 0 || toIndex >= playQueue.size) {
            Log.w("MusicVM", "队列索引无效: from=$fromIndex, to=$toIndex")
            return
        }
        
        val item = playQueue.removeAt(fromIndex)
        playQueue.add(toIndex, item)
        
        // 调整当前播放歌曲索引
        when {
            currentQueueIndex.intValue == fromIndex -> {
                currentQueueIndex.intValue = toIndex
            }
            fromIndex < currentQueueIndex.intValue && toIndex >= currentQueueIndex.intValue -> {
                currentQueueIndex.intValue = currentQueueIndex.intValue - 1
            }
            fromIndex > currentQueueIndex.intValue && toIndex <= currentQueueIndex.intValue -> {
                currentQueueIndex.intValue = currentQueueIndex.intValue + 1
            }
        }
        
        Log.d("MusicVM", "队列歌曲移动: from=$fromIndex, to=$toIndex, 当前播放索引: ${currentQueueIndex.intValue}")
        
        savePlaybackState()
    }

    fun moveQueueItems(fromIndices: List<Int>, toIndex: Int) {
        if (fromIndices.isEmpty()) return
        
        val sortedIndices = fromIndices.sorted()
        val minIndex = sortedIndices.first()
        val maxIndex = sortedIndices.last()
        val rangeSize = fromIndices.size
        
        if (toIndex < 0 || toIndex > playQueue.size - rangeSize) return
        
        // 构建新队列（先删后插），一次性替换避免逐个修改触发 Compose 并发闪退
        val newQueue = playQueue.toMutableList()
        for (i in sortedIndices.reversed()) {
            if (i < newQueue.size) newQueue.removeAt(i)
        }
        val songsToMove = sortedIndices.mapNotNull { idx -> playQueue.getOrNull(idx) }
        for ((i, song) in songsToMove.withIndex()) {
            val insertPos = (toIndex + i).coerceAtMost(newQueue.size)
            newQueue.add(insertPos, song)
        }

        playQueue.clear()
        playQueue.addAll(newQueue)

        val newIndices = (toIndex until toIndex + rangeSize).toSet()
        
        when {
            currentQueueIndex.intValue in fromIndices -> {
                val oldPos = fromIndices.indexOf(currentQueueIndex.intValue)
                currentQueueIndex.intValue = toIndex + oldPos
            }
            currentQueueIndex.intValue in minIndex..maxIndex -> {
                if (toIndex <= minIndex) {
                    currentQueueIndex.intValue = currentQueueIndex.intValue + rangeSize
                } else {
                    currentQueueIndex.intValue = currentQueueIndex.intValue - rangeSize
                }
            }
            minIndex < currentQueueIndex.intValue && toIndex >= currentQueueIndex.intValue -> {
                currentQueueIndex.intValue = currentQueueIndex.intValue - rangeSize
            }
            maxIndex > currentQueueIndex.intValue && toIndex <= currentQueueIndex.intValue -> {
                currentQueueIndex.intValue = currentQueueIndex.intValue + rangeSize
            }
        }
        
        Log.d("MusicVM", "批量移动: from=$sortedIndices, to=$toIndex, 新索引=$newIndices")
        
        savePlaybackState()
    }

    /**
     * 清空播放队列
     */
    fun clearQueue() {
        val previousSize = playQueue.size
        playQueue.clear()
        currentQueueIndex.intValue = -1
        currentSong.value = null
        
        if (player.isPlaying) {
            player.pause()
            isPlaying.value = false
        }
        
        // 隐藏通知
        mediaSessionManager.hideNotification()
        audioManager.abandonAudioFocus(audioFocusChangeListener)
        Log.d("MusicVM", "队列已清空，之前大小: $previousSize")
        
        savePlaybackState()
    }
    
    /**
     * 添加歌单到歌单队列
     * @param playlist 歌单
     */
    fun addPlaylistToQueue(playlist: PlaylistQueueItem) {
        if (!playlistQueue.any { it.id == playlist.id }) {
            playlistQueue.add(playlist)
            Log.d("MusicVM", "添加歌单到队列: ${playlist.name}, 歌单队列大小: ${playlistQueue.size}")
        } else {
            Log.d("MusicVM", "歌单已在队列中: ${playlist.name}")
        }
    }
    
    /**
     * 从歌单队列移除指定歌单
     * @param playlistId 歌单ID
     */
    fun removePlaylistFromQueue(playlistId: String) {
        val index = playlistQueue.indexOfFirst { it.id == playlistId }
        if (index == -1) {
            Log.d("MusicVM", "歌单不在队列中: $playlistId")
            return
        }
        
        playlistQueue.removeAt(index)
        
        // 如果移除的是当前播放的歌单或之前的歌单，需要调整索引
        if (index <= currentPlaylistIndex.intValue) {
            currentPlaylistIndex.intValue = (currentPlaylistIndex.intValue - 1).coerceAtLeast(0)
        }
        
        Log.d("MusicVM", "从歌单队列移除: $playlistId, 新歌单队列大小: ${playlistQueue.size}")
    }
    
    /**
     * 清空歌单队列
     */
    fun clearPlaylistQueue() {
        playlistQueue.clear()
        currentPlaylistIndex.intValue = -1
        Log.d("MusicVM", "歌单队列已清空")
    }
    
    /**
     * 播放指定歌单
     * @param playlist 歌单
     */
    fun playPlaylist(playlist: PlaylistQueueItem) {
        val playlistIndex = playlistQueue.indexOfFirst { it.id == playlist.id }
        if (playlistIndex == -1) {
            // 歌单不在队列中，先添加
            addPlaylistToQueue(playlist)
            currentPlaylistIndex.intValue = playlistQueue.size - 1
        } else {
            currentPlaylistIndex.intValue = playlistIndex
        }
        
        playQueue.clear()
        playQueue.addAll(playlist.songs)
        currentQueueIndex.intValue = 0
        
        if (playlist.songs.isNotEmpty()) {
            startPlaying(playlist.songs[0], playlist.songs)
        }
        
        Log.d("MusicVM", "播放歌单: ${playlist.name}, 歌曲数: ${playlist.songs.size}")
    }
    
    /**
     * 重新排序歌单队列
     * @param fromIndex 起始索引
     * @param toIndex 目标索引
     */
    fun movePlaylistQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || fromIndex >= playlistQueue.size || toIndex < 0 || toIndex >= playlistQueue.size) {
            Log.w("MusicVM", "歌单队列索引无效: from=$fromIndex, to=$toIndex")
            return
        }
        
        val item = playlistQueue.removeAt(fromIndex)
        playlistQueue.add(toIndex, item)
        
        // 调整当前播放歌单索引
        when {
            currentPlaylistIndex.intValue == fromIndex -> {
                currentPlaylistIndex.intValue = toIndex
            }
            fromIndex < currentPlaylistIndex.intValue && toIndex >= currentPlaylistIndex.intValue -> {
                currentPlaylistIndex.intValue = currentPlaylistIndex.intValue - 1
            }
            fromIndex > currentPlaylistIndex.intValue && toIndex <= currentPlaylistIndex.intValue -> {
                currentPlaylistIndex.intValue = currentPlaylistIndex.intValue + 1
            }
        }
        
        Log.d("MusicVM", "歌单队列重排序: from=$fromIndex, to=$toIndex, 当前播放歌单索引: ${currentPlaylistIndex.intValue}")
    }
    
    // 辅助方法
    private val yrcLineRegex = Regex("""\[\d+,\s*\d+]\(\d+,""")
    
    /** 自动检测并解析歌词：YRC → 逐字, LRC → 逐行 */
    private fun parseLyricAuto(content: String): List<LyricEntry> {
        if (content.isBlank()) return emptyList()
        return if (yrcLineRegex.containsMatchIn(content)) {
            parseYrc(content)
        } else {
            parseLrc(content)
        }
    }

    /** 解析 YRC 逐字歌词 */
    private fun parseYrc(yrc: String): List<LyricEntry> {
        val out = mutableListOf<LyricEntry>()
        val headerRegex = Regex("""\[(\d+),\s*(\d+)]""")
        val segRegex = Regex("""\((\d+),\s*(\d+),\s*[-\d]+\)([^()\n\r]+)""")

        yrc.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (!line.startsWith("[")) return@forEach

            val header = headerRegex.find(line) ?: return@forEach
            val start = header.groupValues[1].toLong()
            val dur = header.groupValues[2].toLong()
            val end = start + dur

            val segs = segRegex.findAll(line).toList()
            if (segs.isEmpty()) {
                val text = line.substringAfter("]").trim()
                if (text.isNotEmpty()) out.add(LyricEntry(start, end, text))
            } else {
                val words = mutableListOf<WordTiming>()
                val sb = StringBuilder()
                for (m in segs) {
                    val ws = m.groupValues[1].toLong()
                    val wd = m.groupValues[2].toLong()
                    val we = ws + wd
                    val t = m.groupValues[3]
                    sb.append(t)
                    words.add(WordTiming(ws, we, t.length))
                }
                out.add(LyricEntry(start, end, sb.toString(), words))
            }
        }
        return out.sortedBy { it.startTimeMs }
    }

    /** 解析 LRC 逐行歌词（跳过空行和元数据行） */
    private fun parseLrc(lrc: String): List<LyricEntry> {
        val tag = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{2,3}))?]""")
        val timeline = mutableListOf<Pair<Long, String>>()

        lrc.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            // 跳过 JSON/元数据片段
            if (line.startsWith("{") || line.startsWith("}")) return@forEach
            if (line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:") ||
                line.startsWith("[by:") || line.startsWith("[offset:")) return@forEach

            val m = tag.find(line) ?: return@forEach
            val mm = m.groupValues[1].toInt()
            val ss = m.groupValues[2].toInt()
            val msStr = m.groupValues.getOrNull(3).orEmpty()
            val ms = when (msStr.length) {
                0 -> 0
                2 -> msStr.toInt() * 10
                else -> msStr.toInt()
            }
            val time = mm * 60_000L + ss * 1_000L + ms
            val text = line.substring(m.range.last + 1).trim()
            if (text.isNotEmpty()) {
                timeline.add(time to text)
            }
        }

        timeline.sortBy { it.first }
        val out = mutableListOf<LyricEntry>()
        for (i in timeline.indices) {
            val (start, text) = timeline[i]
            val end = if (i < timeline.lastIndex) timeline[i + 1].first else start + 5_000L
            out.add(LyricEntry(start, end, text))
        }
        return out
    }

    private fun startProgressUpdater() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isPlaying.value) {
                val currentPos = player.currentPosition
                currentPosition.longValue = currentPos
                
                mediaSessionManager.updatePlaybackState(true, currentPos)
                
                val idx = currentLrc.indexOfLast { it.startTimeMs <= player.currentPosition }
                if (idx != -1) {
                    currentLineIndex.intValue = idx
                    // 更新桌面歌词
                    DesktopLyricService.updateLyric(currentLrc, idx, true)
                }
                delay(50)  // 每50ms更新一次，保证逐字歌词流畅
            }
        }
    }

    private fun saveHistory() {
        prefs.edit().putString("play_history", gson.toJson(historyList.toList())).apply()
    }

    fun saveSearchHistory() {
        prefs.edit().putString("search_history", gson.toJson(searchHistory.toList())).apply()
    }

    private fun loadDataFromPrefs() {
        val pJson = prefs.getString("play_history", null)
        if (!pJson.isNullOrEmpty()) {
            val list: List<Song> = gson.fromJson(pJson, object : TypeToken<List<Song>>() {}.type)
            historyList.addAll(list.map { Song.normalize(it) })
        }
        val sJson = prefs.getString("search_history", null)
        if (!sJson.isNullOrEmpty()) {
            val list: List<String> = gson.fromJson(sJson, object : TypeToken<List<String>>() {}.type)
            searchHistory.addAll(list)
        }
    }
    
    /**
     * 打印队列状态（debug）
     */
    private fun printQueueStatus() {
        Log.d("MusicVM", "=== 播放队列状态 ===")
        Log.d("MusicVM", "队列大小: ${playQueue.size}")
        Log.d("MusicVM", "当前索引: $currentQueueIndex")
        playQueue.forEachIndexed { index, song ->
            val isCurrent = index == currentQueueIndex.intValue
            Log.d("MusicVM", "${index + 1}. ${song.name} - ${song.artist} ${if (isCurrent) "[当前]" else ""}")
        }
        Log.d("MusicVM", "当前歌曲: ${currentSong.value?.name ?: "无"}")
        Log.d("MusicVM", "播放模式: ${playMode.value}")
        Log.d("MusicVM", "==================")
    }

    // ========== 播放状态保存与恢复 ==========

    /**
     * 保存播放状态
     * 在播放歌曲时调用，记录当前播放队列状态
     */
    private fun savePlaybackState() {
        val context = getApplication<Application>()
        
        // 仅在开启"离开后保留列表"时保存
        if (!DownloadSettingsStore.isKeepPlaylistOnExitEnabled(context)) {
            return
        }
        
        if (playQueue.isNotEmpty()) {
            val state = PlaybackState(
                songs = playQueue.toList(),
                currentIndex = currentQueueIndex.intValue
            )
            PlaybackStateStore.savePlaybackState(context, state)
            Log.d("MusicVM", "播放状态已保存: ${playQueue.size}首歌曲, 当前索引: ${currentQueueIndex.intValue}")
        }
    }

    /**
     * 在启动时恢复播放状态
     */
    private fun restorePlaybackStateOnStartup() {
        val context = getApplication<Application>()
        
        // 仅在开启"离开后保留列表"时恢复
        if (!DownloadSettingsStore.isKeepPlaylistOnExitEnabled(context)) {
            Log.d("MusicVM", "未开启离开后保留列表，跳过状态恢复")
            return
        }
        
        val savedState = PlaybackStateStore.loadPlaybackState(context)
        if (savedState != null && savedState.songs.isNotEmpty()) {
            Log.d("MusicVM", "恢复播放状态: ${savedState.songs.size}首歌曲, 当前索引: ${savedState.currentIndex}")
            
            // 恢复播放队列（需要对反序列化后的歌曲进行normalize）
            playQueue.clear()
            playQueue.addAll(savedState.songs.map { Song.normalize(it) })
            
            // 恢复当前播放索引
            val validIndex = savedState.currentIndex.coerceIn(0, playQueue.size - 1)
            currentQueueIndex.intValue = validIndex
            
            // 恢复当前歌曲状态
            val currentSongData = playQueue.getOrNull(validIndex)
            if (currentSongData != null) {
                currentSong.value = currentSongData
                
                // 检查是否需要自动播放
                val autoPlay = DownloadSettingsStore.isAutoPlayOnStartEnabled(context)
                if (autoPlay) {
                    Log.d("MusicVM", "开启了启动时播放，开始播放")
                    startPlaying(currentSongData, playQueue)
                } else {
                    Log.d("MusicVM", "未开启启动时播放，恢复状态但不播放（暂停状态）")
                    // 恢复状态但不播放（暂停状态），但需要加载歌曲数据
                    restorePlaybackStateWithoutPlaying(currentSongData, playQueue)
                }
            }
        } else {
            Log.d("MusicVM", "没有保存的播放状态")
        }
    }
    
    /**
     * 恢复播放状态但不播放（暂停状态），但加载好歌曲数据（歌词、封面等）
     */
    private fun restorePlaybackStateWithoutPlaying(song: Song, sourceList: List<Song>) {
        Log.d("MusicVM", "恢复状态但不播放，加载歌曲数据: ${song.name}")
        
        // 清空歌词和状态
        currentLrc.clear()
        currentLineIndex.intValue = 0
        currentPosition.longValue = 0L
        totalDuration.longValue = 0L
        
        // 记录来源列表和索引
        if (sourceList.isNotEmpty()) {
            currentPlayingList.clear()
            currentPlayingList.addAll(sourceList)
            val indexInSource = sourceList.indexOfFirst { isSameSong(it, song) }
            currentPlayingListIndex.intValue = if (indexInSource != -1) indexInSource else 0
            Log.d("MusicVM", "已记录来源列表，大小=${currentPlayingList.size}, 歌曲索引=${currentPlayingListIndex.intValue}")
        }
        
        // 设置为暂停状态
        isPlaying.value = false
        
        val context = getApplication<Application>()
        
        // 配置播放器（但不播放）
        try {
            var localSongPath: String? = null
            var localCoverPath: String? = null
            
            if (song.isLocal) {
                localSongPath = song.url
                Log.d("MusicVM", "本地歌曲，使用URL作为路径: $localSongPath")
            } else {
                localSongPath = CacheManager.getCachedMp3Path(context, song)
                localCoverPath = CacheManager.getCachedCoverPath(context, song)
                Log.d("MusicVM", "网络歌曲，本地路径: $localSongPath")
            }
            
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(song.name)
                .setArtist(song.artist)
                .setArtworkUri(if (localCoverPath != null) {
                    if (localCoverPath.startsWith("content://") || localCoverPath.startsWith("file://")) {
                        Uri.parse(localCoverPath)
                    } else {
                        Uri.fromFile(File(localCoverPath))
                    }
                } else Uri.parse(song.pic))
                .build()
            
            var finalUrl = song.url
            
            if (song.isBiliVideo) {
                viewModelScope.launch(Dispatchers.IO) {
                    var localPath = localSongPath
                    if (localPath == null) {
                        // 从网络获取音频流
                        try {
                            val streamInfo = biliApi.getBestAudioStream(song.bvid, song.cid)
                            if (streamInfo != null && streamInfo.url.isNotEmpty()) {
                                finalUrl = streamInfo.url
                            }
                        } catch (e: Exception) {
                            Log.e("MusicVM", "获取B站音频流失败", e)
                        }
                    }
                    
                    val mediaUri = if (localPath != null) {
                        if (localPath.startsWith("content://") || localPath.startsWith("file://")) {
                            Uri.parse(localPath)
                        } else {
                            Uri.fromFile(File(localPath))
                        }
                    } else {
                        Uri.parse(finalUrl)
                    }
                    
                    // 播放器操作必须在主线程执行
                    withContext(Dispatchers.Main) {
                        try {
                            val mediaItem = MediaItem.Builder()
                                .setUri(mediaUri)
                                .setMediaMetadata(mediaMetadata)
                                .build()
                            
                            player.setMediaItem(mediaItem)
                            player.prepare()
                            
                            Log.d("MusicVM", "播放器已准备好（暂停状态）: ${song.name}")
                        } catch (e: Exception) {
                            Log.e("MusicVM", "播放器准备失败: ${song.name}", e)
                        }
                        
                        mediaSessionManager.updateMetadata(
                            title = song.name,
                            artist = song.artist,
                            album = "专辑",
                            duration = 0L,
                            artworkUrl = localCoverPath ?: song.pic
                        )
                        mediaSessionManager.updatePlaybackState(false, 0L)
                    }
                }
            } else {
                val mediaUri = if (localSongPath != null) {
                    if (localSongPath.startsWith("content://") || localSongPath.startsWith("file://")) {
                        Uri.parse(localSongPath)
                    } else {
                        Uri.fromFile(File(localSongPath))
                    }
                } else {
                    Uri.parse(finalUrl)
                }
                
                try {
                    val mediaItem = MediaItem.Builder()
                        .setUri(mediaUri)
                        .setMediaMetadata(mediaMetadata)
                        .build()
                    
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    
                    Log.d("MusicVM", "播放器已准备好（暂停状态）: ${song.name}")
                } catch (e: Exception) {
                    Log.e("MusicVM", "播放器准备失败: ${song.name}", e)
                }
                
                mediaSessionManager.updateMetadata(
                    title = song.name,
                    artist = song.artist,
                    album = "专辑",
                    duration = 0L,
                    artworkUrl = localCoverPath ?: song.pic
                )
                mediaSessionManager.updatePlaybackState(false, 0L)
            }
            
        } catch (e: Exception) {
            Log.e("MusicVM", "恢复状态初始化错误", e)
        }
        
        // 异步加载歌词
        viewModelScope.launch {
            try {
                var lrcContent = ""
                var translatedLrcContent: String? = null
                
                if (song.isLocal) {
                    val customLyrics = SongCustomDataStore.getLyrics(context, song.url)
                    if (customLyrics.isNotEmpty()) {
                        lrcContent = customLyrics
                    } else {
                        val lyricSource = DownloadSettingsStore.getLyricSource(context)
                        if (lyricSource == 0) {
                            val localMusicManager = LocalMusicManager(context)
                            val lyrics = localMusicManager.extractLyrics(song.url)
                            if (!lyrics.isNullOrEmpty()) {
                                lrcContent = lyrics
                            }
                        } else {
                            lrcContent = fetchNetworkLyrics(song.name, song.artist)
                        }
                    }
                } else {
                    val customLyrics = SongCustomDataStore.getLyrics(context, song.url)
                    if (customLyrics.isNotEmpty()) {
                        lrcContent = customLyrics
                    } else {
                        // 优先从cache目录读取缓存歌词
                        val (cachedLrc, cachedTrans) = CacheManager.readCachedLyricsBilingual(context, song)
                        if (cachedLrc != null) {
                            lrcContent = cachedLrc
                            translatedLrcContent = cachedTrans
                            Log.d("MusicVM", "使用缓存歌词: ${song.name}, 有翻译=${cachedTrans != null}")
                        } else if (song.source == SongSource.NETEASE) {
                            val (neteaseLrc, neteaseTrans) = fetchNeteaseLyric(song)
                            lrcContent = neteaseLrc ?: ""
                            translatedLrcContent = neteaseTrans
                        } else {
                            // 从网络获取歌词
                            lrcContent = if (!song.lrc.isNullOrEmpty()) {
                                if (song.lrc.startsWith("http")) api.getLrcByUrl(song.lrc)
                                else api.getLrcById(id = song.id)
                            } else ""
                            Log.d("MusicVM", "非网易云歌曲歌词加载完成: ${song.name}, 内容长度=${lrcContent.length}")
                        }
                    }
                }
                
                currentLrc.clear()
                currentTranslatedLrc.clear()
                if (lrcContent.isNotEmpty()) {
                    currentLrc.addAll(parseLyricAuto(lrcContent))
                } else {
                    currentLrc.add(LyricEntry(0, 5000, "暂无歌词"))
                }
                if (!translatedLrcContent.isNullOrBlank()) {
                    currentTranslatedLrc.addAll(parseLyricAuto(translatedLrcContent))
                }
            } catch (e: Exception) {
                currentLrc.clear()
                currentTranslatedLrc.clear()
                currentLrc.add(LyricEntry(0, 5000, "暂无歌词"))
            }
        }
        
        // 应用自定义封面
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!song.isLocal) {
                    val customCover = SongCustomDataStore.getCover(context, song.url)
                    if (customCover.isNotEmpty()) {
                        // 更新当前歌曲的封面
                        val updatedSong = song.copy(pic = customCover)
                        currentSong.value = updatedSong
                        // 更新播放队列中的歌曲
                        val index = playQueue.indexOfFirst { isSameSong(it, song) }
                        if (index != -1) {
                            playQueue[index] = updatedSong
                        }
                        Log.d("MusicVM", "应用自定义封面")
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicVM", "加载封面失败", e)
            }
        }
    }

    /**
     * 清除保存的播放状态
     */
    fun clearSavedPlaybackState() {
        val context = getApplication<Application>()
        PlaybackStateStore.clearPlaybackState(context)
        Log.d("MusicVM", "已清除保存的播放状态")
    }

    /**
     * 更新播放状态保存（在播放新歌曲时自动调用）
     */
    fun updatePlaybackStateOnPlay() {
        savePlaybackState()
    }

    override fun onCleared() {
        super.onCleared()
        
        // 注销蓝牙断开监听
        try {
            bluetoothDisconnectReceiver?.let {
                getApplication<Application>().unregisterReceiver(it)
                Log.d("MusicVM", "蓝牙断开监听已注销")
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "注销蓝牙断开监听失败", e)
        }
        
        audioManager.abandonAudioFocus(audioFocusChangeListener)
        mediaSessionManager.release()
        player.release()
    }
    
    // ========== 分p视频播放相关方法 ==========
    
    /**
     * 播放歌曲或显示分p选择对话框
     * 如果歌曲是B站分p视频，在sourceList中查找兄弟分p并显示选择对话框
     * @return true 如果显示了分p选择对话框（调用方应直接返回，不再执行播放）
     */
    fun playOrShowMultiPageSelection(song: Song, sourceList: List<Song>): Boolean {
        if (!song.isPartOfMultiPage) return false
        
        // 从来源列表中查找同一视频的所有分p
        val siblingPages = sourceList.filter { 
            it.parentBvid == song.parentBvid 
        }.sortedBy { it.pageIndex }
        
        if (siblingPages.size > 1) {
            showMultiPageSelection(song, siblingPages)
            return true
        }
        return false
    }
    
    /**
     * 显示分p选择对话框
     * @param song 触发选择的歌曲
     * @param pages 分p列表
     */
    fun showMultiPageSelection(song: Song, pages: List<Song>) {
        multiPageSelectionState.value = MultiPageSelectionState(
            show = true,
            song = song,
            pages = pages
        )
        Log.d("MusicVM", "显示分p选择对话框，共${pages.size}个分p")
    }
    
    /**
     * 关闭分p选择对话框
     */
    fun dismissMultiPageSelection() {
        multiPageSelectionState.value = MultiPageSelectionState()
        Log.d("MusicVM", "关闭分p选择对话框")
    }
    
    /**
     * 播放选中的分p
     * @param pageSong 选中的分p歌曲
     */
    fun playSelectedPage(pageSong: Song) {
        dismissMultiPageSelection()
        playSong(pageSong, multiPageSelectionState.value.pages)
        Log.d("MusicVM", "播放选中的分p: ${pageSong.name}")
    }
    
    /**
     * 检查当前播放的是否是分p视频的最后一p
     * @return true 如果是最后一p
     */
    fun isLastPageOfMultiPageVideo(): Boolean {
        val current = currentSong.value ?: return false
        if (!current.isPartOfMultiPage) return false
        return current.pageIndex >= current.pageCount
    }
    
    /**
     * 获取当前分p在队列中的下一个分p（同一视频的）
     * @return 下一个分p的歌曲，如果没有则返回null
     */
    fun getNextPageInQueue(): Song? {
        val current = currentSong.value ?: return null
        if (!current.isPartOfMultiPage || current.pageIndex >= current.pageCount) {
            return null
        }
        
        // 在播放队列中查找同一视频的下一个分p
        val nextIndex = currentQueueIndex.intValue + 1
        if (nextIndex < playQueue.size) {
            val nextSong = playQueue[nextIndex]
            if (nextSong.parentBvid == current.parentBvid && 
                nextSong.pageIndex == current.pageIndex + 1) {
                return nextSong
            }
        }
        return null
    }
    
    /**
     * 检查是否需要显示分p选择对话框
     * @param song 要播放的歌曲
     * @return true 如果需要显示选择对话框
     */
    fun needShowMultiPageSelection(song: Song): Boolean {
        // 如果是分p视频的第一p（或者不是从分p列表播放的），需要显示选择对话框
        return song.isPartOfMultiPage && song.pageIndex == 1
    }

    /**
     * 从播放队列中获取同一视频的所有分p
     * @param song 参考歌曲
     * @return 同一视频的所有分p歌曲列表
     */
    fun getMultiPageSongs(song: Song): List<Song> {
        if (!song.isPartOfMultiPage) {
            return listOf(song)
        }
        
        // 从播放队列中筛选同一视频的所有分p
        return playQueue.filter { 
            it.parentBvid == song.parentBvid 
        }.sortedBy { it.pageIndex }
    }
    
    /**
     * 获取当前播放进度信息（用于显示分p进度）
     * @return Pair(当前分p索引, 总分p数)
     */
    fun getCurrentPageProgress(): Pair<Int, Int> {
        val current = currentSong.value ?: return Pair(0, 0)
        if (!current.isPartOfMultiPage) {
            return Pair(1, 1)
        }
        return Pair(current.pageIndex, current.pageCount)
    }

    // ========== 歌词相关方法 ==========
    
    /**
     * 从网络获取歌词
     * @param songName 歌曲名字
     * @param artistName 歌手名字
     * @return 歌词内容，如果获取失败则返回空字符串
     */
    private suspend fun fetchNetworkLyrics(songName: String, artistName: String): String {
        try {
            // 搜索歌曲
            val searchResults = api.searchSongs(keyword = songName)
            if (searchResults.isEmpty()) {
                return ""
            }
            
            // 尝试匹配歌手名字
            var targetSong: Song? = null
            if (artistName.isNotEmpty() && artistName != "未知歌手") {
                targetSong = searchResults.find { 
                    it.artist.contains(artistName) || artistName.contains(it.artist)
                }
            }
            
            // 如果没有找到匹配的歌手，使用第一个结果
            if (targetSong == null) {
                targetSong = searchResults.first()
            }
            
            // 获取歌词
            return if (!targetSong.lrc.isNullOrEmpty()) {
                if (targetSong.lrc.startsWith("http")) {
                    api.getLrcByUrl(targetSong.lrc)
                } else {
                    api.getLrcById(id = targetSong.id)
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e("MusicVM", "获取网络歌词失败", e)
            return ""
        }
    }

    /**
     * 检查是否需要自动缓存歌曲
     * 播放次数超过3次且未下载过则自动下载
     */
    private fun checkAutoCache(song: Song) {
        val context = getApplication<Application>()
        
        // 检查自动缓存是否启用
        if (!DownloadSettingsStore.isAutoCacheEnabled(context)) {
            return
        }
        
        // 本地歌曲不需要缓存
        if (song.isLocal) {
            return
        }
        
        // 检查是否已经缓存
        if (CacheManager.isCached(context, song)) {
            return
        }
        
        // 检查播放次数（使用MusicStatsManager复用现有逻辑）
        val statsManager = MusicStatsManager(context)
        val playCount = statsManager.getPlayCountMap()[song.id.ifBlank { song.url }] ?: 0
        
        Log.d("MusicVM", "自动缓存检查 - 歌曲: ${song.name}, 播放次数: $playCount")
        
        // 播放次数超过3次则自动缓存
        if (playCount > 3) {
            Log.d("MusicVM", "自动缓存触发 - 歌曲: ${song.name}, 开始缓存")
            
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val result = CacheManager.cacheSong(context, song)
                    if (result.isSuccess) {
                        Log.d("MusicVM", "自动缓存成功 - ${song.name}: ${result.getOrThrow()}")
                    } else {
                        Log.e("MusicVM", "自动缓存失败 - ${song.name}: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e("MusicVM", "自动缓存异常 - ${song.name}", e)
                }
            }
        }
    }
    
    companion object {
        private const val FADE_DURATION_MS = 500L
        private const val FADE_STEP_MS = 50L

        /**
         * 静态方法：获取历史记录
         * @param context 上下文
         * @return 历史播放歌曲列表
         */
        fun getHistoryList(context: Context): List<Song> {
            val prefs = context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
            val gson = Gson()
            val json = prefs.getString("play_history", null) ?: return emptyList()
            return try {
                val list = gson.fromJson(json, object : TypeToken<List<Song>>() {}.type) as List<Song>
                list.map { Song.normalize(it) }
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        /**
         * 静态方法：保存历史记录
         * @param context 上下文
         * @param history 历史播放歌曲列表
         */
        fun saveHistoryList(context: Context, history: List<Song>) {
            val prefs = context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
            val gson = Gson()
            prefs.edit().putString("play_history", gson.toJson(history)).apply()
        }
    }
}
