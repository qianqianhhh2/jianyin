package com.qian.jianyin.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.qian.jianyin.MainActivity
import com.qian.jianyin.MediaSessionManager
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 统一音频播放服务
 *
 * 一个服务统一管理：
 * - MediaSession（媒体会话，处理蓝牙/线控/系统通知栏控制）
 * - 前台通知（既是保活通知也是媒体控制通知，只有 ONE 通知）
 * - WakeLock（CPU 唤醒锁）
 * - Audio Becoming Noisy（耳机拔出自动暂停）
 *
 * 参考 NeriPlayer 的 AudioPlayerService 架构，避免多通知抢音频焦点的问题。
 */
class AudioPlaybackService : Service() {

    companion object {
        private const val TAG = "AudioPlaybackSvc"
        private const val CHANNEL_ID = "jianyin_playback_channel"
        private const val NOTIFICATION_ID = 101

        // Intent Actions（通知按钮点击后发回 Service 的 action）
        const val ACTION_PLAY = "com.qian.jianyin.action.PLAY"
        const val ACTION_PAUSE = "com.qian.jianyin.action.PAUSE"
        const val ACTION_NEXT = "com.qian.jianyin.action.NEXT"
        const val ACTION_PREV = "com.qian.jianyin.action.PREVIOUS"
        const val ACTION_STOP = "com.qian.jianyin.action.STOP"
        const val ACTION_SEEK_TO = "com.qian.jianyin.action.SEEK_TO"

        @Volatile
        private var instance: AudioPlaybackService? = null

        /** 静态回调持有者，用于 ViewModel 设置回调后服务读取 */
        @Volatile
        var pendingControlCallback: MediaSessionManager.MediaControlCallback? = null

        // ---------- 供外部（ViewModel）调用的静态方法 ----------

        fun updatePlaybackState(context: Context, isPlaying: Boolean, position: Long = 0L) {
            instance?.apply {
                // 延迟绑定：如果回调还没设置（在 MainActivity 启动了服务但 ViewModel 还没初始化），现在绑定
                if (controlCallback == null) {
                    pendingControlCallback?.let {
                        controlCallback = it
                        pendingControlCallback = null
                    }
                }
                updatePlaybackStateInternal(isPlaying, position)
            }
        }

        fun updateMetadata(
            context: Context,
            title: String,
            artist: String,
            album: String? = null,
            duration: Long = 0L,
            artworkUrl: String? = null
        ) {
            instance?.updateMetadataInternal(title, artist, album, duration, artworkUrl)
        }

        fun acquireWakeLock(context: Context) {
            instance?.acquireWakeLockInternal()
        }

        fun releaseWakeLock(context: Context) {
            instance?.releaseWakeLockInternal()
        }

        fun notifyPlayState(isPlaying: Boolean) {
            instance?.updateNotification()
        }
    }

    // --- 核心组件 ---
    private lateinit var mediaSession: MediaSessionCompat
    private val notificationManager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    // --- WakeLock ---
    private var wakeLock: PowerManager.WakeLock? = null

    // --- 状态 ---
    private var isPlaying = false
    private var latestPosition: Long = 0L
    private var currentMetadata: MediaMetadataCompat? = null
    private var currentArtworkBitmap: Bitmap? = null
    private val artworkCache = mutableMapOf<String, Bitmap>()
    private var currentArtworkUrl: String? = null

    // --- 协程 ---
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // --- 耳机拔出广播 ---
    private lateinit var becomingNoisyReceiver: BroadcastReceiver

