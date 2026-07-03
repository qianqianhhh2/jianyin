package com.qian.jianyin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.qian.jianyin.playback.AudioPlaybackService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "收到开机广播")

            val savedState = PlaybackStateStore.loadPlaybackState(context)
            if (savedState != null && savedState.isPlaying && savedState.songs.isNotEmpty()) {
                Log.d("BootReceiver", "用户上次正在播放，恢复播放服务")

                try {
                    val serviceIntent = Intent(context, AudioPlaybackService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d("BootReceiver", "播放服务启动成功")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "启动播放服务失败", e)
                }
            } else {
                Log.d("BootReceiver", "用户未在播放，跳过启动")
            }
        }
    }
}
