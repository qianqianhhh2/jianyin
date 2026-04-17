package com.qian.jianyin

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.*
import kotlin.collections.ArrayList

/**
 * 本地音乐管理器
 */
class LocalMusicManager(private val context: Context) {
    
    /**
     * 支持的音乐文件格式
     */
    private val supportedFormats = setOf(".mp3", ".wav", ".flac", ".m4a", ".ogg")
    
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
        
        // 先计算总文件数
        val allFiles = folder.walk().filter { it.isFile }.toList()
        val totalFiles = allFiles.size
        var processedCount = 0
        var musicCount = 0
        
        Log.d("LocalMusicManager", "总文件数: $totalFiles")
        
        allFiles.forEach { file ->
            processedCount++
            val extension = "." + file.extension.lowercase()
            if (supportedFormats.contains(extension)) {
                musicCount++
                Log.d("LocalMusicManager", "找到音乐文件: ${file.absolutePath}")
                val song = parseSongFromFile(file)
                if (song != null) {
                    songs.add(song)
                    Log.d("LocalMusicManager", "成功解析歌曲: ${song.name} - ${song.artist}")
                    Log.d("LocalMusicManager", "歌曲歌词: ${song.lrc ?: "无"}")
                    // 回调进度
                    progressCallback?.invoke(musicCount - 1, musicCount, song.name)
                } else {
                    Log.d("LocalMusicManager", "解析歌曲失败: ${file.absolutePath}")
                }
            } else {
                Log.d("LocalMusicManager", "跳过非音乐文件: ${file.absolutePath}")
            }
        }
        
        Log.d("LocalMusicManager", "扫描完成，共遍历 $totalFiles 个文件，找到 $musicCount 个音乐文件，成功解析 ${songs.size} 首歌曲")
        
