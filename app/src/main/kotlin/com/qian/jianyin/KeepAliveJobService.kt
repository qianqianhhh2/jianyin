package com.qian.jianyin

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
/**
 * 保活任务服务
 * 去你妈的烂橘子
 * 使用 JobScheduler 定期检查 PlaybackService 和 DaemonService 的运行状态，
 * 当这些服务停止时自动拉起，确保应用的核心功能持续运行。
 * 
 * 任务间隔为 15 分钟，支持设备重启后自动恢复。
 */
class KeepAliveJobService : JobService() {
    
    /**
     * 伴生对象
     * 
     * 提供任务调度和取消的静态方法。
     */
    companion object {
        /**
         * 任务 ID
         */
        private const val JOB_ID = 1001
        
        /**
         * 任务间隔（毫秒）
         */
        private const val JOB_INTERVAL = 15 * 60 * 1000L
        
        /**
         * 调度保活任务
         * 
         * 使用 JobScheduler 调度定期执行的保活任务，
         * 任务间隔为 15 分钟，支持设备重启后自动恢复。
         * 
         * @param context 上下文
         */
        fun scheduleJob(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            
            val componentName = ComponentName(context, KeepAliveJobService::class.java)
            
            val jobInfo = JobInfo.Builder(JOB_ID, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setPeriodic(JOB_INTERVAL)
                .setPersisted(true)
                .build()
            
            jobScheduler.schedule(jobInfo)
            Log.d("KeepAliveJob", "Job 已调度")
        }
        
        /**
         * 取消保活任务
         * 
         * 取消已调度的保活任务。
         * 
         * @param context 上下文
         */
        fun cancelJob(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            Log.d("KeepAliveJob", "Job 已取消")
        }
    }
    
    /**
     * 执行保活任务
     * 
     * 检查 PlaybackService 和 DaemonService 的运行状态，
     * 当这些服务停止时自动拉起。
     * 
     * @param params 任务参数
     * @return 是否需要在后台继续执行，返回 false
     */
    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("KeepAliveJob", "Job 执行，检测服务状态")
        
        val context = applicationContext
        val playbackServiceRunning = isServiceRunning(context, PlaybackService::class.java)
        val daemonServiceRunning = isServiceRunning(context, DaemonService::class.java)
        
        if (!playbackServiceRunning) {
            Log.d("KeepAliveJob", "PlaybackService 未运行，尝试拉起")
            try {
                val intent = Intent(context, PlaybackService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("KeepAliveJob", "拉起 PlaybackService 失败", e)
            }
        }
        
        if (!daemonServiceRunning) {
            Log.d("KeepAliveJob", "DaemonService 未运行，尝试拉起")
            try {
                val intent = Intent(context, DaemonService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("KeepAliveJob", "拉起 DaemonService 失败", e)
            }
        }
        
        return false
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
     * 停止保活任务
     * 
     * 当系统需要停止任务时调用，返回 false 表示不需要重新调度。
     * 
     * @param params 任务参数
     * @return 是否需要重新调度，返回 false
     */
    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d("KeepAliveJob", "Job 停止")
        return false
    }
}