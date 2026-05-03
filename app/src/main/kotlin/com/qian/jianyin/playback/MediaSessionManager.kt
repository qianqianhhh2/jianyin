package com.qian.jianyin

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.lang.ref.WeakReference
import androidx.media.session.MediaButtonReceiver

/**
 * 媒体会话管理器
 * 
 * 负责管理应用的媒体会话，让系统识别应用为媒体播放器，
 * 支持蓝牙耳机控制、系统媒体控制和媒体通知显示。
 * 
 * 使用 MediaSessionCompat + NotificationCompat.MediaStyle 实现，
 * 提供完整的媒体控制功能和状态管理。
 * 
 * @property context 应用上下文
 */
class MediaSessionManager private constructor(context: Context) {
    
    /**
     * 伴生对象
     * 
     * 提供单例实例获取方法和常量定义。
     */
    companion object {
        @Volatile
        private var instance: MediaSessionManager? = null
        
        /**
         * 获取 MediaSessionManager 单例实例
         * 
         * @param context 应用上下文
         * @return MediaSessionManager 实例
         */
        fun getInstance(context: Context): MediaSessionManager {
            return instance ?: synchronized(this) {
                instance ?: MediaSessionManager(context.applicationContext).also { instance = it }
            }
        }
        
        // 通知相关常量
        private const val CHANNEL_ID = "jianyin_music_channel"
        private const val CHANNEL_NAME = "简音"
        private const val NOTIFICATION_ID = 101
        
        // 媒体动作常量
        private const val ACTION_PLAY = "com.qian.jianyin.ACTION_PLAY"
        private const val ACTION_PAUSE = "com.qian.jianyin.ACTION_PAUSE"
        private const val ACTION_NEXT = "com.qian.jianyin.ACTION_NEXT"
        private const val ACTION_PREVIOUS = "com.qian.jianyin.ACTION_PREVIOUS"
        private const val ACTION_STOP = "com.qian.jianyin.ACTION_STOP"
    }
    
    /**
     * 上下文弱引用，避免内存泄漏
     */
    private val contextRef = WeakReference(context)
    
    /**
     * 应用上下文
     */
    private val context: Context get() = contextRef.get() ?: throw IllegalStateException("Context is null")
    
    /**
     * 媒体会话实例，核心组件
     */
    private var mediaSession: MediaSessionCompat? = null
    
    /**
     * 通知管理器
     */
    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    /**
     * 当前播放状态
     */
    private var isPlaying = false
    
    /**
     * 当前媒体元数据
     */
    private var currentMetadata: MediaMetadataCompat? = null
    
    /**
     * 最新播放位置
     */
    private var latestPlaybackPosition: Long = 0L

    /**
     * 封面图片缓存，避免重复加载
     */
    private val artworkCache = mutableMapOf<String, Bitmap>()

    
    /**
     * 控制回调接口，用于将控制命令传递给 ViewModel
     */
    var controlCallback: MediaControlCallback? = null
    
    /**
     * 媒体控制回调接口
     * 
     * 用于接收媒体控制命令并传递给 ViewModel 处理。
     */
    interface MediaControlCallback {
        /**
         * 播放命令回调
         */
        fun onPlay()
        
        /**
         * 暂停命令回调
         */
        fun onPause()
        
        /**
         * 下一首命令回调
         */
        fun onNext()
        
        /**
         * 上一首命令回调
         */
        fun onPrevious()
        
        /**
         * 停止命令回调
         */
        fun onStop()
        
        /**
         * 跳转命令回调
         * 
         * @param position 目标位置（毫秒）
         */
        fun onSeekTo(position: Long)
    }
    
