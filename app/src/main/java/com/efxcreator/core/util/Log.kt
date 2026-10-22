package com.efxcreator.core.util

import com.efxcreator.core.constants.AppConstants
import android.util.Log as AndroidLog

/**
 * android.util.Log 와 동일한 시그니처의 로그 유틸.
 * TAG 가 AppConstants.LOG_ENABLED_FEATURES 화이트리스트에 있을 때만 출력한다 (E는 항상 출력).
 * Light-Stick-Music의 core.util.Log 포팅.
 */
object Log {

    private fun isLoggable(tag: String): Boolean {
        return AppConstants.LOG_ENABLED && tag in AppConstants.LOG_ENABLED_FEATURES
    }

    fun v(tag: String, msg: String): Int {
        if (isLoggable(tag) && AppConstants.LOG_VERBOSE_ENABLED) {
            return AndroidLog.v(tag, msg)
        }
        return 0
    }

    fun v(tag: String, msg: String, tr: Throwable): Int {
        if (isLoggable(tag) && AppConstants.LOG_VERBOSE_ENABLED) {
            return AndroidLog.v(tag, msg, tr)
        }
        return 0
    }

    fun d(tag: String, msg: String): Int {
        if (isLoggable(tag)) {
            return AndroidLog.d(tag, msg)
        }
        return 0
    }

    fun d(tag: String, msg: String, tr: Throwable): Int {
        if (isLoggable(tag)) {
            return AndroidLog.d(tag, msg, tr)
        }
        return 0
    }

    fun i(tag: String, msg: String): Int {
        if (isLoggable(tag)) {
            return AndroidLog.i(tag, msg)
        }
        return 0
    }

    fun i(tag: String, msg: String, tr: Throwable): Int {
        if (isLoggable(tag)) {
            return AndroidLog.i(tag, msg, tr)
        }
        return 0
    }

    fun w(tag: String, msg: String): Int {
        if (isLoggable(tag)) {
            return AndroidLog.w(tag, msg)
        }
        return 0
    }

    fun w(tag: String, msg: String, tr: Throwable): Int {
        if (isLoggable(tag)) {
            return AndroidLog.w(tag, msg, tr)
        }
        return 0
    }

    fun w(tag: String, tr: Throwable): Int {
        if (isLoggable(tag)) {
            return AndroidLog.w(tag, tr)
        }
        return 0
    }

    fun e(tag: String, msg: String): Int {
        return AndroidLog.e(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable): Int {
        return AndroidLog.e(tag, msg, tr)
    }
}
