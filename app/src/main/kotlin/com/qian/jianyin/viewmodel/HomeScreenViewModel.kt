package com.qian.jianyin

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.qian.jianyin.netease.NeteasePlaylistResult
import com.qian.jianyin.netease.NeteaseSongSearchResult
import com.qian.jianyin.netease.api.NeteaseApiService

data class HomePlaylist(
    val name: String,
    val playlistId: String,
    val subTitle: String,
    val isRank: Boolean = false
)

data class HomeSectionState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

data class HomeUiState(
    val recommendedPlaylists: HomeSectionState<NeteasePlaylistResult> = HomeSectionState(),
    val hotSongs: HomeSectionState<NeteaseSongSearchResult> = HomeSectionState(),
    val radarSongs: HomeSectionState<NeteaseSongSearchResult> = HomeSectionState()
)

class HomeScreenViewModel(application: Application) : AndroidViewModel(application) {

    val topLists = listOf(
        HomePlaylist("热歌榜", "3778678", "官方TOP排行榜，每日更新", true),
        HomePlaylist("飙升榜", "19723756", "热度上升最快的100首单曲", true),
        HomePlaylist("新歌榜", "3779629", "一月内发行的新歌官方TOP", true)
    )

    val coverMap = mutableStateMapOf<String, String?>()

    var uiState by mutableStateOf(HomeUiState(
        recommendedPlaylists = HomeSectionState(loading = true),
        hotSongs = HomeSectionState(loading = true),
        radarSongs = HomeSectionState(loading = true)
    ))
        private set

    val refreshTrigger = mutableIntStateOf(0)

    init {
        loadAll()
    }

    fun refresh() {
        refreshTrigger.intValue++
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            loadRecommendedPlaylists()
            loadHotSongs()
            loadRadarSongs()
            fetchTopListCovers()
        }
    }

    private suspend fun loadRecommendedPlaylists() {
        uiState = uiState.copy(
            recommendedPlaylists = uiState.recommendedPlaylists.copy(loading = true, error = null)
        )
        try {
            val items = withContext(Dispatchers.IO) {
                NeteaseApiService.getRecommendedPlaylists(20)
            }
            uiState = uiState.copy(
                recommendedPlaylists = HomeSectionState(items = items)
            )
            // 预加载封面
            items.forEach { pl ->
                if (!coverMap.containsKey(pl.id)) {
                    coverMap[pl.id] = pl.picUrl
                }
            }
        } catch (e: Exception) {
            uiState = uiState.copy(
                recommendedPlaylists = uiState.recommendedPlaylists.copy(
                    loading = false,
                    error = e.message
                )
            )
        }
    }

    private suspend fun loadHotSongs() {
        uiState = uiState.copy(
            hotSongs = uiState.hotSongs.copy(loading = true, error = null)
        )
        try {
            val songs = withContext(Dispatchers.IO) {
                NeteaseApiService.searchSongs("热歌", 12)
            }
            uiState = uiState.copy(
                hotSongs = HomeSectionState(items = songs)
            )
        } catch (e: Exception) {
            uiState = uiState.copy(
                hotSongs = uiState.hotSongs.copy(loading = false, error = e.message)
            )
        }
    }

    private suspend fun loadRadarSongs() {
        uiState = uiState.copy(
            radarSongs = uiState.radarSongs.copy(loading = true, error = null)
        )
        try {
            val songs = withContext(Dispatchers.IO) {
                NeteaseApiService.searchSongs("私人雷达", 12)
            }
            uiState = uiState.copy(
                radarSongs = HomeSectionState(items = songs)
            )
        } catch (e: Exception) {
            uiState = uiState.copy(
                radarSongs = uiState.radarSongs.copy(loading = false, error = e.message)
            )
        }
    }

    private suspend fun fetchTopListCovers() {
        topLists.forEach { rank ->
            if (coverMap[rank.playlistId] == null) {
                try {
                    val songs = withContext(Dispatchers.IO) {
                        PlaylistSyncManager.fetchPlaylist(rank.playlistId)
                    }
                    if (!songs.isNullOrEmpty()) {
                        coverMap[rank.playlistId] = songs[0].pic
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