    /**
     * 初始化媒体会话
     * 
     * 创建通知渠道、初始化 MediaSessionCompat 实例，
     * 设置回调处理系统控制命令，并激活会话。
     */
    fun initialize() {
        Log.d("MediaSession", "开始初始化媒体会话")
        
        // 1. 创建通知渠道 (Android 8.0+ 必需)
        createNotificationChannel()
        
        // 2. 创建 MediaSessionCompat
        mediaSession = MediaSessionCompat(context, "JianyinMusicSession").apply {
            Log.d("MediaSession", "创建 MediaSessionCompat 实例")
            
            // ✅ 设置完整的标志，支持蓝牙耳机和系统控制
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            
            // 3. 设置回调，处理来自系统的控制命令
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    Log.d("MediaSession", "系统控制: 播放命令接收")
                    controlCallback?.onPlay()
                    updatePlaybackState(true, latestPlaybackPosition)
                }
                
                override fun onPause() {
                    Log.d("MediaSession", "系统控制: 暂停命令接收")
                    controlCallback?.onPause()
                    updatePlaybackState(false, latestPlaybackPosition)
                }
                
                override fun onStop() {
                    Log.d("MediaSession", "系统控制: 停止命令接收")
                    controlCallback?.onStop()
                    hideNotification()
                }
                
                override fun onSkipToNext() {
                    Log.d("MediaSession", "系统控制: 下一首命令接收")
                    controlCallback?.onNext()
                }
                
                override fun onSkipToPrevious() {
                    Log.d("MediaSession", "系统控制: 上一首命令接收")
                    controlCallback?.onPrevious()
                }
                
                override fun onSeekTo(pos: Long) {
                    Log.d("MediaSession", "系统控制: 跳转到 $pos")
                    controlCallback?.onSeekTo(pos)
                }
                
                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    if (mediaButtonEvent == null) {
                        return false
                    }
                    
                    Log.d("MediaSession", "物理按钮事件接收: ${mediaButtonEvent.action}")
                    
                    // 获取 KeyEvent
                    val keyEvent = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                Log.d("MediaSession", "媒体按钮: 播放/暂停")
                                controlCallback?.let { 
                                    if (isPlaying) it.onPause() else it.onPlay() 
                                }
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                Log.d("MediaSession", "媒体按钮: 播放")
                                controlCallback?.onPlay()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                Log.d("MediaSession", "媒体按钮: 暂停")
                                controlCallback?.onPause()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                Log.d("MediaSession", "媒体按钮: 下一首")
                                controlCallback?.onNext()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                Log.d("MediaSession", "媒体按钮: 上一首")
                                controlCallback?.onPrevious()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_STOP -> {
                                Log.d("MediaSession", "媒体按钮: 停止")
                                controlCallback?.onStop()
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })
            
