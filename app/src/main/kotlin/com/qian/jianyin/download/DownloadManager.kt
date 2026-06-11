package com.qian.jianyin

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.qian.jianyin.bili.BiliApi
import com.qian.jianyin.netease.api.NeteaseApiService
import com.kyant.taglib.Picture
import com.kyant.taglib.PropertyMap
import com.kyant.taglib.TagLib
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import org.json.JSONObject

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
    
    private const val DEFAULT_DOWNLOAD_DIR = "jianyin/download"
    
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
            val validCustomUri = if (customUri != null && customUri.toString().isNotBlank()) {
                customUri
            } else null

            // 获取下载目录（不再创建歌曲子目录）
            val downloadDirUri = getOrCreateDownloadDirectory(context, validCustomUri)

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

            // 处理网易云歌曲
            var neteaseLrc: String? = null
            if (song.source == SongSource.NETEASE) {
                val qualityLevel = DownloadSettingsStore.getDownloadQuality(context)
                val downloadInfo = NeteaseApiService.getSongDownloadUrl(song.id, qualityLevel)
                if (downloadInfo != null && downloadInfo.url.isNotBlank()) {
                    audioUrl = downloadInfo.url
                    Log.d("DownloadManager", "网易云下载URL (quality=$qualityLevel): ${downloadInfo.url.take(80)}...")
                } else {
                    throw Exception("无法获取网易云下载链接，请先登录")
                }
                // 同时获取双语歌词（原文+翻译）
                neteaseLrc = fetchNeteaseBilingualLyric(song.id)
            }

            // 文件名：歌曲名-歌手.mp3
            val audioFileName = "${sanitizeFileName(song.name)}-${sanitizeFileName(song.artist)}.mp3"

            // 检查文件是否已存在
            if (!fileExists(context, downloadDirUri, audioFileName)) {
                // 先下载封面到临时文件（用于嵌入）
                var tempCoverFile: File? = null
                if (song.pic.isNotEmpty()) {
                    try {
                        tempCoverFile = File.createTempFile("temp_cover_${song.id}", ".jpg", context.cacheDir)
                        downloadFileToTemp(context, song.pic, tempCoverFile, false)
                    } catch (e: Exception) {
                        Log.e("DownloadManager", "下载封面失败: ${e.message}")
                    }
                }

                // 下载音频文件
                downloadFileToUri(context, audioUrl, downloadDirUri, audioFileName, progressCallback, song.isBiliVideo)
                results.add("音频文件")

                // 将歌词和封面嵌入到MP3文件中
                val audioFileUri = getFileUri(context, downloadDirUri, audioFileName)
                val lrcContent = neteaseLrc ?: song.lrc

                if (audioFileUri != null) {
                    val embedded = embedMetadataIntoMp3WithTempCover(context, audioFileUri, tempCoverFile, lrcContent, song.name, song.artist)
                    if (embedded) {
                        results.add("嵌入歌词和封面")
                    }
                }

                // 清理临时封面文件
                tempCoverFile?.delete()
            } else {
                results.add("文件已存在")
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
        Log.d("DownloadManager", "=== getLocalSongUri START ===")
        Log.d("DownloadManager", "Song: ${song.name} - ${song.artist}")

        // 新格式：歌曲名-歌手.mp3，直接在 download 目录下
        val audioFileName = "${sanitizeFileName(song.name)}-${sanitizeFileName(song.artist)}.mp3"

        // 优先尝试自定义路径（SAF）
        if (DownloadSettingsStore.isUsingCustomPath(context)) {
            val customUri = DownloadSettingsStore.getCustomUri(context)
            Log.d("DownloadManager", "Trying SAF path: customUri=${customUri?.toString() ?: "null"}")

            if (customUri != null) {
                val downloadDirUri = getDirectoryUri(context, customUri, "download")
                if (downloadDirUri != null) {
                    val result = getFileUri(context, downloadDirUri, audioFileName)
                    if (result != null) {
                        Log.d("DownloadManager", "SAF path successful: $result")
                        Log.d("DownloadManager", "=== getLocalSongUri END (SAF) ===")
                        return result
                    }
                }
                Log.w("DownloadManager", "SAF path failed, falling back to default path")
            }
        }

        // 回退到默认下载目录（传统文件路径）
        Log.d("DownloadManager", "Trying default file path")
        val defaultDirUri = getDefaultDownloadUri(context)
        val result = getFileUri(context, defaultDirUri, audioFileName)
        if (result != null) {
            Log.d("DownloadManager", "Default path successful: $result")
            Log.d("DownloadManager", "=== getLocalSongUri END (default) ===")
            return result
        }

        Log.w("DownloadManager", "Both SAF and default paths failed")
        Log.d("DownloadManager", "=== getLocalSongUri END (null) ===")
        return null
    }
    
    /**
     * 获取本地封面文件Uri
     * 从嵌入MP3的封面中提取，保存到临时文件并返回Uri
     * @param context 上下文
     * @param song 歌曲对象
     * @return 封面Uri（临时文件），不存在则返回 null
     */
    fun getLocalCoverUri(context: Context, song: Song): Uri? {
        val audioUri = getLocalSongUri(context, song) ?: return null
        return try {
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(audioUri, "r")
                ?: return null
            val pictures = TagLib.getPictures(parcelFileDescriptor.dup().detachFd())
            parcelFileDescriptor.close()
            if (pictures.isNotEmpty()) {
                val coverData = pictures.first().data
                val tempCoverFile = File.createTempFile("embedded_cover_", ".jpg", context.cacheDir)
                tempCoverFile.writeBytes(coverData)
                Uri.fromFile(tempCoverFile)
            } else null
        } catch (e: Exception) {
            Log.e("DownloadManager", "读取嵌入封面失败: ${e.message}")
            null
        }
    }

    /**
     * 获取本地歌词文件Uri
     * 从嵌入MP3的歌词中提取，保存到临时文件并返回Uri
     * @param context 上下文
     * @param song 歌曲对象
     * @return 歌词Uri（临时文件），不存在则返回 null
     */
    fun getLocalLrcUri(context: Context, song: Song): Uri? {
        val lyrics = readEmbeddedLyrics(context, song) ?: return null
        return try {
            val tempLrcFile = File.createTempFile("embedded_lrc_", ".lrc", context.cacheDir)
            tempLrcFile.writeText(lyrics)
            Uri.fromFile(tempLrcFile)
        } catch (e: Exception) {
            Log.e("DownloadManager", "写入临时歌词文件失败: ${e.message}")
            null
        }
    }

    /**
     * 从已下载的单文件MP3中读取嵌入歌词
     * @param context 上下文
     * @param song 歌曲对象
     * @return 歌词文本，不存在则返回 null
     */
    fun readEmbeddedLyrics(context: Context, song: Song): String? {
        val audioUri = getLocalSongUri(context, song) ?: return null
        return try {
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(audioUri, "r")
                ?: return null
            val lyricsValues = TagLib.getMetadataPropertyValues(
                parcelFileDescriptor.dup().detachFd(), "LYRICS"
            )
            parcelFileDescriptor.close()
            lyricsValues?.firstOrNull()
        } catch (e: Exception) {
            Log.e("DownloadManager", "读取嵌入歌词失败: ${e.message}")
            null
        }
    }

    /**
     * 从已下载的单文件MP3中读取双语歌词（原文 + 翻译）
     * @return Pair(原文歌词, 翻译歌词)，任一为null表示不存在
     */
    fun readEmbeddedLyricsBilingual(context: Context, song: Song): Pair<String?, String?> {
        val combined = readEmbeddedLyrics(context, song) ?: return null to null
        val separator = "\n[TRANSLATED]\n"
        val idx = combined.indexOf(separator)
        return if (idx == -1) {
            combined to null
        } else {
            combined.substring(0, idx) to combined.substring(idx + separator.length)
        }
    }

    /**
     * 获取网易云双语歌词（原文+翻译），用分隔符合并
     */
    private suspend fun fetchNeteaseBilingualLyric(songId: String): String? {
        return try {
            val response = NeteaseApiService.getLyricRaw(songId)
            val root = JSONObject(response)
            val yrc = root.optJSONObject("yrc")?.optString("lyric").orEmpty()
            val lrc = root.optJSONObject("lrc")?.optString("lyric").orEmpty()
            val original = yrc.ifBlank { lrc.ifBlank { null } } ?: return null
            val translated = root.optJSONObject("ytlrc")?.optString("lyric")
                ?: root.optJSONObject("tlyric")?.optString("lyric")
                ?: ""
            if (translated.isNotBlank()) {
                "$original\n[TRANSLATED]\n$translated"
            } else {
                original
            }
        } catch (e: Exception) {
            Log.e("DownloadManager", "获取网易云双语歌词失败: $songId", e)
            null
        }
    }

    /**
     * 获取本地歌曲文件路径（兼容旧代码）
     * @param context 上下文
     * @param song 歌曲对象
     * @return 本地歌曲文件路径（如果是SAF目录则返回content URI），不存在则返回 null
     */
    fun getLocalSongPath(context: Context, song: Song): String? {
        val uri = getLocalSongUri(context, song) ?: return null
        return if (uri.scheme == "file") {
            uri.path
        } else {
            uri.toString()
        }
    }
    
    /**
     * 获取本地封面文件路径（兼容旧代码）
     * @param context 上下文
     * @param song 歌曲对象
     * @return 本地封面文件路径（如果是SAF目录则返回content URI），不存在则返回 null
     */
    fun getLocalCoverPath(context: Context, song: Song): String? {
        val uri = getLocalCoverUri(context, song) ?: return null
        return if (uri.scheme == "file") {
            uri.path
        } else {
            uri.toString()
        }
    }
    
    /**
     * 获取本地歌词文件路径（兼容旧代码）
     * @param context 上下文
     * @param song 歌曲对象
     * @return 本地歌词文件路径（如果是SAF目录则返回content URI），不存在则返回 null
     */
    fun getLocalLrcPath(context: Context, song: Song): String? {
        val uri = getLocalLrcUri(context, song) ?: return null
        return if (uri.scheme == "file") {
            uri.path
        } else {
            uri.toString()
        }
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
     * 检查URI是否为SAF tree URI
     */
    private fun isSafTreeUri(uri: Uri): Boolean {
        return uri.scheme == "content" && uri.toString().contains("com.android.externalstorage")
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
        
        // 如果是文件URI，使用传统文件方式
        if (parentUri.scheme == "file") {
            val file = File(parentUri.path, dirName)
            if (!file.exists()) {
                file.mkdirs()
            }
            return Uri.fromFile(file)
        }
        
        // SAF方式
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
        Log.d("DownloadManager", "findFile: parentUri=$parentUri, fileName=$fileName, scheme=${parentUri.scheme}")
        
        // 如果是文件URI，使用传统文件方式
        if (parentUri.scheme == "file") {
            val filePath = "${parentUri.path}/$fileName"
            val file = File(parentUri.path, fileName)
            val exists = file.exists()
            Log.d("DownloadManager", "File URI mode: path=$filePath, exists=$exists")
            return if (exists) Uri.fromFile(file) else null
        }
        
        // SAF方式
        try {
            // 确定正确的docId
            val docId = if (parentUri.toString().contains("/document/")) {
                // 这是一个document URI，提取document ID
                val documentPath = parentUri.lastPathSegment
                Log.d("DownloadManager", "Document URI detected, extracting docId from path segment: $documentPath")
                documentPath ?: DocumentsContract.getTreeDocumentId(parentUri)
            } else {
                // 这是一个tree URI
                DocumentsContract.getTreeDocumentId(parentUri)
            }
            Log.d("DownloadManager", "SAF mode: docId=$docId")
            
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, docId)
            Log.d("DownloadManager", "SAF mode: childrenUri=$childrenUri")
            
            context.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { cursor ->
                Log.d("DownloadManager", "SAF mode: cursor count=${cursor.count}")
                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                    Log.d("DownloadManager", "SAF mode: found item=$name")
                    if (name == fileName) {
                        val childDocId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                        val resultUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, childDocId)
                        Log.d("DownloadManager", "SAF mode: match found! resultUri=$resultUri")
                        return resultUri
                    }
                }
            }
            Log.w("DownloadManager", "SAF mode: file not found - $fileName")
        } catch (e: Exception) {
            Log.e("DownloadManager", "SAF findFile error: ${e.message}", e)
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
        // 如果是文件URI，使用传统文件方式
        if (parentUri.scheme == "file") {
            val file = File(parentUri.path, fileName)
            if (file.exists()) {
                file.delete()
            }
            file.createNewFile()
            return Uri.fromFile(file)
        }
        
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
            fileName.endsWith(".lrc", ignoreCase = true) -> "application/octet-stream"  // 使用二进制类型避免自动添加.txt后缀
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
     * 获取或创建下载目录
     * @param context 上下文
     * @param customUri 自定义基础目录Uri（SAF授权）
     * @return 下载目录Uri
     */
    private fun getOrCreateDownloadDirectory(context: Context, customUri: Uri?): Uri {
        // 默认下载目录已包含 "download" 子路径，直接返回
        if (customUri == null) return getDefaultDownloadUri(context)

        // SAF自定义路径：在用户选中的目录下创建 download 子目录
        val docId = DocumentsContract.getTreeDocumentId(customUri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(customUri, docId)
        return DocumentsContract.createDocument(
            context.contentResolver,
            documentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            "download"
        ) ?: throw Exception("无法创建download目录")
    }

    /**
     * 下载文件到临时文件
     * @param context 上下文
     * @param url 文件URL
     * @param tempFile 临时文件
     * @param isBiliStream 是否为B站音频流
     */
    private suspend fun downloadFileToTemp(context: Context, url: String, tempFile: File, isBiliStream: Boolean) {
        val requestBuilder = Request.Builder().url(url)

        if (isBiliStream) {
            val biliApi = BiliApi.getInstance(context)
            val cookies = biliApi.getCookies()
            val cookieString = if (cookies.isNotEmpty()) {
                cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            } else ""

            requestBuilder
                .header("Referer", "https://www.bilibili.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            if (cookieString.isNotEmpty()) {
                requestBuilder.header("Cookie", cookieString)
            }
        }

        val request = requestBuilder.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("下载失败: ${response.code}")
            response.body?.byteStream()?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    /**
     * 使用临时封面文件将歌词和封面嵌入到MP3文件中
     * @param context 上下文
     * @param audioUri MP3文件Uri
     * @param tempCoverFile 临时封面文件（可为null）
     * @param lrcContent 歌词内容（可为null）
     * @param songName 歌曲名称
     * @param artistName 歌手名称
     * @return 是否嵌入成功
     */
    private fun embedMetadataIntoMp3WithTempCover(
        context: Context,
        audioUri: Uri,
        tempCoverFile: File?,
        lrcContent: String?,
        songName: String,
        artistName: String
    ): Boolean {
        return try {
            // 获取文件描述符
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(audioUri, "rw")
            if (parcelFileDescriptor == null) {
                Log.e("DownloadManager", "无法获取文件描述符")
                return false
            }

            // 创建属性 Map (PropertyMap = HashMap<String, Array<String>>)
            val propertyMap: PropertyMap = hashMapOf()
            propertyMap["TITLE"] = arrayOf(songName)
            propertyMap["ARTIST"] = arrayOf(artistName)

            // 嵌入歌词（MP3 使用 UNSYNCEDLYRICS）
            if (lrcContent != null && lrcContent.isNotBlank()) {
                propertyMap["LYRICS"] = arrayOf(lrcContent)
                propertyMap["UNSYNCEDLYRICS"] = arrayOf(lrcContent)
            }

            // 保存文本属性
            TagLib.savePropertyMap(parcelFileDescriptor.dup().detachFd(), propertyMap)

            // 嵌入封面
            if (tempCoverFile != null && tempCoverFile.exists()) {
                try {
                    val coverBytes = tempCoverFile.readBytes()
                    val mimeType = detectPictureMimeType(coverBytes)
                    val picture = Picture(
                        data = coverBytes,
                        description = "",
                        pictureType = "Front Cover",
                        mimeType = mimeType
                    )
                    TagLib.savePictures(parcelFileDescriptor.dup().detachFd(), arrayOf(picture))
                    Log.d("DownloadManager", "封面嵌入成功 (TagLib)")
                } catch (e: Exception) {
                    Log.e("DownloadManager", "嵌入封面失败: ${e.message}")
                }
            }

            // 关闭文件描述符
            parcelFileDescriptor.close()

            Log.d("DownloadManager", "MP3元数据嵌入完成")
            true
        } catch (e: Exception) {
            Log.e("DownloadManager", "嵌入元数据失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 将歌词和封面嵌入到MP3文件中
     * @param context 上下文
     * @param audioUri MP3文件Uri
     * @param coverUri 封面图片Uri（可为null）
     * @param lrcContent 歌词内容（可为null）
     * @param songName 歌曲名称
     * @param artistName 歌手名称
     * @return 是否嵌入成功
     */
    private fun embedMetadataIntoMp3(
        context: Context,
        audioUri: Uri,
        coverUri: Uri?,
        lrcContent: String?,
        songName: String,
        artistName: String
    ): Boolean {
        return try {
            // 获取文件描述符
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(audioUri, "rw")
            if (parcelFileDescriptor == null) {
                Log.e("DownloadManager", "无法获取文件描述符")
                return false
            }

            // 创建属性 Map (PropertyMap = HashMap<String, Array<String>>)
            val propertyMap: PropertyMap = hashMapOf()
            propertyMap["TITLE"] = arrayOf(songName)
            propertyMap["ARTIST"] = arrayOf(artistName)

            // 嵌入歌词（MP3 使用 UNSYNCEDLYRICS）
            if (lrcContent != null && lrcContent.isNotBlank()) {
                propertyMap["LYRICS"] = arrayOf(lrcContent)
                propertyMap["UNSYNCEDLYRICS"] = arrayOf(lrcContent)
            }

            // 保存文本属性
            TagLib.savePropertyMap(parcelFileDescriptor.dup().detachFd(), propertyMap)

            // 嵌入封面
            if (coverUri != null) {
                try {
                    val coverBytes = context.contentResolver.openInputStream(coverUri)?.use { it.readBytes() }
                    if (coverBytes != null) {
                        val mimeType = detectPictureMimeType(coverBytes)
                        val picture = Picture(
                            data = coverBytes,
                            description = "",
                            pictureType = "Front Cover",
                            mimeType = mimeType
                        )
                        TagLib.savePictures(parcelFileDescriptor.dup().detachFd(), arrayOf(picture))
                        Log.d("DownloadManager", "封面嵌入成功 (TagLib)")
                    }
                } catch (e: Exception) {
                    Log.e("DownloadManager", "嵌入封面失败: ${e.message}")
                }
            }

            // 关闭文件描述符
            parcelFileDescriptor.close()

            Log.d("DownloadManager", "MP3元数据嵌入完成")
            true
        } catch (e: Exception) {
            Log.e("DownloadManager", "嵌入元数据失败: ${e.message}", e)
            false
        }
    }

    /**
     * 从Uri创建临时文件
     */
    private fun createTempFileFromUri(context: Context, uri: Uri): File? {
        return try {
            val tempFile = File.createTempFile("temp_audio", ".mp3", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e("DownloadManager", "创建临时文件失败: ${e.message}")
            null
        }
    }

    /**
     * 将文件写入Uri
     */
    private fun writeFileToUri(context: Context, file: File, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            file.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
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

    /**
     * 检测图片的 MIME 类型
     * @param data 图片数据
     * @return MIME 类型字符串
     */
    private fun detectPictureMimeType(data: ByteArray): String {
        return when {
            data.size >= 4 &&
                    data[0] == 0xFF.toByte() &&
                    data[1] == 0xD8.toByte() &&
                    data[2] == 0xFF.toByte() -> "image/jpeg"
            data.size >= 8 &&
                    data[0] == 0x89.toByte() &&
                    data[1] == 0x50.toByte() &&
                    data[2] == 0x4E.toByte() &&
                    data[3] == 0x47.toByte() &&
                    data[4] == 0x0D.toByte() &&
                    data[5] == 0x0A.toByte() &&
                    data[6] == 0x1A.toByte() &&
                    data[7] == 0x0A.toByte() -> "image/png"
            data.size >= 12 &&
                    data[0] == 0x52.toByte() &&
                    data[1] == 0x49.toByte() &&
                    data[2] == 0x46.toByte() &&
                    data[3] == 0x46.toByte() &&
                    data[8] == 0x57.toByte() &&
                    data[9] == 0x45.toByte() &&
                    data[10] == 0x42.toByte() &&
                    data[11] == 0x50.toByte() -> "image/webp"
            else -> "image/jpeg" // 默认为 JPEG
        }
    }
}