    // --- 回调（通知 ViewModel） ---
    var controlCallback: MediaSessionManager.MediaControlCallback? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "onCreate")

        // 从静态持有者读取回调（由 MediaSessionManager.initialize() 设置）
        pendingControlCallback?.let {
            controlCallback = it
            pendingControlCallback = null
        }

        createNotificationChannel()

        // 创建 MediaSessionCompat
        mediaSession = MediaSessionCompat(this, "JianyinMusicSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(mediaSessionCallback)
            isActive = true
        }

        // 立即进入前台（bootstrap 通知）
        startForegroundImmediately(buildBootstrapNotification())

        // 注册耳机拔出广播
        becomingNoisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && isPlaying) {
                    Log.d(TAG, "耳机拔出，暂停播放")
                    controlCallback?.onPause()
                }
            }
        }
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(becomingNoisyReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(becomingNoisyReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action} flags=$flags startId=$startId")

        // 处理媒体按钮（来自 MediaButtonReceiver）
        if (intent != null) {
            MediaButtonReceiver.handleIntent(mediaSession, intent)
        }

        when (intent?.action) {
            ACTION_PLAY -> {
                controlCallback?.onPlay()
                updatePlaybackStateInternal(true, latestPosition)
            }
            ACTION_PAUSE -> {
                controlCallback?.onPause()
                updatePlaybackStateInternal(false, latestPosition)
            }
            ACTION_NEXT -> {
                controlCallback?.onNext()
            }
            ACTION_PREV -> {
                controlCallback?.onPrevious()
            }
            ACTION_STOP -> {
                controlCallback?.onStop()
                hideNotification()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SEEK_TO -> {
                val pos = intent.getLongExtra("position", 0L)
                controlCallback?.onSeekTo(pos)
            }
            null -> {
                // 被系统杀死后由 START_STICKY 重建，无 Intent
                Log.d(TAG, "Sticky restart, no intent")
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved — 保持播放")
        if (isPlaying) {
            // 播放中不做任何事，保持服务运行
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        instance = null
        unregisterReceiver(becomingNoisyReceiver)
        serviceScope.cancel()
        releaseWakeLockInternal()
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    // ========== MediaSession Callback ==========

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Log.d(TAG, "MediaSession: onPlay")
            controlCallback?.onPlay()
            updatePlaybackStateInternal(true, latestPosition)
        }

        override fun onPause() {
            Log.d(TAG, "MediaSession: onPause")
            controlCallback?.onPause()
            updatePlaybackStateInternal(false, latestPosition)
        }

        override fun onStop() {
            Log.d(TAG, "MediaSession: onStop")
            controlCallback?.onStop()
            hideNotification()
        }

        override fun onSkipToNext() {
            Log.d(TAG, "MediaSession: onSkipToNext")
            controlCallback?.onNext()
        }

        override fun onSkipToPrevious() {
            Log.d(TAG, "MediaSession: onSkipToPrevious")
            controlCallback?.onPrevious()
        }

        override fun onSeekTo(pos: Long) {
            Log.d(TAG, "MediaSession: onSeekTo $pos")
            controlCallback?.onSeekTo(pos)
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
            if (mediaButtonEvent == null) return false
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (isPlaying) controlCallback?.onPause() else controlCallback?.onPlay()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        controlCallback?.onPlay()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        controlCallback?.onPause()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        controlCallback?.onNext()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        controlCallback?.onPrevious()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        controlCallback?.onStop()
                        return true
                    }
                }
            }
            return super.onMediaButtonEvent(mediaButtonEvent)
        }
    }

    // ========== 播放状态 / 元数据 更新 ==========

    fun updatePlaybackStateInternal(isPlaying: Boolean, position: Long = 0L) {
        this.isPlaying = isPlaying
        this.latestPosition = position

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, position, 1.0f)

        mediaSession.setPlaybackState(stateBuilder.build())
        updateNotification()
    }

    fun updateMetadataInternal(
        title: String,
        artist: String,
        album: String? = null,
        duration: Long = 0L,
        artworkUrl: String? = null
    ) {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album ?: "未知专辑")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)

        currentArtworkBitmap?.let {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
        }

        currentMetadata = metadataBuilder.build()
        mediaSession.setMetadata(currentMetadata)

        // 异步加载封面
        if (artworkUrl != null && artworkUrl != currentArtworkUrl) {
            currentArtworkUrl = artworkUrl
            loadArtworkAsync(artworkUrl)
        } else {
            updateNotification()
        }
    }

    private fun loadArtworkAsync(artworkUrl: String) {
        artworkCache[artworkUrl]?.let {
            currentArtworkBitmap = it
            updateMetadataArtworkInternal(it)
            updateNotification()
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val bitmap = if (artworkUrl.startsWith("http")) {
                    downloadBitmap(artworkUrl)
                } else {
                    loadBitmapFromFile(artworkUrl)
                }
                if (bitmap != null && artworkUrl == currentArtworkUrl) {
                    artworkCache[artworkUrl] = bitmap
                    withContext(Dispatchers.Main) {
                        currentArtworkBitmap = bitmap
                        updateMetadataArtworkInternal(bitmap)
                        updateNotification()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载封面失败", e)
                withContext(Dispatchers.Main) {
                    updateNotification()
                }
            }
        }
    }

    private fun updateMetadataArtworkInternal(bitmap: Bitmap) {
        currentMetadata?.let { metadata ->
            val updated = MediaMetadataCompat.Builder(metadata)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                .build()
            currentMetadata = updated
            mediaSession.setMetadata(updated)
        }
    }

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

    private fun loadBitmapFromFile(filePath: String): Bitmap? {
        return try {
            if (filePath.startsWith("content://")) {
                val uri = Uri.parse(filePath)
                contentResolver.openInputStream(uri)?.use { input ->
                    if (filePath.lowercase().endsWith(".gif")) {
                        decodeGifFirstFrame(input)
                    } else {
                        BitmapFactory.decodeStream(input)
                    }
                }
            } else {
                val file = File(filePath)
                if (file.exists()) {
                    if (filePath.lowercase().endsWith(".gif")) {
                        decodeGifFirstFrame(file.inputStream())
                    } else {
                        BitmapFactory.decodeFile(filePath)
                    }
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载本地封面失败", e)
            null
        }
    }

    private fun decodeGifFirstFrame(inputStream: InputStream): Bitmap? {
        return try {
            val movie = android.graphics.Movie.decodeStream(inputStream)
            if (movie != null) {
                val bitmap = Bitmap.createBitmap(movie.width(), movie.height(), Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                movie.draw(canvas, 0f, 0f)
                bitmap
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // ========== 通知 ==========

    fun updateNotification(artworkBitmap: Bitmap? = null) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    fun hideNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun buildBootstrapNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("简音")
            .setContentText("准备播放...")
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = currentMetadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "简音"
        val artist = currentMetadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
        val duration = currentMetadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
        val artwork = currentArtworkBitmap ?: currentMetadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist.ifEmpty { "未在播放" })
            .setLargeIcon(artwork)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        // 进度条
        if (duration > 0) {
            val pos = mediaSession.controller?.playbackState?.position ?: latestPosition
            builder.setProgress(duration.toInt(), pos.toInt(), false)

            val formatTime = { ms: Long ->
                val totalSeconds = ms / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                String.format("%d:%02d", minutes, seconds)
            }
            builder.setSubText("${formatTime(pos)} / ${formatTime(duration)}")
        }

        // 控制按钮
        val prevAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous, "上一首",
            buildServicePendingIntent(ACTION_PREV, 1)
        )
        val playPauseAction = NotificationCompat.Action(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "暂停" else "播放",
            buildServicePendingIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY, 2)
        )
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next, "下一首",
            buildServicePendingIntent(ACTION_NEXT, 3)
        )

        builder.addAction(prevAction)
        builder.addAction(playPauseAction)
        builder.addAction(nextAction)

        // 删除时停止（滑动关闭通知）
        builder.setDeleteIntent(buildServicePendingIntent(ACTION_STOP, 5))

        return builder.build()
    }

    /**
     * 构建指向本 Service 的 PendingIntent（通知按钮点击后通过 onStartCommand 处理）
     */
    private fun buildServicePendingIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this,
            requestCode,
            Intent(this, AudioPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ========== 通知渠道 ==========

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "简音播放控制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放控制通知"
                setShowBadge(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    // ========== 前台服务 ==========

    private fun startForegroundImmediately(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "startForeground success")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    // ========== WakeLock ==========

    internal fun acquireWakeLockInternal() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "jianyin:playback_wakelock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.let { wl ->
                if (!wl.isHeld) {
                    wl.acquire(10 * 60 * 1000L) // 10分钟超时，足够覆盖正常播放场景
                    Log.d(TAG, "WakeLock acquired")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock", e)
        }
    }

    internal fun releaseWakeLockInternal() {
        try {
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                    Log.d(TAG, "WakeLock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock", e)
        }
    }
}
