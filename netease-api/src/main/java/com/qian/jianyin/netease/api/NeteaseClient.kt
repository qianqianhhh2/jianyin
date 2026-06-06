package com.qian.jianyin.netease.api

import com.qian.jianyin.netease.CryptoMode
import com.qian.jianyin.netease.JsonUtil.jsonQuote
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.brotli.dec.BrotliInputStream
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.io.ByteArrayOutputStream

fun InputStream.readBytesCompat(bufferSize: Int = 8 * 1024): ByteArray {
    ByteArrayOutputStream().use { out ->
        val buf = ByteArray(bufferSize)
        while (true) {
            val n = this.read(buf)
            if (n == -1) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}

class NeteaseClient {
    private val okHttpClient: OkHttpClient
    private val cookieStore: MutableMap<String, MutableList<Cookie>> = mutableMapOf()
    private val cookieLock = Any()

    @Volatile
    private var persistedCookies: Map<String, String> = emptyMap()

    init {
        okHttpClient = OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val host = url.host
                    synchronized(cookieLock) {
                        val list = cookieStore.getOrPut(host) { mutableListOf() }
                        list.removeAll { c -> cookies.any { it.name == c.name } }
                        list.addAll(cookies)
                    }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return synchronized(cookieLock) {
                        cookieStore[url.host]?.toList() ?: emptyList()
                    }
                }
            })
            .build()
    }

    fun evictConnections() {
        okHttpClient.connectionPool.evictAll()
    }

    fun hasLogin(): Boolean = !persistedCookies["MUSIC_U"].isNullOrBlank()

    fun setPersistedCookies(cookies: Map<String, String>) {
        val m = cookies.toMutableMap()
        m.putIfAbsent("os", "pc")
        m.putIfAbsent("appver", "8.10.35")
        persistedCookies = m.toMap()

        seedCookieJarFromPersisted("music.163.com")
        seedCookieJarFromPersisted("interface.music.163.com")
    }

    private fun seedCookieJarFromPersisted(host: String) {
        val snapshot = persistedCookies
        synchronized(cookieLock) {
            val list = cookieStore.getOrPut(host) { mutableListOf() }
            snapshot.forEach { (name, value) ->
                val c = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(host)
                    .path("/")
                    .build()
                list.removeAll { it.name == name }
                list.add(c)
            }
        }
    }

    fun getCookies(): Map<String, String> {
        return synchronized(cookieLock) {
            val result = LinkedHashMap<String, String>()
            cookieStore.values.forEach { list -> list.forEach { cookie -> result[cookie.name] = cookie.value } }
            result
        }
    }

    fun logout() {
        synchronized(cookieLock) {
            cookieStore.clear()
        }
        persistedCookies = emptyMap()
    }

    private fun getCsrfCookie(): String? = synchronized(cookieLock) {
        cookieStore.values
            .asSequence()
            .flatMap { it.asSequence() }
            .firstOrNull { it.name == "__csrf" }
            ?.value
    }

    private fun buildPersistedCookieHeader(): String? {
        val map = persistedCookies.toMutableMap()
        map.putIfAbsent("os", "pc")
        map.putIfAbsent("appver", "8.10.35")
        if (map.isEmpty()) return null
        return map.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    @Throws(IOException::class)
    fun ensureWeapiSession() {
        request(
            url = "https://music.163.com/",
            params = emptyMap(),
            mode = CryptoMode.API,
            method = "GET",
            usePersistedCookies = true
        )
    }

    @Throws(IOException::class)
    fun request(
        url: String,
        params: Map<String, Any>,
        mode: CryptoMode = CryptoMode.WEAPI,
        method: String = "POST",
        usePersistedCookies: Boolean = true
    ): String {
        val requestUrl = url.toHttpUrl()

        val bodyParams: Map<String, String> = when (mode) {
            CryptoMode.WEAPI -> com.qian.jianyin.netease.NeteaseCrypto.weApiEncrypt(params)
            CryptoMode.EAPI  -> com.qian.jianyin.netease.NeteaseCrypto.eApiEncrypt(requestUrl.encodedPath, params)
            CryptoMode.LINUX -> com.qian.jianyin.netease.NeteaseCrypto.linuxApiEncrypt(params)
            CryptoMode.API   -> params.mapValues { it.value.toString() }
        }

        var reqUrl = requestUrl
        val builder = Request.Builder()
            .header("Accept", "*/*")
            .header("Accept-Language", "zh-CN,zh-Hans;q=0.9")
            .header("Connection", "keep-alive")
            .header("Referer", "https://music.163.com")
            .header("Host", requestUrl.host)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; NeriPlayer) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")

        if (usePersistedCookies) {
            buildPersistedCookieHeader()?.let { builder.header("Cookie", it) }
        }

        if (mode == CryptoMode.WEAPI) {
            val csrf = if (usePersistedCookies) {
                persistedCookies["__csrf"] ?: getCsrfCookie() ?: ""
            } else {
                getCsrfCookie() ?: ""
            }
            reqUrl = requestUrl.newBuilder()
                .setQueryParameter("csrf_token", csrf)
                .build()
        }

        builder.url(reqUrl)

        when (method.uppercase(Locale.getDefault())) {
            "POST" -> {
                val formBodyBuilder = FormBody.Builder(StandardCharsets.UTF_8)
                bodyParams.forEach { (k, v) -> formBodyBuilder.add(k, v) }
                builder.post(formBodyBuilder.build())
            }
            "GET" -> {
                val urlBuilder = reqUrl.newBuilder()
                bodyParams.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
                builder.url(urlBuilder.build())
            }
            else -> throw IllegalArgumentException("不支持的请求方法: $method")
        }

        okHttpClient.newCall(builder.build()).execute().use { resp ->
            val responseBody = resp.body ?: throw IOException("Empty response body")
            val encoding = resp.header("Content-Encoding")?.lowercase(Locale.getDefault())
            val bytes = when (encoding) {
                "br"   -> BrotliInputStream(responseBody.byteStream()).use { it.readBytesCompat() }
                "gzip" -> GZIPInputStream(responseBody.byteStream()).use { it.readBytesCompat() }
                else   -> responseBody.bytes()
            }
            if (!resp.isSuccessful) {
                val msg = String(bytes, StandardCharsets.UTF_8)
                throw IOException("HTTP ${resp.code}: $msg")
            }
            return String(bytes, StandardCharsets.UTF_8)
        }
    }

    @Throws(IOException::class)
    fun callWeApi(path: String, params: Map<String, Any>, usePersistedCookies: Boolean = true): String {
        val p = if (path.startsWith("/")) path else "/$path"
        val url = "https://music.163.com/weapi$p"
        return request(url, params, CryptoMode.WEAPI, "POST", usePersistedCookies)
    }

    @Throws(IOException::class)
    fun callEApi(path: String, params: Map<String, Any>, usePersistedCookies: Boolean = true): String {
        val p = if (path.startsWith("/")) path else "/$path"
        val url = "https://interface.music.163.com/eapi$p"
        return request(url, params, CryptoMode.EAPI, "POST", usePersistedCookies)
    }

    @Throws(IOException::class)
    fun callLinuxApi(path: String, params: Map<String, Any>, usePersistedCookies: Boolean = true): String {
        val p = if (path.startsWith("/")) path else "/$path"
        val url = "https://music.163.com/api$p"
        return request(url, params, CryptoMode.LINUX, "POST", usePersistedCookies)
    }

    @Throws(IOException::class)
    fun loginByPhone(phone: String, password: String, countryCode: Int = 86, remember: Boolean = true): String {
        val params = mutableMapOf<String, Any>(
            "phone" to phone,
            "countrycode" to countryCode,
            "remember" to remember.toString(),
            "password" to com.qian.jianyin.netease.NeteaseCrypto.md5Hex(password),
            "type" to "1"
        )
        return callEApi("/w/login/cellphone", params, usePersistedCookies = false)
    }

    @Throws(IOException::class)
    fun searchSongs(
        keyword: String,
        limit: Int = 30,
        offset: Int = 0,
        type: Int = 1,
        usePersistedCookies: Boolean = true
    ): String {
        val url = "https://music.163.com/weapi/cloudsearch/get/web"
        val params = mutableMapOf<String, Any>(
            "s" to keyword,
            "type" to type.toString(),
            "limit" to limit.toString(),
            "offset" to offset.toString(),
            "total" to "true"
        )
        return request(url, params, CryptoMode.WEAPI, "POST", usePersistedCookies)
    }

    @Throws(IOException::class)
    fun getSongUrl(songId: Long, bitrate: Int = 320000): String {
        fun call(usePersistedCookies: Boolean): String {
            val url = "https://music.163.com/weapi/song/enhance/player/url"
            val params = mutableMapOf<String, Any>(
                "ids" to "[$songId]",
                "br" to bitrate.toString()
            )
            return request(url, params, CryptoMode.WEAPI, "POST", usePersistedCookies = usePersistedCookies)
        }

        // 总是尝试使用 cookies（与 NeriPlayer 一致）
        var resp = call(usePersistedCookies = true)
        return try {
            val code = JSONObject(resp).optInt("code", -1)
            if (code == 301 && hasLogin()) {
                try { ensureWeapiSession() } catch (_: Exception) {}
                resp = call(usePersistedCookies = true)
            }
            resp
        } catch (_: Exception) {
            resp
        }
    }

    fun getSongDownloadUrl(songId: Long, level: String = "lossless"): String {
        fun call(usePersistedCookies: Boolean): String {
            val params = mutableMapOf<String, Any>(
                "ids" to "[$songId]",
                "level" to level,
                "encodeType" to "flac",
            )
            return callEApi(
                "/song/enhance/player/url/v1",
                params,
                usePersistedCookies = usePersistedCookies
            )
        }

        val preferPersistedCookies = hasLogin()
        var resp = call(usePersistedCookies = preferPersistedCookies)
        return try {
            val code = JSONObject(resp).optInt("code", -1)
            if (code == 301 && hasLogin()) {
                try { ensureWeapiSession() } catch (_: Exception) {}
                resp = call(usePersistedCookies = true)
            }
            resp
        } catch (_: Exception) {
            resp
        }
    }

    @Throws(IOException::class)
    fun getSongDetail(ids: List<Long>): String {
        require(ids.isNotEmpty()) { "ids must not be empty" }
        val url = "https://music.163.com/weapi/v3/song/detail"
        val idsParam = ids.joinToString(",")
        val detailParam = ids.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]"
        ) { id -> """{"id":$id}""" }
        val params = mapOf(
            "c" to detailParam,
            "ids" to "[$idsParam]"
        )
        return request(url, params, CryptoMode.WEAPI, "POST", usePersistedCookies = true)
    }

    @Throws(IOException::class)
    fun getUserPlaylists(userId: Long, offset: Int = 0, limit: Int = 30): String {
        val url = "https://music.163.com/weapi/user/playlist"
        val params = mutableMapOf<String, Any>(
            "uid" to userId.toString(),
            "offset" to offset.toString(),
            "limit" to limit.toString(),
            "includeVideo" to "true"
        )
        return request(url, params, CryptoMode.WEAPI, "POST", usePersistedCookies = true)
    }

    @Throws(IOException::class)
    fun getPlaylistDetail(playlistId: Long, n: Int = 100000, s: Int = 8): String {
        val url = "https://music.163.com/api/v6/playlist/detail"
        val params = mutableMapOf<String, Any>(
            "id" to playlistId.toString(),
            "n" to n.toString(),
            "s" to s.toString()
        )
        return request(url, params, CryptoMode.API, "POST", usePersistedCookies = true)
    }

    @Throws(IOException::class)
    fun getLyricNew(songId: Long): String {
        val params = mutableMapOf<String, Any>(
            "id" to songId.toString(),
            "cp" to "false",
            "lv" to 0,
            "tv" to 0,
            "rv" to 0,
            "yv" to 0,
            "ytv" to 0,
            "yrv" to 0,
        )

        fun call(): String = this.callEApi("/song/lyric/v1", params, usePersistedCookies = true)

        var resp = call()
        try {
            val code = JSONObject(resp).optInt("code", 200)
            if (code == 301 && this.hasLogin()) {
                try { this.ensureWeapiSession() } catch (_: Exception) {}
                resp = call()
            }
        } catch (_: Exception) { }
        return resp
    }

    @Throws(IOException::class)
    fun getRecommendedPlaylists(limit: Int = 30, usePersistedCookies: Boolean = true): String {
        val url = "https://music.163.com/weapi/personalized/playlist"
        val params = mapOf("limit" to limit.toString())
        return request(url, params, CryptoMode.WEAPI, "POST", usePersistedCookies = usePersistedCookies)
    }

    @Throws(IOException::class)
    fun getCurrentUserId(): Long {
        val raw = getCurrentUserAccount()
        val root = JSONObject(raw)
        if (root.optInt("code", -1) != 200) {
            throw IllegalStateException("获取用户信息失败: $raw")
        }
        val profile = root.optJSONObject("profile")
        return profile?.optLong("userId")
            ?: throw IllegalStateException("未找到 userId: $raw")
    }

    @Throws(IOException::class)
    fun getCurrentUserAccount(): String {
        return callWeApi("/w/nuser/account/get", emptyMap(), usePersistedCookies = true)
    }

    @Throws(IOException::class)
    fun getLikedPlaylistId(userId: Long): String {
        val uid = if (userId == 0L) getCurrentUserId() else userId
        val raw = getUserPlaylists(uid, 0, 1000)
        return try {
            val root = JSONObject(raw)
            val list = root.optJSONArray("playlist") ?: org.json.JSONArray()
            var likedId: Long? = null
            for (i in 0 until list.length()) {
                val pl = list.optJSONObject(i) ?: continue
                val specialType = pl.optInt("specialType", 0)
                val name = pl.optString("name", "")
                val creatorId = pl.optJSONObject("creator")?.optLong("userId") ?: -1L
                if (creatorId == uid && (specialType == 5 || name.contains("我喜欢的音乐"))) {
                    likedId = pl.optLong("id")
                    break
                }
            }
            if (likedId != null) {
                """{ "code": 200, "playlistId": $likedId }"""
            } else {
                """{ "code": 404, "msg": "liked playlist not found" }"""
            }
        } catch (e: Exception) {
            """{ "code": 500, "msg": ${jsonQuote(e.message ?: "parse error")} }"""
        }
    }

    @Throws(IOException::class)
    fun likeSong(songId: Long, like: Boolean = true, time: Long? = null): String {
        val params = mutableMapOf<String, Any>(
            "trackId" to songId.toString(),
            "like" to like.toString()
        )
        time?.let { params["time"] = it.toString() }
        return callWeApi("/song/like", params, usePersistedCookies = true)
    }
}