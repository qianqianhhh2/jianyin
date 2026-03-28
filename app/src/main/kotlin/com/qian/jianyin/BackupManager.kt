package com.qian.jianyin

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份管理类
 * 负责应用数据的备份与恢复功能
 * 备份内容包括：同步的歌单、收藏的歌曲、听歌次数、历史记录
 * 备份文件存储在 download/jianyin/backup 目录
 */
class BackupManager(private val context: Context) {
    companion object {
        private const val BACKUP_DIR = "download/jianyin/backup"
        private const val TAG = "BackupManager"
    }
    
    /**
     * 创建备份文件
     * @return 备份文件的绝对路径
     * @throws Exception 备份失败时抛出异常
     */
    fun backupData(): String {
        try {
            // 创建备份目录
            val backupDir = getBackupDirectory()
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            
            // 生成备份文件名
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
            val fileName = "jianyin_backup_${dateFormat.format(Date())}.json"
            val backupFile = File(backupDir, fileName)
            
            // 收集数据
            val backupData = collectBackupData()
            
            // 写入文件
            val gson = Gson()
            val json = gson.toJson(backupData)
            
            FileWriter(backupFile).use {
                it.write(json)
            }
            
            return backupFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "备份失败", e)
            throw e
        }
    }
    
    /**
     * 恢复备份数据
     * @param backupFile 备份文件
     * @return 是否恢复成功
     */
    fun restoreData(backupFile: File): Boolean {
        try {
            // 读取备份文件
            val gson = Gson()
            val json = FileReader(backupFile).use { it.readText() }
            
            val type = object : TypeToken<BackupData>() {}.type
            val backupData = gson.fromJson<BackupData>(json, type)
            
            // 恢复数据
            restorePlaylistData(backupData.playlists)
            restoreFavoritesData(backupData.favorites)
            restorePlayCountData(backupData.playCounts)
            restoreHistoryData(backupData.history)
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "恢复失败", e)
            return false
        }
    }
    
    /**
     * 收集备份数据
     * @return 备份数据对象
     */
    private fun collectBackupData(): BackupData {
        return BackupData(
            playlists = PlaylistDataStore.getAll(context),
            favorites = PlaylistDataStore.getFavoritesPlaylist(context).songs,
            playCounts = MusicStatsManager.getPlayCounts(context),
            history = MusicViewModel.getHistoryList(context)
        )
    }
    
    /**
     * 恢复歌单数据
     * @param playlists 歌单列表
     */
    private fun restorePlaylistData(playlists: List<UserSyncedPlaylist>) {
        // 清空现有歌单
        PlaylistDataStore.clearAll(context)
        // 恢复歌单
        playlists.forEach {
            PlaylistDataStore.save(context, it)
        }
    }
    
    /**
     * 恢复收藏数据
     * @param favorites 收藏歌曲列表
     */
    private fun restoreFavoritesData(favorites: List<Song>) {
        // 清空收藏
        val favoritesPlaylist = PlaylistDataStore.getFavoritesPlaylist(context)
        val updatedPlaylist = favoritesPlaylist.copy(songs = emptyList())
        PlaylistDataStore.save(context, updatedPlaylist)
        // 恢复收藏
        favorites.forEach {
            PlaylistDataStore.addToFavorites(context, it)
        }
    }
    
    /**
     * 恢复播放次数数据
     * @param playCounts 播放次数映射
     */
    private fun restorePlayCountData(playCounts: Map<String, Int>) {
        MusicStatsManager.savePlayCounts(context, playCounts)
    }
    
    /**
     * 恢复历史记录数据
     * @param history 历史记录列表
     */
    private fun restoreHistoryData(history: List<Song>) {
        MusicViewModel.saveHistoryList(context, history)
    }
    
    /**
     * 获取备份目录
     * @return 备份目录文件对象
     */
    fun getBackupDirectory(): File {
        return File(Environment.getExternalStorageDirectory(), BACKUP_DIR)
    }
    
    /**
     * 获取所有备份文件
     * @return 备份文件列表，按修改时间降序排列
     */
    fun getBackupFiles(): List<File> {
        val backupDir = getBackupDirectory()
        if (!backupDir.exists()) return emptyList()
        
        return backupDir.listFiles { file ->
            file.isFile && file.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    /**
     * 备份数据类
     * 包含所有需要备份的数据
     * @property playlists 同步的歌单列表
     * @property favorites 收藏的歌曲列表
     * @property playCounts 每首歌的听歌次数映射
     * @property history 历史记录列表
     */
    data class BackupData(
        val playlists: List<UserSyncedPlaylist>,
        val favorites: List<Song>,
        val playCounts: Map<String, Int>,
        val history: List<Song>
    )
}
