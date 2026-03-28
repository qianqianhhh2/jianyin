package com.qian.jianyin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 音乐统计管理类
 * 专门负责统计歌曲播放次数的工具类
 * 独立于原有业务逻辑，通过 SharedPreferences 存储
 */
class MusicStatsManager(context: Context) {
    private val prefs = context.getSharedPreferences("music_stats_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * 获取所有歌曲的播放次数映射表
     * @return 歌曲ID到播放次数的映射
     */
    fun getPlayCountMap(): Map<String, Int> {
        val json = prefs.getString("play_counts", null) ?: return emptyMap()
        return try {
            gson.fromJson(json, object : TypeToken<Map<String, Int>>() {}.type)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 记录一次播放
     * 供原有逻辑在播放时顺便调用
     * @param songId 歌曲ID
     */
    fun recordPlay(songId: String) {
        if (songId.isBlank()) return
        val counts = getPlayCountMap().toMutableMap()
        counts[songId] = (counts[songId] ?: 0) + 1
        prefs.edit().putString("play_counts", gson.toJson(counts)).apply()
    }

    /**
     * 根据播放次数对歌曲列表进行排序，取前10个作为"最近最爱"
     * @param allHistory 历史播放歌曲列表
     * @return 排序后的前10首歌曲
     */
    fun getTopFavorites(allHistory: List<Song>): List<Song> {
        val counts = getPlayCountMap()
        return allHistory
            .distinctBy { it.id.ifBlank { it.url } } // 去重
            .sortedByDescending { counts[it.id.ifBlank { it.url }] ?: 0 } // 按次数降序
            .take(10)
    }
    
    companion object {
        /**
         * 静态方法：获取所有歌曲的播放次数
         * @param context 上下文
         * @return 歌曲ID到播放次数的映射
         */
        fun getPlayCounts(context: Context): Map<String, Int> {
            val prefs = context.getSharedPreferences("music_stats_prefs", Context.MODE_PRIVATE)
            val gson = Gson()
            val json = prefs.getString("play_counts", null) ?: return emptyMap()
            return try {
                gson.fromJson(json, object : TypeToken<Map<String, Int>>() {}.type)
            } catch (e: Exception) {
                emptyMap()
            }
        }
        
        /**
         * 静态方法：保存播放次数
         * @param context 上下文
         * @param playCounts 歌曲ID到播放次数的映射
         */
        fun savePlayCounts(context: Context, playCounts: Map<String, Int>) {
            val prefs = context.getSharedPreferences("music_stats_prefs", Context.MODE_PRIVATE)
            val gson = Gson()
            prefs.edit().putString("play_counts", gson.toJson(playCounts)).apply()
        }
    }
}
