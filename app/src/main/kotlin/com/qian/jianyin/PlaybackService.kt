package com.qian.jianyin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    /**
     * 媒体会话实例
     */
    private var mediaSession: MediaSession? = null
    
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
        
        // 创建通知渠道
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
     * 
     * @param intent 启动服务的 Intent
     * @param flags 启动标志
     * @param startId 启动 ID
     * @return 服务启动模式
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 让 AndroidX 的 MediaButtonReceiver 处理媒体按钮事件
        intent?.let {
            if (Intent.ACTION_MEDIA_BUTTON == it.action) {
                // 获取 MediaSessionManager 实例
                val mediaSessionManager = MediaSessionManager.getInstance(applicationContext)
                // 获取 MediaSessionCompat
                val session = mediaSessionManager.getMediaSession()
                
                // 将事件传递给 MediaSessionCompat
                val keyEvent = it.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent != null) {
                    val handled = session?.controller?.dispatchMediaButtonEvent(keyEvent) ?: false
                    if (handled) {
                        Log.d("PlaybackService", "媒体按钮事件已处理")
                    }
                }
            }
        }
        
        // 确保服务在前台运行
        val mainIntent = Intent(this, MainActivity::class.java)
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
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
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
        
        return START_STICKY
    }

    /**
     * 获取媒体会话实例
     * 
     * 当控制器请求媒体会话时调用，返回当前的 MediaSession 实例。
     * 
     * @param controllerInfo 控制器信息
     * @return MediaSession 实例
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    /**
     * 销毁服务时调用
     * 
     * 释放 ExoPlayer 和 MediaSession 资源，
     * 清理相关引用。
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
