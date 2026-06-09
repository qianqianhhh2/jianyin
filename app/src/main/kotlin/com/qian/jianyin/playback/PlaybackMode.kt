package com.qian.jianyin

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 播放模式枚举
 * 定义应用支持的播放模式
 */
enum class PlaybackMode(val label: String) {
    /** 顺序播放模式 */
    SEQUENCE("顺序播放"),
    /** 随机播放模式 */
    RANDOM("随机播放"),
    /** 单曲循环模式 */
    SINGLE("单曲循环"),
    /** 持续播放模式 - 播完当前歌单后按顺序播放下一个歌单，播完所有歌单后停止 */
    CONTINUOUS("持续播放"),
    /** 心动模式 - 从今日推荐、个性化推荐和用户曲库中按比例随机选取歌曲 */
    HEARTBEAT("心动模式");

    /**
     * 获取对应播放模式的图标
     * @return 图标资源
     */
    fun getIcon(): ImageVector {
        return when (this) {
            SEQUENCE -> Icons.Default.Repeat
            RANDOM -> Icons.Default.Shuffle
            SINGLE -> Icons.Default.RepeatOne
            CONTINUOUS -> Icons.Default.PlaylistPlay
            HEARTBEAT -> Icons.Outlined.HeartBroken
        }
    }

    /**
     * 获取心动模式的自定义图标资源ID
     * @return 图标资源ID，若无自定义图标则返回null
     */
    fun getCustomIconRes(): Int? {
        return when (this) {
            HEARTBEAT -> com.qian.jianyin.R.drawable.suki
            else -> null
        }
    }

    /**
     * 获取下一个播放模式
     * @return 下一个播放模式
     */
    fun next(): PlaybackMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}
