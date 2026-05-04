package com.qian.jianyin

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.ouom.biliapi.BiliApi
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

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
 * @property isBiliVideo 是否为B站视频
 * @property bvid B站视频ID
 * @property cid B站视频cid
 */
data class SongMetadata(
    val id: String,
    val name: String,
    val artist: String,
    val url: String,
    val pic: String,
    val lrc: String?,
    val downloadTime: Long = System.currentTimeMillis(),
    val isBiliVideo: Boolean = false,
    val bvid: String = "",
    val cid: Long = 0
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
     * @param customUri 自定义下载目录Uri（SAF授权），默认为 null
     * @param progressCallback 进度回调
     * @return 下载结果
     */
    suspend fun downloadSong(
        context: Context,
        song: Song,
        customUri: Uri? = null,
        progressCallback: ((Float) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val songDirName = sanitizeFileName("${song.name}-${song.artist}")
            
            val validCustomUri = if (customUri != null && customUri.toString().isNotBlank()) {
                customUri
            } else null
            
            val songDirUri = getOrCreateDirectory(context, validCustomUri, songDirName)
            
            val results = mutableListOf<String>()
            
            var audioUrl = song.url
            
            // 处理B站视频
            if (song.isBiliVideo && song.bvid.isNotEmpty()) {
                val biliApi = BiliApi.getInstance(context)
                val streamInfo = biliApi.getBestAudioStream(song.bvid, song.cid)
                if (streamInfo != null && streamInfo.url.isNotEmpty()) {
                    audioUrl = streamInfo.url
                } else {
                    throw Exception("无法获取B站音频流")
                }
            }
            
            audioUrl.let { url ->
                val audioFileName = "${sanitizeFileName(song.name)}.mp3"
                if (!fileExists(context, songDirUri, audioFileName)) {
                    // 下载音频文件
                    downloadFileToUri(context, url, songDirUri, audioFileName, progressCallback, song.isBiliVideo)
                    results.add("音频文件")
                }
            }
            
            song.pic.let { url ->
                if (!fileExists(context, songDirUri, "cover.jpg")) {
                    downloadFileToUri(context, url, songDirUri, "cover.jpg", null, false)
                    results.add("封面图片")
                }
            }
            
            song.lrc?.let { url ->
                if (!fileExists(context, songDirUri, "lyrics.lrc")) {
                    downloadFileToUri(context, url, songDirUri, "lyrics.lrc", null, false)
                    results.add("歌词文件")
                }
            }
            
            val metadata = SongMetadata(
                id = song.id,
                name = song.name,
                artist = song.artist,
                url = audioUrl,
                pic = song.pic,
                lrc = song.lrc,
                isBiliVideo = song.isBiliVideo,
                bvid = song.bvid,
                cid = song.cid
            )
            if (!fileExists(context, songDirUri, "metadata.json")) {
                writeTextToUri(context, songDirUri, "metadata.json", gson.toJson(metadata))
                results.add("元数据")
            }
            
            Result.success("下载完成: ${results.joinToString(", ")}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 批量下载歌曲
     * @param context 上下文
     * @param songs 歌曲列表
     * @param customUri 自定义下载目录Uri（SAF授权），默认为 null
     * @param progressCallback 进度回调
     * @return 下载结果
     */
    suspend fun downloadSongs(
        context: Context,
        songs: List<Song>,
        customUri: Uri? = null,
        progressCallback: ((Int, Int, String, Float) -> Unit)? = null
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<String>()
            
            val validCustomUri = if (customUri != null && customUri.toString().isNotBlank()) {
                customUri
            } else null
            
            for ((index, song) in songs.withIndex()) {
                progressCallback?.invoke(index, songs.size, song.name, 0f)
                
                val songResult = downloadSong(
                    context,
                    song,
                    validCustomUri
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
     * 获取本地歌曲文件Uri
     * @param context 上下文
     * @param song 歌曲对象
     * @return 本地歌曲文件Uri，不存在则返回 null
     */
    fun getLocalSongUri(context: Context, song: Song): Uri? {
        val customUri = if (DownloadSettingsStore.isUsingCustomPath(context)) DownloadSettingsStore.getCustomUri(context) else null
        val songDirUri = getDirectoryUri(context, customUri, sanitizeFileName("${song.name}-${song.artist}"))
        if (songDirUri == null) return null
        
        val audioFileName = "${sanitizeFileName(song.name)}.mp3"
        return getFileUri(context, songDirUri, audioFileName)
    }
    
    /**
     * 获取本地封面文件Uri
     * @param context 上下文
     * @param song 歌曲对象
     * @return 本地封面文件Uri，不存在则返回 null
     */
    fun getLocalCoverUri(context: Context, song: Song): Uri? {
        val customUri = if (DownloadSettingsStore.isUsingCustomPath(context)) DownloadSettingsStore.getCustomUri(context) else null
        val songDirUri = getDirectoryUri(context, customUri, sanitizeFileName("${song.name}-${song.artist}"))
        if (songDirUri == null) return null
        
        return getFileUri(context, songDirUri, "cover.jpg")
    }
    
    /**
     * 获取本地歌词文件Uri
     * @param context 上下文
     * @param song 歌曲对象
     * @return 本地歌词文件Uri，不存在则返回 null
     */
    fun getLocalLrcUri(context: Context, song: Song): Uri? {
        val customUri = if (DownloadSettingsStore.isUsingCustomPath(context)) DownloadSettingsStore.getCustomUri(context) else null
        val songDirUri = getDirectoryUri(context, customUri, sanitizeFileName("${song.name}-${song.artist}"))
        if (songDirUri == null) return null
        
        return getFileUri(context, songDirUri, "lyrics.lrc")
    }
    
    /**
     * 获取本地歌曲文件路径（兼容旧代码）
     * @param context 上下文
     * @param song 歌曲对象
     * @return 本地歌曲文件路径，不存在则返回 null
     */
    fun getLocalSongPath(context: Context, song: Song): String? {
        val uri = getLocalSongUri(context, song)
        return uri?.let { getPathFromUri(context, it) }
    }
    
    /**
     * 获取本地封面文件路径（兼容旧代码）
     * @param context 上下文
     * @param song 歌曲对象
     * @return 本地封面文件路径，不存在则返回 null
     */
    fun getLocalCoverPath(context: Context, song: Song): String? {
        val uri = getLocalCoverUri(context, song)
        return uri?.let { getPathFromUri(context, it) }
    }
    
    /**
     * 获取本地歌词文件路径（兼容旧代码）
     * @param context 上下文
     * @param song 歌曲对象
     * @return 本地歌词文件路径，不存在则返回 null
     */
    fun getLocalLrcPath(context: Context, song: Song): String? {
        val uri = getLocalLrcUri(context, song)
        return uri?.let { getPathFromUri(context, it) }
    }
    
    /**
     * 下载文件到指定目录（使用SAF）
     * @param context 上下文
     * @param url 文件URL
     * @param parentDirUri 父目录Uri
     * @param fileName 文件名
     * @param progressCallback 进度回调
     * @param isBiliStream 是否为B站音频流
     */
    private suspend fun downloadFileToUri(context: Context, url: String, parentDirUri: Uri, fileName: String, progressCallback: ((Float) -> Unit)? = null, isBiliStream: Boolean = false) {
        val requestBuilder = Request.Builder().url(url)
        
        // 为B站音频流添加必要的headers
        if (isBiliStream) {
            val biliApi = BiliApi.getInstance(context)
            val cookies = biliApi.getCookies()
            val cookieString = if (cookies.isNotEmpty()) {
                cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            } else ""
            
            requestBuilder
                .header("Referer", "https://www.bilibili.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            if (cookieString.isNotEmpty()) {
                requestBuilder.header("Cookie", cookieString)
            }
        }
        
        val request = requestBuilder.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("下载失败: ${response.code}")
            
            val totalSize = response.body?.contentLength() ?: 0
            var downloadedSize = 0L
            
            // 创建文件Uri
            val fileUri = createFile(context, parentDirUri, fileName)
            
            response.body?.byteStream()?.use { input ->
                context.contentResolver.openOutputStream(fileUri)?.use { output ->
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
                } ?: throw Exception("无法创建输出流")
            }
        }
    }
    
    /**
     * 写入文本内容到文件（使用SAF）
     */
    private fun writeTextToUri(context: Context, parentDirUri: Uri, fileName: String, content: String) {
        val fileUri = createFile(context, parentDirUri, fileName)
        context.contentResolver.openOutputStream(fileUri)?.use { output ->
            output.write(content.toByteArray())
        } ?: throw Exception("无法创建输出流")
    }
    
    /**
     * 获取或创建目录（使用SAF）
     * @param context 上下文
     * @param baseUri 基础目录Uri，如果为null则使用默认下载目录
     * @param dirName 目录名
     * @return 目录Uri
     */
    private fun getOrCreateDirectory(context: Context, baseUri: Uri?, dirName: String): Uri {
        val parentUri = baseUri ?: getDefaultDownloadUri(context)
        if (parentUri.toString().isBlank()) {
            return getOrCreateDirectory(context, null, dirName)
        }
        val docId = DocumentsContract.getTreeDocumentId(parentUri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, docId)
        return DocumentsContract.createDocument(
            context.contentResolver,
            documentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            dirName
        ) ?: throw Exception("无法创建目录: $dirName")
    }
    
    /**
     * 获取目录Uri（不创建）
     * @param context 上下文
     * @param baseUri 基础目录Uri
     * @param dirName 目录名
     * @return 目录Uri，如果不存在返回null
     */
    private fun getDirectoryUri(context: Context, baseUri: Uri?, dirName: String): Uri? {
        val parentUri = baseUri ?: getDefaultDownloadUri(context)
        return findFile(context, parentUri, dirName)
    }
    
    /**
     * 检查文件是否存在
     */
    private fun fileExists(context: Context, parentUri: Uri, fileName: String): Boolean {
        return findFile(context, parentUri, fileName) != null
    }
    
    /**
     * 在目录中查找文件
     */
    private fun findFile(context: Context, parentUri: Uri, fileName: String): Uri? {
        val docId = DocumentsContract.getTreeDocumentId(parentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, docId)
        context.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                if (name == fileName) {
                    val childDocId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    return DocumentsContract.buildDocumentUriUsingTree(parentUri, childDocId)
                }
            }
        }
        return null
    }
    
    /**
     * 获取文件Uri
     */
    private fun getFileUri(context: Context, parentUri: Uri, fileName: String): Uri? {
        return findFile(context, parentUri, fileName)
    }
    
    /**
     * 创建文件
     */
    private fun createFile(context: Context, parentUri: Uri, fileName: String): Uri {
        // 先检查文件是否已存在，如果存在则删除
        findFile(context, parentUri, fileName)?.let { existingUri ->
            DocumentsContract.deleteDocument(context.contentResolver, existingUri)
        }
        
        return DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            getMimeType(fileName),
            fileName
        ) ?: throw Exception("无法创建文件: $fileName")
    }
    
    /**
     * 根据文件名获取MIME类型
     */
    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".lrc", ignoreCase = true) -> "text/plain"
            fileName.endsWith(".json", ignoreCase = true) -> "application/json"
            else -> "application/octet-stream"
        }
    }
    
    /**
     * 获取默认下载目录Uri
     */
    private fun getDefaultDownloadUri(context: Context): Uri {
        val defaultDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            DEFAULT_DOWNLOAD_DIR
        )
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
        return Uri.fromFile(defaultDir)
    }
    
    /**
     * 从Uri获取路径（仅用于兼容旧代码）
     */
    private fun getPathFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        return null
    }
    
    /**
     * 获取默认下载目录（兼容旧代码）
     * @param context 上下文
     * @return 下载目录
     */
    fun getDownloadDirectory(context: Context): File {
        val defaultDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            DEFAULT_DOWNLOAD_DIR
        )
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
        return defaultDir
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