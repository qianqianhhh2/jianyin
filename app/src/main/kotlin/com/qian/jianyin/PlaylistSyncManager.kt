package com.qian.jianyin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 独立的歌单同步工具类
 */
object PlaylistSyncManager {
    private val client = OkHttpClient()
    private val gson = Gson()
    private const val BASE_URL = "https://api.qijieya.cn/meting/"

    /**
     * 根据歌单ID同步歌曲列表（使用默认音质）
     */
    suspend fun fetchPlaylist(playlistId: String): List<Song>? = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL?type=playlist&id=$playlistId"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val json = response.body?.string() ?: return@withContext null
                val type = object : TypeToken<List<Song>>() {}.type
                
                // 返回歌曲列表
                gson.fromJson<List<Song>>(json, type)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 根据歌单ID同步歌曲列表（使用指定音质）
     */
    suspend fun fetchPlaylist(playlistId: String, context: Context): List<Song>? = withContext(Dispatchers.IO) {
        try {
            val playQuality = DownloadSettingsStore.getPlayQuality(context)
            val url = if (playQuality != 320) {
                "$BASE_URL?type=playlist&id=$playlistId&br=$playQuality"
            } else {
                "$BASE_URL?type=playlist&id=$playlistId"
            }
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val json = response.body?.string() ?: return@withContext null
                val type = object : TypeToken<List<Song>>() {}.type
                
                // 返回歌曲列表
                gson.fromJson<List<Song>>(json, type)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