        songs
    }
    
    /**
     * 从文件解析歌曲信息
     */
    fun parseSongFromFile(file: File): Song? {
        try {
            // 解析元数据
            return parseMetadata(file)
        } catch (e: Exception) {
            Log.e("LocalMusicManager", "解析文件失败: ${file.absolutePath}, 错误: ${e.message}")
            // 解析失败，也返回一个基本的对象
            val fileName = file.nameWithoutExtension
            var songName = fileName
            var songArtist = "未知歌手"
            
            // 尝试从文件名中解析歌手和歌曲名
            val parts = fileName.split(" - ")
            if (parts.size >= 2) {
                songArtist = parts[0].trim()
                songName = parts.drop(1).joinToString(" - ").trim()
            }
            
            return Song(
                id = "local_${file.hashCode()}",
                name = songName,
                artist = songArtist,
                url = file.absolutePath,
                pic = "",
                lrc = null,
                isLocal = true
            )
        }
    }
    
    /**
     * 解析歌曲元数据
     */
    private fun parseMetadata(file: File): Song {
        try {
            // 使用MediaMetadataRetriever解析元数据
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            
            // 解析歌曲名
            val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
            var songName = if (!title.isNullOrEmpty()) title else file.nameWithoutExtension
            
            // 解析歌手
            val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
            var songArtist = if (!artist.isNullOrEmpty()) artist else "未知歌手"
            
            // 如果歌手解析失败，尝试从文件名中解析
            if (songArtist == "未知歌手") {
                val fileName = file.nameWithoutExtension
                val parts = fileName.split(" - ")
                if (parts.size >= 2) {
                    songArtist = parts[0].trim()
                    // 如果歌曲名也解析失败，从文件名中解析
                    if (title.isNullOrEmpty()) {
                        songName = parts.drop(1).joinToString(" - ").trim()
                    }
                }
            }
            
            // 解析封面
            var coverPath = ""
            val art = retriever.embeddedPicture
            if (art != null) {
                // 将封面保存为临时文件
                val coverFile = File(context.cacheDir, "${file.hashCode()}_cover.jpg")
                coverFile.outputStream().use {
                    it.write(art)
                }
                coverPath = coverFile.absolutePath
            }
            
            retriever.release()
            
            // 创建并返回歌曲对象
            return Song(
                id = "local_${file.hashCode()}",
                name = songName,
                artist = songArtist,
                url = file.absolutePath,
                pic = coverPath,
                lrc = null,
                isLocal = true
            )
        } catch (e: Exception) {
            Log.e("LocalMusicManager", "解析元数据失败: ${file.absolutePath}, 错误: ${e.message}")
            // 解析失败时使用默认值
            val fileName = file.nameWithoutExtension
            var songName = fileName
            var songArtist = "未知歌手"
            
            // 尝试从文件名中解析歌手和歌曲名
            val parts = fileName.split(" - ")
            if (parts.size >= 2) {
                songArtist = parts[0].trim()
                songName = parts.drop(1).joinToString(" - ").trim()
            }
            
            return Song(
                id = "local_${file.hashCode()}",
                name = songName,
                artist = songArtist,
                url = file.absolutePath,
                pic = "",
                lrc = null, 
                isLocal = true
            )
        }
    }
    
    /**
     * 提取歌曲歌词
     */
    fun extractLyrics(filePath: String): String? {
        Log.i("LocalMusicManager", "=== 开始提取歌词: $filePath ===")
        try {
            // 尝试从音乐文件中提取内嵌歌词
            val file = File(filePath)
            Log.i("LocalMusicManager", "文件路径: ${file.absolutePath}")
            Log.i("LocalMusicManager", "文件扩展名: ${file.extension}")
            
            // 使用 jaudiotagger 库提取歌词
            Log.i("LocalMusicManager", "尝试使用 jaudiotagger 库提取内嵌歌词")
            try {
                Log.i("LocalMusicManager", "开始读取音频文件")
                val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
                Log.i("LocalMusicManager", "成功读取音频文件")
                Log.i("LocalMusicManager", "音频格式: ${audioFile.audioHeader.format}")
                
                val tag = audioFile.tag
                Log.i("LocalMusicManager", "标签是否存在: ${tag != null}")
                
                if (tag != null) {
                    // 尝试从不同的标签字段中提取歌词
                    Log.i("LocalMusicManager", "开始尝试从标签字段中提取歌词")
                    
                    // 根据不同的音频格式使用不同的字段策略
                    val extension = file.extension.lowercase()
                    when (extension) {
                        "mp3" -> {
                            // MP3 使用 ID3 标签
                            Log.i("LocalMusicManager", "使用 ID3 标签策略")
                            val id3Fields = listOf(
                                org.jaudiotagger.tag.FieldKey.LYRICS,
                                org.jaudiotagger.tag.FieldKey.COMMENT,
                                org.jaudiotagger.tag.FieldKey.GENRE
                            )
                            for (field in id3Fields) {
                                val content = tag.getFirst(field)
                                Log.i("LocalMusicManager", "ID3 ${field.name} 字段内容: ${content ?: "无"}")
                                if (!content.isNullOrEmpty()) {
                                    if (content.length > 20 && content.contains("\n")) {
                                        Log.i("LocalMusicManager", "从 MP3 文件的 ${field.name} 字段中提取歌词成功: $content")
                                        return content
                                    }
                                }
                            }
                        }
                        "flac", "ogg" -> {
                            // FLAC 和 OGG 使用 Vorbis 评论
                            Log.i("LocalMusicManager", "使用增强型 Vorbis/FLAC 搜索策略")
                            
                            // 已知的 FieldKey
                            val keys = listOf(
                                org.jaudiotagger.tag.FieldKey.LYRICS,
                                org.jaudiotagger.tag.FieldKey.COMMENT
                            )
                            for (key in keys) {
                                val content = tag.getFirst(key)
                                Log.i("LocalMusicManager", "尝试字段 ${key.name}: ${content ?: "无"}")
                                if (!content.isNullOrEmpty() && content.length > 5) {
                                    Log.i("LocalMusicManager", "从 ${extension.uppercase()} 文件的 ${key.name} 字段中提取歌词成功: $content")
                                    return content
                                }
                            }
                            
                            // 处理 FLAC 特殊情况
                            if (extension == "flac") {
                                Log.i("LocalMusicManager", "处理 FLAC 特殊标签类型")
                                if (tag is org.jaudiotagger.tag.flac.FlacTag) {
                                    val vorbisTag = tag.vorbisCommentTag
                                    if (vorbisTag != null) {
                                        Log.i("LocalMusicManager", "FlacTag 包含 VorbisCommentTag")
                                        val flacFields = listOf("LYRICS", "UNSYNCEDLYRICS", "LYRIC", "USLT", "DESCRIPTION")
                                        for (fieldName in flacFields) {
                                            val content = vorbisTag.getFirst(fieldName)
                                            Log.i("LocalMusicManager", "尝试 FLAC 字段 $fieldName: ${content ?: "无"}")
                                            if (!content.isNullOrEmpty() && content.length > 5) {
                                                Log.i("LocalMusicManager", "从 FLAC 文件的 $fieldName 字段中提取歌词成功: $content")
                                                return content
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 3. 扫描所有包含LYRIC字样的原始字段
                            Log.i("LocalMusicManager", "开始扫描所有字段")
                            try {
                                val allFields = tag.fields
                                Log.i("LocalMusicManager", "开始扫描所有字段")
                                for (field in allFields) {
                                    val fieldId = field.id
                                    val fieldContent = field.toString()
                                    Log.i("LocalMusicManager", "FLAC 原始字段: $fieldId 内容: $fieldContent")
                                    if (fieldId.contains("LYRIC", ignoreCase = true)) {
                                        Log.i("LocalMusicManager", "通过模糊匹配找到字段 $fieldId: $fieldContent")
                                        if (!fieldContent.isNullOrEmpty() && fieldContent.length > 5) {
                                            return fieldContent
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("LocalMusicManager", "扫描字段失败: ${e.message}")
                            }
                            
                            // 4. 尝试其他可能的字段
                            val additionalFields = listOf(
                                "lyrics", "LYRICS", "comment", "COMMENT",
                                "UNSYNCEDLYRICS", "LYRIC", "USLT", "DESCRIPTION"
                            )
                            for (fieldName in additionalFields) {
                                val content = tag.getFirst(fieldName)
                                Log.i("LocalMusicManager", "尝试附加字段 $fieldName: ${content ?: "无"}")
                                if (!content.isNullOrEmpty() && content.length > 5) {
                                    Log.i("LocalMusicManager", "从 ${extension.uppercase()} 文件的 $fieldName 字段中提取歌词成功: $content")
                                    return content
                                }
                            }
                        }
                        "m4a" -> {
                            // M4A 使用 MP4 标签
                            Log.i("LocalMusicManager", "使用 MP4 标签策略")
                            val mp4Fields = listOf(
                                org.jaudiotagger.tag.FieldKey.LYRICS,
                                org.jaudiotagger.tag.FieldKey.COMMENT
                            )
                            for (field in mp4Fields) {
                                val content = tag.getFirst(field)
                                Log.i("LocalMusicManager", "MP4 ${field.name} 字段内容: ${content ?: "无"}")
                                if (!content.isNullOrEmpty()) {
                                    if (content.length > 20 && content.contains("\n")) {
                                        Log.i("LocalMusicManager", "从 M4A 文件的 ${field.name} 字段中提取歌词成功: $content")
                                        return content
                                    }
                                }
                            }
                        }
                        else -> {
                            // 其他格式使用通用策略
                            Log.i("LocalMusicManager", "使用通用标签策略")
                            val generalFields = listOf(
                                org.jaudiotagger.tag.FieldKey.LYRICS,
                                org.jaudiotagger.tag.FieldKey.COMMENT,
                                org.jaudiotagger.tag.FieldKey.TITLE,
                                org.jaudiotagger.tag.FieldKey.ARTIST,
                                org.jaudiotagger.tag.FieldKey.ALBUM,
                                org.jaudiotagger.tag.FieldKey.GENRE,
                                org.jaudiotagger.tag.FieldKey.COMPOSER,
                                org.jaudiotagger.tag.FieldKey.YEAR
                            )
                            for (field in generalFields) {
                                val content = tag.getFirst(field)
                                Log.i("LocalMusicManager", "通用 ${field.name} 字段内容: ${content ?: "无"}")
                                if (!content.isNullOrEmpty()) {
                                    if (content.length > 20 && content.contains("\n")) {
                                        Log.i("LocalMusicManager", "从 ${extension.uppercase()} 文件的 ${field.name} 字段中提取歌词成功: $content")
                                        return content
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LocalMusicManager", "jaudiotagger 解析失败: ${e.message}")
                e.printStackTrace()
            }
            
            // 尝试查找同名的lrc文件
            Log.i("LocalMusicManager", "尝试查找同名的lrc文件")
            val lrcFile = File(file.parent, "${file.nameWithoutExtension}.lrc")
            Log.i("LocalMusicManager", "查找的LRC文件路径: ${lrcFile.absolutePath}")
            if (lrcFile.exists()) {
                Log.i("LocalMusicManager", "找到同名lrc文件: ${lrcFile.absolutePath}")
                val lrcContent = lrcFile.readText()
                Log.i("LocalMusicManager", "LRC文件内容: $lrcContent")
                return lrcContent
            } else {
                Log.i("LocalMusicManager", "未找到同名lrc文件")
            }
            
            // 尝试查找歌词文件的其他可能后缀
            Log.i("LocalMusicManager", "尝试查找歌词文件的其他可能后缀")
            val possibleLrcNames = listOf(
                "${file.nameWithoutExtension}.lrc",
                "${file.nameWithoutExtension}.txt",
                "lyrics_${file.nameWithoutExtension}.lrc"
            )
            
            for (lrcName in possibleLrcNames) {
                val altLrcFile = File(file.parent, lrcName)
                Log.i("LocalMusicManager", "查找的歌词文件路径: ${altLrcFile.absolutePath}")
                if (altLrcFile.exists()) {
                    Log.i("LocalMusicManager", "找到歌词文件: ${altLrcFile.absolutePath}")
                    val lrcContent = altLrcFile.readText()
                    Log.i("LocalMusicManager", "歌词文件内容: $lrcContent")
                    return lrcContent
                } else {
                    Log.i("LocalMusicManager", "未找到歌词文件: ${altLrcFile.absolutePath}")
                }
            }
        } catch (e: Exception) {
            Log.e("LocalMusicManager", "提取歌词失败: ${e.message}")
            e.printStackTrace()
        }
        
        Log.i("LocalMusicManager", "=== 未找到歌词 ===")
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
                // 对路径进行URL解码
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
                        inputStream?.use {input ->
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