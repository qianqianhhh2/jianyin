package com.qian.jianyin

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 版本更新数据类
 * @property versionCode 版本代码
 * @property versionName 版本名称
 * @property updateLog 更新日志
 * @property downloadUrl 下载链接
 */
data class VersionUpdate(
    val versionCode: Int,
    val versionName: String,
    val updateLog: String,
    val downloadUrl: String
)

/**
 * 版本检查管理器
 * 负责检查应用版本更新
 */
class VersionChecker(private val context: Context) {
    private val client = OkHttpClient()
    private val gson = Gson()
    
    // 版本检查的 JSON URL
    private val versionCheckUrl = "https://update.xn--twt-zz0ja.xyz/update.json"
    
    /**
     * 获取当前应用版本信息
     * @return 当前版本代码和版本名称
     */
    fun getCurrentVersion(): Pair<Int, String> {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                0
            )
            Pair(packageInfo.versionCode, packageInfo.versionName ?: "0.0.0")
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("VersionChecker", "获取当前版本信息失败", e)
            Pair(0, "0.0.0")
        }
    }
    
    /**
     * 检查版本更新
     * @return 版本更新信息，如果没有更新或检查失败则返回 null
     */
    suspend fun checkForUpdates(): VersionUpdate? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(versionCheckUrl)
                    .build()
                
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (!responseBody.isNullOrEmpty()) {
                        val versionUpdate = gson.fromJson(responseBody, VersionUpdate::class.java)
                        
                        // 比较版本代码
                        val (currentVersionCode, _) = getCurrentVersion()
                        if (versionUpdate.versionCode > currentVersionCode) {
                            return@withContext versionUpdate
                        }
                    }
                }
                null
            } catch (e: Exception) {
                Log.e("VersionChecker", "检查版本更新失败", e)
                null
            }
        }
    }
}
