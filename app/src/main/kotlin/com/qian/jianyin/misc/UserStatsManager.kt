package com.qian.jianyin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

/**
 * 用户统计管理器
 * 追踪：今日播放数、连续启动天数、常听时段、曲库总数、收藏数、用户资料
 */
class UserStatsManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("user_stats_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ========== 用户资料 ==========

    data class UserProfile(
        val avatarPath: String = "",
        val nickname: String = "音乐爱好者",
        val signature: String = "享受每一段旋律"
    )

    fun getProfile(): UserProfile {
        val json = prefs.getString("user_profile", null) ?: return UserProfile()
        return try { gson.fromJson(json, UserProfile::class.java) } catch (_: Exception) { UserProfile() }
    }

    fun saveProfile(profile: UserProfile) {
        prefs.edit().putString("user_profile", gson.toJson(profile)).apply()
    }

    fun updateNickname(name: String) {
        saveProfile(getProfile().copy(nickname = name))
    }

    fun updateSignature(sig: String) {
        saveProfile(getProfile().copy(signature = sig))
    }

    fun updateAvatar(path: String) {
        saveProfile(getProfile().copy(avatarPath = path))
    }

    // ========== 今日播放统计 ==========

    fun recordPlayToday() {
        val today = dateFormat.format(Date())
        val key = "plays_$today"
        val count = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, count).apply()
    }

    fun getTodayPlayCount(): Int {
        val today = dateFormat.format(Date())
        return prefs.getInt("plays_$today", 0)
    }

    // ========== 连续启动天数 ==========

    fun recordAppOpen() {
        val today = dateFormat.format(Date())
        val openDays = getOpenDaysSet().toMutableSet()
        openDays.add(today)
        prefs.edit().putString("open_days", gson.toJson(openDays.toList())).apply()
    }

    fun getConsecutiveDays(): Int {
        val openDays = getOpenDaysSet().map { dateFormat.parse(it)!! }.sortedDescending()
        if (openDays.isEmpty()) return 0
        var consecutive = 1
        val cal = Calendar.getInstance()
        cal.time = openDays.first()
        for (i in 1 until openDays.size) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val expected = dateFormat.format(cal.time)
            val actual = dateFormat.format(openDays[i])
            if (expected == actual) consecutive++ else break
        }
        return consecutive
    }

    private fun getOpenDaysSet(): Set<String> {
        val json = prefs.getString("open_days", null) ?: return emptySet()
        return try {
            gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type).toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    // ========== 常听时段统计 ==========

    fun recordPlayHour() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val key = "hour_$hour"
        val count = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, count).apply()
    }

    fun getFrequentTimePeriod(): String {
        val hourCounts = (0..23).map { hour ->
            hour to prefs.getInt("hour_$hour", 0)
        }
        val max = hourCounts.maxByOrNull { it.second } ?: return "暂无数据"
        if (max.second == 0) return "暂无数据"
        val hour = max.first
        return when (hour) {
            in 0..5 -> "深夜 (${hour}:00)"
            in 6..8 -> "清晨 (${hour}:00)"
            in 9..11 -> "上午 (${hour}:00)"
            in 12..13 -> "午间 (${hour}:00)"
            in 14..17 -> "下午 (${hour}:00)"
            in 18..20 -> "傍晚 (${hour}:00)"
            in 21..23 -> "夜间 (${hour}:00)"
            else -> "${hour}:00"
        }
    }

    // ========== 曲库总数 ==========

    fun getTotalSongs(): Int {
        return PlaylistDataStore.getAll(context).sumOf { it.songs.size }
    }

    // ========== 收藏数 ==========

    fun getFavoritesCount(): Int {
        return PlaylistDataStore.getFavoritesPlaylist(context).songs.size
    }

    // ========== 备份兼容 ==========

    companion object {
        fun getStatsBackup(context: Context): Map<String, Any?> {
            val prefs = context.getSharedPreferences("user_stats_prefs", Context.MODE_PRIVATE)
            return prefs.all.mapKeys { it.key }
        }
    }
}
