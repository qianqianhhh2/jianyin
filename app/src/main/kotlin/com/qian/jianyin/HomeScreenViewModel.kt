package com.qian.jianyin

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

// 统一的歌单/榜单模型
data class HomePlaylist(
    val name: String,
    val playlistId: String,
    val subTitle: String,
    val isRank: Boolean = false
)

class HomeScreenViewModel : ViewModel() {

    // 1. 推荐歌单配置
    val recommendedPlaylists = listOf(
        HomePlaylist("复古摇摆 | 曼城风韵", "7673743198", "气质犹存 我为电狂"),
        HomePlaylist("下班/独处/舒适区", "14095931252", "昨天在想你"),
        HomePlaylist("R&B血型|微醺节奏", "13993704429", "心好好冷"),
        HomePlaylist("『日系催眠轻声向』", "2050898967", "世界晚安. 夜行少女"),
        HomePlaylist("布鲁斯蓝调soul", "2683083724", "美式经典老歌 躺赢耶")
    )

    // 2. 排行榜配置
    val topLists = listOf(
        HomePlaylist("热歌榜", "3778678", "一周内收听所有线上歌曲官方TOP排行榜，每日更新。", true),
        HomePlaylist("飙升榜", "19723756", "云音乐中每天热度上升最快的100首单曲，每日更新。", true),
        HomePlaylist("新歌榜", "3779629", "一周内收听一月内发行的新歌官方TOP，每天更新。", true)
    )

    // 3. 封面图映射表 [PlaylistID -> ImageURL]
    val coverMap = mutableStateMapOf<String, String?>()

    init {
        // 启动时自动抓取所有封面
        (recommendedPlaylists + topLists).forEach { item ->
            fetchFirstCover(item.playlistId)
        }
    }

    private fun fetchFirstCover(id: String) {
        viewModelScope.launch {
            try {
                // 使用默认音质获取封面
                val songs = PlaylistSyncManager.fetchPlaylist(id)
                if (!songs.isNullOrEmpty()) {
                    coverMap[id] = songs[0].pic
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