            // 4. 激活会话
            isActive = true
            Log.d("MediaSession", "MediaSession 已激活，controlCallback: ${controlCallback != null}")
        }
    }
    
    /**
     * 创建通知渠道
     * 
     * 为 Android 8.0+ 设备创建媒体通知渠道，
     * 用于显示媒体控制通知。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放控制"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // 静音
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 更新播放状态
     * 
     * 更新媒体会话的播放状态，包括播放/暂停状态和播放位置，
     * 并在播放状态时显示媒体控制通知。
     * 
     * @param isPlaying 是否正在播放
     * @param position 当前播放位置（毫秒）
     */
    fun updatePlaybackState(isPlaying: Boolean, position: Long = 0L) {
        this.isPlaying = isPlaying
        this.latestPlaybackPosition = position
        val stateBuilder = PlaybackStateCompat.Builder()
            // ✅ 必须包含所有蓝牙耳机支持的动作
            .setActions(
                PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_PAUSE
                    or PlaybackStateCompat.ACTION_PLAY_PAUSE
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    or PlaybackStateCompat.ACTION_STOP
                    or PlaybackStateCompat.ACTION_SEEK_TO
                    or PlaybackStateCompat.ACTION_FAST_FORWARD
                    or PlaybackStateCompat.ACTION_REWIND
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING 
                else PlaybackStateCompat.STATE_PAUSED,
                position,
                1.0f
            )
            .setActiveQueueItemId(0) // 设置活动的队列项ID
        
        mediaSession?.setPlaybackState(stateBuilder.build())
        
        Log.d("MediaSession", "更新播放状态: 正在播放=$isPlaying, 位置=$position")
        
        // 无论播放还是暂停，都显示通知，只是更新通知上的播放/暂停按钮状态
        showNotification()
    }
    
    /**
     * 更新媒体元数据
     * 
     * 更新当前播放媒体的元数据信息，包括标题、艺术家、专辑、时长和封面。
     * 封面图片会异步加载并更新。
     * 
     * @param title 歌曲标题
     * @param artist 艺术家
     * @param album 专辑名称
     * @param duration 歌曲时长（毫秒）
     * @param artworkUrl 封面图片URL或本地路径
     */
    fun updateMetadata(
        title: String,
        artist: String,
        album: String? = null,
        duration: Long = 0L,
        artworkUrl: String? = null
    ) {
        // 总是创建新的元数据构建器，避免保留旧的封面信息
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album ?: "未知专辑")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        
        currentMetadata = metadataBuilder.build()
        mediaSession?.setMetadata(currentMetadata)
        
        Log.d("MediaSession", "更新元数据: 标题=$title, 歌手=$artist, 时长=$duration, 封面=$artworkUrl")
        
        // 异步加载封面图片
        if (artworkUrl != null) {
            loadArtworkAsync(artworkUrl)
        } else {
            // 如果没有封面，直接显示通知
            if (isPlaying) {
                showNotification()
            }
        }
    }
    
    /**
     * 异步加载封面图片
     * 
     * 根据 URL 类型（网络或本地）异步加载封面图片，
     * 加载完成后更新媒体元数据和通知。
     * 
     * @param artworkUrl 封面图片URL或本地路径
     */
    private fun loadArtworkAsync(artworkUrl: String) {
        // 先检查缓存
        artworkCache[artworkUrl]?.let {
            Log.d("MediaSession", "从缓存加载封面")
            // 直接在主线程处理缓存的情况
            currentMetadata?.let { metadata ->
                val updatedMetadata = MediaMetadataCompat.Builder(metadata)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                    .build()
                
                mediaSession?.setMetadata(updatedMetadata)
                currentMetadata = updatedMetadata
                
                if (isPlaying) {
                    showNotification(artworkBitmap = it)
                }
            }
            return
        }
        
        Log.d("MediaSession", "开始加载封面: $artworkUrl")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = if (artworkUrl.startsWith("http")) {
                    // 网络URL，通过网络下载
                    Log.d("MediaSession", "从网络下载封面")
                    downloadBitmap(artworkUrl)
                } else {
                    // 本地文件路径，从本地加载
                    Log.d("MediaSession", "从本地加载封面")
                    loadBitmapFromFile(artworkUrl)
                }
                
                if (bitmap != null) {
                    // 缓存图片
                    artworkCache[artworkUrl] = bitmap
                    Log.d("MediaSession", "封面加载成功，大小: ${bitmap.width}x${bitmap.height}，已缓存")
                    withContext(Dispatchers.Main) {
                        currentMetadata?.let { metadata ->
                            val updatedMetadata = MediaMetadataCompat.Builder(metadata)
                                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                                .build()
                            
                            mediaSession?.setMetadata(updatedMetadata)
                            currentMetadata = updatedMetadata
                            
                            // 只在播放状态且通知需要更新时才显示
                            if (isPlaying) {
                                Log.d("MediaSession", "更新通知封面")
                                showNotification(artworkBitmap = bitmap)
                            }
                        }
                    }
                } else {
                    Log.d("MediaSession", "封面加载失败，bitmap为null")
                    // 加载失败时也要确保通知显示（无封面）
                    withContext(Dispatchers.Main) {
                        if (isPlaying) {
                            showNotification()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaSession", "加载封面异常", e)
                e.printStackTrace()
                // 异常时也要确保通知显示（无封面）
                withContext(Dispatchers.Main) {
                    if (isPlaying) {
                        showNotification()
                    }
                }
            }
        }
    }
    
    /**
     * 从网络下载位图
     * 
     * 通过 HTTP 连接下载网络图片并转换为 Bitmap。
     * 如果是 GIF 文件，只取第一帧。
     * 
     * @param url 图片URL
     * @return 下载的 Bitmap，失败则返回 null
     */
    private fun downloadBitmap(url: String): Bitmap? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input: InputStream = connection.inputStream
            
            if (url.lowercase().endsWith(".gif")) {
                decodeGifFirstFrame(input)
            } else {
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 从本地文件加载位图
     * 
     * 从本地文件系统加载图片并转换为 Bitmap。
     * 如果是 GIF 文件，只取第一帧。
     * 
     * @param filePath 本地文件路径
     * @return 加载的 Bitmap，失败则返回 null
     */
    private fun loadBitmapFromFile(filePath: String): Bitmap? {
        return try {
            Log.d("MediaSession", "尝试从本地加载封面: $filePath")
            val file = File(filePath)
            if (file.exists()) {
                Log.d("MediaSession", "本地封面文件存在: ${file.absolutePath}")
                val input = file.inputStream()
                
                if (filePath.lowercase().endsWith(".gif")) {
                    decodeGifFirstFrame(input)
                } else {
                    BitmapFactory.decodeFile(filePath)
                }
            } else {
                Log.d("MediaSession", "本地封面文件不存在: ${file.absolutePath}")
                null
            }
        } catch (e: Exception) {
            Log.e("MediaSession", "加载本地封面失败", e)
            null
        }
    }
    
    /**
     * 解码 GIF 文件的第一帧
     * 
     * @param inputStream GIF 文件输入流
     * @return 第一帧的 Bitmap，失败则返回 null
     */
    private fun decodeGifFirstFrame(inputStream: InputStream): Bitmap? {
        return try {
            val movie = android.graphics.Movie.decodeStream(inputStream)
            if (movie != null) {
                val bitmap = Bitmap.createBitmap(movie.width(), movie.height(), Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                movie.draw(canvas, 0f, 0f)
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("MediaSession", "解码GIF第一帧失败", e)
            null
        }
    }
    
    /**
     * 显示媒体控制通知
     * 
     * 创建并显示媒体控制通知，包含播放控制按钮和媒体信息，
     * 支持折叠视图和展开视图。
     * 
     * @param artworkBitmap 封面图片 Bitmap，可选
     */
    fun showNotification(artworkBitmap: Bitmap? = null) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 创建媒体样式通知
        val mediaStyle = MediaNotificationCompat.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken) // 关键：绑定到媒体会话
            .setShowActionsInCompactView(0, 1, 2) // 在折叠视图中显示的动作索引
            .setShowCancelButton(false) // 不显示取消按钮，避免与前台服务冲突
        
        // 获取当前元数据
        val title = currentMetadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "简音"
        val artist = currentMetadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "未知歌手"
        val duration = currentMetadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
        
        // 优先使用传入的封面，其次从元数据中获取
        val finalArtworkBitmap = artworkBitmap ?: currentMetadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
        
        // 构建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play) // 使用系统图标或您的应用图标
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(finalArtworkBitmap)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(mediaStyle) // 使用 MediaStyle
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // 保持通知常驻
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setDeleteIntent(
                buildMediaButtonPendingIntent(ACTION_STOP)
            )
    
        // ✅ 关键修复：添加进度条
        if (duration > 0) {
            val playbackState = mediaSession?.controller?.playbackState
            val currentPosition = playbackState?.position ?: 0L
            
            notification.setProgress(
                duration.toInt(), 
                currentPosition.toInt(), 
                false  // false 表示确定的进度，true 表示不确定的进度
            )
            
            // 显示进度文本
            val formatTime = { ms: Long ->
                val totalSeconds = ms / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                String.format("%d:%02d", minutes, seconds)
            }
            
            notification.setSubText("${formatTime(currentPosition)} / ${formatTime(duration)}")
        }
        
        // 添加控制按钮
        addNotificationActions(notification)
        
        // 显示通知，使用相同的通知 ID 确保与前台服务通知更新而不是替换
        val notificationObj = notification.build()
        notificationManager.notify(NOTIFICATION_ID, notificationObj)
        
        // 服务已经在 onCreate 中启动了前台模式，这里只需要更新通知
        
        Log.d("MediaSession", "显示通知: $title - $artist")
    }
    
    /**
     * 隐藏媒体控制通知
     * 
     * 取消显示的媒体控制通知。
     */
    fun hideNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        Log.d("MediaSession", "隐藏通知")
    }
    
    /**
     * 添加通知控制按钮
     * 
     * 为媒体控制通知添加播放/暂停、上一首、下一首等控制按钮。
     * 
     * @param builder 通知构建器
     */
    private fun addNotificationActions(builder: NotificationCompat.Builder) {
        // 上一首
        val prevAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            "上一首",
            buildMediaButtonPendingIntent(ACTION_PREVIOUS)
        )
        
        // 播放/暂停
        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseAction = NotificationCompat.Action(
            playPauseIcon,
            if (isPlaying) "暂停" else "播放",
            buildMediaButtonPendingIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
        )
        
        // 下一首
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "下一首",
            buildMediaButtonPendingIntent(ACTION_NEXT)
        )
        
        builder.addAction(prevAction)
        builder.addAction(playPauseAction)
        builder.addAction(nextAction)
    }
    
    /**
     * 构建媒体按钮的 PendingIntent
     * 
     * 使用 AndroidX 的标准方式处理媒体按钮事件，
     * 为不同的媒体动作创建对应的 PendingIntent。
     * 
     * @param action 媒体动作
     * @return 构建的 PendingIntent
     */
    private fun buildMediaButtonPendingIntent(action: String): PendingIntent {
        // 使用 AndroidX 的标准方式处理媒体按钮
        val keyCode = when (action) {
            ACTION_PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            ACTION_PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            ACTION_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            ACTION_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            ACTION_STOP -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        
        // 为每个动作使用不同的 requestCode，确保 PendingIntent 唯一
        val requestCode = when (action) {
            ACTION_PLAY -> 1
            ACTION_PAUSE -> 2
            ACTION_NEXT -> 3
            ACTION_PREVIOUS -> 4
            ACTION_STOP -> 5
            else -> 0
        }
        
        // 使用 AndroidX 的 MediaButtonReceiver 构建 PendingIntent
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
        intent.setClass(context, androidx.media.session.MediaButtonReceiver::class.java)
        intent.putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * 启动前台服务
     * 
     * 为服务创建前台通知并启动前台服务模式，
     * 提高服务优先级，减少被系统杀死的概率。
     * 
     * @param service 要启动为前台服务的 Service
     */
    fun startForegroundService(service: Service) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("简音")
            .setContentText("音乐播放中")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        service.startForeground(NOTIFICATION_ID, notification)
    }
    
    /**
     * 停止前台服务
     * 
     * 取消前台服务通知，停止前台服务模式。
     */
    fun stopForegroundService() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
    
    /**
     * 释放资源
     * 
     * 释放媒体会话资源，停止通知，清除回调引用。
     */
    fun release() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        controlCallback = null
    }
    /**
     * 获取媒体会话实例
     * 
     * @return MediaSessionCompat 实例，可能为 null
     */
    fun getMediaSession(): MediaSessionCompat? {
        return mediaSession
    }

}
