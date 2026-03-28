package com.qian.jianyin

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * 守护进程服务
 * 
 * 负责监控 PlaybackService 和 KeepAliveJobService 的运行状态，
 * 当这些服务停止时自动拉起，确保应用的核心功能持续运行。
 * 
 * 采用无限循环的方式，每 30 秒检查一次服务状态。
 */
class DaemonService : Service() {
    
    /**
     * 绑定服务时调用
     * 
     * 本服务不支持绑定，返回 null。
     * 
     * @param intent 绑定意图
     * @return 绑定接口，返回 null
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    /**
     * 启动服务时调用
     * 
     * 启动守护进程的监控逻辑，每 30 秒检查一次服务状态，
     * 当 PlaybackService 或 KeepAliveJobService 停止时自动拉起。
     * 
     * @param intent 启动意图
     * @param flags 启动标志
     * @param startId 启动 ID
     * @return 服务启动模式，返回 START_STICKY
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("DaemonService", "守护进程启动")
        
        while (true) {
            try {
                Thread.sleep(30000)
                
                val playbackServiceRunning = isServiceRunning(this, PlaybackService::class.java)
                val keepAliveJobRunning = isServiceRunning(this, KeepAliveJobService::class.java)
                val mainActivityRunning = isActivityRunning(this, MainActivity::class.java)
                
                if (!playbackServiceRunning) {
                    Log.d("DaemonService", "检测到 PlaybackService 停止，尝试拉起")
                    startPlaybackService()
                }
                
                if (!keepAliveJobRunning) {
                    Log.d("DaemonService", "检测到 KeepAliveJobService 停止，尝试拉起")
                    startKeepAliveJob()
                }
            } catch (e: Exception) {
                Log.e("DaemonService", "守护进程异常", e)
            }
        }
        
        return START_STICKY
    }
    
    /**
     * 检查服务是否正在运行
     * 
     * 通过 ActivityManager 获取当前运行的服务列表，
     * 检查指定的服务是否在运行中。
     * 
     * @param context 上下文
     * @param serviceClass 服务类
     * @return 是否正在运行
     */
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val services = manager.getRunningServices(Integer.MAX_VALUE)
        for (service in services) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    /**
     * 检查 Activity 是否正在运行
     * 
     * 通过 ActivityManager 获取当前运行的任务列表，
     * 检查指定的 Activity 是否在运行中。
     * 
     * @param context 上下文
     * @param activityClass Activity 类
     * @return 是否正在运行
     */
    private fun isActivityRunning(context: Context, activityClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val tasks = manager.getRunningTasks(Integer.MAX_VALUE)
        for (task in tasks) {
            if (activityClass.name == task.topActivity?.className) {
                return true
            }
        }
        return false
    }
    
    /**
     * 启动 PlaybackService
     * 
     * 根据 Android 版本选择合适的启动方式，
     * Android 8.0+ 使用 startForegroundService，
     * 低于 Android 8.0 使用 startService。
     */
    private fun startPlaybackService() {
        try {
            val intent = Intent(this, PlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d("DaemonService", "PlaybackService 拉起成功")
        } catch (e: Exception) {
            Log.e("DaemonService", "拉起服务失败", e)
        }
    }
    
    /**
     * 启动 KeepAliveJobService
     * 
     * 启动保活任务服务，确保应用的保活机制正常运行。
     */
    private fun startKeepAliveJob() {
        try {
            val intent = Intent(this, KeepAliveJobService::class.java)
            startService(intent)
            Log.d("DaemonService", "KeepAliveJobService 拉起成功")
        } catch (e: Exception) {
            Log.e("DaemonService", "拉起 KeepAliveJobService 失败", e)
        }
    }
    
    /**
     * 销毁服务时调用
     * 
     * 停止守护进程并记录日志。
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d("DaemonService", "守护进程停止")
    }
}