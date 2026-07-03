package com.qian.jianyin.netease.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.qian.jianyin.netease.NeteasePlaylistResult
import com.qian.jianyin.netease.NeteaseSongSearchResult
import com.qian.jianyin.netease.data.auth.NeteaseCookieRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object NeteaseApiService {
    private val client = NeteaseClient()
    private var cookieRepository: NeteaseCookieRepository? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        cookieRepository = NeteaseCookieRepository(appContext!!)
        // 初始化时加载保存的 cookies
        cookieRepository?.getCookiesOnce()?.takeIf { it.isNotEmpty() }?.let {
            client.setPersistedCookies(it)
        }
    }

    val isLoggedIn: Boolean
        get() = client.hasLogin()

    fun setCookies(cookies: Map<String, String>): Boolean {
        val ok = cookieRepository?.saveCookies(cookies) ?: false
        if (ok) {
            client.setPersistedCookies(cookies)
        } else {
            android.util.Log.w("NeteaseApi", "setCookies: 验证失败，未保存")
        }
        return ok
    }

    fun getCookies(): Map<String, String> {
        return cookieRepository?.getCookiesOnce() ?: client.getCookies()
    }

    fun logout() {
        client.logout()
        cookieRepository?.clear()
    }

    suspend fun searchSongs(keyword: String, limit: Int = 30, offset: Int = 0): List<NeteaseSongSearchResult> = withContext(Dispatchers.IO) {
        val response = client.searchSongs(keyword, limit, offset, usePersistedCookies = isLoggedIn)
        parseSearchResult(response)
    }

    private fun parseSearchResult(response: String): List<NeteaseSongSearchResult> {
        val songs = mutableListOf<NeteaseSongSearchResult>()
        try {
            val root = JSONObject(response)
            val result = root.optJSONObject("result") ?: return songs
            val songList = result.optJSONArray("songs") ?: return songs

            for (i in 0 until songList.length()) {
                val songJson = songList.optJSONObject(i) ?: continue
                val id = songJson.optString("id", "")
                val name = songJson.optString("name", "未知歌曲")

                val artists = parseArtists(songJson.optJSONArray("ar"))
                val artist = artists.joinToString("/")
                val artistId = artists.firstOrNull() ?: ""

                val album = songJson.optJSONObject("al")
                val albumName = album?.optString("name", "") ?: ""
                val albumId = album?.optString("id", "") ?: ""
                val picUrl = album?.optString("picUrl", "") ?: ""

                val durationMs = songJson.optLong("dt", 0)

                songs.add(NeteaseSongSearchResult(
                    id = id,
                    name = name,
                    artist = artist,
                    artistId = artistId,
                    album = albumName,
                    albumId = albumId,
                    duration = durationMs,
                    picUrl = picUrl
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return songs
    }

    private fun parseArtists(artistsArray: JSONArray?): List<String> {
        if (artistsArray == null) return emptyList()
        val artists = mutableListOf<String>()
        for (i in 0 until artistsArray.length()) {
            val artist = artistsArray.optJSONObject(i)
            artist?.optString("name")?.let { artists.add(it) }
        }
        return artists
    }

    suspend fun getSongDetail(songId: String): NeteaseSongSearchResult? = withContext(Dispatchers.IO) {
        try {
            val response = client.getSongDetail(listOf(songId.toLong()))
            parseSongDetail(response, songId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseSongDetail(response: String, songId: String): NeteaseSongSearchResult? {
        try {
            val root = JSONObject(response)
            val songs = root.optJSONArray("songs") ?: return null
            if (songs.length() == 0) return null

            val songJson = songs.optJSONObject(0) ?: return null
            val id = songJson.optString("id", songId)
            val name = songJson.optString("name", "未知歌曲")

            val artists = parseArtists(songJson.optJSONArray("ar"))
            val artist = artists.joinToString("/")
            val artistId = artists.firstOrNull() ?: ""

            val album = songJson.optJSONObject("album")
            val albumName = album?.optString("name", "") ?: ""
            val albumId = album?.optString("id", "") ?: ""
            val picUrl = album?.optString("picUrl", "") ?: album?.optString("blurPicUrl", "") ?: ""

            val durationMs = songJson.optLong("dt", 0)

            return NeteaseSongSearchResult(
                id = id,
                name = name,
                artist = artist,
                artistId = artistId,
                album = albumName,
                albumId = albumId,
                duration = durationMs,
                picUrl = picUrl
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // 音质降级链：从高到低
    private val QUALITY_FALLBACK_ORDER = listOf(
        "jymaster", "sky", "jyeffect", "hires", "lossless", "exhigh", "standard"
    )

    /**
     * 获取播放 URL，自动从首选音质向下降级尝试。
     * @param qualityLevel 首选音质，如 "exhigh"、"lossless" 等
     */
    suspend fun getSongUrl(songId: String, qualityLevel: String = "exhigh"): String? = withContext(Dispatchers.IO) {
        val candidates = buildQualityCandidates(qualityLevel)
        for ((index, quality) in candidates.withIndex()) {
            try {
                android.util.Log.d("NeteaseApi", "getSongUrl: songId=$songId, trying quality=$quality")
                val response = client.getSongDownloadUrl(songId.toLong(), quality)
                val url = parsePlaybackUrl(response)
                if (url != null) {
                    if (index > 0) {
                        android.util.Log.w("NeteaseApi", "getSongUrl: 音质降级 $qualityLevel -> $quality")
                    }
                    return@withContext url
                }
                // 需要登录，不重试
                if (isRequiresLogin(response)) break
            } catch (e: Exception) {
                android.util.Log.e("NeteaseApi", "getSongUrl: quality=$quality 请求异常", e)
            }
        }
        android.util.Log.e("NeteaseApi", "getSongUrl: 所有音质均不可用")
        null
    }

    data class DownloadUrlInfo(
        val url: String,
        val type: String
    )

    suspend fun getSongDownloadUrl(songId: String, level: String = "lossless"): DownloadUrlInfo? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("NeteaseApi", "getSongDownloadUrl: songId=$songId, level=$level")
            val response = client.getSongDownloadUrl(songId.toLong(), level)
            android.util.Log.d("NeteaseApi", "getSongDownloadUrl: 响应长度=${response.length}")
            parseDownloadUrl(response)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getSongDownloadUrl: 请求异常", e)
            e.printStackTrace()
            null
        }
    }

    private fun buildQualityCandidates(preferredQuality: String): List<String> {
        val normalized = preferredQuality.trim().lowercase().ifBlank { "exhigh" }
        val idx = QUALITY_FALLBACK_ORDER.indexOf(normalized)
        return if (idx >= 0) {
            QUALITY_FALLBACK_ORDER.drop(idx)
        } else {
            listOf(normalized, "exhigh", "standard").distinct()
        }
    }

    /** 从播放响应中提取 URL，处理 JSONObject.NULL 等情况 */
    private fun parsePlaybackUrl(rawResponse: String): String? {
        return try {
            val root = JSONObject(rawResponse)
            when (root.optInt("code", -1)) {
                200 -> {
                    val data = extractDataObject(root) ?: return null
                    val url = optCleanString(data, "url") ?: return null
                    url
                }
                else -> null
            }
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "parsePlaybackUrl: 解析异常", e)
            null
        }
    }

    private fun isRequiresLogin(rawResponse: String): Boolean {
        return try {
            JSONObject(rawResponse).optInt("code", -1) == 301
        } catch (_: Exception) {
            false
        }
    }

    private fun extractDataObject(root: JSONObject): JSONObject? {
        return when (val data = root.opt("data")) {
            is JSONObject -> data
            is JSONArray -> data.optJSONObject(0)
            else -> null
        }
    }

    private fun optCleanString(obj: JSONObject, key: String): String? {
        return when (val raw = obj.opt(key)) {
            null, JSONObject.NULL -> null
            is String -> raw.trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            else -> raw.toString().trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
        }
    }

    private fun parseDownloadUrl(response: String): DownloadUrlInfo? {
        try {
            val root = JSONObject(response)
            if (root.optInt("code", -1) != 200) return null
            val data = extractDataObject(root) ?: return null
            val url = optCleanString(data, "url") ?: return null
            val type = optCleanString(data, "type") ?: "mp3"
            return DownloadUrlInfo(url = url, type = type)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "parseDownloadUrl: 解析异常", e)
            return null
        }
    }

    suspend fun getLyric(songId: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = client.getLyricNew(songId.toLong())
            parseLyric(response)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** 返回原始 JSON 响应，供调用方自行提取 yrc/lrc/ytlrc */
    suspend fun getLyricRaw(songId: String): String = withContext(Dispatchers.IO) {
        client.getLyricNew(songId.toLong())
    }

    private val legacyLrcTimestampRegex = Regex("""\[(\d{1,2}):(\d{2}):(\d{2,3})]""")

    private fun normalizeLegacyLrcTimestamps(content: String): String {
        if (content.isEmpty()) return content
        return legacyLrcTimestampRegex.replace(content) { match ->
            val minutes = match.groupValues[1].padStart(2, '0')
            val seconds = match.groupValues[2]
            val fraction = match.groupValues[3]
            "[$minutes:$seconds.$fraction]"
        }
    }

    private fun parseLyric(response: String): String? {
        try {
            val root = JSONObject(response)
            val yrc = root.optJSONObject("yrc")?.optString("lyric")
            if (!yrc.isNullOrBlank()) return yrc
            val lrc = root.optJSONObject("lrc")?.optString("lyric")
            return if (!lrc.isNullOrBlank()) normalizeLegacyLrcTimestamps(lrc) else null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    suspend fun getPlaylistDetail(playlistId: String): List<NeteaseSongSearchResult> = withContext(Dispatchers.IO) {
        try {
            val response = client.getPlaylistDetail(playlistId.toLong())
            val root = JSONObject(response)
            val code = root.optInt("code", -1)
            if (code != 200) return@withContext emptyList()

            val playlist = root.optJSONObject("playlist") ?: return@withContext emptyList()

            // 1. 优先从 playlist.tracks 解析（已含封面、歌手等完整信息）
            val trackMap = mutableMapOf<Long, NeteaseSongSearchResult>()
            val tracksArr = playlist.optJSONArray("tracks")
            if (tracksArr != null) {
                for (i in 0 until tracksArr.length()) {
                    val t = tracksArr.optJSONObject(i) ?: continue
                    parseSongFromJson(t)?.let { trackMap[it.id.toLong()] = it }
                }
            }

            // 2. 获取完整 trackIds 列表（保持歌单曲序）
            val trackIds = mutableListOf<Long>()
            val trackIdsArr = playlist.optJSONArray("trackIds")
            if (trackIdsArr != null) {
                for (i in 0 until trackIdsArr.length()) {
                    val id = trackIdsArr.optJSONObject(i)?.optLong("id", 0L) ?: 0L
                    if (id != 0L) trackIds.add(id)
                }
            }
            if (trackIds.isEmpty()) return@withContext emptyList()

            // 3. 补缺：tracks 中缺失的 ID，分批通过 getSongDetail 获取（每批 300）
            val missingIds = trackIds.filterNot { trackMap.containsKey(it) }
            if (missingIds.isNotEmpty()) {
                missingIds.chunked(300).forEach { batch ->
                    try {
                        val detailResp = client.getSongDetail(batch)
                        parseSongDetailArray(detailResp)?.forEach { song ->
                            trackMap[song.id.toLong()] = song
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 4. 按 trackIds 原始顺序输出
            trackIds.mapNotNull { trackMap[it] }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** 从单个歌曲 JSON 解析 NeteaseSongSearchResult */
    private fun parseSongFromJson(t: JSONObject): NeteaseSongSearchResult? {
        val id = t.optString("id", "")
        val name = t.optString("name", "")
        if (id.isBlank() || name.isBlank()) return null

        val artists = parseArtists(t.optJSONArray("ar"))
        val artist = artists.joinToString("/")
        val artistId = artists.firstOrNull() ?: ""
        val album = t.optJSONObject("al") ?: t.optJSONObject("album")
        val albumName = album?.optString("name", "") ?: ""
        val albumId = album?.optString("id", "") ?: ""
        val picUrl = album?.optString("picUrl", "") ?: ""
        val durationMs = t.optLong("dt", 0)

        return NeteaseSongSearchResult(
            id = id,
            name = name,
            artist = artist,
            artistId = artistId,
            album = albumName,
            albumId = albumId,
            duration = durationMs,
            picUrl = picUrl
        )
    }

    /** 从 getSongDetail 响应解析歌曲列表 */
    private fun parseSongDetailArray(raw: String): List<NeteaseSongSearchResult>? {
        return try {
            val root = JSONObject(raw)
            val code = root.optInt("code", -1)
            if (code != 200) return null
            val songs = root.optJSONArray("songs") ?: return null
            val out = mutableListOf<NeteaseSongSearchResult>()
            for (i in 0 until songs.length()) {
                val t = songs.optJSONObject(i) ?: continue
                parseSongFromJson(t)?.let { out.add(it) }
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getRecommendedPlaylists(limit: Int = 30): List<NeteasePlaylistResult> = withContext(Dispatchers.IO) {
        val playlists = mutableListOf<NeteasePlaylistResult>()
        try {
            val response = client.getRecommendedPlaylists(limit)
            val root = JSONObject(response)
            val result = root.optJSONArray("result") ?: return@withContext playlists

            for (i in 0 until result.length()) {
                val pl = result.optJSONObject(i) ?: continue
                playlists.add(NeteasePlaylistResult(
                    id = pl.optString("id", ""),
                    name = pl.optString("name", ""),
                    picUrl = pl.optString("picUrl", ""),
                    trackCount = pl.optInt("trackCount", 0),
                    creatorNickname = pl.optJSONObject("creator")?.optString("nickname", null)
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext playlists
    }

    suspend fun getCurrentUserId(): Long = withContext(Dispatchers.IO) {
        client.getCurrentUserId()
    }

    suspend fun getUserPlaylists(userId: Long): List<NeteasePlaylistResult> = withContext(Dispatchers.IO) {
        val playlists = mutableListOf<NeteasePlaylistResult>()
        try {
            val response = client.getUserPlaylists(userId)
            val root = JSONObject(response)
            val list = root.optJSONArray("playlist") ?: return@withContext playlists

            for (i in 0 until list.length()) {
                val pl = list.optJSONObject(i) ?: continue
                playlists.add(NeteasePlaylistResult(
                    id = pl.optString("id", ""),
                    name = pl.optString("name", ""),
                    picUrl = pl.optString("coverImgUrl", ""),
                    trackCount = pl.optInt("trackCount", 0),
                    creatorNickname = pl.optJSONObject("creator")?.optString("nickname", null)
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext playlists
    }
}