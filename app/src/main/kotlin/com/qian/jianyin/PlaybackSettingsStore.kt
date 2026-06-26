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
    private const val KEY_GRADIENT_BRIGHTNESS = "gradient_brightness" // 大播放器渐变层亮度 (0.0-1.0)

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

    // ========== 大播放器渐变层亮度设置 ==========

    // 默认渐变层透明度值（基准值）
    const val DEFAULT_GRADIENT_TOP_ALPHA = 0.5f
    const val DEFAULT_GRADIENT_MIDDLE_ALPHA = 0.1f
    const val DEFAULT_GRADIENT_BOTTOM_ALPHA = 0.9f

    /**
     * 获取大播放器渐变层亮度调整系数
     * @param context 上下文
     * @return 亮度调整系数（0.1-2.0），默认 1.0（原始亮度）
     */
    fun getGradientBrightnessMultiplier(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_GRADIENT_BRIGHTNESS, 1.0f)
    }

    /**
     * 设置大播放器渐变层亮度调整系数
     * @param context 上下文
     * @param multiplier 亮度调整系数（0.1-2.0），1.0为原始亮度
     */
    fun setGradientBrightnessMultiplier(context: Context, multiplier: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_GRADIENT_BRIGHTNESS, multiplier.coerceIn(0.1f, 2.0f))
            .apply()
    }

}
