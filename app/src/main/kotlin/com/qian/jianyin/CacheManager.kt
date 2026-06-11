package com.qian.jianyin

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import com.qian.jianyin.bili.BiliApi

/**
 * 缓存管理类
 * 负责歌曲播放时的自动缓存（保存mp3、封面、歌词到cache目录）
 */
object CacheManager {
    private val client = OkHttpClient()
    private const val CACHE_DIR = "jianyin/cache"

    /**
     * 获取缓存目录
     */
    fun getCacheDirectory(context: Context): File {
        val cacheDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            CACHE_DIR
        )
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }

    /**
     * 获取缓存文件名（不含扩展名）
     * 格式：歌曲名-歌手
     */
    private fun getCacheFileName(song: Song): String {
        return "${sanitizeFileName(song.name)}-${sanitizeFileName(song.artist)}"
    }

    /**
     * 检查歌曲是否已缓存
     */
    fun isCached(context: Context, song: Song): Boolean {
        val cacheDir = getCacheDirectory(context)
        val fileName = getCacheFileName(song)
        val mp3File = File(cacheDir, "$fileName.mp3")
        return mp3File.exists()
    }

    /**
     * 获取缓存的mp3文件路径
     */
    fun getCachedMp3Path(context: Context, song: Song): String? {
        val cacheDir = getCacheDirectory(context)
        val fileName = getCacheFileName(song)
        val mp3File = File(cacheDir, "$fileName.mp3")
        return if (mp3File.exists()) mp3File.absolutePath else null
    }

    /**
     * 获取缓存的封面文件路径
     */
    fun getCachedCoverPath(context: Context, song: Song): String? {
        val cacheDir = getCacheDirectory(context)
        val fileName = getCacheFileName(song)
        val coverFile = File(cacheDir, "$fileName.jpg")
        return if (coverFile.exists()) coverFile.absolutePath else null
    }

    /**
     * 获取缓存的歌词文件路径
     */
    fun getCachedLrcPath(context: Context, song: Song): String? {
        val cacheDir = getCacheDirectory(context)
        val fileName = getCacheFileName(song)
        val lrcFile = File(cacheDir, "$fileName.lrc")
        return if (lrcFile.exists()) lrcFile.absolutePath else null
    }

    /**
     * 读取缓存的歌词内容
     */
    fun readCachedLyrics(context: Context, song: Song): String? {
        val lrcPath = getCachedLrcPath(context, song)
        if (lrcPath != null) {
            try {
                return File(lrcPath).readText()
            } catch (e: Exception) {
                Log.e("CacheManager", "读取缓存歌词失败: ${e.message}")
            }
        }
        return null
    }

    /**
     * 读取缓存的双语歌词（原文 + 翻译）
     * @return Pair(原文歌词, 翻译歌词)，任一为null表示不存在
     */
    fun readCachedLyricsBilingual(context: Context, song: Song): Pair<String?, String?> {
        val combined = readCachedLyrics(context, song) ?: return null to null
        val separator = "\n[TRANSLATED]\n"
        val idx = combined.indexOf(separator)
        return if (idx == -1) {
            combined to null
        } else {
            combined.substring(0, idx) to combined.substring(idx + separator.length)
        }
    }

    /**
     * 缓存歌曲（mp3、封面、歌词）
     * @param context 上下文
     * @param song 歌曲对象
     * @param mp3Url mp3下载URL（可为null，为null时使用song.url）
     * @param coverUrl 封面URL（可为null，为null时使用song.pic）
     * @param lrcContent 歌词内容（可为null）
     * @param progressCallback 进度回调
     * @return 缓存结果
     */
    suspend fun cacheSong(
        context: Context,
        song: Song,
        mp3Url: String? = null,
        coverUrl: String? = null,
        lrcContent: String? = null,
        progressCallback: ((Float) -> Unit)? = null
    ): Result<String> {
        return try {
            val cacheDir = getCacheDirectory(context)
            val fileName = getCacheFileName(song)

            // 检查是否已缓存
            if (isCached(context, song)) {
                Log.d("CacheManager", "歌曲已缓存: ${song.name}")
                return Result.success("已缓存")
            }

            val results = mutableListOf<String>()

            // 1. 缓存mp3文件
            val finalMp3Url = mp3Url ?: song.url
            if (finalMp3Url.isNotBlank()) {
                val mp3File = File(cacheDir, "$fileName.mp3")
                downloadFile(finalMp3Url, mp3File, song.isBiliVideo, context, progressCallback)
                results.add("mp3")
            }

            // 2. 缓存封面文件
            val finalCoverUrl = coverUrl ?: song.pic
            if (finalCoverUrl.isNotBlank()) {
                try {
                    val coverFile = File(cacheDir, "$fileName.jpg")
                    downloadFile(finalCoverUrl, coverFile, false, context, null)
                    results.add("封面")
                } catch (e: Exception) {
                    Log.e("CacheManager", "缓存封面失败: ${e.message}")
                }
            }

            // 3. 缓存歌词文件
            if (lrcContent != null && lrcContent.isNotBlank()) {
                try {
                    val lrcFile = File(cacheDir, "$fileName.lrc")
                    lrcFile.writeText(lrcContent)
                    results.add("歌词")
                } catch (e: Exception) {
                    Log.e("CacheManager", "缓存歌词失败: ${e.message}")
                }
            }

            Log.d("CacheManager", "缓存完成: ${song.name}, 内容: ${results.joinToString(", ")}")
            Result.success("缓存完成: ${results.joinToString(", ")}")
        } catch (e: Exception) {
            Log.e("CacheManager", "缓存失败: ${song.name}", e)
            Result.failure(e)
        }
    }

    /**
     * 下载文件到本地
     */
    private suspend fun downloadFile(
        url: String,
        targetFile: File,
        isBiliStream: Boolean,
        context: Context,
        progressCallback: ((Float) -> Unit)?
    ) {
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

            response.body?.byteStream()?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        if (totalSize > 0 && progressCallback != null) {
                            val progress = downloadedSize.toFloat() / totalSize
                            progressCallback.invoke(progress)
                        }
                    }
                }
            }
        }
    }

    /**
     * 清理文件名中的非法字符
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
     * 获取缓存目录大小（MB）
     */
    fun getCacheSize(context: Context): Long {
        val cacheDir = getCacheDirectory(context)
        if (!cacheDir.exists()) return 0

        var totalSize = 0L
        cacheDir.walk().filter { it.isFile }.forEach { file ->
            totalSize += file.length()
        }
        return totalSize / (1024 * 1024) // 转换为MB
    }

    /**
     * 清空缓存目录
     */
    fun clearCache(context: Context): Boolean {
        val cacheDir = getCacheDirectory(context)
        if (!cacheDir.exists()) return true

        try {
            cacheDir.walk().filter { it.isFile }.forEach { file ->
                file.delete()
            }
            Log.d("CacheManager", "缓存已清空")
            return true
        } catch (e: Exception) {
            Log.e("CacheManager", "清空缓存失败: ${e.message}")
            return false
        }
    }
}