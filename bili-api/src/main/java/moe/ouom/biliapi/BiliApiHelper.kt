package moe.ouom.biliapi

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * BiliAPI 单文件封装，提供哔哩哔哩登录、歌单同步、音频流获取等功能
 * 
 * 使用方法：
 * 1. 初始化 API 实例
 *    val biliApi = BiliApi.getInstance(context)
 * 
 * 2. 保存登录 Cookie（从 WebView 登录后获取）
 *    val cookies = mapOf(
 *        "SESSDATA" to "your_sessdata",
 *        "DedeUserID" to "your_user_id",
 *        "bili_jct" to "your_bili_jct"
 *    )
 *    biliApi.saveCookies(cookies)
 * 
 * 3. 验证登录状态
 *    val isValid = runBlocking { biliApi.validateLoginSession() }
 * 
 * 4. 获取用户歌单
 *    val folders = runBlocking { biliApi.getUserFavFolders() }
 * 
 * 5. 获取歌单内容
 *    val items = runBlocking { biliApi.getFavFolderItems(folderId) }
 * 
 * 6. 获取音频流
 *    val streams = runBlocking { biliApi.getAudioStreams(bvid, cid) }
 *    val bestStream = runBlocking { biliApi.getBestAudioStream(bvid, cid) }
 * 
 * 7. 搜索视频
 *    val results = runBlocking { biliApi.searchVideos("关键词") }
 */
class BiliApi private constructor(
    private val context: Context,
    private val cookieRepo: BiliCookieRepository,
    private val client: BiliClient
) {

    companion object {
        @Volatile
        private var instance: BiliApi? = null

        fun getInstance(context: Context): BiliApi {
            return instance ?: synchronized(this) {
                instance ?: buildInstance(context).also { instance = it }
            }
        }

        private fun buildInstance(context: Context): BiliApi {
            val cookieRepo = BiliCookieRepository(context)
            val client = BiliClient(cookieRepo)
            return BiliApi(context.applicationContext, cookieRepo, client)
        }
    }

    val cookieFlow: StateFlow<Map<String, String>>
        get() = cookieRepo.cookieFlow

    val authHealthFlow: StateFlow<SavedCookieAuthHealth>
        get() = cookieRepo.authHealthFlow

    fun getCookies(): Map<String, String> = cookieRepo.getCookiesOnce()

    fun getAuthHealth(): SavedCookieAuthHealth = cookieRepo.getAuthHealthOnce()

    fun saveCookies(cookies: Map<String, String>) {
        cookieRepo.saveCookies(cookies)
    }

    fun saveCookiesFromJson(json: String): Boolean {
        android.util.Log.d("BiliLogin", "saveCookiesFromJson: 收到JSON: $json")
        return runCatching {
            android.util.Log.d("BiliLogin", "saveCookiesFromJson: 解析JSON")
            val bundle = BiliAuthBundle.fromJson(json)
            android.util.Log.d("BiliLogin", "saveCookiesFromJson: 解析结果 - cookies: ${bundle.cookies}, hasLoginCookies: ${bundle.hasLoginCookies()}")
            if (bundle.hasLoginCookies()) {
                android.util.Log.d("BiliLogin", "saveCookiesFromJson: 保存cookies")
                cookieRepo.saveCookies(bundle.cookies, bundle.savedAt)
                android.util.Log.d("BiliLogin", "saveCookiesFromJson: 保存成功")
                true
            } else {
                android.util.Log.d("BiliLogin", "saveCookiesFromJson: 没有登录凭证")
                false
            }
        }.onFailure { e ->
            android.util.Log.e("BiliLogin", "saveCookiesFromJson: 解析JSON失败", e)
        }.getOrDefault(false)
    }

    fun clearCookies() {
        cookieRepo.clear()
    }

    suspend fun validateLoginSession(): Boolean? = client.validateLoginSession()

    suspend fun getUserFavFolders(): List<BiliClient.FavFolder> {
        val mid = getMidFromCookies() ?: throw IllegalStateException("Not logged in: missing DedeUserID")
        return client.getUserCreatedFavFolders(mid)
    }

    suspend fun getFavFolderInfo(mediaId: Long): BiliClient.FavFolder = client.getFavFolderInfo(mediaId)

    suspend fun getFavFolderItems(mediaId: Long): List<BiliClient.FavResourceItem> = client.getAllFavFolderItems(mediaId)

    suspend fun getVideoInfo(bvid: String): BiliClient.VideoBasicInfo = client.getVideoBasicInfoByBvid(bvid)

    suspend fun getVideoInfo(avid: Long): BiliClient.VideoBasicInfo = client.getVideoBasicInfoByAvid(avid)

    suspend fun searchVideos(keyword: String, page: Int = 1): BiliClient.SearchVideoPage = client.searchVideos(keyword, page)

    suspend fun getAudioStreams(bvid: String, cid: Long): List<BiliAudioStreamInfo> = client.getAllAudioStreams(bvid, cid)

    suspend fun getBestAudioStream(
        bvid: String,
        cid: Long,
        qualityKey: String = BiliQuality.HIGH.key
    ): BiliAudioStreamInfo? {
        // Get cid from video info if not provided
        val actualCid = if (cid == 0L) {
            val videoInfo = client.getVideoBasicInfoByBvid(bvid)
            videoInfo.cid
        } else {
            cid
        }
        
        if (actualCid == 0L) {
            throw IOException("Invalid cid for bvid: $bvid")
        }
        
        val streams = client.getAllAudioStreams(bvid, actualCid)
        return selectStreamByPreference(streams, qualityKey)
    }

    private fun getMidFromCookies(): Long? = getCookies()["DedeUserID"]?.toLongOrNull()
}

