package com.qian.jianyin

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import moe.ouom.biliapi.BiliApi

@UnstableApi
object BiliPlayerHelper {
    private const val BILI_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val BILI_REFERER = "https://www.bilibili.com"

    fun createPlayer(context: Context, biliApi: BiliApi): ExoPlayer {
        val cookies = biliApi.getCookies()
        val cookieString = if (cookies.isNotEmpty()) {
            cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        } else ""

        val defaultProperties = mutableMapOf<String, String>()
        defaultProperties["Referer"] = BILI_REFERER
        if (cookieString.isNotEmpty()) {
            defaultProperties["Cookie"] = cookieString
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(BILI_USER_AGENT)
            .setDefaultRequestProperties(defaultProperties)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
}