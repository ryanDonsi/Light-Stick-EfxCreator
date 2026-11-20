package com.efxcreator.data

import android.content.Context
import android.util.Log

/**
 * BLE 명령 최소 간격 설정을 SDK 에 적용한다 (`LSBluetooth.setMinCommandInterval`, 허용 범위 0~1000ms, 기본 20ms).
 *
 * 이 함수는 새 SDK 에서 추가되므로 아직 없는 SDK(aar)로도 앱이 빌드되도록 리플렉션으로 호출한다.
 * SDK 가 업데이트되어 안정되면 `LSBluetooth.setMinCommandInterval(ms)` 직접 호출로 바꾼다.
 * SDK 는 값을 저장하지 않으므로 앱을 켤 때마다 [applyStored] 로 다시 적용해야 한다.
 */
object BleCommandInterval {

    private const val TAG = "BleCommandInterval"
    private const val SDK_CLASS = "com.lightstick.LSBluetooth"

    private val setter by lazy {
        runCatching { Class.forName(SDK_CLASS).getMethod("setMinCommandInterval", java.lang.Long.TYPE) }.getOrNull()
    }
    private val getter by lazy {
        runCatching { Class.forName(SDK_CLASS).getMethod("getMinCommandInterval") }.getOrNull()
    }

    /** 현재 SDK 가 이 설정을 지원하는가 */
    val isSupported: Boolean get() = setter != null

    /** 저장하지 않고 SDK 에만 적용한다. 범위를 벗어나면 범위 안으로 맞춘다. 적용했으면 true */
    private fun applyToSdk(ms: Long): Boolean {
        val method = setter ?: return false
        val value = ms.coerceIn(PreferencesManager.BLE_INTERVAL_MIN_MS, PreferencesManager.BLE_INTERVAL_MAX_MS)
        return runCatching { method.invoke(null, value) }
            .onFailure { Log.w(TAG, "setMinCommandInterval($value) failed: ${it.cause?.message ?: it.message}") }
            .isSuccess
    }

    /** 사용자가 값을 바꿨을 때: 저장하고 연결 중인 기기를 포함해 바로 적용한다 */
    fun set(context: Context, ms: Long) {
        val value = ms.coerceIn(PreferencesManager.BLE_INTERVAL_MIN_MS, PreferencesManager.BLE_INTERVAL_MAX_MS)
        PreferencesManager(context).bleMinCommandIntervalMs = value
        applyToSdk(value)
    }

    /** 앱 시작 때: 저장해 둔 값을 SDK 에 다시 적용한다 (연결 전에 불러야 첫 명령부터 적용된다) */
    fun applyStored(context: Context) {
        applyToSdk(PreferencesManager(context).bleMinCommandIntervalMs)
    }

    /** SDK 가 현재 쓰는 전역값 (지원하지 않으면 null) */
    fun current(): Long? =
        runCatching { (getter?.invoke(null) as? Number)?.toLong() }.getOrNull()
}
