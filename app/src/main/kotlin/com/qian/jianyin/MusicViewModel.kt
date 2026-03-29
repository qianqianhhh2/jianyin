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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    // 音频管理器
    private val audioManager: AudioManager by lazy {
        getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    
    // ✅ 关键修复：添加媒体会话管理器
    private val mediaSessionManager = MediaSessionManager.getInstance(application)
    
    // --- 状态订阅 ---
    val searchResults = mutableStateListOf<Song>()
    val historyList = mutableStateListOf<Song>()    
    val searchHistory = mutableStateListOf<String>() 
    
    // ✅ 修复1: 清晰的播放队列管理
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

    // ✅ 新增：用于UI自动滚动追踪的状态
    val currentPlayingList = mutableStateListOf<Song>()   // 当前播放歌曲的来源列表
    val currentPlayingListIndex = mutableIntStateOf(-1)    // 当前歌曲在来源列表中的索引

        // 收藏状态
    val isCurrentSongFavorited = mutableStateOf(false)

    // 推荐搜索词
    val recommendedSearches = listOf("周杰伦", "陈奕迅", "林俊杰", "五月天", "邓紫棋", "告白气球", "十周年", "平凡之路")

    val player = ExoPlayer.Builder(application).build()
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
        
        // ✅ 关键修复：初始化媒体会话管理器
        initializeMediaSession()
        
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying.value = isPlayingNow
                if (isPlayingNow) {
                    totalDuration.longValue = player.duration
                    startProgressUpdater()
                    
                    // ✅ 更新媒体会话播放状态
                    mediaSessionManager.updatePlaybackState(true, player.currentPosition)
                } else {
                    progressJob?.cancel()
                    
                    // ✅ 更新媒体会话播放状态
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
                        
                        // ✅ 关键修复：更新媒体会话的元数据，包含正确的时长
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
    }
    
    /**
     * 初始化媒体会话
     */
    private fun initializeMediaSession() {
        // ✅ 设置媒体会话控制回调
        mediaSessionManager.controlCallback = object : MediaSessionManager.MediaControlCallback {
            override fun onPlay() {
                Log.d("MusicVM", "从通知栏收到播放命令")
                togglePlay()
            }
            
            override fun onPause() {
                Log.d("MusicVM", "从通知栏收到暂停命令")
                togglePlay()
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
        
        // ✅ 初始化媒体会话管理器
        mediaSessionManager.initialize()
    }

    // 切换模式方法
    fun togglePlayMode() {
        playMode.value = playMode.value.next()
        Log.d("MusicVM", "播放模式切换为: ${playMode.value}")
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
    
    // --- 核心播放逻辑（已修复）---
    
    /**
     * 可靠的歌曲匹配函数（新增）
     */
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
    
    /**
     * 播放歌曲（完整修复版）
     * 修复问题：点击歌单或搜索结果中的任意歌曲，将正确播放该歌曲，而不是第一首。
     * 新增功能：记录歌曲来源列表，支持UI自动滚动。
     */
    fun playSong(song: Song, newQueue: List<Song>? = null) {
        Log.d("MusicVM", "playSong 被调用: ${song.name}, 来源队列大小=${newQueue?.size ?: "无"}")
        
        // ✅ 修复1: 清晰的队列管理逻辑
        if (newQueue != null && newQueue.isNotEmpty()) {
            // 🅰️ 场景A: 播放整个歌单/列表（如从搜索、歌单详情页点击）
            playQueue.clear()
            playQueue.addAll(newQueue)
            
            // 使用可靠的匹配函数查找被点击歌曲在列表中的准确位置
            val index = newQueue.indexOfFirst { isSameSong(it, song) }
            currentQueueIndex.intValue = if (index != -1) index else 0
            
            val songToPlay = playQueue[currentQueueIndex.intValue]
            Log.d("MusicVM", "列表播放模式。队列大小=${playQueue.size}, 目标索引=$index, 即将播放: ${songToPlay.name}")
            
            // ✅ 关键修改：传递完整的源列表 newQueue
            startPlaying(songToPlay, newQueue)
        } else {
            // 🅱️ 场景B: 播放单曲 或 歌曲已在现有队列中
            val isSongInQueue = playQueue.any { isSameSong(it, song) }
            
            if (playQueue.isEmpty() || !isSongInQueue) {
                // 队列为空 或 歌曲不在队列中 -> 创建新队列（单曲播放）
                playQueue.clear()
                playQueue.add(song)
                currentQueueIndex.intValue = 0
                Log.d("MusicVM", "单曲播放模式。创建新队列，播放: ${song.name}")
                
                // ✅ 关键修改：传递仅包含这首歌的列表
                startPlaying(song, listOf(song))
            } else {
                // 歌曲已在队列中 -> 准确定位并播放
                val index = playQueue.indexOfFirst { isSameSong(it, song) }
                if (index != -1) {
                    currentQueueIndex.intValue = index
                    val songToPlay = playQueue[currentQueueIndex.intValue]
                    Log.d("MusicVM", "从现有队列中定位。索引=$index, 播放: ${songToPlay.name}")
                    
                    // ✅ 关键修改：传递当前的播放队列 playQueue
                    startPlaying(songToPlay, playQueue)
                } else {
                    // 安全回退
                    Log.e("MusicVM", "错误：匹配逻辑不一致。将歌曲添加到队列末尾。")
                    playQueue.add(song)
                    currentQueueIndex.intValue = playQueue.size - 1
                    
                    // ✅ 关键修改：传递更新后的播放队列
                    startPlaying(song, playQueue)
                }
            }
        }
        // 打印当前队列状态（调试用）
        printQueueStatus()
    }
    
    /**
     * 开始播放歌曲（内部方法，已增强）
     * 新增功能：记录歌曲来源列表，为UI自动滚动提供数据。
     */
    private fun startPlaying(song: Song, sourceList: List<Song>? = null) {
        Log.d("MusicVM", "startPlaying: ${song.name}, sourceList大小=${sourceList?.size ?: "无"}")
        
        // 1. 请求音频焦点
        if (audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        ) {
            Log.e("MusicVM", "无法获取音频焦点，播放中止")
            return
        }
        
        // 2. 更新当前歌曲和索引状态
        currentSong.value = song
        currentLrc.clear()
        currentLineIndex.intValue = 0
        currentPosition.longValue = 0L
        totalDuration.longValue = 0L
        
        // 3. ==== 新增逻辑：记录来源列表和索引，用于UI自动滚动 ====
        if (sourceList != null) {
            // 更新全局的"当前播放列表"引用
            currentPlayingList.clear()
            currentPlayingList.addAll(sourceList)
            // 查找并记录歌曲在源列表中的准确位置
            val indexInSource = sourceList.indexOfFirst { isSameSong(it, song) }
            currentPlayingListIndex.intValue = if (indexInSource != -1) indexInSource else 0
            Log.d("MusicVM", "已记录来源列表，大小=${currentPlayingList.size}, 歌曲索引=${currentPlayingListIndex.intValue}")
        } else {
            // 如果没有提供源列表，则默认使用 playQueue
            val indexInQueue = playQueue.indexOfFirst { isSameSong(it, song) }
            currentPlayingListIndex.intValue = if (indexInQueue != -1) indexInQueue else 0
            currentPlayingList.clear()
            currentPlayingList.addAll(playQueue)
        }
        
        // 4. 历史记录处理
        if (historyList.contains(song)) {
            historyList.remove(song)
        }
        historyList.add(song) 
        if (historyList.size > 50) {
            historyList.removeAt(0)
        }
        saveHistory()
        
        // 5. 记录播放次数，用于最近最爱排序
        val statsManager = MusicStatsManager(getApplication())
        statsManager.recordPlay(song.id.ifBlank { song.url })

        // 5. 配置并准备播放器
        try {
            // 优先使用本地文件
            val context = getApplication<Application>()
            val localSongPath = DownloadManager.getLocalSongPath(context, song)
            val localCoverPath = DownloadManager.getLocalCoverPath(context, song)
            
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(song.name)
                .setArtist(song.artist)
                .setArtworkUri(Uri.parse(localCoverPath ?: song.pic))
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(localSongPath ?: song.url))
                .setMediaMetadata(mediaMetadata)
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            
            isPlaying.value = true
            
            // 6. 更新媒体会话
            mediaSessionManager.updateMetadata(
                title = song.name,
                artist = song.artist,
                album = "专辑",
                duration = 0L, // 初始为0，播放器准备好后会自动更新
                artworkUrl = localCoverPath ?: song.pic
            )
            mediaSessionManager.updatePlaybackState(true, 0L)
            
            Log.d("MusicVM", "播放器已开始准备: ${song.name}")
            
        } catch (e: Exception) {
            Log.e("MusicVM", "播放初始化错误", e)
            audioManager.abandonAudioFocus(audioFocusChangeListener)
            isPlaying.value = false
        }

        // 7. 异步加载歌词（优先使用本地已下载的歌词）
        viewModelScope.launch {
            try {
                val localLrcPath = DownloadManager.getLocalLrcPath(getApplication(), song)
                val lrcContent = if (localLrcPath != null) {
                    // 使用本地已下载的歌词
                    File(localLrcPath).readText()
                } else if (!song.lrc.isNullOrEmpty()) {
                    // 从网络获取歌词
                    if (song.lrc.startsWith("http")) api.getLrcByUrl(song.lrc)
                    else api.getLrcById(id = song.id)
                } else ""
                currentLrc.clear()
                currentLrc.addAll(parseLrc(lrcContent))
            } catch (e: Exception) {
                currentLrc.clear()
                currentLrc.add(LrcLine(0, "暂无歌词"))
            }
        }
        checkIfCurrentSongIsFavorited()
    }
    
    // ✅ 修复3: 改进的下一首/上一首逻辑
    fun nextSong() {
        if (playQueue.isEmpty()) {
            Log.d("MusicVM", "队列为空，无法下一首")
            return
        }
        
        // ✅ 关键修复：验证当前索引的有效性
        if (currentQueueIndex.intValue < 0 || currentQueueIndex.intValue >= playQueue.size) {
            Log.w("MusicVM", "当前索引无效，重置为0")
            currentQueueIndex.intValue = 0
        }
        
        val nextSong = when (playMode.value) {
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
                // 顺序播放
                val nextIndex = (currentQueueIndex.intValue + 1) % playQueue.size
                currentQueueIndex.intValue = nextIndex
                Log.d("MusicVM", "顺序播放模式，下一首索引: $nextIndex")
                playQueue[nextIndex]
            }
        }
        
        // ✅ 修复：下一首时也传递当前播放队列作为来源列表
        startPlaying(nextSong, playQueue)
        Log.d("MusicVM", "下一首: ${nextSong.name}, 索引: $currentQueueIndex")
    }

    fun previousSong() {
        if (playQueue.isEmpty()) {
            Log.d("MusicVM", "队列为空，无法上一首")
            return
        }
        
        // ✅ 关键修复：验证当前索引的有效性
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
        
        // ✅ 修复：上一首时也传递当前播放队列作为来源列表
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
        // ✅ 更新媒体会话播放状态 (暂停时)
        mediaSessionManager.updatePlaybackState(false, player.currentPosition) // 关键：传递当前播放位置
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
            // ✅ 更新媒体会话播放状态 (播放时)
            mediaSessionManager.updatePlaybackState(true, player.currentPosition)
        }
    }
}


    fun seekTo(pos: Long) {
        val newPosition = if (pos < 0) 0L else if (pos > totalDuration.longValue) totalDuration.longValue else pos
        player.seekTo(newPosition)
        currentPosition.longValue = newPosition
        // ✅ 更新媒体会话播放状态
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
                
                // ✅ 更新媒体会话播放位置
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

    private fun saveSearchHistory() {
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
     * 打印队列状态（调试用）
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
        // ✅ 释放媒体会话资源
        mediaSessionManager.release()
        player.release()
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
