package com.qian.jianyin

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份管理类
 *
 * 备份范围：播放次数、设置（不含账号）、播放记录、自定义歌单（喜欢的歌曲 + 自建歌单）。
 * 不备份：平台自动同步的歌单（B站 bili_*、网易云在线歌单），这些可通过联网同步恢复。
 */
class BackupManager(private val context: Context) {
    companion object {
        private const val BACKUP_DIR = "download/jianyin/backup"
        private const val TAG = "BackupManager"
        private const val FAVORITES_PLAYLIST_ID = "jianyin_favorites_playlist"
    }

    /** 是否为自定义歌单（喜欢的歌曲 或 用户自建） */
    private fun isCustomPlaylist(p: UserSyncedPlaylist): Boolean =
        p.id == FAVORITES_PLAYLIST_ID || p.isLocalPlaylist || p.id.startsWith("local_")

    // ── 备份 ──────────────────────────────────────────────────

    fun backupData(): String {
        try {
            val backupDir = getBackupDirectory()
            if (!backupDir.exists()) backupDir.mkdirs()

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
            val fileName = "jianyin_backup_${dateFormat.format(Date())}.json"
            val backupFile = File(backupDir, fileName)

            val backupData = collectBackupData()
            val json = Gson().toJson(backupData)

            FileWriter(backupFile).use { it.write(json) }
            return backupFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "备份失败", e)
            throw e
        }
    }

    private fun collectBackupData(): BackupData {
        val allPlaylists = PlaylistDataStore.getAll(context)
        // 只保留自定义歌单（喜欢的歌曲 + 用户自建），排除平台同步歌单
        val customPlaylists = allPlaylists.filter { isCustomPlaylist(it) }

        return BackupData(
            customPlaylists = customPlaylists,
            playCounts = MusicStatsManager.getPlayCounts(context),
            history = MusicViewModel.getHistoryList(context),
            settings = collectSettings()
        )
    }

    private fun collectSettings(): Map<String, Any> = mapOf(
        "playQuality" to DownloadSettingsStore.getPlayQuality(context),
        "downloadQuality" to DownloadSettingsStore.getDownloadQuality(context),
        "lyricSource" to DownloadSettingsStore.getLyricSource(context),
        "darkMode" to DownloadSettingsStore.getDarkMode(context),
        "fadeEnabled" to DownloadSettingsStore.isFadeEnabled(context),
        "autoCacheEnabled" to DownloadSettingsStore.isAutoCacheEnabled(context),
        "defaultOpener" to DownloadSettingsStore.isDefaultMusicOpenerEnabled(context),
        "keepPlaylistOnExit" to DownloadSettingsStore.isKeepPlaylistOnExitEnabled(context),
        "autoPlayOnStart" to DownloadSettingsStore.isAutoPlayOnStartEnabled(context),
        "useCustomPath" to DownloadSettingsStore.isUsingCustomPath(context),
        "customUri" to (DownloadSettingsStore.getCustomUri(context)?.toString() ?: "")
    )

    // ── 恢复 ──────────────────────────────────────────────────

    fun restoreData(backupFile: File): Boolean {
        try {
            val gson = Gson()
            val json = FileReader(backupFile).use { it.readText() }

            // 尝试新版本 BackupData
            try {
                val type = object : TypeToken<BackupData>() {}.type
                val backupData = gson.fromJson<BackupData>(json, type)
                restoreCustomPlaylists(backupData.customPlaylists)
                restorePlayCountData(backupData.playCounts)
                restoreHistoryData(backupData.history)
                restoreSettingsData(backupData.settings)
            } catch (_: Exception) {
                // 兼容旧版本备份格式
                restoreLegacyFormat(gson, json)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "恢复失败", e)
            return false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun restoreLegacyFormat(gson: Gson, json: String) {
        val oldType = object : TypeToken<Map<String, Any>>() {}.type
        val oldData = gson.fromJson<Map<String, Any>>(json, oldType)

        val oldSongsMapper = { map: Map<String, Any> -> mapToSong(map) }

        // 自定义歌单：旧版中 favorites 字段 + playlists 中的 local 歌单
        val oldPlaylists = (oldData["playlists"] as? List<Map<String, Any>> ?: emptyList()).map {
            UserSyncedPlaylist(
                id = it["id"] as? String ?: "",
                name = it["name"] as? String ?: "",
                coverPic = it["coverPic"] as? String ?: "",
                songs = (it["songs"] as? List<Map<String, Any>> ?: emptyList()).map(oldSongsMapper),
                isLocalPlaylist = it["isLocalPlaylist"] as? Boolean ?: false
            )
        }
        val customPlaylists = oldPlaylists.filter { isCustomPlaylist(it) }

        // 旧版 favorites 可能不在 playlists 中，作为独立歌单恢复
        val oldFavorites = (oldData["favorites"] as? List<Map<String, Any>> ?: emptyList()).map(oldSongsMapper)
        val hasFavPlaylist = customPlaylists.any { it.id == FAVORITES_PLAYLIST_ID }
        if (oldFavorites.isNotEmpty() && !hasFavPlaylist) {
            val favPlaylist = UserSyncedPlaylist(
                id = FAVORITES_PLAYLIST_ID,
                name = "我喜欢的音乐",
                coverPic = oldFavorites.firstOrNull()?.pic ?: "",
                songs = oldFavorites
            )
            restoreCustomPlaylists(customPlaylists + favPlaylist)
        } else {
            restoreCustomPlaylists(customPlaylists)
        }

        val playCounts = (oldData["playCounts"] as? Map<String, Int>) ?: emptyMap()
        restorePlayCountData(playCounts)

        val history = (oldData["history"] as? List<Map<String, Any>> ?: emptyList()).map(oldSongsMapper)
        restoreHistoryData(history)

        val settings = (oldData["settings"] as? Map<String, Any>) ?: emptyMap()
        restoreSettingsData(settings)
    }

    private fun restoreCustomPlaylists(customPlaylists: List<UserSyncedPlaylist>) {
        // 移除所有自定义歌单，保留平台歌单不动
        val allPlaylists = PlaylistDataStore.getAll(context)
        val platformPlaylists = allPlaylists.filter { !isCustomPlaylist(it) }

        PlaylistDataStore.clearAll(context)
        // 先恢复平台歌单
        platformPlaylists.forEach { PlaylistDataStore.save(context, it) }
        // 再写入自定义歌单
        customPlaylists.forEach { PlaylistDataStore.save(context, it) }
    }

    private fun restorePlayCountData(playCounts: Map<String, Int>) {
        MusicStatsManager.savePlayCounts(context, playCounts)
    }

    private fun restoreHistoryData(history: List<Song>) {
        MusicViewModel.saveHistoryList(context, history)
    }

    private fun restoreSettingsData(settings: Map<String, Any>) {
        with(DownloadSettingsStore) {
            settings["playQuality"]?.toString()?.let { setPlayQuality(context, it) }
            settings["downloadQuality"]?.toString()?.let { setDownloadQuality(context, it) }
            (settings["lyricSource"] as? Number)?.let { setLyricSource(context, it.toInt()) }
            (settings["darkMode"] as? Number)?.let { setDarkMode(context, it.toInt()) }
            (settings["fadeEnabled"] as? Boolean)?.let { setFadeEnabled(context, it) }
            (settings["autoCacheEnabled"] as? Boolean)?.let { setAutoCacheEnabled(context, it) }
            (settings["defaultOpener"] as? Boolean)?.let { setDefaultMusicOpenerEnabled(context, it) }
            (settings["keepPlaylistOnExit"] as? Boolean)?.let { setKeepPlaylistOnExitEnabled(context, it) }
            (settings["autoPlayOnStart"] as? Boolean)?.let { setAutoPlayOnStartEnabled(context, it) }
            val useCustomPath = settings["useCustomPath"] as? Boolean ?: false
            val customUriStr = settings["customUri"] as? String ?: ""
            val oldCustomPath = settings["customPath"] as? String ?: ""
            if (useCustomPath && customUriStr.isNotEmpty()) {
                setCustomUri(context, android.net.Uri.parse(customUriStr))
            } else if (!useCustomPath || (customUriStr.isEmpty() && oldCustomPath.isEmpty())) {
                setCustomUri(context, null)
            }
        }
    }

    private fun mapToSong(map: Map<String, Any>): Song = Song(
        id = map["id"] as? String ?: "",
        name = map["name"] as? String ?: "",
        artist = map["artist"] as? String ?: "",
        url = map["url"] as? String ?: "",
        pic = map["pic"] as? String ?: "",
        lrc = map["lrc"] as? String ?: "",
        source = (map["source"] as? String)?.let { try { SongSource.valueOf(it) } catch (_: Exception) { null } } ?: SongSource.NETEASE,
        isLocal = map["isLocal"] as? Boolean ?: false,
        isBiliVideo = map["isBiliVideo"] as? Boolean ?: false,
        bvid = map["bvid"] as? String ?: "",
        cid = (map["cid"] as? Number)?.toLong() ?: 0L
    )

    // ── 文件操作 ──────────────────────────────────────────────

    fun getBackupDirectory(): File =
        File(Environment.getExternalStorageDirectory(), BACKUP_DIR)

    fun getBackupFiles(): List<File> {
        val backupDir = getBackupDirectory()
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles { it.isFile && it.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    // ── 数据类 ────────────────────────────────────────────────

    data class BackupData(
        val customPlaylists: List<UserSyncedPlaylist>,
        val playCounts: Map<String, Int>,
        val history: List<Song>,
        val settings: Map<String, Any>
    )
}
