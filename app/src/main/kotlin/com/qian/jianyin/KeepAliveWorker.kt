package com.qian.jianyin

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import androidx.media3.common.util.UnstableApi

@UnstableApi

/**
 * 保活 Worker
 *
 * 使用 WorkManager 定期检查 PlaybackService 运行状态，
 * 当服务停止时自动拉起。采用协程实现，避免线程阻塞。
 *
 * 任务间隔为 15 分钟，支持设备重启后自动恢复。
 */
class KeepAliveWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "keep_alive_worker"
        private const val CHECK_INTERVAL_MINUTES = 15L

        /**
         * 调度保活任务
         *
         * @param context 上下文
         */
        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<KeepAliveWorker>(
                CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d("KeepAliveWorker", "保活任务已调度")
        }

        /**
         * 取消保活任务
         *
         * @param context 上下文
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d("KeepAliveWorker", "保活任务已取消")
        }
    }

    /**
     * 执行保活检查
     *
     * @return 工作结果
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("KeepAliveWorker", "执行保活检查")

        val context = applicationContext

        if (!isPlaybackServiceRunning(context)) {
            Log.d("KeepAliveWorker", "PlaybackService 未运行，尝试拉起")
            startPlaybackService(context)
        } else {
            Log.d("KeepAliveWorker", "PlaybackService 运行正常")
        }

        Result.success()
    }

    /**
     * 检查 PlaybackService 是否正在运行
     *
     * @param context 上下文
     * @return 是否正在运行
     */
    private fun isPlaybackServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any {
            it.service.className == PlaybackService::class.java.name
        }
    }

    /**
     * 启动 PlaybackService
     *
     * @param context 上下文
     */
    private fun startPlaybackService(context: Context) {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d("KeepAliveWorker", "PlaybackService 启动成功")
        } catch (e: Exception) {
            Log.e("KeepAliveWorker", "启动 PlaybackService 失败", e)
        }
    }
}
