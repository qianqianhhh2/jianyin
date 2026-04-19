package com.qian.jianyin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.ouom.biliapi.BiliApi
import moe.ouom.biliapi.BiliClient

object BiliPlaylistSyncManager {
    private val gson = Gson()

    suspend fun getUserPlaylists(context: Context): List<UserSyncedPlaylist>? = withContext(Dispatchers.IO) {
        try {
            val biliApi = BiliApi.getInstance(context)
            val folders = biliApi.getUserFavFolders()

            val playlists = folders.map { folder ->
                val songs = biliApi.getFavFolderItems(folder.id)
                UserSyncedPlaylist(
                    id = "bili_${folder.id}",
                    name = folder.name,
                    coverPic = folder.cover,
                    songs = songs.map { item ->
                        Song(
                            id = item.bvid,
                            name = item.title,
                            artist = item.owner,
                            url = "",
                            pic = item.pic,
                            isBiliVideo = true,
                            bvid = item.bvid,
                            cid = item.cid
                        )
                    }
                )
            }
            playlists
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchPlaylistItems(context: Context, mediaId: Long): List<Song>? = withContext(Dispatchers.IO) {
        try {
            val biliApi = BiliApi.getInstance(context)
            val items = biliApi.getFavFolderItems(mediaId)
            items.map { item ->
                Song(
                    id = item.bvid,
                    name = item.title,
                    artist = item.owner,
                    url = "",
                    pic = item.pic,
                    isBiliVideo = true,
                    bvid = item.bvid,
                    cid = item.cid
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun validateAndGetLoginStatus(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val biliApi = BiliApi.getInstance(context)
            val isValid = biliApi.validateLoginSession()
            isValid == true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}