package com.example.stablecamera

import android.util.Log

object LogUtil {
    private const val TAG = "StableCamera"

    fun d(msg: String) = Log.d(TAG, msg)
    fun e(msg: String, tr: Throwable? = null) = Log.e(TAG, msg, tr)
    fun i(msg: String) = Log.i(TAG, msg)
}
