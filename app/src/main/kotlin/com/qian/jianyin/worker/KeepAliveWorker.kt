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
import com.qian.jianyin.playback.AudioPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import androidx.media3.common.util.UnstableApi

/**
 * 保活 Worker
 *
 * 每 15 分钟检查一次播放服务是否存活。
 * 仅在用户上次正在播放时恢复服务，避免无播放时无意义保活。
 *
 * 国产系统保活要点：
 * - WorkManager 利用 AlarmManager 对齐唤醒，系统无法完全禁止
 * - 使用 startForegroundService 直接启动前台服务
 */
@UnstableApi
class KeepAliveWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "jianyin_keep_alive"
        private const val TAG = "KeepAliveWorker"

        /**
         * 检查间隔（分钟）—— 不要太短，否则被系统判定为频繁唤醒
         */
        private const val CHECK_INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<KeepAliveWorker>(
                CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setInitialDelay(CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "保活任务已调度，间隔: ${CHECK_INTERVAL_MINUTES}分钟")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "保活任务已取消")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "执行保活检查")
        val context = applicationContext

        val savedState = PlaybackStateStore.loadPlaybackState(context)

        if (savedState != null && savedState.isPlaying && savedState.songs.isNotEmpty()) {
            Log.d(TAG, "检测到播放状态，检查服务是否存活")

            if (!isPlaybackServiceRunning(context)) {
                Log.w(TAG, "播放服务已死，尝试复活")
                startPlaybackService(context)
            } else {
                Log.d(TAG, "播放服务正常")
            }
        } else {
            Log.d(TAG, "无播放状态，跳过")
        }

        Result.success()
    }

    private fun isPlaybackServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == AudioPlaybackService::class.java.name
        }
    }

    private fun startPlaybackService(context: Context) {
        try {
            val intent = Intent(context, AudioPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "服务复活成功")
        } catch (e: Exception) {
            Log.e(TAG, "服务复活失败: ${e.message}", e)
        }
    }
}
