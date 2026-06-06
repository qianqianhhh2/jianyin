package com.qian.jianyin.netease

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.qian.jianyin.netease.auth.WebLoginCompletionWatcher
import com.qian.jianyin.netease.auth.shouldAutoCompleteNeteaseWebLogin

class NeteaseWebLoginActivity : ComponentActivity() {

    companion object {
        const val RESULT_COOKIE_MAP_JSON = "result_cookie_map_json"
        private const val TARGET_URL = "https://music.163.com/"
        private val ALLOWED_LOGIN_DOMAINS = setOf(
            "163.com",
            "126.net",
            "163yun.com"
        )
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36"
    }

    private lateinit var webView: WebView
    private var hasReturned = false
    private var initialCookies: Map<String, String> = emptyMap()
    private val loginCompletionWatcher = WebLoginCompletionWatcher(::maybeReturnIfLoggedIn)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                allowFileAccess = false
                allowContentAccess = false
                userAgentString = DESKTOP_UA
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            webChromeClient = WebChromeClient()
            webViewClient = InnerClient()
        }

        setContentView(webView)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (this@NeteaseWebLoginActivity::webView.isInitialized && webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        finish()
                    }
                }
            }
        )

        initialCookies = readCookieMap()
        loginCompletionWatcher.start()
        webView.loadUrl(TARGET_URL)
    }

    override fun onDestroy() {
        loginCompletionWatcher.stop()
        if (this::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun readCookieMap(): Map<String, String> {
        val cm = CookieManager.getInstance()
        val main = cm.getCookie("https://music.163.com").orEmpty()
        val api = cm.getCookie("https://interface.music.163.com").orEmpty()
        val api3 = cm.getCookie("https://interface3.music.163.com").orEmpty()
        val merged = listOf(main, api, api3).filter { it.isNotBlank() }.joinToString("; ")
        if (merged.isBlank()) {
            return emptyMap()
        }
        return cookieStringToMap(merged)
    }

    private fun cookieStringToMap(raw: String): MutableMap<String, String> {
        val map = linkedMapOf<String, String>()
        raw.split(';')
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains('=') }
            .forEach { part ->
                val idx = part.indexOf('=')
                val key = part.substring(0, idx).trim()
                val value = part.substring(idx + 1).trim()
                if (key.isNotEmpty()) map[key] = value
            }
        return map
    }

    private fun normalizeNeteaseCookies(cookies: Map<String, String>): Map<String, String> {
        val sanitized = linkedMapOf<String, String>()
        cookies.forEach { (key, value) ->
            val k = key.trim()
            val v = value.trim()
            if (k.isNotBlank() && v.isNotBlank() && !v.contains(';')) {
                sanitized[k] = v
            }
        }
        if (sanitized.isNotEmpty()) {
            sanitized.putIfAbsent("os", "pc")
            sanitized.putIfAbsent("appver", "8.10.35")
        }
        return sanitized
    }

    private inner class InnerClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val currentRequest = request ?: return false
            val uri = currentRequest.url
            if (!isAllowedLoginUri(uri)) {
                return true
            }
            if (currentRequest.isForMainFrame) {
                loginCompletionWatcher.scheduleCheck()
            }
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            val host = runCatching { url?.let(Uri::parse)?.host }.getOrNull()
            if (hostMatchesAnyDomain(host, ALLOWED_LOGIN_DOMAINS)) {
                loginCompletionWatcher.scheduleCheck()
            }
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
            view?.post { loginCompletionWatcher.scheduleCheck() }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val host = runCatching { url?.let(Uri::parse)?.host }.getOrNull()
            if (hostMatchesAnyDomain(host, ALLOWED_LOGIN_DOMAINS)) {
                loginCompletionWatcher.scheduleCheck()
            }
        }
    }

    private fun maybeReturnIfLoggedIn(): Boolean {
        if (hasReturned) {
            return true
        }
        CookieManager.getInstance().flush()
        val currentCookies = readCookieMap()
        if (!shouldAutoCompleteNeteaseWebLogin(initialCookies, currentCookies)) {
            return false
        }

        hasReturned = true
        val json = org.json.JSONObject(currentCookies as Map<*, *>).toString()
        setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_COOKIE_MAP_JSON, json))
        finish()
        return true
    }

    private fun isAllowedLoginUri(uri: Uri?): Boolean {
        val resolvedUri = uri ?: return false
        if (resolvedUri.toString() == "about:blank") {
            return true
        }
        if (!resolvedUri.scheme.equals("https", ignoreCase = true)) {
            return false
        }
        return hostMatchesAnyDomain(resolvedUri.host, ALLOWED_LOGIN_DOMAINS)
    }

    private fun hostMatchesAnyDomain(host: String?, domains: Iterable<String>): Boolean {
        val normalizedHost = host?.lowercase()?.trim() ?: ""
        if (normalizedHost.isBlank()) {
            return false
        }
        return domains.any { domain ->
            val normalizedDomain = domain.lowercase().trim()
            normalizedHost == normalizedDomain || normalizedHost.endsWith(".$normalizedDomain")
        }
    }
}