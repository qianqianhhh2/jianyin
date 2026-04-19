package moe.ouom.biliapi.util

import android.util.Log

object BiliLogger {
    private const val TAG = "BiliAPI"

    fun d(tag: String = TAG, message: String) {
        Log.d(tag, message)
    }

    fun i(tag: String = TAG, message: String) {
        Log.i(tag, message)
    }

    fun w(tag: String = TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }
}
