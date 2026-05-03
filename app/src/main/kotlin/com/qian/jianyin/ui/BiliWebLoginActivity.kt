package com.qian.jianyin

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri

class BiliWebLoginActivity : ComponentActivity() {

    companion object {
        const val RESULT_COOKIE = "result_cookie_json"
        private const val LOGIN_URL = "https://passport.bilibili.com/login"
        private const val LOGIN_CHECK_INTERVAL_MS = 500L
        private const val LOGIN_CHECK_DELAY_MS = 1000L
        private const val LOGIN_STABLE_COUNT = 3

        private val ALLOWED_LOGIN_DOMAINS = setOf(
            "bilibili.com",
            "hdslb.com",
            "biliimg.com"
        )

        private val IMPORTANT_COOKIE_KEYS = listOf(
            "SESSDATA",
            "bili_jct",
            "DedeUserID",
            "DedeUserID__ckMd5",
            "buvid3",
            "sid"
        )
    }

    private lateinit var webView: WebView
    private var hasReturned = false
    private var loginCheckCount = 0
    private var loginCheckRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        forceFreshWebContext()

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = InnerClient()
        }
        setContentView(webView)

        webView.loadUrl(LOGIN_URL)
        scheduleLoginCheck()
    }

    override fun onDestroy() {
        loginCheckRunnable?.let { handler.removeCallbacks(it) }
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    private fun forceFreshWebContext() {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.removeSessionCookies(null)
        val domains = listOf(
            ".bilibili.com", "bilibili.com", "www.bilibili.com", "m.bilibili.com"
        )
        val keys = listOf(
            "SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "buvid3", "sid"
        )
        domains.forEach { d ->
            keys.forEach { k ->
                cm.setCookie(
                    "https://$d",
                    "$k=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
                )
            }
        }
        cm.flush()
        WebStorage.getInstance().deleteAllData()
        if (::webView.isInitialized) {
            webView.clearCache(true)
            webView.clearHistory()
        }
    }

    private fun scheduleLoginCheck() {
        loginCheckRunnable = Runnable {
            if (hasReturned) return@Runnable
            if (checkLoginSuccess()) {
                loginCheckCount++
                if (loginCheckCount >= LOGIN_STABLE_COUNT) {
                    returnLoginResult()
                } else {
                    handler.postDelayed(loginCheckRunnable!!, LOGIN_CHECK_INTERVAL_MS)
                }
            } else {
                loginCheckCount = 0
                handler.postDelayed(loginCheckRunnable!!, LOGIN_CHECK_INTERVAL_MS)
            }
        }
        handler.postDelayed(loginCheckRunnable!!, LOGIN_CHECK_DELAY_MS)
    }

    private fun checkLoginSuccess(): Boolean {
        val cookieMap = readCookieForDomains(
            listOf(
                ".bilibili.com",
                "bilibili.com",
                "www.bilibili.com",
                "m.bilibili.com"
            )
        )
        val hasImportantCookies = IMPORTANT_COOKIE_KEYS.any { cookieMap.containsKey(it) }
        return hasImportantCookies && !cookieMap["SESSDATA"].isNullOrBlank()
    }

    private fun returnLoginResult() {
        if (hasReturned) return
        hasReturned = true

        CookieManager.getInstance().flush()
        val cookieMap = readCookieForDomains(
            listOf(
                ".bilibili.com",
                "bilibili.com",
                "www.bilibili.com",
                "m.bilibili.com"
            )
        )
        android.util.Log.d("BiliLogin", "returnLoginResult: 读取到的cookieMap: $cookieMap")

        val json = org.json.JSONObject().apply {
            cookieMap.forEach { (k, v) -> put(k, v) }
        }.toString()
        android.util.Log.d("BiliLogin", "returnLoginResult: 生成的JSON: $json")

        setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_COOKIE, json))
        android.util.Log.d("BiliLogin", "returnLoginResult: 返回登录结果")
        finish()
    }

    private fun isAllowedLoginUri(uri: Uri?): Boolean {
        val resolvedUri = uri ?: return false
        if (resolvedUri.toString() == "about:blank") return true
        if (!resolvedUri.scheme.equals("https", ignoreCase = true)) return false
        return hostMatchesAnyDomain(resolvedUri.host, ALLOWED_LOGIN_DOMAINS)
    }

    private fun hostMatchesAnyDomain(host: String?, domains: Set<String>): Boolean {
        if (host == null) return false
        return domains.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }

    private fun isAllowedMainFrameRequest(request: WebResourceRequest, extraCheck: (Uri?) -> Boolean): Boolean {
        val uri = request.url
        if (!request.isForMainFrame) return true
        return extraCheck(uri)
    }

    private inner class InnerClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val currentRequest = request ?: return false
            val uri = currentRequest.url
            if (!isAllowedMainFrameRequest(currentRequest) { isAllowedLoginUri(it) }) {
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
        }
    }

    private fun readCookieForDomains(domains: List<String>): Map<String, String> {
        val cm = CookieManager.getInstance()
        val result = linkedMapOf<String, String>()
        domains.forEach { d ->
            val raw = cm.getCookie("https://$d").orEmpty()
            if (raw.isBlank()) return@forEach
            raw.split(';')
                .map { it.trim() }
                .forEach { pair ->
                    val eq = pair.indexOf('=')
                    if (eq > 0) {
                        val k = pair.substring(0, eq)
                        val v = pair.substring(eq + 1)
                        result[k] = v
                    }
                }
        }
        return result
    }
}