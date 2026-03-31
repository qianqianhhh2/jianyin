package com.qian.jianyin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.common.util.UnstableApi

/**
 * 播放服务
 *
 * 继承自 MediaSessionService，提供媒体播放功能，
 * 处理媒体按钮事件和前台服务通知。
 *
 * 使用 ExoPlayer 作为媒体播放器，
 * 与 MediaSessionManager 配合处理媒体控制。
 *
 * 支持绑定机制保活，当客户端绑定时服务优先级提升。
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    /**
     * 媒体会话实例
     */
    private var mediaSession: MediaSession? = null

    /**
     * 绑定客户端计数
     */
    private var bindCount = 0

    /**
     * 通知渠道 ID
     */
    private val CHANNEL_ID = "jianyin_music_channel"

    /**
     * 通知 ID
     */
    private val NOTIFICATION_ID = 101

    /**
     * 创建服务时调用
     *
     * 初始化通知渠道、ExoPlayer 和 MediaSession。
     */
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player)
            .setId("JianyinMusicSession")
            .build()
    }

    /**
     * 创建通知渠道
     *
     * 为 Android 8.0+ 设备创建媒体通知渠道，
     * 用于显示前台服务通知。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "简音",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放控制"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 启动服务时调用
     *
     * 处理媒体按钮事件，确保服务在前台运行，
     * 并返回 START_STICKY 确保服务被系统杀死后能重新启动。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (Intent.ACTION_MEDIA_BUTTON == it.action) {
                val mediaSessionManager = MediaSessionManager.getInstance(applicationContext)
                val session = mediaSessionManager.getMediaSession()
                val keyEvent = it.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent != null) {
                    val handled = session?.controller?.dispatchMediaButtonEvent(keyEvent) ?: false
                    if (handled) {
                        Log.d("PlaybackService", "媒体按钮事件已处理")
                    }
                }
            }
        }

        startForegroundService()
        return START_STICKY
    }

    /**
     * 启动前台服务通知
     */
    private fun startForegroundService() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("简音")
            .setContentText("音乐播放中")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * 绑定服务时调用
     *
     * 增加绑定计数，提升服务优先级。
     */
    override fun onBind(intent: Intent?): IBinder? {
        bindCount++
        Log.d("PlaybackService", "服务被绑定，当前绑定数: $bindCount")
        return super.onBind(intent)
    }

    /**
     * 解绑服务时调用
     *
     * 减少绑定计数。
     */
    override fun onUnbind(intent: Intent?): Boolean {
        bindCount--
        Log.d("PlaybackService", "服务解绑，当前绑定数: $bindCount")
        return super.onUnbind(intent)
    }

    /**
     * 获取媒体会话实例
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    /**
     * 销毁服务时调用
     *
     * 释放 ExoPlayer 和 MediaSession 资源。
     */
    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
