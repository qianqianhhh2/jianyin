package com.qian.jianyin

import android.util.Log
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * 从备用音源接口获取歌曲播放 URL
 */
internal suspend fun fetchBackupAudioUrl(songId: String, backupApiUrl: String): String {
    val baseUrl = if (backupApiUrl.endsWith("/")) backupApiUrl else "$backupApiUrl/"
    val requestUrl = "${baseUrl}?type=song&id=$songId&br=320"
    Log.d("MusicVM", "fetchBackupAudioUrl: 请求备用音源: $requestUrl")

    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val request = Request.Builder()
        .url(requestUrl)
        .build()

    return suspendCancellableCoroutine { continuation ->
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    if (!response.isSuccessful) {
                        Log.e("MusicVM", "fetchBackupAudioUrl: 请求失败，状态码: ${response.code}")
                        continuation.resume("")
                        return
                    }

                    val contentType = response.header("Content-Type", "") ?: ""
                    Log.d("MusicVM", "fetchBackupAudioUrl: Content-Type: $contentType")

                    if (contentType.contains("application/json") || contentType.contains("text/")) {
                        val body = response.body?.string() ?: ""
                        Log.d("MusicVM", "fetchBackupAudioUrl: 响应内容: ${body.take(500)}")

                        try {
                            val jsonArray = JSONArray(body)
                            if (jsonArray.length() > 0) {
                                val firstObj = jsonArray.getJSONObject(0)
                                val audioUrl = firstObj.optString("url", "")
                                if (audioUrl.isNotBlank()) {
                                    Log.d("MusicVM", "fetchBackupAudioUrl: JSON解析获取到音频URL: $audioUrl")
                                    continuation.resume(audioUrl)
                                    return
                                }
                            }
                        } catch (jsonE: Exception) {
                            Log.d("MusicVM", "fetchBackupAudioUrl: JSON解析失败，尝试作为音频URL处理")
                        }
                    }

                    val finalUrl = response.request.url.toString()
                    Log.d("MusicVM", "fetchBackupAudioUrl: 直接使用请求URL作为音频源: $finalUrl")
                    continuation.resume(finalUrl)
                } catch (e: Exception) {
                    Log.e("MusicVM", "fetchBackupAudioUrl: 处理响应失败", e)
                    continuation.resume("")
                } finally {
                    response.close()
                }
            }

            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e("MusicVM", "fetchBackupAudioUrl: 请求备用音源失败", e)
                continuation.resume("")
            }
        })

        continuation.invokeOnCancellation {
            try {
                client.dispatcher.cancelAll()
            } catch (_: Exception) {
            }
        }
    }
}
