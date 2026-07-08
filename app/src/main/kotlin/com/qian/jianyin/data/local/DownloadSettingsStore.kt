package com.qian.jianyin

import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 下载设置存储类
 * 负责管理下载路径的设置，包括默认路径和自定义路径
 * 使用SAF（Storage Access Framework）授权方式
 */
object DownloadSettingsStore {
    private const val PREFS_NAME = "download_settings"
    private const val KEY_CUSTOM_URI = "custom_download_uri"
    private const val KEY_USE_CUSTOM_PATH = "use_custom_path"
    private const val KEY_DOWNLOAD_QUALITY = "download_quality"
    private const val KEY_PLAY_QUALITY = "play_quality"
    private const val KEY_LYRIC_SOURCE = "lyric_source" // 0: 内嵌, 1: 网络
    private const val KEY_DARK_MODE = "dark_mode" // 0: 跟随系统, 1: 浅色, 2: 深色
    private const val KEY_FADE_ENABLED = "fade_enabled"
    private const val KEY_AUTO_CACHE_ENABLED = "auto_cache_enabled"
    private const val KEY_DEFAULT_MUSIC_OPENER = "default_music_opener"
    private const val KEY_KEEP_PLAYLIST_ON_EXIT = "keep_playlist_on_exit"    // 离开后保留列表
    private const val KEY_AUTO_PLAY_ON_START = "auto_play_on_start"            // 启动时播放
    private const val KEY_THEME_SOURCE = "theme_source"                         // 0: 内置配色, 1: 取色
    private const val KEY_SEED_COLOR = "seed_color"                             // 种子色 ARGB hex
    private const val KEY_VIBRATION_ENABLED = "vibration_enabled"               // 震动开关
    private const val KEY_BACKUP_AUDIO_API_URL = "backup_audio_api_url"         // 备用音源API地址
    private const val KEY_HAZE_EFFECT_ENABLED = "haze_effect_enabled"         // Haze效果开关
    // 旧版key用于迁移
    private const val KEY_CUSTOM_PATH_OLD = "custom_download_path"

    val NETEASE_QUALITY_FALLBACK_ORDER = listOf(
        "jymaster", "sky", "jyeffect", "hires", "lossless", "exhigh", "higher", "standard"
    )

    fun netEaseQualityLabel(key: String): String = when (key) {
        "standard" -> "标准 (128kbps)"
        "higher" -> "较高 (192kbps)"
        "exhigh" -> "极高 (320kbps)"
        "lossless" -> "无损 (CD级)"
        "hires" -> "Hi-Res (高解析)"
        "jyeffect" -> "高清环绕声"
        "sky" -> "沉浸环绕声"
        "jymaster" -> "母带"
        else -> key
    }

    fun netEaseQualityToBitrate(level: String): Int = when (level.lowercase()) {
        "standard" -> 128000
        "higher" -> 192000
        "exhigh" -> 320000
        "lossless", "hires", "jyeffect", "sky", "jymaster" -> 1411200
        else -> 320000
    }

    val qualityOptions: List<String>
        get() = NETEASE_QUALITY_FALLBACK_ORDER.reversed()

    private val _darkModeFlow = MutableStateFlow(0)
    val darkModeFlow: StateFlow<Int> = _darkModeFlow.asStateFlow()

    private val _fadeEnabledFlow = MutableStateFlow(false)
    val fadeEnabledFlow: StateFlow<Boolean> = _fadeEnabledFlow.asStateFlow()

    private val _themeSourceFlow = MutableStateFlow(0) // 0: 内置, 1: 取色
    val themeSourceFlow: StateFlow<Int> = _themeSourceFlow.asStateFlow()

    private val _seedColorFlow = MutableStateFlow(0L) // ARGB long
    val seedColorFlow: StateFlow<Long> = _seedColorFlow.asStateFlow()

    // 专辑封面取色（实时，不持久化）
    private val _coverColorFlow = MutableStateFlow(0L)
    val coverColorFlow: StateFlow<Long> = _coverColorFlow.asStateFlow()

    fun initDarkMode(context: Context) {
        _darkModeFlow.value = getDarkMode(context)
    }
    
