package com.qian.jianyin

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.collections.ArrayList

/**
 * 本地音乐管理器
 */
class LocalMusicManager(private val context: Context) {

    /**
     * 支持的音乐文件格式
     */
    private val supportedFormats = setOf(".mp3", ".wav", ".flac", ".m4a", ".ogg")
    

    /** 扫描时需要排除的目录 */
    private val excludedDirs = setOf(".thumbnails", "Android", "data", "obb", ".Trash")

    /**
     * 生成稳定的本地歌曲唯一ID（基于绝对路径）
     */
    private fun localSongId(file: File): String = "local_${file.absolutePath}"

    /**
     * 获取封面缓存文件路径
     */
    private fun coverCacheFile(file: File): File =
        File(context.cacheDir, "cover_${file.absolutePath.hashCode()}.jpg")

    /**
     * 从文件名解析歌手和歌名（支持多种分隔符）
     */
    private fun parseFileName(fileName: String): Pair<String, String> {
        val separators = listOf(" - ", " -- ", " – ", " — ", "_")
        for (sep in separators) {
            val parts = fileName.split(sep)
            if (parts.size >= 2) {
                val artist = parts[0].trim()
                val title = parts.drop(1).joinToString(sep).trim()
                if (artist.isNotEmpty() && title.isNotEmpty()) {
                    return artist to title
                }
            }
        }
        return "未知歌手" to fileName
    }

    /**
     * 扫描指定文件夹中的音乐文件
     */
    suspend fun scanFolder(folderPath: String, progressCallback: ((Int, Int, String) -> Unit)? = null): List<Song> = withContext(Dispatchers.IO) {
        val songs = ArrayList<Song>()
        val folder = File(folderPath)

        Log.d("LocalMusicManager", "开始扫描文件夹: $folderPath")

        if (!folder.exists()) {
            Log.d("LocalMusicManager", "文件夹不存在: $folderPath")
            return@withContext songs
        }

        if (!folder.isDirectory) {
            Log.d("LocalMusicManager", "路径不是文件夹: $folderPath")
            return@withContext songs
        }

        Log.d("LocalMusicManager", "文件夹存在且是目录，开始遍历")

        // 先收集所有音乐文件路径（避免全量 walk 到内存）
        val musicFiles = folder.walk()
            .onEnter { dir -> dir.name !in excludedDirs }
            .filter { it.isFile && supportedFormats.contains("." + it.extension.lowercase()) }
            .toList()

        val totalMusicFiles = musicFiles.size
        Log.d("LocalMusicManager", "找到 $totalMusicFiles 个音乐文件")

        var processedCount = 0
        musicFiles.forEach { file ->
            processedCount++
            val song = parseSongFromFile(file)
            if (song != null) {
                songs.add(song)
                progressCallback?.invoke(processedCount, totalMusicFiles, song.name)
            } else {
                progressCallback?.invoke(processedCount, totalMusicFiles, file.name)
            }
        }

        Log.d("LocalMusicManager", "扫描完成，共 $totalMusicFiles 个音乐文件，成功解析 ${songs.size} 首")

        songs
    }

    /**
     * 从文件解析歌曲信息
     */
    fun parseSongFromFile(file: File): Song? {
        try {
            return parseMetadata(file)
        } catch (e: Exception) {
            Log.e("LocalMusicManager", "解析文件失败: ${file.absolutePath}, 错误: ${e.message}")
            val (artist, title) = parseFileName(file.nameWithoutExtension)
            return Song(
                id = localSongId(file),
                name = title,
                artist = artist,
                url = file.absolutePath,
                pic = "",
                lrc = null,
                source = SongSource.LOCAL,
                isLocal = true
            )
        }
    }

    /**
     * 解析歌曲元数据（歌名、歌手、封面、歌词）
     */
    private fun parseMetadata(file: File): Song {
        // --- 1. MediaMetadataRetriever 获取基础元数据 ---
        var songName: String
        var songArtist: String
        var songAlbum: String
        var retriever: MediaMetadataRetriever? = null

        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            songName = if (!title.isNullOrEmpty()) title else file.nameWithoutExtension

            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            songArtist = if (!artist.isNullOrEmpty()) artist else "未知歌手"

            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            songAlbum = if (!album.isNullOrEmpty()) album else ""

            // 回退：从文件名解析
            if (songArtist == "未知歌手") {
                val (fileNameArtist, fileNameTitle) = parseFileName(file.nameWithoutExtension)
                songArtist = fileNameArtist
                if (title.isNullOrEmpty()) {
                    songName = fileNameTitle
                }
            }
        } catch (e: Exception) {
            Log.e("LocalMusicManager", "MediaMetadataRetriever 解析失败: ${e.message}")
            val (artist, title) = parseFileName(file.nameWithoutExtension)
            songName = title
            songArtist = artist
            songAlbum = ""
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }

