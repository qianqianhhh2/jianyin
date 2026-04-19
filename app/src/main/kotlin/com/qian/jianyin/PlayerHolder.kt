package com.qian.jianyin

import android.content.Context
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * 全局播放器单例持有者。
 * 确保整个应用中只有一个 ExoPlayer 实例，被 ViewModel 和 Service 共享。
 */
object PlayerHolder {
    // 使用‘by lazy’确保线程安全且仅在首次访问时初始化
    private var player: ExoPlayer? = null

    /**
     * 获取或创建全局播放器实例。
     * @param context 应用上下文，用于构建播放器。
     */
    @Synchronized
    fun getPlayer(context: Context): ExoPlayer {
        if (player == null) {
            // 在此处可以配置播放器的各项参数
            val trackSelector = DefaultTrackSelector(context)
            player = ExoPlayer.Builder(context)
                .setTrackSelector(trackSelector)
                .build().apply {
                    // 可以在这里设置一些播放器默认参数，如重复模式
                    repeatMode = ExoPlayer.REPEAT_MODE_OFF
                }
        }
        return player!!
    }

    /**
     * 释放播放器资源。在应用退出或确定不再需要时调用。
     */
    @Synchronized
    fun releasePlayer() {
        player?.release()
        player = null
    }
}