class BiliClient(
    private val cookieRepo: BiliCookieRepository,
    client: OkHttpClient? = null
) {

    companion object {
        private const val TAG = "BiliAPI-BiliClient"

        private const val BASE_PLAY_URL = "https://api.bilibili.com/x/player/wbi/playurl"
        private const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
        private const val FINGERPRINT_URL = "https://api.bilibili.com/x/frontend/finger/spi"
        private const val WEB_TICKET_URL = "https://api.bilibili.com/bapis/biliapi.api.ticket.v1.Ticket/GenWebTicket"
        private const val VIEW_URL = "https://api.bilibili.com/x/web-interface/view"
        private const val SEARCH_TYPE_URL = "https://api.bilibili.com/x/web-interface/search/type"
        private const val HAS_LIKE_URL = "https://api.bilibili.com/x/web-interface/archive/has/like"
        private const val FAV_FOLDER_CREATED_LIST_ALL = "https://api.bilibili.com/x/v3/fav/folder/created/list-all"
        private const val FAV_FOLDER_INFO = "https://api.bilibili.com/x/v3/fav/folder/info"
        private const val FAV_RESOURCE_LIST = "https://api.bilibili.com/x/v3/fav/resource/list"

        private const val DEFAULT_WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val FINGERPRINT_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 13_2_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.3 Mobile/15E148 Safari/604.1 Edg/114.0.0.0"
        private const val WEB_TICKET_UA = "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/115.0"
        private const val REFERER = "https://www.bilibili.com"

        private val MIXIN_INDEX = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 62, 6, 63, 57, 20, 34, 52, 59, 11, 36, 44
        )

        private const val WBI_CACHE_MS = 10 * 60 * 1000L
        private const val ANON_COOKIE_CACHE_MS = 60 * 60 * 1000L
        private const val EMPTY_AUDIO_RETRY_COUNT = 3
        private const val EMPTY_AUDIO_RETRY_DELAY_MS = 250L
        private const val WEB_TICKET_KEY = "XgwSnGZ1p"

        const val FNVAL_DASH = 1 shl 4
        const val FNVAL_DOLBY = 1 shl 8
    }

    private val http: OkHttpClient = client ?: OkHttpClient.Builder()
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val anonCookieMutex = Mutex()

    @Volatile
    private var cachedWbiKey: Pair<String, String>? = null

    @Volatile
    private var wbiKeyCachedAt: Long = 0L

    @Volatile
    private var cachedAnonCookies: Map<String, String>? = null

    @Volatile
    private var anonCookiesCachedAt: Long = 0L

    data class PlayOptions(
        val qn: Int? = null,
        val fnval: Int = FNVAL_DASH or FNVAL_DOLBY,
        val fnver: Int = 0,
        val fourk: Int = 0,
        val platform: String = "pc",
        val highQuality: Int? = null,
        val tryLook: Int? = null,
        val isHdr: Int? = null,
        val deviceName: String? = null,
        val devicePlatform: String = "android",
        val mobiApp: String = "android"
    )

    data class VideoBasicInfo(
        val bvid: String,
        val aid: Long,
        val cid: Long,
        val title: String,
        val pic: String,
        val owner: String,
        val stat: Stat
    ) {
        data class Stat(
            val view: Int,
            val like: Int,
            val coin: Int,
            val favorite: Int
        )
    }

    data class PlayInfo(
        val videoInfo: VideoBasicInfo,
        val audioStreams: List<BiliAudioStreamInfo>
    )

    data class FavFolder(
        val id: Long,
        val fid: Long,
        val mid: Long,
        val name: String,
        val mediaCount: Int,
        val cover: String,
        val isPublic: Int
    )

    data class FavResourceItem(
        val id: Long,
        val bvid: String,
        val aid: Long,
        val cid: Long,
        val title: String,
        val pic: String,
        val owner: String,
        val duration: Int
    )

    data class SearchVideoPage(
        val page: Int,
        val pageSize: Int,
        val total: Int,
        val items: List<SearchVideoItem>
    ) {
        data class SearchVideoItem(
            val bvid: String,
            val aid: Long,
            val cid: Long,
            val title: String,
            val pic: String,
            val owner: String,
            val duration: Int,
            val view: Int,
            val danmaku: Int,
            val reply: Int,
            val favorite: Int,
            val coin: Int,
            val share: Int,
            val like: Int
        )
    }

    suspend fun getVideoBasicInfoByBvid(bvid: String): VideoBasicInfo {
        val url = VIEW_URL.toHttpUrl().newBuilder()
            .addQueryParameter("bvid", bvid)
            .build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_WEB_UA)
            .header("Referer", REFERER)
            .build()

        val resp = http.newCall(req).execute()
        val responseBody = resp.body?.string() ?: ""
        if (!resp.isSuccessful) {
            throw IOException("Failed to get video info: ${resp.code}, response: $responseBody")
        }

        val json = JSONObject(responseBody)
        val code = json.optInt("code", 0)
        val message = json.optString("message", "")
        if (code != 0) {
            throw IOException("API error: code=$code, message=$message, response: $responseBody")
        }
        
        val data = json.optJSONObject("data") ?: throw IOException("Invalid video info response: missing data field, response: $responseBody")

        val stat = data.optJSONObject("stat") ?: JSONObject()
        val owner = data.optJSONObject("owner") ?: JSONObject()
        
        // Get cid from pages array
        var cid = data.optLong("cid", 0)
        if (cid == 0L) {
            val pages = data.optJSONArray("pages")
            if (pages != null && pages.length() > 0) {
                val firstPage = pages.optJSONObject(0)
                cid = firstPage?.optLong("cid", 0) ?: 0
            }
        }

        return VideoBasicInfo(
            bvid = data.optString("bvid", bvid),
            aid = data.optLong("aid", 0),
            cid = cid,
            title = data.optString("title", ""),
            pic = data.optString("pic", ""),
            owner = owner.optString("name", ""),
            stat = VideoBasicInfo.Stat(
                view = stat.optInt("view", 0),
                like = stat.optInt("like", 0),
                coin = stat.optInt("coin", 0),
                favorite = stat.optInt("favorite", 0)
            )
        )
    }

    suspend fun getVideoBasicInfoByAvid(avid: Long): VideoBasicInfo {
        val url = VIEW_URL.toHttpUrl().newBuilder()
            .addQueryParameter("aid", avid.toString())
            .build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_WEB_UA)
            .header("Referer", REFERER)
            .build()

        val resp = http.newCall(req).execute()
        val responseBody = resp.body?.string() ?: ""
        if (!resp.isSuccessful) {
            throw IOException("Failed to get video info: ${resp.code}, body: $responseBody")
        }

        val json = JSONObject(responseBody)
        val data = json.optJSONObject("data") ?: throw IOException("Invalid video info response: $responseBody")

        val stat = data.optJSONObject("stat") ?: JSONObject()
        val owner = data.optJSONObject("owner") ?: JSONObject()

        return VideoBasicInfo(
            bvid = data.optString("bvid", ""),
            aid = data.optLong("aid", avid),
            cid = data.optLong("cid", 0),
            title = data.optString("title", ""),
            pic = data.optString("pic", ""),
            owner = owner.optString("name", ""),
            stat = VideoBasicInfo.Stat(
                view = stat.optInt("view", 0),
                like = stat.optInt("like", 0),
                coin = stat.optInt("coin", 0),
                favorite = stat.optInt("favorite", 0)
            )
        )
    }

    suspend fun getAllAudioStreams(bvid: String, cid: Long): List<BiliAudioStreamInfo> {
        val playInfo = getPlayInfoByBvid(bvid, cid)
        return playInfo.audioStreams
    }

    suspend fun getPlayInfoByBvid(bvid: String, cid: Long, options: PlayOptions = PlayOptions()): PlayInfo {
        val params = mutableMapOf(
            "bvid" to bvid,
            "cid" to cid.toString(),
            "fnval" to options.fnval.toString(),
            "fnver" to options.fnver.toString(),
            "fourk" to options.fourk.toString(),
            "platform" to options.platform
        )

        val urlBuilder = BASE_PLAY_URL.toHttpUrl().newBuilder()
        val url = buildWbiUrl(urlBuilder, params)

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_WEB_UA)
            .header("Referer", REFERER)
            .build()

        val resp = http.newCall(req).execute()
        val responseBody = resp.body?.string() ?: ""
        if (!resp.isSuccessful) {
            throw IOException("Failed to get play info: ${resp.code}, url: ${url}, response: $responseBody")
        }

        val json = JSONObject(responseBody)
        val code = json.optInt("code", 0)
        val message = json.optString("message", "")
        if (code != 0) {
            throw IOException("API error: code=$code, message=$message, url: ${url}, response: $responseBody")
        }
        
        val data = json.optJSONObject("data") ?: throw IOException("Invalid play info response: missing data field, url: ${url}, response: $responseBody")

        val audioStreams = mutableListOf<BiliAudioStreamInfo>()
        val dash = data.optJSONObject("dash")
        dash?.let {
            val audioJson = it.optJSONArray("audio")
            audioJson?.let {arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i)
                    item?.let {obj ->
                        val baseUrl = obj.optString("baseUrl", "")
                        val backupUrls = mutableListOf<String>()
                        val backupJson = obj.optJSONArray("backupUrl")
                        backupJson?.let {backupArr ->
                            for (j in 0 until backupArr.length()) {
                                backupArr.optString(j)?.takeIf { it.isNotBlank() }?.let(backupUrls::add)
                            }
                        }
                        val codecs = obj.optString("codecs", "")
                        val id = obj.optInt("id", 0)
                        val bitrate = obj.optInt("bitrate", 0)

                        audioStreams.add(BiliAudioStreamInfo(
                            url = baseUrl,
                            backupUrls = backupUrls,
                            codec = codecs,
                            qualityId = id,
                            bitrateKbps = bitrate / 1000
                        ))
                    }
                }
            }
        }

        return PlayInfo(
            videoInfo = getVideoBasicInfoByBvid(bvid),
            audioStreams = audioStreams
        )
    }

    suspend fun searchVideos(keyword: String, page: Int = 1): SearchVideoPage {
        val url = SEARCH_TYPE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("keyword", keyword)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "20")
            .addQueryParameter("search_type", "video")
            .build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_WEB_UA)
            .header("Referer", REFERER)
            .build()

        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw IOException("Failed to search videos: ${resp.code}")
        }

        val json = JSONObject(resp.body?.string() ?: "{}")
        val data = json.optJSONObject("data") ?: throw IOException("Invalid search response")
        val result = data.optJSONObject("result") ?: JSONObject()
        val list = result.optJSONArray("list") ?: JSONArray()

        val items = mutableListOf<SearchVideoPage.SearchVideoItem>()
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i)
            item?.let {obj ->
                items.add(SearchVideoPage.SearchVideoItem(
                    bvid = obj.optString("bvid", ""),
                    aid = obj.optLong("aid", 0),
                    cid = obj.optLong("cid", 0),
                    title = obj.optString("title", ""),
                    pic = obj.optString("pic", ""),
                    owner = obj.optJSONObject("owner")?.optString("name", "") ?: "",
                    duration = obj.optInt("duration", 0),
                    view = obj.optInt("view", 0),
                    danmaku = obj.optInt("danmaku", 0),
                    reply = obj.optInt("reply", 0),
                    favorite = obj.optInt("favorite", 0),
                    coin = obj.optInt("coin", 0),
                    share = obj.optInt("share", 0),
                    like = obj.optInt("like", 0)
                ))
            }
        }

        return SearchVideoPage(
            page = page,
            pageSize = 20,
            total = result.optInt("numResults", 0),
            items = items
        )
    }

    suspend fun getUserCreatedFavFolders(mid: Long): List<FavFolder> {
        val url = FAV_FOLDER_CREATED_LIST_ALL.toHttpUrl().newBuilder()
            .addQueryParameter("up_mid", mid.toString())
            .build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_WEB_UA)
            .header("Referer", REFERER)
            .apply {
                val cookies = cookieRepo.getCookiesOnce()
                if (cookies.isNotEmpty()) {
                    val cookieStr = cookies.entries.joinToString(";") { "${it.key}=${it.value}" }
                    header("Cookie", cookieStr)
                }
            }
            .build()

        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw IOException("Failed to get fav folders: ${resp.code}")
        }

        val json = JSONObject(resp.body?.string() ?: "{}")
        val data = json.optJSONObject("data") ?: throw IOException("Invalid fav folders response")
        val list = data.optJSONArray("list") ?: JSONArray()

        val folders = mutableListOf<FavFolder>()
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i)
            item?.let {obj ->
                folders.add(FavFolder(
                    id = obj.optLong("id", 0),
                    fid = obj.optLong("fid", 0),
                    mid = obj.optLong("mid", mid),
                    name = obj.optString("title", ""),
                    mediaCount = obj.optInt("media_count", 0),
                    cover = obj.optString("cover", ""),
                    isPublic = obj.optInt("attr", 0)
                ))
            }
        }

        return folders
    }

    suspend fun getFavFolderInfo(mediaId: Long): FavFolder {
        val url = FAV_FOLDER_INFO.toHttpUrl().newBuilder()
            .addQueryParameter("media_id", mediaId.toString())
            .build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_WEB_UA)
            .header("Referer", REFERER)
            .apply {
                val cookies = cookieRepo.getCookiesOnce()
                if (cookies.isNotEmpty()) {
                    val cookieStr = cookies.entries.joinToString(";") { "${it.key}=${it.value}" }
                    header("Cookie", cookieStr)
                }
            }
            .build()

        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw IOException("Failed to get fav folder info: ${resp.code}")
        }

        val json = JSONObject(resp.body?.string() ?: "{}")
        val data = json.optJSONObject("data") ?: throw IOException("Invalid fav folder info response")

        return FavFolder(
            id = data.optLong("id", 0),
            fid = data.optLong("fid", 0),
            mid = data.optLong("mid", 0),
            name = data.optString("title", ""),
            mediaCount = data.optInt("media_count", 0),
            cover = data.optString("cover", ""),
            isPublic = data.optInt("attr", 0)
        )
    }

    suspend fun getAllFavFolderItems(mediaId: Long): List<FavResourceItem> {
        val items = mutableListOf<FavResourceItem>()
        var page = 1
        val pageSize = 20

        while (true) {
            val pageItems = getFavFolderItems(mediaId, page, pageSize)
            items.addAll(pageItems)
            if (pageItems.size < pageSize) break
            page++
        }

        return items
    }

    suspend fun getFavFolderItems(mediaId: Long, page: Int = 1, pageSize: Int = 20): List<FavResourceItem> {
        val url = FAV_RESOURCE_LIST.toHttpUrl().newBuilder()
            .addQueryParameter("media_id", mediaId.toString())
            .addQueryParameter("pn", page.toString())
            .addQueryParameter("ps", pageSize.toString())
            .addQueryParameter("order", "mtime")
            .addQueryParameter("type", "0")
            .build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_WEB_UA)
            .header("Referer", REFERER)
            .apply {
                val cookies = cookieRepo.getCookiesOnce()
                if (cookies.isNotEmpty()) {
                    val cookieStr = cookies.entries.joinToString(";") { "${it.key}=${it.value}" }
                    header("Cookie", cookieStr)
                }
            }
            .build()

        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw IOException("Failed to get fav items: ${resp.code}")
        }

        val json = JSONObject(resp.body?.string() ?: "{}")
        val data = json.optJSONObject("data") ?: throw IOException("Invalid fav items response")
        val list = data.optJSONArray("medias") ?: JSONArray()

        val items = mutableListOf<FavResourceItem>()
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i)
            item?.let {obj ->
                // Get cid from different possible locations
                var cid = obj.optLong("cid", 0)
                if (cid == 0L) {
                    // Try to get cid from page field
                    val page = obj.optJSONObject("page")
                    if (page != null) {
                        cid = page.optLong("cid", 0)
                    }
                }
                if (cid == 0L) {
                    // Try to get cid from pages array
                    val pages = obj.optJSONArray("pages")
                    if (pages != null && pages.length() > 0) {
                        val firstPage = pages.optJSONObject(0)
                        cid = firstPage?.optLong("cid", 0) ?: 0
                    }
                }
                
                items.add(FavResourceItem(
                    id = obj.optLong("id", 0),
                    bvid = obj.optString("bvid", ""),
                    aid = obj.optLong("aid", 0),
                    cid = cid,
                    title = obj.optString("title", ""),
                    pic = obj.optString("cover", ""),
                    owner = obj.optJSONObject("upper")?.optString("name", "") ?: "",
                    duration = obj.optInt("duration", 0)
                ))
            }
        }

        return items
    }

    suspend fun validateLoginSession(): Boolean? {
        val cookies = cookieRepo.getCookiesOnce()
        if (cookies.isEmpty()) return false

        val url = NAV_URL.toHttpUrl().newBuilder().build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_WEB_UA)
            .header("Referer", REFERER)
            .header("Cookie", cookies.entries.joinToString(";") { "${it.key}=${it.value}" })
            .build()

        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) return false

        val json = JSONObject(resp.body?.string() ?: "{}")
        val data = json.optJSONObject("data") ?: return false
        val isLogin = data.optBoolean("isLogin", false)

        return isLogin
    }

    private suspend fun getWbiKeys(): Pair<String, String> {
        val now = System.currentTimeMillis()
        if (cachedWbiKey != null && (now - wbiKeyCachedAt) < WBI_CACHE_MS) {
            return cachedWbiKey!!
        }

        try {
            return fetchWbiKeysFromNav()
        } catch (e: Exception) {
            try {
                return fetchWbiKeysFromTicket()
            } catch (e2: Exception) {
                throw IOException("Failed to get WBI keys from both nav and ticket APIs: ${e.message}, ${e2.message}", e2)
            }
        }
    }

    private suspend fun fetchWbiKeysFromNav(): Pair<String, String> {
        val cookies = getEffectiveCookies()
        val cookieStr = if (cookies.isNotEmpty()) {
            cookies.entries.joinToString(";") { "${it.key}=${it.value}" }
        } else ""

        val req = Request.Builder()
            .url(NAV_URL)
            .header("User-Agent", DEFAULT_WEB_UA)
            .header("Referer", REFERER)
            .apply {
                if (cookieStr.isNotEmpty()) {
                    header("Cookie", cookieStr)
                }
            }
            .build()

        val resp = http.newCall(req).execute()
        val responseBody = resp.body?.string() ?: ""
        if (!resp.isSuccessful) {
            throw IOException("Failed to get WBI keys from nav: ${resp.code}, response: $responseBody")
        }

        val json = JSONObject(responseBody)
        val code = json.optInt("code", 0)
        val message = json.optString("message", "")
        if (code != 0) {
            throw IOException("Nav API error: code=$code, message=$message, response: $responseBody")
        }
        
        val data = json.optJSONObject("data") ?: throw IOException("Invalid nav response: missing data field, response: $responseBody")
        val wbiImg = data.optJSONObject("wbi_img") ?: throw IOException("Invalid nav response: missing wbi_img field, response: $responseBody")
        val imgUrl = wbiImg.optString("img_url", "")
        val subUrl = wbiImg.optString("sub_url", "")

        return ensureValidWbiKeys(imgUrl, subUrl)
    }

    private suspend fun fetchWbiKeysFromTicket(): Pair<String, String> {
        val ts = System.currentTimeMillis() / 1000L
        val hexSign = webTicketHmacSha256Hex("ts$ts")
        val urlBuilder = WEB_TICKET_URL.toHttpUrl().newBuilder()
            .addQueryParameter("key_id", "ec02")
            .addQueryParameter("hexsign", hexSign)
            .addQueryParameter("context[ts]", ts.toString())

        val cookies = getEffectiveCookies()
        val csrf = cookies["bili_jct"].orEmpty()
        if (csrf.isNotBlank()) {
            urlBuilder.addQueryParameter("csrf", csrf)
        }

        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", WEB_TICKET_UA)
            .post(ByteArray(0).toRequestBody(null))
            .build()

        val resp = http.newCall(req).execute()
        val responseBody = resp.body?.string() ?: ""
        if (!resp.isSuccessful) {
            throw IOException("Failed to get WBI keys from ticket: ${resp.code}, response: $responseBody")
        }

        val json = JSONObject(responseBody)
        val code = json.optInt("code", 0)
        val message = json.optString("message", "")
        if (code != 0) {
            throw IOException("Ticket API error: code=$code, message=$message, response: $responseBody")
        }
        
        val data = json.optJSONObject("data") ?: throw IOException("Invalid ticket response: missing data field, response: $responseBody")
        val nav = data.optJSONObject("nav") ?: throw IOException("Invalid ticket response: missing nav field, response: $responseBody")
        val imgUrl = nav.optString("img", "")
        val subUrl = nav.optString("sub", "")

        return ensureValidWbiKeys(imgUrl, subUrl)
    }

    private fun ensureValidWbiKeys(imgUrl: String, subUrl: String): Pair<String, String> {
        if (imgUrl.isBlank() || subUrl.isBlank()) {
            throw IOException("Invalid WBI keys: img=$imgUrl, sub=$subUrl")
        }
        val imgKey = imgUrl.substringAfterLast('/').substringBefore('.')
        val subKey = subUrl.substringAfterLast('/').substringBefore('.')

        if (imgKey.isBlank() || subKey.isBlank()) {
            throw IOException("Invalid WBI keys after parsing: imgKey=$imgKey, subKey=$subKey, imgUrl=$imgUrl, subUrl=$subUrl")
        }

        val now = System.currentTimeMillis()
        cachedWbiKey = Pair(imgKey, subKey)
        wbiKeyCachedAt = now
        return cachedWbiKey!!
    }

    private fun webTicketHmacSha256Hex(message: String): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        val secretKey = javax.crypto.spec.SecretKeySpec(WEB_TICKET_KEY.toByteArray(), "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(message.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private suspend fun getEffectiveCookies(): Map<String, String> {
        val stored = cookieRepo.getCookiesOnce()
        if (stored.isNotEmpty()) return stored
        return ensureAnonCookies()
    }

    private suspend fun ensureAnonCookies(): Map<String, String> {
        val now = System.currentTimeMillis()
        if (cachedAnonCookies != null && (now - anonCookiesCachedAt) < ANON_COOKIE_CACHE_MS) {
            return cachedAnonCookies!!
        }

        anonCookieMutex.withLock {
            val againNow = System.currentTimeMillis()
            if (cachedAnonCookies != null && (againNow - anonCookiesCachedAt) < ANON_COOKIE_CACHE_MS) {
                return cachedAnonCookies!!
            }

            val cookies = fetchAnonCookies()
            cachedAnonCookies = cookies
            anonCookiesCachedAt = againNow
            return cookies
        }
    }

    private suspend fun fetchAnonCookies(): Map<String, String> {
        val req = Request.Builder()
            .url(FINGERPRINT_URL)
            .header("User-Agent", FINGERPRINT_UA)
            .build()

        val resp = http.newCall(req).execute()
        val responseBody = resp.body?.string() ?: ""
        if (!resp.isSuccessful) {
            throw IOException("Failed to get anon cookies: ${resp.code}, body: $responseBody")
        }

        val json = JSONObject(responseBody)
        val data = json.optJSONObject("data") ?: JSONObject()
        val map = mutableMapOf<String, String>()

        val buvid3 = data.optString("b_3", data.optString("buvid3", ""))
        val buvid4 = data.optString("b_4", data.optString("buvid4", ""))
        val buvidFp = data.optString("buvid_fp", "")
        val buvidFpPlain = data.optString("buvid_fp_plain", "")
        val bLsid = data.optString("b_lsid", "")

        if (buvid3.isNotBlank()) map["buvid3"] = buvid3
        if (buvid4.isNotBlank()) map["buvid4"] = buvid4
        if (buvidFp.isNotBlank()) map["buvid_fp"] = buvidFp
        if (buvidFpPlain.isNotBlank()) map["buvid_fp_plain"] = buvidFpPlain
        if (bLsid.isNotBlank()) map["b_lsid"] = bLsid

        return map
    }

    private fun getMixinKey(orig: String): String {
        val s = StringBuilder()
        for (i in MIXIN_INDEX) {
            if (i < orig.length) {
                s.append(orig[i])
            }
        }
        return s.toString().substring(0, 32)
    }

    private fun md5(text: String): String {
        val bytes = text.toByteArray()
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun filterValue(v: String): String {
        return v.replace(Regex("""[!'()*]"""), "")
    }

    private fun urlEncode(v: String): String {
        return URLEncoder.encode(v, Charsets.UTF_8.name())
            .replace("+", "%20")
    }

    private suspend fun buildWbiUrl(url: HttpUrl.Builder, params: Map<String, String>): HttpUrl {
        val (imgKey, subKey) = getWbiKeys()
        val mixinKey = getMixinKey(imgKey + subKey)
        
        val filteredParams = params.mapValues { (_, v) -> filterValue(v) }.toMutableMap()
        val wts = (System.currentTimeMillis() / 1000L).toString()
        filteredParams["wts"] = wts
        
        val sortedParams = filteredParams.toSortedMap()
        val queryString = sortedParams.entries.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }
        val wbiSign = md5(queryString + mixinKey)
        
        sortedParams.forEach { (k, v) ->
            url.addQueryParameter(k, v)
        }
        url.addQueryParameter("w_rid", wbiSign)
        
        return url.build()
    }

}

