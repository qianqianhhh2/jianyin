package moe.ouom.biliapi.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private const val BILI_AUTH_PREFS = "bili_auth_secure_prefs"
private const val KEY_BILI_AUTH_BUNDLE = "bili_auth_bundle"

private val Context.biliCookieStore by preferencesDataStore("bili_auth_store")

object BiliCookieKeys {
    val COOKIE_JSON = stringPreferencesKey("bili_cookie_json")
}

private val BILI_LOGIN_COOKIE_KEYS = listOf(
    "SESSDATA",
    "DedeUserID",
    "bili_jct"
)

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
            put(
                "cookies",
                JSONObject().apply {
                    cookies.forEach { (key, value) -> put(key, value) }
                }
            )
            put("savedAt", savedAt)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): BiliAuthBundle {
            return runCatching {
                val root = JSONObject(json)
                val cookiesJson = root.optJSONObject("cookies") ?: JSONObject()
                val cookies = linkedMapOf<String, String>()
                val keys = cookiesJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    cookies[key] = cookiesJson.optString(key, "")
                }
                val savedAt = root.optLong("savedAt", 0L)
                BiliAuthBundle(
                    cookies = cookies,
                    savedAt = savedAt
                ).normalized(savedAt = savedAt)
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
        encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            BILI_AUTH_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val initialBundle = loadAuthBundle()
        _authFlow = MutableStateFlow(initialBundle)
        _cookieFlow = MutableStateFlow(initialBundle.cookies)
        _authHealthFlow = MutableStateFlow(
            evaluateBiliAuthHealth(initialBundle)
        )
    }

    fun getCookiesOnce(): Map<String, String> = _cookieFlow.value

    fun getAuthHealthOnce(): SavedCookieAuthHealth = _authHealthFlow.value

    fun getAuthHealth(
        now: Long = System.currentTimeMillis()
    ): SavedCookieAuthHealth = evaluateBiliAuthHealth(_authFlow.value, now)

    fun saveCookies(
        cookies: Map<String, String>,
        savedAt: Long = System.currentTimeMillis()
    ) {
        val normalized = BiliAuthBundle(
            cookies = cookies,
            savedAt = savedAt
        ).normalized(savedAt = savedAt)
        encryptedPrefs.edit {
            putString(KEY_BILI_AUTH_BUNDLE, normalized.toJson())
        }
        _authFlow.value = normalized
        _cookieFlow.value = normalized.cookies
        _authHealthFlow.value = evaluateBiliAuthHealth(normalized)
    }

    fun clear() {
        encryptedPrefs.edit {
            remove(KEY_BILI_AUTH_BUNDLE)
        }
        _authFlow.value = BiliAuthBundle()
        _cookieFlow.value = emptyMap()
        _authHealthFlow.value = SavedCookieAuthHealth(state = SavedCookieAuthState.Missing)
    }

    private fun loadAuthBundle(): BiliAuthBundle {
        val json = encryptedPrefs.getString(KEY_BILI_AUTH_BUNDLE, null) ?: return BiliAuthBundle()
        return BiliAuthBundle.fromJson(json)
    }
}
