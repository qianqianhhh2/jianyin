package com.qian.jianyin

/**
 * 导入状态管理器
 * 用于管理歌单导入的状态和进度
 */
object ImportStateManager {
    // 导入状态
    var isImporting = false
        private set
    
    // 总歌曲数量
    var totalSongs = 0
        private set
    
    // 当前处理的歌曲索引
    var currentSongIndex = 0
        private set
    
    // 当前处理的歌曲名称
    var currentSongName = ""
        private set
    
    // 当前进度 (0.0 - 1.0)
    var currentProgress = 0f
        private set
    
    // 错误信息
    var errorMessage = ""
        private set
    
    /**
     * 开始导入
     * @param total 总歌曲数量
     */
    fun startImport(total: Int) {
        isImporting = true
        totalSongs = total
        currentSongIndex = 0
        currentSongName = ""
        currentProgress = 0f
        errorMessage = ""
    }
    
    /**
     * 更新当前歌曲信息
     * @param index 当前歌曲索引
     * @param songName 当前歌曲名称
     */
    fun updateCurrentSong(index: Int, songName: String) {
        currentSongIndex = index
        currentSongName = songName
        currentProgress = if (totalSongs > 0) (index + 1).toFloat() / totalSongs else 0f
    }
    
    /**
     * 更新进度
     * @param progress 进度值 (0.0 - 1.0)
     */
    fun updateProgress(progress: Float) {
        currentProgress = progress
    }
    
    /**
     * 导入失败
     * @param error 错误信息
     */
    fun importFailed(error: String) {
        errorMessage = error
        isImporting = false
    }
    
    /**
     * 导入完成
     */
    fun importComplete() {
        isImporting = false
        currentProgress = 1f
    }
    
    /**
     * 重置状态
     */
    fun reset() {
        isImporting = false
        totalSongs = 0
        currentSongIndex = 0
        currentSongName = ""
        currentProgress = 0f
        errorMessage = ""
    }
}