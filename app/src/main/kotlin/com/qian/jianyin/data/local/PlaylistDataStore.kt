package com.qian.jianyin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 用户同步歌单数据类
 * 用于存储用户同步的歌单信息
 * @property id 歌单ID
 * @property name 歌单名称
 * @property coverPic 歌单封面图片地址
 * @property songs 歌单中的歌曲列表
 * @property isLocalPlaylist 是否为用户本地创建的歌单
 */
data class UserSyncedPlaylist(
    val id: String,
    val name: String,
    val coverPic: String,
    val songs: List<Song>,
    val isLocalPlaylist: Boolean = false
)

/**
 * 歌单数据存储类
 * 负责歌单数据的本地存储和管理
 */
object PlaylistDataStore {
    private const val KEY = "user_synced_playlists"
    private const val PREFS_NAME = "playlists"
    private const val PLAYLISTS_DIR = "playlists"
    private val gson = Gson()

    // 收藏歌单的特殊ID和名称
    private const val FAVORITES_PLAYLIST_ID = "jianyin_favorites_playlist"
    private const val FAVORITES_PLAYLIST_NAME = "我喜欢的音乐"

    private fun getPlaylistsDir(context: Context): File {
        val dir = File(context.filesDir, PLAYLISTS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getPlaylistFile(context: Context, playlistId: String): File {
        return File(getPlaylistsDir(context), "${playlistId}.json")
    }

    fun save(context: Context, playlist: UserSyncedPlaylist) {
        val list = getAll(context).toMutableList()
        list.removeAll { it.id == playlist.id }
        list.add(0, playlist)
        saveToPrefs(context, list)
        if (playlist.isLocalPlaylist) {
            savePlaylistToFile(context, playlist)
        }
    }

    fun update(context: Context, updatedPlaylist: UserSyncedPlaylist) {
        val list = getAll(context).toMutableList()
        val index = list.indexOfFirst { it.id == updatedPlaylist.id }
        if (index != -1) {
            list[index] = updatedPlaylist
            saveToPrefs(context, list)
            if (updatedPlaylist.isLocalPlaylist) {
                savePlaylistToFile(context, updatedPlaylist)
            }
        }
    }

    fun delete(context: Context, playlistId: String) {
        val list = getAll(context).toMutableList()
        list.removeAll { it.id == playlistId }
        saveToPrefs(context, list)
        if (playlistId.startsWith("local_")) {
            val file = getPlaylistFile(context, playlistId)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    fun createPlaylist(context: Context, name: String): UserSyncedPlaylist {
        val newPlaylist = UserSyncedPlaylist(
            id = "local_${System.currentTimeMillis()}",
            name = name,
            coverPic = "",
            songs = emptyList(),
            isLocalPlaylist = true
        )
        save(context, newPlaylist)
        return newPlaylist
    }

    fun getAll(context: Context): List<UserSyncedPlaylist> {
        val prefsPlaylists = getPlaylistsFromPrefs(context)
        val localPlaylists = getLocalPlaylists(context)
        return (prefsPlaylists + localPlaylists).map { it.copy(songs = it.songs.map { s -> Song.normalize(s) }) }
    }

    private fun getPlaylistsFromPrefs(context: Context): List<UserSyncedPlaylist> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<UserSyncedPlaylist>>() {}.type
            val list = gson.fromJson<List<UserSyncedPlaylist>>(json, type)
            list.filter { !it.isLocalPlaylist }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getLocalPlaylists(context: Context): List<UserSyncedPlaylist> {
        val dir = getPlaylistsDir(context)
        if (!dir.exists()) return emptyList()

        val playlists = mutableListOf<UserSyncedPlaylist>()
        dir.listFiles()?.filter { it.extension == "json" }?.forEach { file ->
            try {
                val json = file.readText()
                val playlist = gson.fromJson(json, UserSyncedPlaylist::class.java)
                playlists.add(playlist)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return playlists.sortedByDescending { it.id }
    }

    private fun savePlaylistToFile(context: Context, playlist: UserSyncedPlaylist) {
        try {
            val file = getPlaylistFile(context, playlist.id)
            val json = gson.toJson(playlist)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPlaylistById(context: Context, playlistId: String): UserSyncedPlaylist? {
        return getAll(context).find { it.id == playlistId }
    }

    fun getFavoritesPlaylist(context: Context): UserSyncedPlaylist {
        val allPlaylists = getAll(context)
        val favorites = allPlaylists.find { it.id == FAVORITES_PLAYLIST_ID }

        return favorites ?: UserSyncedPlaylist(
            id = FAVORITES_PLAYLIST_ID,
            name = FAVORITES_PLAYLIST_NAME,
            coverPic = "",
            songs = emptyList()
        ).also {
            save(context, it)
        }
    }

    fun isSongInFavorites(context: Context, song: Song): Boolean {
        val favorites = getFavoritesPlaylist(context)
        return favorites.songs.any {
            (it.id.isNotBlank() && it.id == song.id) ||
            (it.url.isNotBlank() && it.url == song.url)
        }
    }

    fun addToFavorites(context: Context, song: Song): Boolean {
        return addSongToPlaylist(context, FAVORITES_PLAYLIST_ID, song)
    }

    fun removeFromFavorites(context: Context, song: Song): Boolean {
        return removeSongFromPlaylist(context, FAVORITES_PLAYLIST_ID, song)
    }

    fun safeDelete(context: Context, playlistId: String) {
        if (playlistId.startsWith("local_")) {
            val file = getPlaylistFile(context, playlistId)
            if (file.exists()) {
                file.delete()
            }
        }
        delete(context, playlistId)
    }

    fun safeUpdate(context: Context, playlist: UserSyncedPlaylist): Boolean {
        return try {
            update(context, playlist)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun removeSongFromPlaylist(context: Context, playlistId: String, song: Song): Boolean {
        val playlist = getPlaylistById(context, playlistId) ?: return false
        val currentSongs = playlist.songs.toMutableList()

        val removed = currentSongs.removeAll {
            (it.id.isNotBlank() && it.id == song.id) ||
            (it.url.isNotBlank() && it.url == song.url)
        }

        if (removed) {
            val updatedPlaylist = playlist.copy(
                songs = currentSongs,
                coverPic = if (currentSongs.isNotEmpty()) currentSongs[0].pic else ""
            )
            update(context, updatedPlaylist)
        }
        return removed
    }

    fun addSongToPlaylist(context: Context, playlistId: String, song: Song): Boolean {
        val playlist = getPlaylistById(context, playlistId) ?: return false
        val currentSongs = playlist.songs.toMutableList()

        val exists = currentSongs.any {
            (it.id.isNotBlank() && it.id == song.id) ||
            (it.url.isNotBlank() && it.url == song.url)
        }

        if (exists) {
            return false
        }

        val songWithSource = if (song.source == SongSource.NETEASE) {
            song.copy(source = Song.detectSource(song))
        } else {
            song
        }

        currentSongs.add(0, songWithSource)
        val updatedPlaylist = playlist.copy(
            songs = currentSongs,
            coverPic = if (currentSongs.isNotEmpty()) currentSongs[0].pic else ""
        )
        update(context, updatedPlaylist)
        return true
    }

    private fun saveToPrefs(context: Context, list: List<UserSyncedPlaylist>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, gson.toJson(list.filter { !it.isLocalPlaylist }))
            .apply()
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        val dir = getPlaylistsDir(context)
        dir.listFiles()?.forEach { it.delete() }
    }
}