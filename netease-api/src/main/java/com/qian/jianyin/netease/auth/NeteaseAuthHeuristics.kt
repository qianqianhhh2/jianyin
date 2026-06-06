package com.qian.jianyin.netease.auth

private const val SYNTHETIC_SAVED_AT = 1L
private const val SYNTHETIC_CHECKED_AT = 2L

private val NETEASE_LOGIN_COOKIE_KEYS = listOf(
    "MUSIC_U"
)

private val NETEASE_COOKIE_NAME_REGEX = Regex("^[!#\$%&'*+.^_`|~0-9A-Za-z-]+$")

data class NeteaseAuthBundle(
    val cookies: Map<String, String> = emptyMap(),
    val savedAt: Long = 0L
) {
    fun hasLoginCookies(): Boolean {
        return NETEASE_LOGIN_COOKIE_KEYS.any { key -> !cookies[key].isNullOrBlank() }
    }

    fun normalized(savedAt: Long = this.savedAt): NeteaseAuthBundle {
        return copy(
            cookies = LinkedHashMap(cookies.filterKeys { it.isNotBlank() }),
            savedAt = savedAt
        )
    }
}

data class NeteaseCookieValidationResult(
    val sanitizedCookies: Map<String, String> = emptyMap(),
    val rejectedKeys: List<String> = emptyList()
) {
    val hasLoginCookie: Boolean
        get() = NETEASE_LOGIN_COOKIE_KEYS.any { key -> !sanitizedCookies[key].isNullOrBlank() }

    val isAccepted: Boolean
        get() = sanitizedCookies.isNotEmpty() && hasLoginCookie
}

fun validateAndSanitizeNeteaseCookies(
    cookies: Map<String, String>,
    includeFallbackCookies: Boolean = true
): NeteaseCookieValidationResult {
    val sanitized = linkedMapOf<String, String>()
    val rejected = linkedSetOf<String>()

    cookies.forEach { (rawKey, rawValue) ->
        val key = rawKey.trim()
        val value = rawValue.trim()
        val rejectedKey = key.ifBlank { "<blank>" }
        when {
            key.isBlank() -> rejected += rejectedKey
            !NETEASE_COOKIE_NAME_REGEX.matches(key) -> rejected += rejectedKey
            value.isBlank() -> rejected += rejectedKey
            value.any { it.isISOControl() } -> rejected += rejectedKey
            ';' in value -> rejected += rejectedKey
            else -> sanitized[key] = value
        }
    }

    if (includeFallbackCookies && sanitized.isNotEmpty()) {
        sanitized.putIfAbsent("os", "pc")
        sanitized.putIfAbsent("appver", "8.10.35")
    }

    return NeteaseCookieValidationResult(
        sanitizedCookies = sanitized,
        rejectedKeys = rejected.toList()
    )
}

fun normalizeNeteaseWebLoginCookies(
    cookies: Map<String, String>
): Map<String, String> {
    return validateAndSanitizeNeteaseCookies(cookies).sanitizedCookies
}

fun shouldAutoCompleteNeteaseWebLogin(
    initialCookies: Map<String, String>,
    currentCookies: Map<String, String>
): Boolean {
    val normalizedCurrent = normalizeNeteaseWebLoginCookies(currentCookies)
    if (normalizedCurrent["MUSIC_U"].isNullOrBlank()) {
        return false
    }

    val currentHasLogin = NETEASE_LOGIN_COOKIE_KEYS.any { key ->
        !normalizedCurrent[key].isNullOrBlank()
    }
    if (!currentHasLogin) {
        return false
    }

    val normalizedInitial = normalizeNeteaseWebLoginCookies(initialCookies)
    return normalizedInitial != normalizedCurrent
}