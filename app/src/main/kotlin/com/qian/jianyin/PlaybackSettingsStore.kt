package com.qian.jianyin

import android.content.Context

/**
 * 播放设置存储类
 * 负责管理播放相关的设置，如歌词字体大小、屏幕常亮等
 */
object PlaybackSettingsStore {
    private const val PREFS_NAME = "playback_settings"
    private const val KEY_LYRIC_FONT_SIZE = "lyric_font_size" // 歌词字体大小 (sp)
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"   // 屏幕常亮

    // ========== 歌词字体大小设置 ==========

    /**
     * 获取歌词字体大小
     * @param context 上下文
     * @return 字体大小（sp），默认 18sp
     */
    fun getLyricFontSize(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_LYRIC_FONT_SIZE, 18f)
    }

    /**
     * 设置歌词字体大小
     * @param context 上下文
     * @param fontSize 字体大小（sp）
     */
    fun setLyricFontSize(context: Context, fontSize: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_LYRIC_FONT_SIZE, fontSize)
            .apply()
    }

    // ========== 屏幕常亮设置 ==========

    /**
     * 获取屏幕常亮设置
     * @param context 上下文
     * @return 是否开启屏幕常亮，默认 false
     */
    fun isKeepScreenOnEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_SCREEN_ON, false)
    }

    /**
     * 设置屏幕常亮
     * @param context 上下文
     * @param enabled 是否开启
     */
    fun setKeepScreenOnEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_KEEP_SCREEN_ON, enabled)
            .apply()
    }
}