class BiliCookieRepository(private val context: Context) {
    private val encryptedPrefs: SharedPreferences
    private val _authFlow: MutableStateFlow<BiliAuthBundle>
    private val _cookieFlow: MutableStateFlow<Map<String, String>>
    private val _authHealthFlow: MutableStateFlow<SavedCookieAuthHealth>

    val cookieFlow: StateFlow<Map<String, String>>
        get() = _cookieFlow.asStateFlow()

    val authHealthFlow: StateFlow<SavedCookieAuthHealth>
        get() = _authHealthFlow.asStateFlow()

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        var tempPrefs: SharedPreferences? = null
        var tempAuthFlow: MutableStateFlow<BiliAuthBundle>? = null
        var tempCookieFlow: MutableStateFlow<Map<String, String>>? = null
        var tempHealthFlow: MutableStateFlow<SavedCookieAuthHealth>? = null
        
        try {
            val prefs = EncryptedSharedPreferences.create(
                context,
                BILI_AUTH_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val initialBundle = loadAuthBundle(prefs)
            tempPrefs = prefs
            tempAuthFlow = MutableStateFlow(initialBundle)
            tempCookieFlow = MutableStateFlow(initialBundle.cookies)
            tempHealthFlow = MutableStateFlow(evaluateBiliAuthHealth(initialBundle))
        } catch (e: Exception) {
            // 解密失败，尝试删除损坏的存储文件
            android.util.Log.e("BiliCookieRepository", "解密失败，尝试删除损坏的存储文件", e)
            try {
                // 删除损坏的存储文件
                val prefsFile = File(context.filesDir.parent, "shared_prefs/${BILI_AUTH_PREFS}.xml")
                if (prefsFile.exists()) {
                    prefsFile.delete()
                }
                // 重新创建EncryptedSharedPreferences实例
                val prefs = EncryptedSharedPreferences.create(
                    context,
                    BILI_AUTH_PREFS,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                // 初始化状态
                val emptyBundle = BiliAuthBundle()
                tempPrefs = prefs
                tempAuthFlow = MutableStateFlow(emptyBundle)
                tempCookieFlow = MutableStateFlow(emptyMap())
                tempHealthFlow = MutableStateFlow(SavedCookieAuthHealth(state = SavedCookieAuthState.Missing))
            } catch (e2: Exception) {
                // 如果删除文件或重新创建失败，使用普通的SharedPreferences作为备选方案
                android.util.Log.e("BiliCookieRepository", "删除文件或重新创建失败，使用普通SharedPreferences", e2)
                val prefs = context.getSharedPreferences(BILI_AUTH_PREFS, Context.MODE_PRIVATE)
                prefs.edit { clear() }
                val emptyBundle = BiliAuthBundle()
                tempPrefs = prefs
                tempAuthFlow = MutableStateFlow(emptyBundle)
                tempCookieFlow = MutableStateFlow(emptyMap())
                tempHealthFlow = MutableStateFlow(SavedCookieAuthHealth(state = SavedCookieAuthState.Missing))
            }
        }
        
        encryptedPrefs = tempPrefs!!
        _authFlow = tempAuthFlow!!
        _cookieFlow = tempCookieFlow!!
        _authHealthFlow = tempHealthFlow!!
    }

    fun getCookiesOnce(): Map<String, String> = _cookieFlow.value

    fun getAuthHealthOnce(): SavedCookieAuthHealth = _authHealthFlow.value

    fun saveCookies(cookies: Map<String, String>, savedAt: Long = System.currentTimeMillis()) {
        val normalized = BiliAuthBundle(cookies = cookies, savedAt = savedAt).normalized(savedAt = savedAt)
        encryptedPrefs.edit { putString(KEY_BILI_AUTH_BUNDLE, normalized.toJson()) }
        _authFlow.value = normalized
        _cookieFlow.value = normalized.cookies
        _authHealthFlow.value = evaluateBiliAuthHealth(normalized)
    }

    fun clear() {
        encryptedPrefs.edit { remove(KEY_BILI_AUTH_BUNDLE) }
        _authFlow.value = BiliAuthBundle()
        _cookieFlow.value = emptyMap()
        _authHealthFlow.value = SavedCookieAuthHealth(state = SavedCookieAuthState.Missing)
    }

    private fun loadAuthBundle(prefs: SharedPreferences): BiliAuthBundle {
        return try {
            val json = prefs.getString(KEY_BILI_AUTH_BUNDLE, null) ?: return BiliAuthBundle()
            BiliAuthBundle.fromJson(json)
        } catch (e: Exception) {
            // 解密失败，返回空的BiliAuthBundle
            android.util.Log.e("BiliCookieRepository", "加载AuthBundle失败，返回空Bundle", e)
            BiliAuthBundle()
        }
    }

    companion object {
        private const val BILI_AUTH_PREFS = "bili_auth_secure_prefs"
        private const val KEY_BILI_AUTH_BUNDLE = "bili_auth_bundle"
    }
}

data class BiliAuthBundle(
    val cookies: Map<String, String> = emptyMap(),
    val savedAt: Long = 0L
) {
    fun hasLoginCookies(): Boolean {
        return !cookies["SESSDATA"].isNullOrBlank()
    }

    fun normalized(savedAt: Long = this.savedAt): BiliAuthBundle {
        return copy(
            cookies = LinkedHashMap(cookies.filterKeys { it.isNotBlank() }),
            savedAt = savedAt
        )
    }

    fun toJson(): String {
        return JSONObject().apply {
            put("cookies", JSONObject().apply { cookies.forEach { (key, value) -> put(key, value) } })
            put("savedAt", savedAt)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): BiliAuthBundle {
            return runCatching {
                val root = JSONObject(json)
                val cookies = linkedMapOf<String, String>()
                // 检查是否有"cookies"字段
                if (root.has("cookies")) {
                    // 标准格式: {"cookies": {...}, "savedAt": 123}
                    val cookiesJson = root.optJSONObject("cookies") ?: JSONObject()
                    val keys = cookiesJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        cookies[key] = cookiesJson.optString(key, "")
                    }
                } else {
                    // 直接格式: {"key1": "value1", "key2": "value2"}
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        cookies[key] = root.optString(key, "")
                    }
                }
                val savedAt = root.optLong("savedAt", System.currentTimeMillis())
                android.util.Log.d("BiliLogin", "fromJson: 解析后的cookies: $cookies, savedAt: $savedAt")
                BiliAuthBundle(cookies = cookies, savedAt = savedAt).normalized(savedAt = savedAt)
            }.onFailure { e ->
                android.util.Log.e("BiliLogin", "fromJson: 解析失败", e)
                BiliAuthBundle()
            }.getOrDefault(BiliAuthBundle())
        }
    }
}

