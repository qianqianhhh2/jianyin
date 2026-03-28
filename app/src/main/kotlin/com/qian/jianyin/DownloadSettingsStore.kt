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
}
