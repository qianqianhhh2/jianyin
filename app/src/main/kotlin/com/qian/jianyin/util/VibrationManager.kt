package com.qian.jianyin.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.qian.jianyin.DownloadSettingsStore

/**
 * 震动管理工具类
 * 所有震动操作会先检查设置中的震动开关
 */
object VibrationManager {

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun isEnabled(context: Context): Boolean {
        return DownloadSettingsStore.isVibrationEnabled(context)
    }

    /**
     * 轻触反馈（点击按钮等）
     */
    fun lightTap(context: Context) {
        if (!isEnabled(context)) return
        getVibrator(context)?.vibrate(
            VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    /**
     * 中等反馈（收藏、切歌等）
     */
    fun mediumTap(context: Context) {
        if (!isEnabled(context)) return
        getVibrator(context)?.vibrate(
            VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    /**
     * 长按/重要操作反馈（下载完成、定时设置等）
     */
    fun heavyTap(context: Context) {
        if (!isEnabled(context)) return
        getVibrator(context)?.vibrate(
            VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    /**
     * 双脉冲反馈（收藏成功等）
     */
    fun doubleTap(context: Context) {
        if (!isEnabled(context)) return
        val timings = longArrayOf(0, 40, 80)
        val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, VibrationEffect.DEFAULT_AMPLITUDE)
        getVibrator(context)?.vibrate(
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        )
    }

    /**
     * 成功操作反馈（下载完成等）
     */
    fun success(context: Context) {
        if (!isEnabled(context)) return
        val timings = longArrayOf(0, 50, 100, 150)
        val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
        getVibrator(context)?.vibrate(
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        )
    }

    /**
     * 滑块档位切换反馈（齿轮感）
     */
    fun tick(context: Context) {
        if (!isEnabled(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getVibrator(context)?.vibrate(
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            )
        } else {
            @Suppress("DEPRECATION")
            getVibrator(context)?.vibrate(10)
        }
    }
}
