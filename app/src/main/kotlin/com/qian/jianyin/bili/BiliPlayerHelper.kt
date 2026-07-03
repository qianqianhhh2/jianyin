package com.qian.jianyin

import android.content.Context
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.qian.jianyin.bili.BiliApi

@UnstableApi
object BiliPlayerHelper {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val REFERER = "https://www.bilibili.com"

    fun createPlayer(context: Context, biliApi: BiliApi): ExoPlayer {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to REFERER))

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        // 关闭音频卸载 —— 国产系统魔改的音频 HAL 层容易导致卸载后无法恢复
        val audioOffload = TrackSelectionParameters.AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
            )
            .build()
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(audioOffload)
            .build()

        // 设置 WakeMode 为 NETWORK —— 播放时自动保持 CPU 唤醒
        player.setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)

        return player
    }
}