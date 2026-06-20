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
import androidx.media3.session.MediaSessionService
import androidx.media3.common.util.UnstableApi

/**
 * 播放服务
 *
 * 继承自 MediaSessionService，提供前台服务能力，
 * 由 MediaSessionManager 统一管理媒体会话。
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

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
     */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()
    }

    /**
     * 创建通知渠道
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
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
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
     * 获取媒体会话实例（由 MediaSessionManager 管理）
     */
    override fun onGetSession(controllerInfo: androidx.media3.session.MediaSession.ControllerInfo): androidx.media3.session.MediaSession? {
        // 返回 null，媒体会话由 MediaSessionManager 统一管理
        return null
    }

    /**
     * 销毁服务时调用
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d("PlaybackService", "服务销毁")
    }
}
