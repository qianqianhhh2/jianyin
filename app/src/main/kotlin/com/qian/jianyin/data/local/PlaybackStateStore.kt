package com.qian.jianyin

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 播放状态存储类
 * 负责保存和恢复播放队列状态
 */
object PlaybackStateStore {
    private const val PREFS_NAME = "playback_state_prefs"
    private const val KEY_PLAYBACK_STATE = "playback_state"
    
    private val gson = Gson()
    
    /**
     * 保存播放状态
     * @param context 上下文
     * @param state 播放状态
     */
    fun savePlaybackState(context: Context, state: PlaybackState) {
        val stateJson = gson.toJson(state)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_PLAYBACK_STATE, stateJson)
            .apply()
    }
    
    /**
     * 加载播放状态
     * @param context 上下文
     * @return 播放状态，如果没有保存的状态则返回 null
     */
    fun loadPlaybackState(context: Context): PlaybackState? {
        val stateJson = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PLAYBACK_STATE, null)
        
        if (stateJson.isNullOrBlank()) {
            return null
        }
        
        return try {
            val type = object : TypeToken<PlaybackState>() {}.type
            gson.fromJson(stateJson, type)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 清除保存的播放状态
     * @param context 上下文
     */
    fun clearPlaybackState(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_PLAYBACK_STATE)
            .apply()
    }
}