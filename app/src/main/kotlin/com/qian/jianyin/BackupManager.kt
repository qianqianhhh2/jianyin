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
            
            // 尝试解析为新版本的 BackupData
            try {
                val type = object : TypeToken<BackupData>() {}.type
                val backupData = gson.fromJson<BackupData>(json, type)
                
                // 恢复数据
                if (backupData.playlistIds.isNotEmpty()) {
                    // 使用歌单ID列表恢复（新版本）
                    restorePlaylistIds(backupData.playlistIds)
                } else {
                    // 使用歌单列表恢复（兼容旧版本）
                    restorePlaylistData(backupData.playlists)
                }
                restoreFavoritesData(backupData.favorites)
                restorePlayCountData(backupData.playCounts)
                restoreHistoryData(backupData.history)
                restoreSettingsData(backupData.settings)
            } catch (e: Exception) {
                // 解析失败，尝试兼容旧版本
                val oldType = object : TypeToken<Map<String, Any>>() {}.type
                val oldData = gson.fromJson<Map<String, Any>>(json, oldType)
                
                // 恢复旧版本数据
                @Suppress("UNCHECKED_CAST")
                val oldPlaylists = oldData["playlists"] as? List<Map<String, Any>> ?: emptyList()
                val playlists = oldPlaylists.map { map ->
                    UserSyncedPlaylist(
                        id = map["id"] as? String ?: "",
                        name = map["name"] as? String ?: "",
                        coverPic = map["coverPic"] as? String ?: "",
                        songs = (map["songs"] as? List<Map<String, Any>> ?: emptyList()).map { songMap ->
                            Song(
                                id = songMap["id"] as? String ?: "",
                                name = songMap["name"] as? String ?: "",
                                artist = songMap["artist"] as? String ?: "",
                                url = songMap["url"] as? String ?: "",
                                pic = songMap["pic"] as? String ?: "",
                                lrc = songMap["lrc"] as? String ?: ""
                            )
                        }
                    )
                }
                
                @Suppress("UNCHECKED_CAST")
                val favorites = (oldData["favorites"] as? List<Map<String, Any>> ?: emptyList()).map { songMap ->
                    Song(
                        id = songMap["id"] as? String ?: "",
                        name = songMap["name"] as? String ?: "",
                        artist = songMap["artist"] as? String ?: "",
                        url = songMap["url"] as? String ?: "",
                        pic = songMap["pic"] as? String ?: "",
                        lrc = songMap["lrc"] as? String ?: ""
                    )
                }
                
                @Suppress("UNCHECKED_CAST")
                val playCounts = oldData["playCounts"] as? Map<String, Int> ?: emptyMap()
                
                @Suppress("UNCHECKED_CAST")
                val history = (oldData["history"] as? List<Map<String, Any>> ?: emptyList()).map { songMap ->
                    Song(
                        id = songMap["id"] as? String ?: "",
                        name = songMap["name"] as? String ?: "",
                        artist = songMap["artist"] as? String ?: "",
                        url = songMap["url"] as? String ?: "",
                        pic = songMap["pic"] as? String ?: "",
                        lrc = songMap["lrc"] as? String ?: ""
                    )
                }
                
                @Suppress("UNCHECKED_CAST")
                val settings = oldData["settings"] as? Map<String, Any> ?: emptyMap()
                
                // 恢复旧版本数据
                restorePlaylistData(playlists)
                restoreFavoritesData(favorites)
                restorePlayCountData(playCounts)
                restoreHistoryData(history)
                restoreSettingsData(settings)
            }
            
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
        val settings = mapOf(
            "playQuality" to DownloadSettingsStore.getPlayQuality(context),
            "downloadQuality" to DownloadSettingsStore.getDownloadQuality(context),
            "lyricSource" to DownloadSettingsStore.getLyricSource(context),
            "useCustomPath" to DownloadSettingsStore.isUsingCustomPath(context),
            "customPath" to (DownloadSettingsStore.getCustomPath(context) ?: "")
        )
        val allPlaylists = PlaylistDataStore.getAll(context)
        // 收集歌单ID列表，排除收藏歌单
        val playlistIds = allPlaylists.filter { it.id != "jianyin_favorites_playlist" }.map { it.id }
        return BackupData(
            playlists = allPlaylists,
            playlistIds = playlistIds,
            favorites = PlaylistDataStore.getFavoritesPlaylist(context).songs,
            playCounts = MusicStatsManager.getPlayCounts(context),
            history = MusicViewModel.getHistoryList(context),
            settings = settings
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
     * 恢复设置数据
     * @param settings 设置数据映射
     */
    private fun restoreSettingsData(settings: Map<String, Any>) {
        // 恢复播放音质
        settings["playQuality"]?.let {
            if (it is Number) {
                DownloadSettingsStore.setPlayQuality(context, it.toInt())
            }
        }
        // 恢复下载音质
        settings["downloadQuality"]?.let {
            if (it is Number) {
                DownloadSettingsStore.setDownloadQuality(context, it.toInt())
            }
        }
        // 恢复歌词来源
        settings["lyricSource"]?.let {
            if (it is Number) {
                DownloadSettingsStore.setLyricSource(context, it.toInt())
            }
        }
        // 恢复自定义路径设置
        val useCustomPath = settings["useCustomPath"] as? Boolean ?: false
        val customPath = settings["customPath"] as? String ?: ""
        if (useCustomPath && customPath.isNotEmpty()) {
            DownloadSettingsStore.setCustomPath(context, customPath)
        } else {
            DownloadSettingsStore.setCustomPath(context, null)
        }
    }
    
    /**
     * 根据歌单ID列表恢复歌单
     * @param playlistIds 歌单ID列表
     */
    private fun restorePlaylistIds(playlistIds: List<String>) {
        // 清空现有歌单（保留收藏歌单）
        val allPlaylists = PlaylistDataStore.getAll(context)
        val favoritesPlaylist = allPlaylists.find { it.id == "jianyin_favorites_playlist" }
        PlaylistDataStore.clearAll(context)
        // 恢复收藏歌单
        favoritesPlaylist?.let {
            PlaylistDataStore.save(context, it)
        }
        
        // 恢复在线歌单
        kotlinx.coroutines.runBlocking {
            playlistIds.forEach { playlistId ->
                try {
                    val songs = PlaylistSyncManager.fetchPlaylist(playlistId, context)
                    if (songs != null && songs.isNotEmpty()) {
                        // 创建歌单对象
                        val playlist = UserSyncedPlaylist(
                            id = playlistId,
                            name = "歌单 $playlistId", // 暂时使用ID作为名称，实际应用中可能需要从网络获取歌单名称
                            coverPic = songs.firstOrNull()?.pic ?: "",
                            songs = songs
                        )
                        PlaylistDataStore.save(context, playlist)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "恢复歌单失败: $playlistId", e)
                }
            }
        }
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
     * @property playlists 同步的歌单列表（兼容旧版本）
     * @property playlistIds 同步的歌单ID列表（新版本）
     * @property favorites 收藏的歌曲列表
     * @property playCounts 每首歌的听歌次数映射
     * @property history 历史记录列表
     * @property settings 设置数据
     */
    data class BackupData(
        val playlists: List<UserSyncedPlaylist>,
        val playlistIds: List<String>,
        val favorites: List<Song>,
        val playCounts: Map<String, Int>,
        val history: List<Song>,
        val settings: Map<String, Any>
    )
}
