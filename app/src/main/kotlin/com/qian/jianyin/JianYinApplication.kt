package com.qian.jianyin

import android.app.Application
import android.util.Log
import com.tencent.bugly.crashreport.CrashReport

class JianYinApplication : Application() {
    
    companion object {
        private const val TAG = "JianYinApplication"
        private const val BUGLY_APP_ID = "81f8233059"
    }
    
    override fun onCreate() {
        super.onCreate()
        initBugly()
    }
    
    private fun initBugly() {
        try {
            CrashReport.initCrashReport(
                applicationContext,
                BUGLY_APP_ID,
                BuildConfig.DEBUG
            )
            Log.d(TAG, "Bugly 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "Bugly 初始化失败", e)
        }
    }
}