        // --- 2. TagLib 读取封面、歌词、标题、歌手 ---
        val tagResult = readTagLibMetadata(file)

        // TagLib 元数据作为兜底：MediaMetadataRetriever 读不到时用 TagLib 的
        if (songName == file.nameWithoutExtension && tagResult.title != null) {
            songName = tagResult.title
            Log.d("LocalMusicManager", "歌曲名从 TagLib 补充: $songName")
        }
        if (songArtist == "未知歌手" && tagResult.artist != null) {
            songArtist = tagResult.artist
            Log.d("LocalMusicManager", "歌手从 TagLib 补充: $songArtist")
        }

        // --- 3. TagLib 失败时用 MediaMetadataRetriever 读取封面兜底 ---
        val finalCoverPath = if (tagResult.coverPath.isEmpty()) {
            readEmbeddedPictureWithRetriever(file)
        } else {
            tagResult.coverPath
        }

        return Song(
            id = localSongId(file),
            name = songName,
            artist = songArtist,
            url = file.absolutePath,
            pic = finalCoverPath,
            lrc = tagResult.lyrics,
            album = songAlbum,
            source = SongSource.LOCAL,
            isLocal = true
        )
    }

    /**
     * TagLib 元数据结果
     */
    private data class TagLibResult(
        val coverPath: String = "",
        val lyrics: String? = null,
        val title: String? = null,
        val artist: String? = null
    )

    /**
     * 使用 TagLib 读取嵌入的封面、歌词、标题、歌手
     */
    private fun readTagLibMetadata(file: File): TagLibResult {
        var coverPath = ""
        var lyrics: String? = null
        var title: String? = null
        var artist: String? = null
        var pfd: ParcelFileDescriptor? = null

        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val metadata = TagLib.getMetadata(pfd.dup().detachFd(), true)
            val propertyMap = metadata?.propertyMap

            // 读取嵌入封面
            val pictures = metadata?.pictures
            if (pictures != null && pictures.isNotEmpty()) {
                val frontCover = pictures.firstOrNull {
                    it.pictureType.equals("Front Cover", ignoreCase = true)
                } ?: pictures.first()
                val coverFile = coverCacheFile(file)
                coverFile.writeBytes(frontCover.data)
                coverPath = coverFile.absolutePath
                Log.d("LocalMusicManager", "TagLib 读取封面成功: $coverPath")
            }

            // 读取所有文本属性
            if (propertyMap != null) {
                // 标题
                title = propertyMap.entries.firstOrNull { (key, _) ->
                    key.equals("TITLE", ignoreCase = true)
                }?.value?.firstOrNull()?.takeIf { it.isNotBlank() }

                // 歌手
                artist = propertyMap.entries.firstOrNull { (key, _) ->
                    key.equals("ARTIST", ignoreCase = true)
                }?.value?.firstOrNull()?.takeIf { it.isNotBlank() }

                // 歌词
                val lyricsFields = listOf("LYRICS", "UNSYNCEDLYRICS", "DESCRIPTION", "LYRIC", "USLT")
                val bomChar = '\uFEFF'
                val nulChar = '\u0000'
                for (field in lyricsFields) {
                    val entry = propertyMap.entries.firstOrNull { (key, _) ->
                        key.equals(field, ignoreCase = true)
                    }
                    if (entry != null && entry.value.isNotEmpty()) {
                        val cleanedLyrics = entry.value[0]
                            .replace(bomChar.toString(), "")
                            .trim(nulChar, ' ')
                            .takeIf { it.isNotBlank() }
                        if (cleanedLyrics != null) {
                            lyrics = cleanedLyrics
                            Log.d("LocalMusicManager", "TagLib 从 ${entry.key} 读取歌词成功，长度: ${lyrics.length}")
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LocalMusicManager", "TagLib 读取失败: ${e.message}")
        } finally {
            try { pfd?.close() } catch (_: Exception) {}
        }

        return TagLibResult(coverPath, lyrics, title, artist)
    }

    /**
     * 使用 MediaMetadataRetriever 读取嵌入图片（TagLib 失败时的兜底）
     */
    private fun readEmbeddedPictureWithRetriever(file: File): String {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val art = retriever.embeddedPicture
            if (art != null) {
                val coverFile = coverCacheFile(file)
                coverFile.outputStream().use { it.write(art) }
                Log.d("LocalMusicManager", "MediaMetadataRetriever 读取封面成功")
                return coverFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e("LocalMusicManager", "MediaMetadataRetriever 读取封面失败: ${e.message}")
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }
        return ""
    }

    /**
     * 提取歌曲歌词（优先 TagLib，兜底 jaudiotagger + 同名 LRC 文件）
     */
    fun extractLyrics(filePath: String): String? {
        Log.i("LocalMusicManager", "=== 开始提取歌词: $filePath ===")
        val file = File(filePath)

        // --- 1. TagLib 提取 ---
        try {
            val tagResult = readTagLibMetadata(file)
            val lyrics = tagResult.lyrics
            if (lyrics != null && lyrics.length > 20) {
                Log.i("LocalMusicManager", "TagLib 提取歌词成功，长度: ${lyrics.length}")
                return lyrics
            }
        } catch (e: Exception) {
            Log.e("LocalMusicManager", "TagLib 提取歌词失败: ${e.message}")
        }

        // --- 2. jaudiotagger 兜底（文件大小限制 50MB 防 OOM）---
        val maxSizeForJaudiotagger = 50L * 1024 * 1024 // 50MB
        if (file.length() <= maxSizeForJaudiotagger) {
            Log.i("LocalMusicManager", "TagLib 未找到歌词，尝试 jaudiotagger（文件大小: ${file.length()} bytes）")
            try {
                val result = extractLyricsWithJaudiotagger(file)
                if (result != null) return result
            } catch (e: Exception) {
                Log.e("LocalMusicManager", "jaudiotagger 解析失败: ${e.message}")
            }
        } else {
            Log.i("LocalMusicManager", "文件过大(${file.length()} bytes)，跳过 jaudiotagger 防 OOM")
        }

        // --- 3. 查找同名 LRC/TXT 文件 ---
        val lrcContent = findExternalLyricFile(file)
        if (lrcContent != null) return lrcContent

        Log.i("LocalMusicManager", "=== 未找到歌词 ===")
        return null
    }

    /**
     * 使用 jaudiotagger 提取歌词（仅限小文件）
     */
    private fun extractLyricsWithJaudiotagger(file: File): String? {
        val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
        val tag = audioFile.tag ?: return null

        val extension = file.extension.lowercase()
        when (extension) {
            "mp3" -> {
                val id3Fields = listOf(
                    org.jaudiotagger.tag.FieldKey.LYRICS,
                    org.jaudiotagger.tag.FieldKey.COMMENT
                )
                for (field in id3Fields) {
                    val content = tag.getFirst(field)
                    if (!content.isNullOrEmpty() && content.contains("\n")) {
                        return content
                    }
                }
            }
            "flac", "ogg" -> {
                // Vorbis 标签
                for (key in listOf(org.jaudiotagger.tag.FieldKey.LYRICS, org.jaudiotagger.tag.FieldKey.COMMENT)) {
                    val content = tag.getFirst(key)
                    if (!content.isNullOrEmpty() && content.length > 5) return content
                }

                // FLAC 特殊处理
                if (extension == "flac" && tag is org.jaudiotagger.tag.flac.FlacTag) {
                    val vorbisTag = tag.vorbisCommentTag
                    if (vorbisTag != null) {
                        for (fn in listOf("LYRICS", "UNSYNCEDLYRICS", "LYRIC", "USLT", "DESCRIPTION")) {
                            val content = vorbisTag.getFirst(fn)
                            if (!content.isNullOrEmpty() && content.length > 5) return content
                        }
                    }
                }

                // 模糊匹配包含 LYRIC 的字段
                try {
                    for (field in tag.fields) {
                        if (field.id.contains("LYRIC", ignoreCase = true)) {
                            val content = field.toString()
                            if (content.length > 5) return content
                        }
                    }
                } catch (_: Exception) {}

                // 额外尝试
                for (fn in listOf("lyrics", "LYRICS", "UNSYNCEDLYRICS", "LYRIC", "USLT", "DESCRIPTION")) {
                    val content = tag.getFirst(fn)
                    if (!content.isNullOrEmpty() && content.length > 5) return content
                }
            }
            "m4a" -> {
                for (field in listOf(org.jaudiotagger.tag.FieldKey.LYRICS, org.jaudiotagger.tag.FieldKey.COMMENT)) {
                    val content = tag.getFirst(field)
                    if (!content.isNullOrEmpty() && content.contains("\n")) return content
                }
            }
            else -> {
                for (field in listOf(
                    org.jaudiotagger.tag.FieldKey.LYRICS,
                    org.jaudiotagger.tag.FieldKey.COMMENT
                )) {
                    val content = tag.getFirst(field)
                    if (!content.isNullOrEmpty() && content.contains("\n")) return content
                }
            }
        }
        return null
    }

    /**
     * 查找同名的外部歌词文件（.lrc / .txt）
     */
    private fun findExternalLyricFile(file: File): String? {
        val possibleNames = listOf(
            "${file.nameWithoutExtension}.lrc",
            "${file.nameWithoutExtension}.txt",
            "lyrics_${file.nameWithoutExtension}.lrc"
        )
        for (name in possibleNames) {
            val lrcFile = File(file.parent, name)
            if (lrcFile.exists()) {
                Log.i("LocalMusicManager", "找到外部歌词文件: ${lrcFile.absolutePath}")
                return lrcFile.readText()
            }
        }
        return null
    }

    /**
     * 从Uri获取文件路径
     */
    fun getPathFromUri(uri: Uri): String? {
        Log.d("LocalMusicManager", "开始解析Uri: $uri")

        return if (uri.scheme == "file") {
            val path = uri.path
            Log.d("LocalMusicManager", "File scheme Uri，路径: $path")
            path
        } else if (DocumentsContract.isDocumentUri(context, uri)) {
            // 处理文档Uri
            val documentId = DocumentsContract.getDocumentId(uri)
            Log.d("LocalMusicManager", "Document Uri，documentId: $documentId")
            if (documentId.startsWith("primary:")) {
                val path = documentId.substringAfter("primary:")
                val fullPath = Environment.getExternalStorageDirectory().absolutePath + "/" + path
                Log.d("LocalMusicManager", "Primary document，解析路径: $fullPath")
                return fullPath
            }
            Log.d("LocalMusicManager", "非Primary document，返回null")
            null
        } else if (uri.toString().startsWith("content://com.android.externalstorage.documents/tree/")) {
            // 处理文档树Uri
            val treeUri = uri.toString()
            Log.d("LocalMusicManager", "Document tree Uri: $treeUri")
            if (treeUri.contains("primary%3A")) {
                var path = treeUri.substringAfter("primary%3A")
                try {
                    path = java.net.URLDecoder.decode(path, "UTF-8")
                } catch (e: Exception) {
                    Log.e("LocalMusicManager", "URL解码失败", e)
                }
                path = path.replace("%2F", "/")
                val fullPath = Environment.getExternalStorageDirectory().absolutePath + "/" + path
                Log.d("LocalMusicManager", "解析文档树Uri路径: $fullPath")
                return fullPath
            }
            Log.d("LocalMusicManager", "文档树Uri不包含primary%3A，返回null")
            null
        } else {
            // 处理其他content
            try {
                Log.d("LocalMusicManager", "处理其他content Uri")
                val contentResolver: ContentResolver = context.contentResolver
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                        val file = File(context.cacheDir, displayName)
                        Log.d("LocalMusicManager", "从content Uri创建临时文件: ${file.absolutePath}")
                        val inputStream: InputStream? = contentResolver.openInputStream(uri)
                        inputStream?.use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        return file.absolutePath
                    }
                }
            } catch (e: Exception) {
                Log.e("LocalMusicManager", "解析content Uri失败", e)
            }
            Log.d("LocalMusicManager", "解析content Uri返回null")
            null
        }
    }
}
