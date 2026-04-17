package com.qian.jianyin

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 一言管理类
 * 用于获取一言并显示
 */
object HitokotoManager {
    /**
     * 获取一言并显示
     */
    suspend fun getHitokotoAndShow(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val hitokoto = fetchHitokoto()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, hitokoto, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                val greeting = getGreeting()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, greeting, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * 从API获取一言
     */
    private fun fetchHitokoto(): String {
        val url = URL("https://v1.hitokoto.cn/?c=f&encode=text")
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val result = reader.use { it.readText() }
                return result.trim()
            } else {
                throw Exception("HTTP error code: $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 根据时间获取问候语
     */
    private fun getGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..5 -> "夜深了，注意休息哦～"
            in 6..11 -> "早上好！今天又是元气满满的一天！"
            in 12..17 -> "中午好！记得好好吃饭哦～"
            else -> "晚上好！今天过得怎么样？"
        }
    }
}