package com.efxcreator.core

import android.util.Log as AndroidLog

/**
 * android.util.Log 와 동일한 시그니처의 로그 래퍼.
 * V/D/I/W 는 [AppConstants.LOG_ENABLED_FEATURES] 에 포함된 TAG 만 출력하고, E 는 항상 출력한다.
 */
object Log {

    private fun isLoggable(tag: String): Boolean =
        AppConstants.LOG_ENABLED && tag in AppConstants.LOG_ENABLED_FEATURES

    fun v(tag: String, msg: String): Int =
        if (isLoggable(tag) && AppConstants.LOG_VERBOSE_ENABLED) AndroidLog.v(tag, msg) else 0

    fun v(tag: String, msg: String, tr: Throwable): Int =
        if (isLoggable(tag) && AppConstants.LOG_VERBOSE_ENABLED) AndroidLog.v(tag, msg, tr) else 0

    fun d(tag: String, msg: String): Int =
        if (isLoggable(tag)) AndroidLog.d(tag, msg) else 0

    fun d(tag: String, msg: String, tr: Throwable): Int =
        if (isLoggable(tag)) AndroidLog.d(tag, msg, tr) else 0

    fun i(tag: String, msg: String): Int =
        if (isLoggable(tag)) AndroidLog.i(tag, msg) else 0

    fun i(tag: String, msg: String, tr: Throwable): Int =
        if (isLoggable(tag)) AndroidLog.i(tag, msg, tr) else 0

    fun w(tag: String, msg: String): Int =
        if (isLoggable(tag)) AndroidLog.w(tag, msg) else 0

    fun w(tag: String, msg: String, tr: Throwable): Int =
        if (isLoggable(tag)) AndroidLog.w(tag, msg, tr) else 0

    fun w(tag: String, tr: Throwable): Int =
        if (isLoggable(tag)) AndroidLog.w(tag, tr) else 0

    fun e(tag: String, msg: String): Int = AndroidLog.e(tag, msg)

    fun e(tag: String, msg: String, tr: Throwable): Int = AndroidLog.e(tag, msg, tr)
}
