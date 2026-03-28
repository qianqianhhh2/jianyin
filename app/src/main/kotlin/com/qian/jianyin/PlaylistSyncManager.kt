package com.qian.jianyin

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
     * 根据歌单ID同步歌曲列表
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
}
