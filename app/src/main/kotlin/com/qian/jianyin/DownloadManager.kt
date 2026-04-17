package com.qian.jianyin

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * 歌曲元数据类
 * 用于存储下载歌曲的详细信息
 * @property id 歌曲ID
 * @property name 歌曲名称
 * @property artist 歌手名称
 * @property url 歌曲播放地址
 * @property pic 歌曲封面地址
 * @property lrc 歌词内容
 * @property downloadTime 下载时间戳
 */
data class SongMetadata(
    val id: String,
    val name: String,
    val artist: String,
    val url: String,
    val pic: String,
    val lrc: String?,
    val downloadTime: Long = System.currentTimeMillis()
)

/**
 * 下载管理类
 * 负责歌曲的下载和管理
 */
object DownloadManager {
    private val client = OkHttpClient()
    private val gson = Gson()
    
    private const val DEFAULT_DOWNLOAD_DIR = "jianyin"
    
    /**
     * 下载歌曲
     * @param context 上下文
     * @param song 歌曲对象
     * @param customPath 自定义下载路径，默认为 null
     * @param progressCallback 进度回调
     * @return 下载结果
     */
    suspend fun downloadSong(
        context: Context,
        song: Song,
        customPath: String? = null,
        progressCallback: ((Float) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val downloadDir = getDownloadDirectory(context, customPath)
            val songDir = File(downloadDir, sanitizeFileName("${song.name}-${song.artist}"))
            
            if (!songDir.exists()) {
                songDir.mkdirs()
            }
            
            val results = mutableListOf<String>()
            
            song.url.let { url ->
                val audioFile = File(songDir, "${sanitizeFileName(song.name)}.mp3")
                if (!audioFile.exists()) {
                    // 根据下载音质设置添加br参数
                    val downloadQuality = DownloadSettingsStore.getDownloadQuality(context)
                    val finalUrl = if (downloadQuality != 320) {
                        if (url.contains("?")) {
                            "${url}&br=$downloadQuality"
                        } else {
                            "${url}?br=$downloadQuality"
                        }
                    } else {
                        url
                    }
                    downloadFile(finalUrl, audioFile, progressCallback)
                    results.add("音频文件")
                }
            }
            
            song.pic.let { url ->
                val coverFile = File(songDir, "cover.jpg")
                if (!coverFile.exists()) {
                    downloadFile(url, coverFile)
                    results.add("封面图片")
                }
            }
            
            song.lrc?.let { url ->
                val lrcFile = File(songDir, "lyrics.lrc")
                if (!lrcFile.exists()) {
                    downloadFile(url, lrcFile)
                    results.add("歌词文件")
                }
            }
            
            val metadataFile = File(songDir, "metadata.json")
            val metadata = SongMetadata(
                id = song.id,
                name = song.name,
                artist = song.artist,
                url = song.url,
                pic = song.pic,
                lrc = song.lrc
            )
            metadataFile.writeText(gson.toJson(metadata))
            results.add("元数据")
            
            Result.success("下载完成: ${results.joinToString(", ")}\n保存位置: ${songDir.absolutePath}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 批量下载歌曲
     * @param context 上下文
     * @param songs 歌曲列表
     * @param customPath 自定义下载路径，默认为 null
     * @param progressCallback 进度回调
     * @return 下载结果
     */
    suspend fun downloadSongs(
        context: Context,
        songs: List<Song>,
        customPath: String? = null,
        progressCallback: ((Int, Int, String, Float) -> Unit)? = null
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<String>()
            
            for ((index, song) in songs.withIndex()) {
                progressCallback?.invoke(index, songs.size, song.name, 0f)
                
                val songResult = downloadSong(
                    context,
                    song,
                    customPath
                ) { progress ->
                    progressCallback?.invoke(index, songs.size, song.name, progress)
                }
                
                if (songResult.isSuccess) {
                    results.add(songResult.getOrThrow())
                } else {
                    results.add("下载失败: ${song.name} - ${songResult.exceptionOrNull()?.message}")
                }
            }
            
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取本地歌曲文件路径
     * @param context 上下文
     * @param song 歌曲对象
     * @return 本地歌曲文件路径，不存在则返回 null
     */
    fun getLocalSongPath(context: Context, song: Song): String? {
        val customPath = if (DownloadSettingsStore.isUsingCustomPath(context)) DownloadSettingsStore.getCustomPath(context) else null
        val downloadDir = getDownloadDirectory(context, customPath)
        val songDir = File(downloadDir, sanitizeFileName("${song.name}-${song.artist}"))
        val audioFile = File(songDir, "${sanitizeFileName(song.name)}.mp3")
        
        return if (audioFile.exists()) audioFile.absolutePath else null
    }
    
    /**
     * 获取本地封面文件路径
     * @param context 上下文
     * @param song 歌曲对象
     * @return 本地封面文件路径，不存在则返回 null
     */
    fun getLocalCoverPath(context: Context, song: Song): String? {
        val customPath = if (DownloadSettingsStore.isUsingCustomPath(context)) DownloadSettingsStore.getCustomPath(context) else null
        val downloadDir = getDownloadDirectory(context, customPath)
        val songDir = File(downloadDir, sanitizeFileName("${song.name}-${song.artist}"))
        val coverFile = File(songDir, "cover.jpg")
        
        return if (coverFile.exists()) coverFile.absolutePath else null
    }
    
    /**
     * 获取本地歌词文件路径
     * @param context 上下文
     * @param song 歌曲对象
     * @return 本地歌词文件路径，不存在则返回 null
     */
    fun getLocalLrcPath(context: Context, song: Song): String? {
        val customPath = if (DownloadSettingsStore.isUsingCustomPath(context)) DownloadSettingsStore.getCustomPath(context) else null
        val downloadDir = getDownloadDirectory(context, customPath)
        val songDir = File(downloadDir, sanitizeFileName("${song.name}-${song.artist}"))
        val lrcFile = File(songDir, "lyrics.lrc")
        
        return if (lrcFile.exists()) lrcFile.absolutePath else null
    }
    
    /**
     * 下载文件
     * @param url 文件URL
     * @param outputFile 目标文件
     */
    private suspend fun downloadFile(url: String, outputFile: File, progressCallback: ((Float) -> Unit)? = null) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("下载失败: ${response.code}")
            
            val totalSize = response.body?.contentLength() ?: 0
            var downloadedSize = 0L
            
            response.body?.byteStream()?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        if (totalSize > 0) {
                            val progress = downloadedSize.toFloat() / totalSize
                            progressCallback?.invoke(progress)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 获取下载目录
     * @param context 上下文
     * @param customPath 自定义路径
     * @return 下载目录
     */
    fun getDownloadDirectory(context: Context, customPath: String? = null): File {
        return if (customPath != null) {
            val customDir = File(customPath)
            if (!customDir.exists()) {
                customDir.mkdirs()
            }
            customDir
        } else {
            val defaultDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                DEFAULT_DOWNLOAD_DIR
            )
            if (!defaultDir.exists()) {
                defaultDir.mkdirs()
            }
            defaultDir
        }
    }
    
    /**
     * 清理文件名中的非法字符
     * @param name 原始文件名
     * @return 清理后的文件名
     */
    private fun sanitizeFileName(name: String): String {
        val invalidChars = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
        var sanitized = name
        for (char in invalidChars) {
            sanitized = sanitized.replace(char, '_')
        }
        return sanitized.trim()
    }
}
