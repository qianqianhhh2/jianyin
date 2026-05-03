package com.qian.jianyin

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 下载状态管理类
 * 用于跟踪下载进度和状态
 */
object DownloadStateManager {
    // 下载状态
    var isDownloading by mutableStateOf(false)
    var currentSongIndex by mutableStateOf(0)
    var totalSongs by mutableStateOf(0)
    var currentProgress by mutableStateOf(0f)
    var currentSongName by mutableStateOf("")
    var errorMessage by mutableStateOf("")
    
    /**
     * 重置下载状态
     */
    fun resetState() {
        isDownloading = false
        currentSongIndex = 0
        totalSongs = 0
        currentProgress = 0f
        currentSongName = ""
        errorMessage = ""
    }
    
    /**
     * 开始下载
     */
    fun startDownload(total: Int) {
        isDownloading = true
        totalSongs = total
        currentSongIndex = 0
        currentProgress = 0f
        currentSongName = ""
        errorMessage = ""
    }
    
    /**
     * 更新当前下载歌曲
     */
    fun updateCurrentSong(index: Int, songName: String) {
        currentSongIndex = index
        currentSongName = songName
        currentProgress = 0f
    }
    
    /**
     * 更新下载进度
     */
    fun updateProgress(progress: Float) {
        currentProgress = progress
    }
    
    /**
     * 下载完成
     */
    fun downloadComplete() {
        isDownloading = false
        currentProgress = 1f
    }
    
    /**
     * 下载失败
     */
    fun downloadFailed(message: String) {
        isDownloading = false
        errorMessage = message
    }
}