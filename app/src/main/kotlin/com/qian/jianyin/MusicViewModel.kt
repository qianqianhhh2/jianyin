package com.qian.jianyin

import android.app.Application
import android.content.Context
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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import moe.ouom.biliapi.BiliApi
import moe.ouom.biliapi.BiliWebLoginHelper
import moe.ouom.biliapi.BiliAudioStreamInfo
import moe.ouom.biliapi.SavedCookieAuthState

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
    val isPlayerSheetVisible = mutableStateOf(false)

    // 进度与歌词
    val currentLrc = mutableStateListOf<LrcLine>()
    val currentLineIndex = mutableIntStateOf(0)
    var currentPosition = mutableLongStateOf(0L)
    var totalDuration = mutableLongStateOf(0L)

    // --- 播放模式 ---
    val playMode = mutableStateOf(PlaybackMode.SEQUENCE)
    
    // --- 进度条样式 ---
    val progressBarStyle = mutableStateOf(ProgressBarStyle.DEFAULT)

    val currentPlayingList = mutableStateListOf<Song>()   // 当前播放歌曲的来源列表
    val currentPlayingListIndex = mutableIntStateOf(-1)    // 当前歌曲在来源列表中的索引
    
    // 歌单更新触发器，用于通知 UI 刷新歌单列表
    val playlistUpdateTrigger = mutableIntStateOf(0)

        // 收藏状态
    val isCurrentSongFavorited = mutableStateOf(false)

    // 推荐搜索词
    val recommendedSearches = listOf("周杰伦", "陈奕迅", "林俊杰", "五月天", "邓紫棋", "告白气球", "十周年", "平凡之路")

    // B站相关状态
    val biliLoginState = mutableStateOf<BiliLoginState>(BiliLoginState.Unknown)
    private val biliApi: BiliApi by lazy { BiliApi.getInstance(application) }

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
                            mediaSessionManager.updateMetadata(
                                title = song.name,
                                artist = song.artist,
                                album = "专辑",
                                duration = duration,
                                artworkUrl = song.pic
                            )
                        }

                        Log.d("MusicVM", "歌曲已准备好，时长: $duration")
                    }
                }
            }
        })

        // 初始化B站登录状态监听
        initializeBiliLoginStateListener()
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
            val folders = biliApi.getUserFavFolders()
            val playlists = folders.map { folder ->
                val songs = biliApi.getFavFolderItems(folder.id)
                UserSyncedPlaylist(
                    id = "bili_${folder.id}",
                    name = folder.name,
                    coverPic = folder.cover,
                    songs = songs.map { item ->
                        Song(
                            id = item.bvid,
                            name = item.title,
                            artist = item.owner,
                            url = "",
                            pic = item.pic,
                            isBiliVideo = true,
                            bvid = item.bvid,
                            cid = item.cid
                        )
                    }
                )
            }
            playlists
        } catch (e: Exception) {
            Log.e("MusicVM", "同步B站歌单失败", e)
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
        playMode.value = playMode.value.next()
        Log.d("MusicVM", "播放模式切换为: ${playMode.value}")
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
                val res = api.searchSongs(keyword = query)
                searchResults.clear()
                searchResults.addAll(res)
                if (!searchHistory.contains(query)) {
                    searchHistory.add(0, query)
                    saveSearchHistory()
                }
            } catch (e: Exception) {
                Log.e("MusicVM", "搜索失败", e)
            } finally {
                isSearching.value = false
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
            
            val songToPlay = playQueue[currentQueueIndex.intValue]
            Log.d("MusicVM", "列表播放模式。队列大小=${playQueue.size}, 目标索引=$index, 即将播放: ${songToPlay.name}")
            
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
        currentLineIndex.intValue = 0
        currentPosition.longValue = 0L
        totalDuration.longValue = 0L
        
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
        
        // 历史记录处理
        if (historyList.contains(song)) {
            historyList.remove(song)
        }
        historyList.add(song) 
        if (historyList.size > 50) {
            historyList.removeAt(0)
        }
        saveHistory()
        
        // 记录播放次数
        val statsManager = MusicStatsManager(getApplication())
        statsManager.recordPlay(song.id.ifBlank { song.url })

        //配置并准备播放器
        try {
            val context = getApplication<Application>()
            var localSongPath: String? = null
            var localCoverPath: String? = null
            
            if (song.isLocal) {
                // 本地歌曲，直接使用歌曲的url作为路径
                localSongPath = song.url
            } else {
                // 网络歌曲，尝试获取本地下载路径
                localSongPath = DownloadManager.getLocalSongPath(context, song)
                localCoverPath = DownloadManager.getLocalCoverPath(context, song)
            }
            
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(song.name)
                .setArtist(song.artist)
                .setArtworkUri(if (localCoverPath != null) Uri.fromFile(File(localCoverPath)) else Uri.parse(song.pic))
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
                            val mediaItem = MediaItem.Builder()
                                .setUri(Uri.fromFile(File(localSongPath)))
                                .setMediaMetadata(mediaMetadata)
                                .build()

                            player.setMediaItem(mediaItem)
                            player.prepare()
                            player.play()
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
                val playQuality = DownloadSettingsStore.getPlayQuality(context)
                finalUrl = if (!song.isLocal && localSongPath == null && playQuality != 320) {
                    // 非本地文件且非默认音质，添加br参数
                    if (song.url.contains("?")) {
                        "${song.url}&br=$playQuality"
                    } else {
                        "${song.url}?br=$playQuality"
                    }
                } else {
                    song.url
                }
                
                val mediaItem = MediaItem.Builder()
                    .setUri(if (song.isLocal && localSongPath != null) Uri.fromFile(File(localSongPath)) else Uri.parse(finalUrl))
                    .setMediaMetadata(mediaMetadata)
                    .build()

                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
                
                isPlaying.value = true
                
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
                        // 尝试获取本地下载的歌词或从网络获取
                        val localLrcPath = DownloadManager.getLocalLrcPath(getApplication(), song)
                        lrcContent = if (localLrcPath != null) {
                            // 使用本地已下载的歌词
                            File(localLrcPath).readText()
                        } else if (!song.lrc.isNullOrEmpty()) {
                            // 从网络获取歌词
                            if (song.lrc.startsWith("http")) api.getLrcByUrl(song.lrc)
                            else api.getLrcById(id = song.id)
                        } else ""
                    }
                }
                
                currentLrc.clear()
                if (lrcContent.isNotEmpty()) {
                    currentLrc.addAll(parseLrc(lrcContent))
                } else {
                    currentLrc.add(LrcLine(0, "暂无歌词"))
                }
            } catch (e: Exception) {
                currentLrc.clear()
                currentLrc.add(LrcLine(0, "暂无歌词"))
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
            PlaybackMode.RANDOM -> {
                // 随机播放
                if (playQueue.size == 1) {
                    playQueue.first()
                } else {
                    var randomIndex: Int
                    do {
                        randomIndex = (0 until playQueue.size).random()
                    } while (randomIndex == currentQueueIndex.intValue)
                    
                    currentQueueIndex.intValue = randomIndex
                    Log.d("MusicVM", "随机播放模式，随机到: ${playQueue[randomIndex].name}")
                    playQueue[randomIndex]
                }
            }
            PlaybackMode.SEQUENCE -> {
                // 顺序播放
                val nextIndex = (currentQueueIndex.intValue + 1) % playQueue.size
                currentQueueIndex.intValue = nextIndex
                Log.d("MusicVM", "顺序播放模式，下一首索引: $nextIndex")
                playQueue[nextIndex]
            }
        }
        
        startPlaying(nextSong, playQueue)
        Log.d("MusicVM", "下一首: ${nextSong.name}, 索引: $currentQueueIndex")
    }

    // 设置歌词内容
    fun setLyrics(lrcContent: String) {
        viewModelScope.launch {
            currentLrc.clear()
            if (lrcContent.isNotEmpty()) {
                currentLrc.addAll(parseLrc(lrcContent))
            } else {
                currentLrc.add(LrcLine(0, "暂无歌词"))
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
            PlaybackMode.RANDOM -> {
                // 随机播放
                if (playQueue.size == 1) {
                    playQueue.first()
                } else {
                    var randomIndex: Int
                    do {
                        randomIndex = (0 until playQueue.size).random()
                    } while (randomIndex == currentQueueIndex.intValue) // 避免和当前歌曲相同
                    
                    currentQueueIndex.intValue = randomIndex
                    Log.d("MusicVM", "随机播放模式，随机到: ${playQueue[randomIndex].name}")
                    playQueue[randomIndex]
                }
            }
            PlaybackMode.SEQUENCE -> {
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
        
        startPlaying(prevSong, playQueue)
        Log.d("MusicVM", "上一首: ${prevSong.name}, 索引: $currentQueueIndex")
    }
    
   // 播放控制
fun togglePlay() {
    if (player.isPlaying) {
        player.pause()
        isPlaying.value = false
        // 释放音频焦点
        audioManager.abandonAudioFocus(audioFocusChangeListener)
        mediaSessionManager.updatePlaybackState(false, player.currentPosition) 
    } else {
        // 重新请求音频焦点
        if (audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        ) {
            player.play()
            isPlaying.value = true
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
    }
    
    // 辅助方法
    private fun parseLrc(lrc: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})[\\.:](\\d{2,3})\\](.*)")
        lrc.lines().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val time = match.groupValues[1].toLong() * 60000 + 
                          match.groupValues[2].toLong() * 1000 + 
                          (match.groupValues[3].toLong().let { if (it < 100) it * 10 else it })
                lines.add(LrcLine(time, match.groupValues[4].trim()))
            }
        }
        return lines.sortedBy { it.time }
    }

    private fun startProgressUpdater() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isPlaying.value) {
                val currentPos = player.currentPosition
                currentPosition.longValue = currentPos
                
                mediaSessionManager.updatePlaybackState(true, currentPos)
                
                val idx = currentLrc.indexOfLast { it.time <= player.currentPosition }
                if (idx != -1) currentLineIndex.intValue = idx
                delay(500)  // 每500ms更新一次
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
            historyList.addAll(list)
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

    override fun onCleared() {
        super.onCleared()
        audioManager.abandonAudioFocus(audioFocusChangeListener)
        mediaSessionManager.release()
        player.release()
    }
    
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
    
    companion object {
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
                gson.fromJson(json, object : TypeToken<List<Song>>() {}.type)
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
