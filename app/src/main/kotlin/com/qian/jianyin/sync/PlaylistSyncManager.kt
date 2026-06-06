package com.qian.jianyin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.qian.jianyin.netease.api.NeteaseApiService

data class HomePlaylistInfo(
    val name: String,
    val playlistId: String,
    val subTitle: String,
    val coverUrl: String,
    val isRank: Boolean = false
)

object PlaylistSyncManager {

    private val homePlaylists = listOf(
        HomePlaylistInfo("复古摇摆 | 曼城风韵", "7673743198", "气质犹存 我为电狂", "https://p2.music.126.net/pcYHpMkdC69VVvWiynNklA==/109951166952713766.jpg"),
        HomePlaylistInfo("下班/独处/舒适区", "14095931252", "昨天在想你", "https://p1.music.126.net/WbR3bALztQeWXhLN1fjLTg==/109951166952713766.jpg"),
        HomePlaylistInfo("R&B血型|微醺节奏", "13993704429", "心好好冷", "https://p2.music.126.net/N4VrR0z8TLqyJGKuqDvp0g==/109951166952713766.jpg"),
        HomePlaylistInfo("『日系催眠轻声向』", "2050898967", "世界晚安. 夜行少女", "https://p1.music.126.net/s-7大人的-UKthF0mRwg==/109951166952713766.jpg"),
        HomePlaylistInfo("布鲁斯蓝调soul", "2683083724", "美式经典老歌 躺赢耶", "https://p1.music.126.net/sb0NIpYpVKCgkd7Z4cK1ZQ==/109951166952713766.jpg"),
        HomePlaylistInfo("热歌榜", "3778678", "一周内收听所有线上歌曲官方TOP排行榜，每日更新。", "https://p2.music.126.net/pcYHpMkdC69VVvWiynNklA==/109951166952713766.jpg", true),
        HomePlaylistInfo("飙升榜", "19723756", "云音乐中每天热度上升最快的100首单曲，每日更新。", "https://p2.music.126.net/pcYHpMkdC69VVvWiynNklA==/109951166952713766.jpg", true),
        HomePlaylistInfo("新歌榜", "3779629", "一周内收听一月内发行的新歌官方TOP，每天更新。", "https://p2.music.126.net/pcYHpMkdC69VVvWiynNklA==/109951166952713766.jpg", true)
    )

    fun getAllHomePlaylists(): List<HomePlaylistInfo> = homePlaylists

    /**
     * 根据歌单ID同步歌曲列表
     */
    suspend fun fetchPlaylist(playlistId: String): List<Song>? = withContext(Dispatchers.IO) {
        try {
            NeteaseApiService.getPlaylistDetail(playlistId).map { it.toSong() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 根据歌单ID同步歌曲列表（context参数保留兼容，实际不再依赖音质参数）
     */
    suspend fun fetchPlaylist(playlistId: String, context: Context): List<Song>? = withContext(Dispatchers.IO) {
        try {
            NeteaseApiService.getPlaylistDetail(playlistId).map { it.toSong() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
