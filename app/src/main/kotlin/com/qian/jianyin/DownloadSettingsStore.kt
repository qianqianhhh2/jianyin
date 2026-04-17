package com.qian.jianyin

import android.content.Context
import android.os.Environment

/**
 * 下载设置存储类
 * 负责管理下载路径的设置，包括默认路径和自定义路径
 */
object DownloadSettingsStore {
    private const val PREFS_NAME = "download_settings"
    private const val KEY_CUSTOM_PATH = "custom_download_path"
    private const val KEY_USE_CUSTOM_PATH = "use_custom_path"
    private const val KEY_DOWNLOAD_QUALITY = "download_quality"
    private const val KEY_PLAY_QUALITY = "play_quality"
    private const val KEY_LYRIC_SOURCE = "lyric_source" // 0: 内嵌, 1: 网络
    
    /**
     * 获取当前下载路径
     * @param context 上下文
     * @return 下载路径
     */
    fun getDownloadPath(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val useCustom = prefs.getBoolean(KEY_USE_CUSTOM_PATH, false)
        
        return if (useCustom) {
            prefs.getString(KEY_CUSTOM_PATH, null) ?: getDefaultDownloadPath()
        } else {
            getDefaultDownloadPath()
        }
    }
    
    /**
     * 设置自定义下载路径
     * @param context 上下文
     * @param path 自定义路径，如果为 null 则使用默认路径
     */
    fun setCustomPath(context: Context, path: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (path != null) {
            prefs.putBoolean(KEY_USE_CUSTOM_PATH, true)
            prefs.putString(KEY_CUSTOM_PATH, path)
        } else {
            prefs.putBoolean(KEY_USE_CUSTOM_PATH, false)
            prefs.remove(KEY_CUSTOM_PATH)
        }
        prefs.apply()
    }
    
    /**
     * 检查是否使用自定义下载路径
     * @param context 上下文
     * @return 是否使用自定义路径
     */
    fun isUsingCustomPath(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_CUSTOM_PATH, false)
    }
    
    /**
     * 获取自定义下载路径
     * @param context 上下文
     * @return 自定义路径，如果未设置则返回 null
     */
    fun getCustomPath(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_PATH, null)
    }
    
    /**
     * 获取默认下载路径
     * @return 默认下载路径
     */
    private fun getDefaultDownloadPath(): String {
        return "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/jianyin"
    }
    
    /**
     * 获取下载音质设置
     * @param context 上下文
     * @return 音质值，默认 192
     */
    fun getDownloadQuality(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DOWNLOAD_QUALITY, 320)
    }
    
    /**
     * 设置下载音质
     * @param context 上下文
     * @param quality 音质值
     */
    fun setDownloadQuality(context: Context, quality: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_DOWNLOAD_QUALITY, quality)
            .apply()
    }
    
    /**
     * 获取播放音质设置
     * @param context 上下文
     * @return 音质值，默认 192
     */
    fun getPlayQuality(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_PLAY_QUALITY, 320)
    }
    
    /**
     * 设置播放音质
     * @param context 上下文
     * @param quality 音质值
     */
    fun setPlayQuality(context: Context, quality: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_PLAY_QUALITY, quality)
            .apply()
    }
    
    /**
     * 获取歌词来源设置
     * @param context 上下文
     * @return 歌词来源，0: 内嵌, 1: 网络，默认 0
     */
    fun getLyricSource(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LYRIC_SOURCE, 0)
    }
    
    /**
     * 设置歌词来源
     * @param context 上下文
     * @param source 歌词来源，0: 内嵌, 1: 网络
     */
    fun setLyricSource(context: Context, source: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_LYRIC_SOURCE, source)
            .commit()
    }
}
