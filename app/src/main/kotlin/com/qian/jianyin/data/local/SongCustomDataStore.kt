package com.qian.jianyin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 歌曲自定义数据类
 * 用于存储用户为歌曲设置的自定义歌词和封面
 * @property songUrl 歌曲URL（作为唯一标识）
 * @property customLyrics 自定义歌词内容
 * @property customCover 自定义封面URI
 */
data class SongCustomData(
    val songUrl: String,
    val customLyrics: String = "",
    val customCover: String = ""
)

/**
 * 歌曲自定义数据存储类
 * 负责本地歌曲的自定义歌词和封面设置的持久化存储
 */
object SongCustomDataStore {
    private const val KEY = "song_custom_data"
    private const val PREFS_NAME = "song_custom_data"
    private val gson = Gson()

    /**
     * 保存歌曲的自定义歌词
     * @param context 上下文
     * @param songUrl 歌曲URL
     * @param lyrics 歌词内容
     */
    fun saveLyrics(context: Context, songUrl: String, lyrics: String) {
        val customData = getCustomData(context, songUrl)
        val updatedData = customData.copy(customLyrics = lyrics)
        saveCustomData(context, updatedData)
    }

    /**
     * 保存歌曲的自定义封面
     * @param context 上下文
     * @param songUrl 歌曲URL
     * @param coverUri 封面URI
     */
    fun saveCover(context: Context, songUrl: String, coverUri: String) {
        val customData = getCustomData(context, songUrl)
        val updatedData = customData.copy(customCover = coverUri)
        saveCustomData(context, updatedData)
        
        // 更新包含该歌曲的歌单封面
        updatePlaylistCover(context, songUrl, coverUri)
    }

    /**
     * 更新包含指定歌曲的歌单封面
     * @param context 上下文
     * @param songUrl 歌曲URL
     * @param coverUri 新的封面URI
     */
    private fun updatePlaylistCover(context: Context, songUrl: String, coverUri: String) {
        val allPlaylists = PlaylistDataStore.getAll(context)
        allPlaylists.forEach { playlist ->
            var updatedPlaylist = playlist
            var needsUpdate = false
            
            // 更新歌单封面（如果第一首歌曲匹配）
            val firstSong = playlist.songs.firstOrNull()
            if (firstSong != null && firstSong.url == songUrl) {
                // 总是更新歌单封面，确保与第一首歌曲的封面一致
                if (playlist.coverPic != coverUri) {
                    updatedPlaylist = playlist.copy(coverPic = coverUri)
                    needsUpdate = true
                }
            }
            
            // 更新歌单中所有匹配歌曲的封面
            val updatedSongs = playlist.songs.map { song ->
                if (song.url == songUrl) {
                    if (song.pic != coverUri) {
                        needsUpdate = true
                        // 保持歌曲的其他属性不变，只更新封面
                        song.copy(pic = coverUri)
                    } else {
                        song
                    }
                } else {
                    song
                }
            }
            
            if (needsUpdate) {
                updatedPlaylist = updatedPlaylist.copy(songs = updatedSongs)
                PlaylistDataStore.update(context, updatedPlaylist)
            }
        }
    }

    /**
     * 获取歌曲的自定义歌词
     * @param context 上下文
     * @param songUrl 歌曲URL
     * @return 歌词内容，如果不存在则返回空字符串
     */
    fun getLyrics(context: Context, songUrl: String): String {
        val customData = getCustomData(context, songUrl)
        return customData.customLyrics
    }

    /**
     * 获取歌曲的自定义封面
     * @param context 上下文
     * @param songUrl 歌曲URL
     * @return 封面URI，如果不存在则返回空字符串
     */
    fun getCover(context: Context, songUrl: String): String {
        val customData = getCustomData(context, songUrl)
        return customData.customCover
    }

    /**
     * 删除歌曲的自定义数据
     * @param context 上下文
     * @param songUrl 歌曲URL
     */
    fun deleteCustomData(context: Context, songUrl: String) {
        val allData = getAllCustomData(context).toMutableList()
        allData.removeAll { it.songUrl == songUrl }
        saveAllCustomData(context, allData)
    }

    /**
     * 清除歌曲的自定义歌词，恢复到内嵌歌词
     * @param context 上下文
     * @param songUrl 歌曲URL
     */
    fun clearLyrics(context: Context, songUrl: String) {
        val customData = getCustomData(context, songUrl)
        val updatedData = customData.copy(customLyrics = "")
        saveCustomData(context, updatedData)
    }

    /**
     * 清除歌曲的自定义封面，恢复到内嵌封面
     * @param context 上下文
     * @param songUrl 歌曲URL
     */
    fun clearCover(context: Context, songUrl: String) {
        val customData = getCustomData(context, songUrl)
        val updatedData = customData.copy(customCover = "")
        saveCustomData(context, updatedData)
        
        // 获取歌曲的内嵌封面
        val localMusicManager = LocalMusicManager(context)
        val songFile = java.io.File(songUrl)
        val song = localMusicManager.parseSongFromFile(songFile)
        val embeddedCover = song?.pic ?: ""
        
        // 更新包含该歌曲的歌单封面
        updatePlaylistCover(context, songUrl, embeddedCover)
    }

    /**
     * 清除歌曲的所有自定义设置，恢复到默认状态
     * @param context 上下文
     * @param songUrl 歌曲URL
     */
    fun clearCustomData(context: Context, songUrl: String) {
        val customData = getCustomData(context, songUrl)
        val updatedData = customData.copy(customLyrics = "", customCover = "")
        saveCustomData(context, updatedData)
        
        // 获取歌曲的内嵌封面
        val localMusicManager = LocalMusicManager(context)
        val songFile = java.io.File(songUrl)
        val song = localMusicManager.parseSongFromFile(songFile)
        val embeddedCover = song?.pic ?: ""
        
        // 更新包含该歌曲的歌单封面
        updatePlaylistCover(context, songUrl, embeddedCover)
    }

    /**
     * 获取所有自定义数据
     * @param context 上下文
     * @return 自定义数据列表
     */
    private fun getAllCustomData(context: Context): List<SongCustomData> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SongCustomData>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取指定歌曲的自定义数据
     * @param context 上下文
     * @param songUrl 歌曲URL
     * @return 自定义数据对象
     */
    private fun getCustomData(context: Context, songUrl: String): SongCustomData {
        val allData = getAllCustomData(context)
        return allData.find { it.songUrl == songUrl }
            ?: SongCustomData(songUrl = songUrl)
    }

    /**
     * 保存自定义数据
     * @param context 上下文
     * @param customData 自定义数据对象
     */
    private fun saveCustomData(context: Context, customData: SongCustomData) {
        val allData = getAllCustomData(context).toMutableList()
        val index = allData.indexOfFirst { it.songUrl == customData.songUrl }
        if (index != -1) {
            allData[index] = customData
        } else {
            allData.add(customData)
        }
        saveAllCustomData(context, allData)
    }

    /**
     * 保存所有自定义数据到 SharedPreferences
     * @param context 上下文
     * @param list 自定义数据列表
     */
    private fun saveAllCustomData(context: Context, list: List<SongCustomData>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, gson.toJson(list))
            .apply()
    }
}
