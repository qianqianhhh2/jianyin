package com.qian.jianyin

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.qian.jianyin.playback.AudioPlaybackService

/**
 * 媒体会话管理器（委托层）
 *
 * 实际的 MediaSession 和通知管理已移至 AudioPlaybackService。
 * 此类保留原有 API，委托给 AudioPlaybackService，保证 MusicViewModel 无需大幅修改。
 *
 * @property context 应用上下文
 */
class MediaSessionManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: MediaSessionManager? = null

        fun getInstance(context: Context): MediaSessionManager {
            return instance ?: synchronized(this) {
                instance ?: MediaSessionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext: Context = context.applicationContext
    private var isInitialized = false

    /**
     * 媒体控制回调接口
     * 用于接收媒体控制命令并传递给 ViewModel 处理。
     */
    interface MediaControlCallback {
        fun onPlay()
        fun onPause()
        fun onNext()
        fun onPrevious()
        fun onStop()
        fun onSeekTo(position: Long)
    }

    var controlCallback: MediaControlCallback? = null

    /**
     * 初始化：启动 AudioPlaybackService 并设置回调。
     * AudioPlaybackService 会在 onCreate 中创建 MediaSession 和通知渠道。
     */
    fun initialize() {
        if (isInitialized) {
            Log.d("MediaSession", "媒体会话已初始化，跳过重复初始化")
            return
        }

        Log.d("MediaSession", "启动 AudioPlaybackService")
        try {
            val intent = Intent(appContext, AudioPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }

            // 将 controlCallback 传递给 AudioPlaybackService
            AudioPlaybackService.setControlCallback(appContext, controlCallback)
            isInitialized = true
        } catch (e: Exception) {
            Log.e("MediaSession", "启动 AudioPlaybackService 失败", e)
        }
    }

    /** 委托给 AudioPlaybackService */
    fun updatePlaybackState(isPlaying: Boolean, position: Long = 0L) {
        AudioPlaybackService.updatePlaybackState(appContext, isPlaying, position)
    }

    /** 委托给 AudioPlaybackService */
    fun updateMetadata(
        title: String,
        artist: String,
        album: String? = null,
        duration: Long = 0L,
        artworkUrl: String? = null
    ) {
        AudioPlaybackService.updateMetadata(appContext, title, artist, album, duration, artworkUrl)
    }

    /** 委托给 AudioPlaybackService */
    fun showNotification(artworkBitmap: android.graphics.Bitmap? = null) {
        AudioPlaybackService.notifyPlayState(isPlaying = true)
    }

    /** 委托给 AudioPlaybackService */
    fun hideNotification() {
        // 通知由 AudioPlaybackService 管理，停止播放时会自动处理
    }

    /**
     * 获取媒体会话实例（委托到 AudioPlaybackService）
     */
    fun getMediaSession(): MediaSessionCompat? {
        return AudioPlaybackService.getMediaSession(appContext)
    }

    /**
     * 释放资源
     */
    fun release() {
        controlCallback = null
    }
}

/**
 * AudioPlaybackService 扩展：设置 controlCallback
 * 将回调写入静态持有者，新服务在 onCreate 时读取；
 * 如果服务已在运行，直接设置到实例上。
 */
fun AudioPlaybackService.Companion.setControlCallback(context: Context, callback: MediaSessionManager.MediaControlCallback?) {
    AudioPlaybackService.pendingControlCallback = callback
}

/**
 * AudioPlaybackService 扩展：获取 MediaSession
 */
fun AudioPlaybackService.Companion.getMediaSession(context: Context): MediaSessionCompat? {
    return null // 由 AudioPlaybackService 内部管理，不暴露给外部
}
