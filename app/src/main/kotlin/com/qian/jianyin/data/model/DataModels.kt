package com.qian.jianyin

import com.qian.jianyin.netease.NeteaseSongSearchResult
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 歌曲类型枚举
 */
enum class SongSource {
    NETEASE,  // 网易云
    QQ,       // QQ音乐
    BILI,     // B站视频
    LOCAL     // 本地文件
}

/**
 * 歌曲数据类
 * 用于存储歌曲的基本信息
 * @property id 歌曲ID
 * @property name 歌曲名称
 * @property artist 歌手名称
 * @property url 歌曲播放地址
 * @property pic 歌曲封面地址
 * @property lrc 歌词内容
 * @property source 歌曲来源
 */
data class Song(
    val id: String = "",
    var name: String = "未知歌曲",
    var artist: String = "未知歌手",
    val url: String = "",
    val pic: String = "",
    val lrc: String? = null,
    val source: SongSource = SongSource.NETEASE,
    val isLocal: Boolean = false,
    val isBiliVideo: Boolean = false,
    val bvid: String = "",
    val cid: Long = 0
) {
    companion object {
        fun detectSource(song: Song): SongSource {
            return when {
                song.isLocal -> SongSource.LOCAL
                song.isBiliVideo || song.bvid.isNotBlank() || song.id.startsWith("BV") -> SongSource.BILI
                song.id.isNotBlank() && song.url.isNotBlank() -> SongSource.NETEASE
                else -> SongSource.NETEASE
            }
        }

        /** 反序列化后用此方法修正 source/isBiliVideo/bvid 字段 */
        fun normalize(song: Song): Song = song.copy(
            source = detectSource(song),
            isBiliVideo = song.isBiliVideo || song.bvid.isNotBlank() || song.id.startsWith("BV"),
            bvid = song.bvid.ifBlank {
                if (song.id.startsWith("BV")) song.id else ""
            }
        )
    }
}

/**
 * 逐字歌词时间片
 * @property startTimeMs 起始毫秒
 * @property endTimeMs 结束毫秒
 * @property charCount 字符数
 */
data class WordTiming(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val charCount: Int = 0
)

/**
 * 歌词行数据类（支持逐字）
 * words == null → 逐行模式（传统 LRC）
 * words != null → 逐字模式（YRC）
 * @property startTimeMs 行起始时间（毫秒）
 * @property endTimeMs 行结束时间（毫秒）
 * @property text 歌词文本
 * @property words 逐字时间片列表
 */
data class LyricEntry(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val words: List<WordTiming>? = null
)

/**
 * 歌词行数据类（旧版兼容）
 * 用于存储单句歌词及其时间戳
 * @property time 时间戳（毫秒）
 * @property text 歌词文本
 */
data class LrcLine(val time: Long, val text: String)

/**
 * 播放状态数据类
 * 用于保存和恢复播放队列状态
 * @property songs 播放队列中的歌曲列表
 * @property currentIndex 当前播放歌曲的索引
 * @property timestamp 保存时间戳
 */
data class PlaybackState(
    val songs: List<Song>,
    val currentIndex: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 将 NeteaseSongSearchResult 映射为 Song（URL 为空，播放时动态获取）
 */
fun NeteaseSongSearchResult.toSong(): Song = Song(
    id = id,
    name = name,
    artist = artist,
    url = "",
    pic = picUrl,
    source = SongSource.NETEASE
)

/**
 * Meting API 接口
 * 用于与 Meting API 进行交互，获取歌曲和歌词数据
 */
interface MetingApi {
    /**
     * 搜索歌曲
     * @param server 音乐服务器，默认为网易云
     * @param type 搜索类型，默认为 search
     * @param keyword 搜索关键词
     * @return 歌曲列表
     */
    @GET("meting/")
    suspend fun searchSongs(
        @Query("server") server: String = "netease",
        @Query("type") type: String = "search",
        @Query("id") keyword: String
    ): List<Song>

    /**
     * 通过 URL 获取歌词
     * 适配直接传入歌词 URL 的情况
     * @param url 歌词 URL
     * @return 歌词内容
     */
    @GET
    suspend fun getLrcByUrl(@Url url: String): String

    /**
     * 通过歌曲 ID 获取歌词
     * 适配只有 ID 的情况
     * @param server 音乐服务器，默认为网易云
     * @param type 类型，默认为 lrc
     * @param id 歌曲 ID
     * @return 歌词内容
     */
    @GET("meting/")
    suspend fun getLrcById(
        @Query("server") server: String = "netease",
        @Query("type") type: String = "lrc",
        @Query("id") id: String
    ): String
}
