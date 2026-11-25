package com.efxcreator.data

import android.content.Context
import android.util.Log
import com.lightstick.LSBluetooth

/**
 * BLE 명령 큐 설정(전송 간격 / 큐 크기)을 SDK 2.200.0 에 적용한다.
 *
 * - 전송 간격: `LSBluetooth.setCommandIntervalMs` (0~1000ms, 기본 50) — 한 명령이 끝난 뒤 다음 명령까지의 최소 시간
 * - 큐 크기: `LSBluetooth.setCommandQueueSize` (1~256, 기본 8) — 기기당 대기 명령 최대 개수.
 *   초과하면 가장 오래된 대기 명령이 실행/콜백 없이 버려진다
 *
 * 두 값은 **연결된 모든 기기에 공통**이며 바로 적용된다(연결 중인 기기 포함). SDK 는 값을 저장하지 않으므로
 * [PreferencesManager]에 저장해 두었다가 앱을 켤 때마다 [applyStored]로 다시 적용해야 한다.
 *
 * 개발/진단용이라 debug 빌드에서만 바꾸고 적용한다. release 에서는 화면이 없고 저장된 값도 무시해 SDK 기본값(50ms / 8)을 쓴다.
 */
object BleCommandSettings {

    private const val TAG = "BleCommandSettings"

    /** 사용자가 간격을 바꿨을 때: 범위 안으로 맞춰 저장하고 바로 적용한다 */
    fun setIntervalMs(context: Context, ms: Long) {
        if (!com.efxcreator.BuildConfig.DEBUG) return
        val value = ms.coerceIn(PreferencesManager.BLE_INTERVAL_MIN_MS, PreferencesManager.BLE_INTERVAL_MAX_MS)
        PreferencesManager(context).bleMinCommandIntervalMs = value
        LSBluetooth.setCommandIntervalMs(value)
    }

    /** 사용자가 큐 크기를 바꿨을 때: 범위 안으로 맞춰 저장하고 바로 적용한다 */
    fun setQueueSize(context: Context, size: Int) {
        if (!com.efxcreator.BuildConfig.DEBUG) return
        val value = size.coerceIn(PreferencesManager.BLE_QUEUE_MIN, PreferencesManager.BLE_QUEUE_MAX)
        PreferencesManager(context).bleCommandQueueSize = value
        LSBluetooth.setCommandQueueSize(value)
    }

    /** 두 값을 SDK 기본값으로 되돌린다 (저장도 기본값으로) */
    fun resetToDefaults(context: Context) {
        setIntervalMs(context, PreferencesManager.BLE_INTERVAL_DEFAULT_MS)
        setQueueSize(context, PreferencesManager.BLE_QUEUE_DEFAULT)
    }

    /** 앱 시작 때: 저장해 둔 값을 SDK 에 다시 적용한다 (연결 전에 불러야 첫 명령부터 적용된다) */
    fun applyStored(context: Context) {
        if (!com.efxcreator.BuildConfig.DEBUG) return
        val prefs = PreferencesManager(context)
        runCatching { LSBluetooth.setCommandIntervalMs(prefs.bleMinCommandIntervalMs) }
            .onFailure { Log.w(TAG, "setCommandIntervalMs failed: ${it.message}") }
        runCatching { LSBluetooth.setCommandQueueSize(prefs.bleCommandQueueSize) }
            .onFailure { Log.w(TAG, "setCommandQueueSize failed: ${it.message}") }
    }

    /** SDK 가 현재 쓰는 전역 전송 간격(ms) */
    fun currentIntervalMs(): Long = LSBluetooth.getCommandIntervalMs()

    /** SDK 가 현재 쓰는 전역 큐 크기 */
    fun currentQueueSize(): Int = LSBluetooth.getCommandQueueSize()
}
