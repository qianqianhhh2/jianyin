package com.qian.jianyin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.qian.jianyin.bili.BiliApi
import com.qian.jianyin.bili.BiliClient

object BiliPlaylistSyncManager {
    private val gson = Gson()

    suspend fun getUserPlaylists(context: Context): List<UserSyncedPlaylist>? = withContext(Dispatchers.IO) {
        try {
            val biliApi = BiliApi.getInstance(context)
            val folders = biliApi.getUserFavFolders()

            val playlists = folders.map { folder ->
                val songs = biliApi.getFavFolderItems(folder.id)
                // 展开分p视频
                val expandedSongs = mutableListOf<Song>()
                songs.forEach { item ->
                    expandedSongs.addAll(expandMultiPageVideo(biliApi, item))
                }
                UserSyncedPlaylist(
                    id = "bili_${folder.id}",
                    name = folder.name,
                    coverPic = folder.cover,
                    songs = expandedSongs
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
            // 展开分p视频
            val expandedSongs = mutableListOf<Song>()
            items.forEach { item ->
                expandedSongs.addAll(expandMultiPageVideo(biliApi, item))
            }
            expandedSongs
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 展开分p视频
     * 如果视频是分p视频，为每个分p创建单独的Song对象
     */
    private suspend fun expandMultiPageVideo(
        biliApi: BiliApi,
        item: BiliClient.FavResourceItem
    ): List<Song> {
        return try {
            val videoInfo = biliApi.getVideoInfo(item.bvid)
            val pages = videoInfo.pages
            
            // 添加调试日志
            android.util.Log.d("BiliPlaylistSync", "处理视频: ${item.bvid}, 标题: ${item.title}, 分p数量: ${pages.size}")
            
            if (pages.size > 1) {
                // 分p视频，为每个分p创建单独的Song
                android.util.Log.d("BiliPlaylistSync", "发现分p视频，展开为 ${pages.size} 个单独播放项")
                pages.map { page ->
                    Song(
                        id = "${item.bvid}_${page.cid}",
                        name = "${item.title} - ${page.part}",
                        artist = item.owner,
                        url = "",
                        pic = videoInfo.pic,
                        isBiliVideo = true,
                        bvid = item.bvid,
                        cid = page.cid,
                        isPartOfMultiPage = true,
                        pageIndex = page.page,
                        pageCount = pages.size,
                        pageName = page.part,
                        parentBvid = item.bvid
                    )
                }
            } else {
                // 非分p视频，保持原样
                android.util.Log.d("BiliPlaylistSync", "非分p视频，保持原样")
                listOf(Song(
                    id = item.bvid,
                    name = item.title,
                    artist = item.owner,
                    url = "",
                    pic = item.pic,
                    isBiliVideo = true,
                    bvid = item.bvid,
                    cid = item.cid,
                    isPartOfMultiPage = false,
                    pageIndex = 1,
                    pageCount = 1,
                    parentBvid = item.bvid
                ))
            }
        } catch (e: Exception) {
            // 获取视频信息失败，返回原始item
            android.util.Log.e("BiliPlaylistSync", "获取视频信息失败: ${item.bvid}, 错误: ${e.message}")
            e.printStackTrace()
            listOf(Song(
                id = item.bvid,
                name = item.title,
                artist = item.owner,
                url = "",
                pic = item.pic,
                isBiliVideo = true,
                bvid = item.bvid,
                cid = item.cid,
                parentBvid = item.bvid
            ))
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