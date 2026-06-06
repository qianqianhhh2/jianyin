package com.qian.jianyin.netease

data class NeteaseSongSearchResult(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String,
    val album: String,
    val albumId: String,
    val duration: Long,
    val picUrl: String
)

data class NeteasePlaylistResult(
    val id: String,
    val name: String,
    val picUrl: String,
    val trackCount: Int,
    val creatorNickname: String?
)