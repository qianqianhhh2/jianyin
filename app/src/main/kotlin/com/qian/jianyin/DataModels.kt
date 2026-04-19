package com.qian.jianyin

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 歌曲数据类
 * 用于存储歌曲的基本信息
 * @property id 歌曲ID
 * @property name 歌曲名称
 * @property artist 歌手名称
 * @property url 歌曲播放地址
 * @property pic 歌曲封面地址
 * @property lrc 歌词内容
 */
data class Song(
    val id: String = "",
    var name: String = "未知歌曲",
    var artist: String = "未知歌手",
    val url: String = "",
    val pic: String = "",
    val lrc: String? = null,
    val isLocal: Boolean = false, // 是否为本地歌曲
    val isBiliVideo: Boolean = false, // 是否为B站视频
    val bvid: String = "", // B站视频ID
    val cid: Long = 0 // B站视频的cid
)

/**
 * 歌词行数据类
 * 用于存储单句歌词及其时间戳
 * @property time 时间戳（毫秒）
 * @property text 歌词文本
 */
data class LrcLine(val time: Long, val text: String)

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
