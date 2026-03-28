package com.qian.jianyin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 用户同步歌单数据类
 * 用于存储用户同步的歌单信息
 * @property id 歌单ID
 * @property name 歌单名称
 * @property coverPic 歌单封面图片地址
 * @property songs 歌单中的歌曲列表
 */
data class UserSyncedPlaylist(
    val id: String, 
    val name: String, 
    val coverPic: String, 
    val songs: List<Song>
)

/**
 * 歌单数据存储类
 * 负责歌单数据的本地存储和管理
 */
object PlaylistDataStore {
    private const val KEY = "user_synced_playlists"
    private const val PREFS_NAME = "playlists"
    private val gson = Gson()
    
    // 收藏歌单的特殊ID和名称
    private const val FAVORITES_PLAYLIST_ID = "jianyin_favorites_playlist"
    private const val FAVORITES_PLAYLIST_NAME = "我喜欢的音乐"

    /**
     * 保存或替换歌单
     * @param context 上下文
     * @param playlist 歌单对象
     */
    fun save(context: Context, playlist: UserSyncedPlaylist) {
        val list = getAll(context).toMutableList()
        list.removeAll { it.id == playlist.id }
        list.add(0, playlist) // 新同步的排在最前面
        saveToPrefs(context, list)
    }

    /**
     * 更新歌单（用于重命名）
     * @param context 上下文
     * @param updatedPlaylist 更新后的歌单对象
     */
    fun update(context: Context, updatedPlaylist: UserSyncedPlaylist) {
        val list = getAll(context).toMutableList()
        val index = list.indexOfFirst { it.id == updatedPlaylist.id }
        if (index != -1) {
            list[index] = updatedPlaylist
            saveToPrefs(context, list)
        }
    }

    /**
     * 删除歌单
     * @param context 上下文
     * @param playlistId 歌单ID
     */
    fun delete(context: Context, playlistId: String) {
        val list = getAll(context).toMutableList()
        list.removeAll { it.id == playlistId }
        saveToPrefs(context, list)
    }

    /**
     * 获取所有缓存歌单
     * @param context 上下文
     * @return 歌单列表
     */
    fun getAll(context: Context): List<UserSyncedPlaylist> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<UserSyncedPlaylist>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取或创建收藏歌单
     * @param context 上下文
     * @return 收藏歌单对象
     */
    fun getFavoritesPlaylist(context: Context): UserSyncedPlaylist {
        val allPlaylists = getAll(context)
        val favorites = allPlaylists.find { it.id == FAVORITES_PLAYLIST_ID }
        
        return favorites ?: UserSyncedPlaylist(
            id = FAVORITES_PLAYLIST_ID,
            name = FAVORITES_PLAYLIST_NAME,
            coverPic = "",
            songs = emptyList()
        ).also {
            // 如果不存在，则创建并保存
            save(context, it)
        }
    }
    
    /**
     * 添加歌曲到收藏歌单
     * @param context 上下文
     * @param song 要添加的歌曲
     */
    fun addToFavorites(context: Context, song: Song) {
        val favorites = getFavoritesPlaylist(context)
        val currentSongs = favorites.songs.toMutableList()
        
        // 检查歌曲是否已在收藏中（通过ID或URL匹配）
        val isAlreadyFavorited = currentSongs.any { 
            (it.id.isNotBlank() && it.id == song.id) || 
            (it.url.isNotBlank() && it.url == song.url)
        }
        
        if (!isAlreadyFavorited) {
            currentSongs.add(0, song) // 新收藏的放在最前面
            
            val updatedPlaylist = favorites.copy(
                songs = currentSongs,
                coverPic = if (currentSongs.isNotEmpty()) currentSongs[0].pic else favorites.coverPic
            )
            save(context, updatedPlaylist)
        }
    }
    
    /**
     * 从收藏歌单移除歌曲
     * @param context 上下文
     * @param song 要移除的歌曲
     */
    fun removeFromFavorites(context: Context, song: Song) {
        val favorites = getFavoritesPlaylist(context)
        val currentSongs = favorites.songs.toMutableList()
        
        // 移除匹配的歌曲（通过ID或URL匹配）
        currentSongs.removeAll { 
            (it.id.isNotBlank() && it.id == song.id) || 
            (it.url.isNotBlank() && it.url == song.url)
        }
        
        val updatedPlaylist = favorites.copy(
            songs = currentSongs,
            coverPic = if (currentSongs.isNotEmpty()) currentSongs[0].pic else ""
        )
        save(context, updatedPlaylist)
    }
    
    /**
     * 检查歌曲是否在收藏中
     * @param context 上下文
     * @param song 要检查的歌曲
     * @return 是否已收藏
     */
    fun isSongInFavorites(context: Context, song: Song): Boolean {
        val favorites = getFavoritesPlaylist(context)
        return favorites.songs.any { 
            (it.id.isNotBlank() && it.id == song.id) || 
            (it.url.isNotBlank() && it.url == song.url)
        }
    }
    
    /**
     * 获取所有歌单（包含收藏歌单）
     * @param context 上下文
     * @return 歌单列表
     */
    fun getAllWithFavorites(context: Context): List<UserSyncedPlaylist> {
        return getAll(context)
    }
    
    /**
     * 安全删除方法，防止删除收藏歌单
     * @param context 上下文
     * @param playlistId 歌单ID
     * @return 是否删除成功
     */
    fun safeDelete(context: Context, playlistId: String): Boolean {
        if (playlistId == FAVORITES_PLAYLIST_ID) {
            return false // 禁止删除收藏歌单
        }
        delete(context, playlistId)
        return true
    }
    
    /**
     * 安全更新方法，防止重命名收藏歌单
     * @param context 上下文
     * @param updatedPlaylist 更新后的歌单对象
     * @return 是否更新成功
     */
    fun safeUpdate(context: Context, updatedPlaylist: UserSyncedPlaylist): Boolean {
        if (updatedPlaylist.id == FAVORITES_PLAYLIST_ID && 
            updatedPlaylist.name != FAVORITES_PLAYLIST_NAME) {
            return false // 禁止重命名收藏歌单
        }
        update(context, updatedPlaylist)
        return true
    }

    /**
     * 保存歌单列表到 SharedPreferences
     * @param context 上下文
     * @param list 歌单列表
     */
    private fun saveToPrefs(context: Context, list: List<UserSyncedPlaylist>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, gson.toJson(list))
            .apply()
    }
    
    /**
     * 清空所有歌单（用于备份恢复）
     * @param context 上下文
     */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