    /**
     * 获取自定义下载Uri
     * @param context 上下文
     * @return 自定义Uri，如果未设置则返回 null
     */
    fun getCustomUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_CUSTOM_URI, null)
        if (uriString != null && uriString.isNotBlank()) {
            return try {
                Uri.parse(uriString.trim())
            } catch (e: Exception) {
                null
            }
        }
        val oldPath = prefs.getString(KEY_CUSTOM_PATH_OLD, null)
        if (oldPath != null) {
            prefs.edit().remove(KEY_CUSTOM_PATH_OLD).apply()
            return null
        }
        return null
    }
    
    /**
     * 设置自定义下载Uri（使用SAF授权）
     * @param context 上下文
     * @param uri 自定义Uri，如果为 null 则使用默认路径
     */
    fun setCustomUri(context: Context, uri: Uri?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (uri != null && uri.toString().isNotBlank()) {
            prefs.putBoolean(KEY_USE_CUSTOM_PATH, true)
            prefs.putString(KEY_CUSTOM_URI, uri.toString().trim())
            prefs.remove(KEY_CUSTOM_PATH_OLD)
        } else {
            prefs.putBoolean(KEY_USE_CUSTOM_PATH, false)
            prefs.remove(KEY_CUSTOM_URI)
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
     * 获取默认下载路径
     * @return 默认下载路径
     */
    private fun getDefaultDownloadPath(): String {
        return "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/jianyin"
    }
    
    /**
     * 获取下载音质设置 (Neri 风格 level key)
     */
    fun getDownloadQuality(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return try {
            prefs.getString(KEY_DOWNLOAD_QUALITY, "exhigh") ?: "exhigh"
        } catch (e: ClassCastException) {
            // 处理旧版本 Integer 类型数据迁移
            val oldValue = prefs.getInt(KEY_DOWNLOAD_QUALITY, 2)
            val quality = when (oldValue) {
                0 -> "standard"
                1 -> "higher"
                2 -> "exhigh"
                3 -> "lossless"
                else -> "exhigh"
            }
            setDownloadQuality(context, quality)
            quality
        }
    }

    fun setDownloadQuality(context: Context, quality: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_DOWNLOAD_QUALITY, quality)
            .apply()
    }

    /**
     * 获取播放音质设置 (Neri 风格 level key)
     */
    fun getPlayQuality(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return try {
            prefs.getString(KEY_PLAY_QUALITY, "exhigh") ?: "exhigh"
        } catch (e: ClassCastException) {
            // 处理旧版本 Integer 类型数据迁移
            val oldValue = prefs.getInt(KEY_PLAY_QUALITY, 2)
            val quality = when (oldValue) {
                0 -> "standard"
                1 -> "higher"
                2 -> "exhigh"
                3 -> "lossless"
                else -> "exhigh"
            }
            setPlayQuality(context, quality)
            quality
        }
    }

    fun setPlayQuality(context: Context, quality: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PLAY_QUALITY, quality)
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

    fun getDarkMode(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DARK_MODE, 0)
    }

    fun setDarkMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_DARK_MODE, mode)
            .apply()
        _darkModeFlow.value = mode
    }

    fun isFadeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FADE_ENABLED, false)
    }

    fun setFadeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FADE_ENABLED, enabled)
            .apply()
        _fadeEnabledFlow.value = enabled
    }

    fun initFadeEnabled(context: Context) {
        _fadeEnabledFlow.value = isFadeEnabled(context)
    }

    fun isAutoCacheEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_CACHE_ENABLED, false)
    }

    fun setAutoCacheEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_CACHE_ENABLED, enabled)
            .apply()
    }

    fun isDefaultMusicOpenerEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEFAULT_MUSIC_OPENER, false)
    }

    fun setDefaultMusicOpenerEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DEFAULT_MUSIC_OPENER, enabled)
            .apply()
    }

    // ========== 启动设置相关 ==========

    /**
     * 获取离开后保留列表设置
     */
    fun isKeepPlaylistOnExitEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_PLAYLIST_ON_EXIT, false)
    }

    /**
     * 设置离开后保留列表
     */
    fun setKeepPlaylistOnExitEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_KEEP_PLAYLIST_ON_EXIT, enabled)
            .apply()
    }

    /**
     * 获取启动时播放设置
     */
    fun isAutoPlayOnStartEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_PLAY_ON_START, false)
    }

    /**
     * 设置启动时播放
     */
    fun setAutoPlayOnStartEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_PLAY_ON_START, enabled)
            .apply()
    }

    fun getThemeSource(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME_SOURCE, 0)
    }

    fun setThemeSource(context: Context, source: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_THEME_SOURCE, source)
            .apply()
        _themeSourceFlow.value = source
    }

    fun initThemeSource(context: Context) {
        _themeSourceFlow.value = getThemeSource(context)
    }

    fun getSeedColor(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_SEED_COLOR, 0L)
    }

    fun setSeedColor(context: Context, argb: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_SEED_COLOR, argb)
            .apply()
        _seedColorFlow.value = argb
    }

    fun initSeedColor(context: Context) {
        _seedColorFlow.value = getSeedColor(context)
    }

    fun setCoverColor(argb: Long) {
        _coverColorFlow.value = argb
    }

    // ========== 震动设置 ==========

    private val _vibrationEnabledFlow = MutableStateFlow(true)
    val vibrationEnabledFlow: StateFlow<Boolean> = _vibrationEnabledFlow.asStateFlow()

    fun isVibrationEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VIBRATION_ENABLED, true)
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_VIBRATION_ENABLED, enabled)
            .apply()
        _vibrationEnabledFlow.value = enabled
    }

    fun initVibrationEnabled(context: Context) {
        _vibrationEnabledFlow.value = isVibrationEnabled(context)
    }

    fun getBackupAudioApiUrl(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BACKUP_AUDIO_API_URL, "") ?: ""
    }

    fun setBackupAudioApiUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_BACKUP_AUDIO_API_URL, url.trim())
            .apply()
    }

    fun isHazeEffectEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HAZE_EFFECT_ENABLED, true)
    }

    fun setHazeEffectEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_HAZE_EFFECT_ENABLED, enabled)
            .apply()
    }
}
