package com.qian.jianyin

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import org.json.JSONObject

/**
 * 歌曲元数据类
 * 用于存储下载歌曲的详细信息
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

    private const val DEFAULT_DOWNLOAD_DIR = "jianyin/download"

    /**
     * 安全复制并消耗 PFD，返回 detachFd 后的文件描述符
     * 调用者负责不再对原始 pfd 进行 close，但 fd 后续由 TagLib 消费
     */
    private fun ParcelFileDescriptor.safeDupFd(): Int {
        val dup = this.dup()
        val fd = dup.detachFd()
        dup.close()
        return fd
    }

    /**
     * 安全地打开文件描述符并执行操作块（保证释放）
     */
    private inline fun <T> withFileDescriptor(
        context: Context,
        audioUri: Uri,
        mode: String = "rw",
        block: (ParcelFileDescriptor) -> T
    ): T {
        val pfd = context.contentResolver.openFileDescriptor(audioUri, mode)
            ?: throw IllegalStateException("无法获取文件描述符: $audioUri")
        try {
            return block(pfd)
        } finally {
            try { pfd.close() } catch (_: Exception) {}
        }
    }

    /**
     * 下载歌曲
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
                neteaseLrc = fetchNeteaseBilingualLyric(song.id)
            }

            val audioFileName = "${sanitizeFileName(song.name)}-${sanitizeFileName(song.artist)}.mp3"

            if (!fileExists(context, downloadDirUri, audioFileName)) {
                // 先下载封面到临时文件（用于嵌入）
                var tempCoverFile: File? = null
                try {
                    if (song.pic.isNotEmpty()) {
                        try {
                            tempCoverFile = File.createTempFile("temp_cover_${song.id}", ".jpg", context.cacheDir)
                            downloadFileToTemp(context, song.pic, tempCoverFile, false)
                        } catch (e: Exception) {
                            Log.e("DownloadManager", "下载封面失败: ${e.message}")
                            tempCoverFile?.delete()
                            tempCoverFile = null
                        }
                    }

                    // 下载音频文件
                    downloadFileToUri(context, audioUrl, downloadDirUri, audioFileName, progressCallback, song.isBiliVideo)
                    results.add("音频文件")

                    // 将歌词和封面嵌入到MP3文件中
                    val audioFileUri = getFileUri(context, downloadDirUri, audioFileName)
                    val lrcContent = neteaseLrc ?: song.lrc

                    if (audioFileUri != null) {
                        val embedded = embedMetadata(context, audioFileUri, tempCoverFile, lrcContent, song.name, song.artist)
                        if (embedded) {
                            results.add("嵌入歌词和封面")
                        }
                    }
                } finally {
                    tempCoverFile?.delete()
                }

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
     */
    fun getLocalSongUri(context: Context, song: Song): Uri? {
        Log.d("DownloadManager", "=== getLocalSongUri ===")
        Log.d("DownloadManager", "Song: ${song.name} - ${song.artist}")

        val audioFileName = "${sanitizeFileName(song.name)}-${sanitizeFileName(song.artist)}.mp3"

        if (DownloadSettingsStore.isUsingCustomPath(context)) {
            val customUri = DownloadSettingsStore.getCustomUri(context)
            Log.d("DownloadManager", "Trying SAF path: customUri=${customUri?.toString() ?: "null"}")

            if (customUri != null) {
                val downloadDirUri = getDirectoryUri(context, customUri, "download")
                if (downloadDirUri != null) {
                    val result = getFileUri(context, downloadDirUri, audioFileName)
                    if (result != null) {
                        Log.d("DownloadManager", "SAF path successful: $result")
                        return result
                    }
                }
                Log.w("DownloadManager", "SAF path failed, falling back to default path")
            }
        }

        Log.d("DownloadManager", "Trying default file path")
        val defaultDirUri = getDefaultDownloadUri(context)
        val result = getFileUri(context, defaultDirUri, audioFileName)
        if (result != null) {
            Log.d("DownloadManager", "Default path successful: $result")
            return result
        }

        Log.w("DownloadManager", "Both SAF and default paths failed")
        return null
    }

    /**
     * 获取本地封面文件Uri（从嵌入MP3的封面中提取）
     */
    fun getLocalCoverUri(context: Context, song: Song): Uri? {
        val audioUri = getLocalSongUri(context, song) ?: return null
        return try {
            withFileDescriptor(context, audioUri, "r") { pfd ->
                val pictures = TagLib.getPictures(pfd.safeDupFd())
                if (pictures.isNotEmpty()) {
                    val coverData = pictures.first().data
                    val tempCoverFile = File.createTempFile("embedded_cover_", ".jpg", context.cacheDir)
                    tempCoverFile.writeBytes(coverData)
                    Uri.fromFile(tempCoverFile)
                } else null
            }
        } catch (e: Exception) {
            Log.e("DownloadManager", "读取嵌入封面失败: ${e.message}")
            null
        }
    }

    /**
     * 获取本地歌词文件Uri（从嵌入MP3的歌词中提取）
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
     */
    fun readEmbeddedLyrics(context: Context, song: Song): String? {
        val audioUri = getLocalSongUri(context, song) ?: return null
        return try {
            withFileDescriptor(context, audioUri, "r") { pfd ->
                TagLib.getMetadataPropertyValues(pfd.safeDupFd(), "LYRICS")?.firstOrNull()
            }
        } catch (e: Exception) {
            Log.e("DownloadManager", "读取嵌入歌词失败: ${e.message}")
            null
        }
    }

    /**
     * 从已下载的单文件MP3中读取双语歌词（原文 + 翻译）
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
     */
    fun getLocalSongPath(context: Context, song: Song): String? {
        val uri = getLocalSongUri(context, song) ?: return null
        return if (uri.scheme == "file") uri.path else uri.toString()
    }

    /**
     * 获取本地封面文件路径（兼容旧代码）
     */
    fun getLocalCoverPath(context: Context, song: Song): String? {
        val uri = getLocalCoverUri(context, song) ?: return null
        return if (uri.scheme == "file") uri.path else uri.toString()
    }

    /**
     * 获取本地歌词文件路径（兼容旧代码）
     */
    fun getLocalLrcPath(context: Context, song: Song): String? {
        val uri = getLocalLrcUri(context, song) ?: return null
        return if (uri.scheme == "file") uri.path else uri.toString()
    }

    // ======================== 元数据嵌入 ========================

    /**
     * 将歌词和封面嵌入到MP3文件中
     * 先一次性写入所有 propertyMap + pictures，避免多次 open 导致覆盖
     */
    private fun embedMetadata(
        context: Context,
        audioUri: Uri,
        tempCoverFile: File?,
        lrcContent: String?,
        songName: String,
        artistName: String
    ): Boolean {
        return try {
            withFileDescriptor(context, audioUri, "rw") { pfd ->
                // 构建所有文本属性
                val propertyMap: PropertyMap = hashMapOf()
                propertyMap["TITLE"] = arrayOf(songName)
                propertyMap["ARTIST"] = arrayOf(artistName)
                if (lrcContent != null && lrcContent.isNotBlank()) {
                    propertyMap["LYRICS"] = arrayOf(lrcContent)
                    propertyMap["UNSYNCEDLYRICS"] = arrayOf(lrcContent)
                }

                // 构建封面图片
                val pictures: Array<Picture>? = if (tempCoverFile != null && tempCoverFile.exists()) {
                    try {
                        val coverBytes = tempCoverFile.readBytes()
                        val mimeType = detectPictureMimeType(coverBytes)
                        arrayOf(Picture(
                            data = coverBytes,
                            description = "",
                            pictureType = "Front Cover",
                            mimeType = mimeType
                        ))
                    } catch (e: Exception) {
                        Log.e("DownloadManager", "读取封面数据失败: ${e.message}")
                        null
                    }
                } else null

                // 写入属性值
                TagLib.savePropertyMap(pfd.safeDupFd(), propertyMap)

                // 写入封面（如果有）
                if (pictures != null) {
                    TagLib.savePictures(pfd.safeDupFd(), pictures)
                }

                Log.d("DownloadManager", "MP3元数据嵌入完成")
            }
            true
        } catch (e: Exception) {
            Log.e("DownloadManager", "嵌入元数据失败: ${e.message}", e)
            false
        }
    }

    // ======================== 文件操作 ========================

    private suspend fun downloadFileToUri(context: Context, url: String, parentDirUri: Uri, fileName: String, progressCallback: ((Float) -> Unit)? = null, isBiliStream: Boolean = false) {
        val requestBuilder = Request.Builder().url(url)

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
                .header("User-Agent", "Mozilla/5.0")
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

    // ======================== SAF 目录管理 ========================

    private fun getOrCreateDirectory(context: Context, baseUri: Uri?, dirName: String): Uri {
        val parentUri = baseUri ?: getDefaultDownloadUri(context)
        if (parentUri.toString().isBlank()) {
            return getOrCreateDirectory(context, null, dirName)
        }

        if (parentUri.scheme == "file") {
            val file = File(parentUri.path, dirName)
            if (!file.exists()) {
                file.mkdirs()
            }
            return Uri.fromFile(file)
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

    private fun getDirectoryUri(context: Context, baseUri: Uri?, dirName: String): Uri? {
        val parentUri = baseUri ?: getDefaultDownloadUri(context)
        return findFile(context, parentUri, dirName)
    }

    private fun fileExists(context: Context, parentUri: Uri, fileName: String): Boolean {
        return findFile(context, parentUri, fileName) != null
    }

    private fun findFile(context: Context, parentUri: Uri, fileName: String): Uri? {
        Log.d("DownloadManager", "findFile: parentUri=$parentUri, fileName=$fileName, scheme=${parentUri.scheme}")

        if (parentUri.scheme == "file") {
            val file = File(parentUri.path, fileName)
            return if (file.exists()) Uri.fromFile(file) else null
        }

        try {
            val docId = if (parentUri.toString().contains("/document/")) {
                parentUri.lastPathSegment ?: DocumentsContract.getTreeDocumentId(parentUri)
            } else {
                DocumentsContract.getTreeDocumentId(parentUri)
            }

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
        } catch (e: Exception) {
            Log.e("DownloadManager", "SAF findFile error: ${e.message}", e)
        }
        return null
    }

    private fun getFileUri(context: Context, parentUri: Uri, fileName: String): Uri? {
        return findFile(context, parentUri, fileName)
    }

    private fun createFile(context: Context, parentUri: Uri, fileName: String): Uri {
        if (parentUri.scheme == "file") {
            val file = File(parentUri.path, fileName)
            if (file.exists()) file.delete()
            file.createNewFile()
            return Uri.fromFile(file)
        }

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

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".lrc", ignoreCase = true) -> "application/octet-stream"
            fileName.endsWith(".json", ignoreCase = true) -> "application/json"
            else -> "application/octet-stream"
        }
    }

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

    private fun getOrCreateDownloadDirectory(context: Context, customUri: Uri?): Uri {
        if (customUri == null) return getDefaultDownloadUri(context)

        val docId = DocumentsContract.getTreeDocumentId(customUri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(customUri, docId)
        return DocumentsContract.createDocument(
            context.contentResolver,
            documentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            "download"
        ) ?: throw Exception("无法创建download目录")
    }

    // ======================== 工具函数 ========================

    private fun sanitizeFileName(name: String): String {
        val invalidChars = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
        var sanitized = name
        for (char in invalidChars) {
            sanitized = sanitized.replace(char, '_')
        }
        return sanitized.trim()
    }

    private fun detectPictureMimeType(data: ByteArray): String {
        // JPEG: FF D8 FF
        if (data.size >= 3 &&
            data[0] == 0xFF.toByte() &&
            data[1] == 0xD8.toByte() &&
            data[2] == 0xFF.toByte()
        ) return "image/jpeg"

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (data.size >= 8 &&
            data[0] == 0x89.toByte() && data[1] == 0x50.toByte() &&
            data[2] == 0x4E.toByte() && data[3] == 0x47.toByte() &&
            data[4] == 0x0D.toByte() && data[5] == 0x0A.toByte() &&
            data[6] == 0x1A.toByte() && data[7] == 0x0A.toByte()
        ) return "image/png"

        // GIF: 47 49 46 38
        if (data.size >= 4 &&
            data[0] == 0x47.toByte() && data[1] == 0x49.toByte() &&
            data[2] == 0x46.toByte() && data[3] == 0x38.toByte()
        ) return "image/gif"

        // BMP: 42 4D
        if (data.size >= 2 &&
            data[0] == 0x42.toByte() && data[1] == 0x4D.toByte()
        ) return "image/bmp"

        // WebP: 52 49 46 46 ... 57 45 42 50
        if (data.size >= 12 &&
            data[0] == 0x52.toByte() && data[1] == 0x49.toByte() &&
            data[2] == 0x46.toByte() && data[3] == 0x46.toByte() &&
            data[8] == 0x57.toByte() && data[9] == 0x45.toByte() &&
            data[10] == 0x42.toByte() && data[11] == 0x50.toByte()
        ) return "image/webp"

        return "image/jpeg"
    }
}