enum class SavedCookieAuthState {
    Missing,
    Valid,
    Expired,
    Invalid
}

data class SavedCookieAuthHealth(
    val state: SavedCookieAuthState,
    val savedAt: Long = 0L,
    val checkedAt: Long = System.currentTimeMillis(),
    val ageMs: Long = 0L,
    val loginCookieKeys: List<String> = emptyList()
)

internal fun evaluateBiliAuthHealth(
    bundle: BiliAuthBundle,
    now: Long = System.currentTimeMillis()
): SavedCookieAuthHealth {
    val normalized = bundle.normalized(savedAt = bundle.savedAt)
    val loginCookieKeys = BILI_LOGIN_COOKIE_KEYS.filter { key ->
        !normalized.cookies[key].isNullOrBlank()
    }
    if (!normalized.hasLoginCookies()) {
        return SavedCookieAuthHealth(
            state = SavedCookieAuthState.Missing,
            savedAt = normalized.savedAt,
            checkedAt = now,
            loginCookieKeys = loginCookieKeys
        )
    }

    val savedAt = normalized.savedAt
    val ageMs = if (savedAt > 0L) {
        (now - savedAt).coerceAtLeast(0L)
    } else {
        Long.MAX_VALUE
    }
    return SavedCookieAuthHealth(
        state = SavedCookieAuthState.Valid,
        savedAt = savedAt,
        checkedAt = now,
        ageMs = ageMs,
        loginCookieKeys = loginCookieKeys
    )
}

