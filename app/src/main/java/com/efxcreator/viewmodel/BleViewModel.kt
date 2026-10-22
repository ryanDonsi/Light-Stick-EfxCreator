package com.efxcreator.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lightstick.LSBluetooth
import com.lightstick.device.Device
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 응원봉 실기기 연동 — 스캔/연결과 편집 중인 타임라인의 재생 동기화(SDK Timeline API).
 *
 * 동기화 방식: [loadTimeline] 으로 프레임을 올린 뒤, 음악 재생 위치를 [updatePosition] 으로
 * 주기적(권장 100ms)으로 전달하면 SDK 가 해당 시각에 맞춰 전송한다.
 */
@SuppressLint("MissingPermission") // 모든 호출 전에 hasPermissions() 로 확인한다
class BleViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _found = MutableStateFlow<List<Device>>(emptyList())
    val found: StateFlow<List<Device>> = _found.asStateFlow()

    /** 현재 연결된 기기 (없으면 null) */
    private val _connected = MutableStateFlow<Device?>(null)
    val connected: StateFlow<Device?> = _connected.asStateFlow()

    private val _connectingMac = MutableStateFlow<String?>(null)
    val connectingMac: StateFlow<String?> = _connectingMac.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var scanJob: Job? = null

    companion object {
        private const val TAG = "BleViewModel"
        private const val SCAN_SECONDS = 10

        /** 현재 OS 버전에서 요청해야 하는 BLE 런타임 권한 */
        fun requiredPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }

    init {
        if (hasPermissions()) {
            runCatching { LSBluetooth.connectedDevices().firstOrNull() }
                .getOrNull()?.let { _connected.value = it }
        }
    }

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun startScan() {
        if (!hasPermissions()) {
            _message.value = "블루투스 권한이 필요합니다."
            return
        }
        _found.value = emptyList()
        _scanning.value = true
        try {
            LSBluetooth.startScan(SCAN_SECONDS) { device ->
                if (_found.value.none { it.mac == device.mac }) {
                    _found.value = _found.value + device
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startScan failed", e)
            _scanning.value = false
            _message.value = "스캔을 시작할 수 없습니다: ${e.message}"
            return
        }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            delay(SCAN_SECONDS * 1000L)
            _scanning.value = false
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        if (_scanning.value && hasPermissions()) runCatching { LSBluetooth.stopScan() }
        _scanning.value = false
    }

    fun connect(device: Device) {
        if (!hasPermissions()) {
            _message.value = "블루투스 권한이 필요합니다."
            return
        }
        stopScan()
        _connectingMac.value = device.mac
        device.connect(
            onConnected = {
                _connectingMac.value = null
                _connected.value = device
                _message.value = "${device.name ?: device.mac} 연결됨"
            },
            onFailed = { e ->
                _connectingMac.value = null
                _message.value = "연결 실패: ${e.message}"
            }
        )
    }

    fun disconnect() {
        val device = _connected.value ?: return
        if (hasPermissions()) runCatching { device.stopTimeline(); device.disconnect() }
        _connected.value = null
    }

    // ── 타임라인 동기화 ──────────────────────────────────────────

    fun loadTimeline(frames: List<Pair<Long, ByteArray>>) {
        val device = connectedOrNull() ?: return
        if (!device.playTimeline(frames)) Log.w(TAG, "playTimeline rejected")
    }

    fun updatePosition(positionMs: Long) {
        connectedOrNull()?.updatePlaybackPosition(positionMs)
    }

    fun pauseTimeline() {
        connectedOrNull()?.pauseTimeline()
    }

    fun resumeTimeline() {
        connectedOrNull()?.resumeTimeline()
    }

    fun stopTimeline() {
        connectedOrNull()?.stopTimeline()
    }

    private fun connectedOrNull(): Device? {
        val device = _connected.value ?: return null
        if (!hasPermissions()) return null
        if (!device.isConnected()) {
            // 연결이 끊겼다
            _connected.value = null
            _message.value = "${device.name ?: device.mac} 연결이 끊어졌습니다."
            return null
        }
        return device
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