private val BILI_LOGIN_COOKIE_KEYS = listOf(
    "SESSDATA",
    "DedeUserID",
    "bili_jct"
)

data class BiliAudioStreamInfo(
    val url: String,
    val backupUrls: List<String> = emptyList(),
    val codec: String = "",
    val qualityId: Int = 0,
    val bitrateKbps: Int = 0,
    val qualityTag: String? = null
)

enum class BiliQuality(val key: String, val label: String) {
    LOW("low", "低音质"),
    MEDIUM("medium", "中音质"),
    HIGH("high", "高音质"),
    LOSSLESS("lossless", "无损音质");

    companion object {
        fun fromKey(key: String): BiliQuality {
            return entries.find { it.key == key } ?: HIGH
        }
    }
}

fun selectStreamByPreference(
    available: List<BiliAudioStreamInfo>,
    preferredKey: String
): BiliAudioStreamInfo? {
    if (available.isEmpty()) return null
    val pref = BiliQuality.fromKey(preferredKey)

    val regularSorted = available
        .filter { it.qualityTag == null }
        .sortedByDescending { it.bitrateKbps }
    val taggedSorted = available
        .filter { it.qualityTag != null }
        .sortedByDescending { it.bitrateKbps }
    val sorted = (regularSorted + taggedSorted).distinctBy { it.url }

    return when (pref) {
        BiliQuality.LOSSLESS -> sorted.firstOrNull()
        BiliQuality.HIGH -> sorted.firstOrNull()
        BiliQuality.MEDIUM -> sorted.getOrNull(1) ?: sorted.firstOrNull()
        BiliQuality.LOW -> sorted.lastOrNull() ?: sorted.firstOrNull()
    }
}

fun prioritizeBiliStreamUrls(stream: BiliAudioStreamInfo): List<String> {
    val urls = mutableListOf<String>()
    if (stream.url.isNotBlank()) urls.add(stream.url)
    urls.addAll(stream.backupUrls.filter { it.isNotBlank() })
    return urls
}