package com.efxcreator.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lightstick.LSBluetooth
import android.net.Uri
import com.efxcreator.core.FirmwareVersionParser
import com.efxcreator.data.PreferencesManager
import com.lightstick.device.Device
import com.lightstick.device.DeviceInfo
import com.lightstick.device.DeviceMode
import com.lightstick.types.Color as LsColor
import com.lightstick.types.Colors
import com.lightstick.types.EffectType
import com.lightstick.types.LSEffectPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.efxcreator.R
import com.efxcreator.core.Loc

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

    /** 한 번이라도 스캔을 시작했는가 (결과 0개 안내를 "검색 전"과 구분하기 위함) */
    private val _hasScanned = MutableStateFlow(false)
    val hasScanned: StateFlow<Boolean> = _hasScanned.asStateFlow()

    private val _found = MutableStateFlow<List<Device>>(emptyList())
    val found: StateFlow<List<Device>> = _found.asStateFlow()

    /** 현재 연결된 기기 (없으면 null) */
    private val _connected = MutableStateFlow<Device?>(null)
    val connected: StateFlow<Device?> = _connected.asStateFlow()

    private val _connectingMac = MutableStateFlow<String?>(null)
    val connectingMac: StateFlow<String?> = _connectingMac.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ── 기기 관리 상태 (디바이스 관리 화면/팝업에서 사용) ──────────────

    private val prefs = PreferencesManager(application)

    private val _info = MutableStateFlow<DeviceInfo?>(null)
    /** 연결된 기기의 DIS 정보 (모델/펌웨어/제조사) */
    val info: StateFlow<DeviceInfo?> = _info.asStateFlow()

    private val _battery = MutableStateFlow<Int?>(null)
    val battery: StateFlow<Int?> = _battery.asStateFlow()

    private val _broadcasting = MutableStateFlow(true)
    /** Broadcasting Mode: 연결된 기기가 보내는 이펙트 신호를 주변 응원봉에도 전파할지 (기기별 저장) */
    val broadcasting: StateFlow<Boolean> = _broadcasting.asStateFlow()

    private val _groupAllowed = MutableStateFlow(true)
    /** BLE Only 모드 기기는 그룹 배정을 지원하지 않는다 */
    val groupAllowed: StateFlow<Boolean> = _groupAllowed.asStateFlow()

    private val _groupCount = MutableStateFlow(prefs.groupCount)
    val groupCount: StateFlow<Int> = _groupCount.asStateFlow()

    private val _otaProgress = MutableStateFlow<Int?>(null)
    /** OTA 진행률(0~100). 진행 중이 아니면 null */
    val otaProgress: StateFlow<Int?> = _otaProgress.asStateFlow()

    /** 펌웨어 파일 확인 결과: [fileVersion] 이 null 이면 파일에서 버전을 읽지 못한 것 */
    data class OtaCheck(val fileVersion: String?, val deviceVersion: String, val updateAvailable: Boolean)

    private val _otaCheck = MutableStateFlow<OtaCheck?>(null)
    val otaCheck: StateFlow<OtaCheck?> = _otaCheck.asStateFlow()
    private var pendingFirmware: ByteArray? = null

    private var scanJob: Job? = null
    private var monitorJob: Job? = null

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
                .getOrNull()?.let {
                    _connected.value = it
                    startMonitoring(it)
                }
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
            _message.value = Loc.str(R.string.device_ui_2)
            return
        }
        // 시스템 설정 때문에 스캔이 조용히 실패하는 경우를 먼저 알려 준다
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (btManager?.adapter?.isEnabled != true) {
            _message.value = Loc.str(R.string.ble_view_model_1)
            return
        }
        // Android 11 이하는 위치 서비스가 꺼져 있으면 BLE 스캔 결과가 오지 않는다
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val locationOn = lm != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || lm.isLocationEnabled)
            if (!locationOn) {
                _message.value = Loc.str(R.string.ble_view_model_2)
                return
            }
        }

        _found.value = emptyList()
        _hasScanned.value = true
        _scanning.value = true
        Log.d(TAG, "startScan: ${SCAN_SECONDS}s (name filter LS/GL/RL)")
        try {
            LSBluetooth.startScan(SCAN_SECONDS) { device ->
                if (_found.value.none { it.mac == device.mac }) {
                    _found.value = _found.value + device
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startScan failed", e)
            _scanning.value = false
            _message.value = Loc.str(R.string.ble_view_model_3, e.message)
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
            _message.value = Loc.str(R.string.device_ui_2)
            return
        }
        stopScan()
        _connectingMac.value = device.mac
        device.connect(
            onConnected = {
                _connectingMac.value = null
                _connected.value = device
                startMonitoring(device)
                _message.value = Loc.str(R.string.ble_view_model_4, device.name ?: device.mac)
            },
            onFailed = { e ->
                _connectingMac.value = null
                _message.value = Loc.str(R.string.ble_view_model_5, e.message)
            }
        )
    }

    fun disconnect() {
        val device = _connected.value ?: return
        if (hasPermissions()) runCatching { device.stopTimeline(); device.disconnect() }
        clearConnectionState()
    }

    private fun clearConnectionState() {
        monitorJob?.cancel()
        _connected.value = null
        _info.value = null
        _battery.value = null
        _groupAllowed.value = true
        _broadcasting.value = true
        _otaProgress.value = null
        _otaCheck.value = null
        pendingFirmware = null
    }

    // ── 기기 정보 / 배터리 / 연결 상태 감시 ───────────────────────

    /** 연결 직후 정보를 읽고, 이후 5초마다 연결을 확인하며 30초마다 배터리를 다시 읽는다 */
    private fun startMonitoring(device: Device) {
        monitorJob?.cancel()
        _broadcasting.value = prefs.getBroadcasting(device.mac)
        monitorJob = viewModelScope.launch {
            refreshInfo()
            readMode(device)
            readBattery(device)
            var elapsed = 0L
            while (true) {
                delay(5_000L)
                elapsed += 5_000L
                if (!hasPermissions() || !device.isConnected()) {
                    if (_connected.value?.mac == device.mac) {
                        _message.value = Loc.str(R.string.ble_view_model_6, device.name ?: device.mac)
                        clearConnectionState()
                    }
                    break
                }
                if (elapsed % 30_000L == 0L) readBattery(device)
            }
        }
    }

    fun refreshInfo() {
        val device = _connected.value ?: return
        if (!hasPermissions()) return
        runCatching { device.fetchDeviceInfo { _info.value = it } }
    }

    private fun readBattery(device: Device) {
        if (!runCatching { device.supportsBattery() }.getOrDefault(false)) return
        runCatching { device.readBattery { r -> r.onSuccess { _battery.value = it } } }
    }

    private fun readMode(device: Device) {
        if (!runCatching { device.supportsDeviceMode() }.getOrDefault(false)) {
            _groupAllowed.value = true   // 구형 펌웨어는 중계기로 간주
            return
        }
        runCatching {
            device.readDeviceMode { r -> r.onSuccess { _groupAllowed.value = it != DeviceMode.BLE } }
        }
    }

    // ── 기기 관리: FIND / 그룹 배정 ──────────────────────────────

    /** 연결한 기기를 찾을 수 있게 3초간 흰색을 빠르게 깜빡인 뒤 켜 둔다 */
    fun sendFind() {
        val device = connectedOrNull() ?: return
        viewModelScope.launch {
            val white = Colors.WHITE
            val blink = LSEffectPayload(
                effectType = EffectType.BLINK, color = white, backgroundColor = LsColor(0, 0, 0),
                period = 3, spf = 100, fade = 100, randomColor = 1, randomDelay = 0,
                broadcasting = if (_broadcasting.value) 1 else 0, effectIndex = 0
            )
            device.sendEffect(blink)
            delay(3_000L)
            if (_connected.value?.mac == device.mac && hasPermissions()) {
                device.sendEffect(blink.copy(effectType = EffectType.ON, period = 0, randomColor = 0))
            }
        }
    }

    // ── 색상 실시간 미리보기 (팔레트 편집 등) ─────────────────────

    private var previewPending: Triple<Int, Int, Int>? = null
    private var previewJob: Job? = null

    private fun solidPayload(type: EffectType, r: Int, g: Int, b: Int) = LSEffectPayload(
        effectType = type, color = LsColor(r, g, b), backgroundColor = LsColor(0, 0, 0),
        period = 0, spf = 100, fade = 100, randomColor = 0, randomDelay = 0,
        broadcasting = if (_broadcasting.value) 1 else 0, effectIndex = 0
    )

    /**
     * 연결된 기기에 색을 바로 켠다. 슬라이더를 끌 때처럼 값이 빠르게 바뀌어도 기기가 밀리지 않도록
     * 약 80ms 간격으로 가장 최근 값만 보낸다. 연결돼 있지 않으면 아무것도 하지 않는다.
     */
    fun previewColor(r: Int, g: Int, b: Int) {
        if (connectedOrNull() == null) return
        previewPending = Triple(r, g, b)
        if (previewJob?.isActive == true) return
        previewJob = viewModelScope.launch {
            while (true) {
                val c = previewPending ?: break
                previewPending = null
                val device = connectedOrNull() ?: break
                device.sendEffect(solidPayload(EffectType.ON, c.first, c.second, c.third))
                delay(80L)
            }
        }
    }

    /** 미리보기를 끝내고 기기를 끈다 */
    fun stopPreview() {
        previewPending = null
        previewJob?.cancel()
        val device = connectedOrNull() ?: return
        device.sendEffect(solidPayload(EffectType.OFF, 0, 0, 0))
    }

    // ── 패턴(이펙트 라이브러리) 디바이스 테스트 ────────────────────

    private val _patternTesting = MutableStateFlow(false)
    val patternTesting: StateFlow<Boolean> = _patternTesting.asStateFlow()
    private var patternJob: Job? = null

    /**
     * 패턴의 단계를 순서대로 기기에 보낸다. [steps] = (보낼 이펙트, 그 이펙트를 유지할 시간 ms).
     * [loop] 면 [stopPatternTest] 를 부를 때까지 반복하고, 아니면 한 번 재생한 뒤 기기를 끈다.
     */
    fun startPatternTest(steps: List<Pair<LSEffectPayload, Long>>, loop: Boolean) {
        val device = connectedOrNull()
        if (device == null) {
            _message.value = Loc.str(R.string.ble_view_model_7)
            return
        }
        if (steps.isEmpty()) return
        patternJob?.cancel()
        previewJob?.cancel()
        patternJob = viewModelScope.launch {
            _patternTesting.value = true
            try {
                do {
                    for ((payload, ms) in steps) {
                        if (connectedOrNull() == null) return@launch
                        device.sendEffect(payload.copy(broadcasting = if (_broadcasting.value) 1 else 0))
                        delay(ms.coerceAtLeast(10L))
                    }
                } while (loop)
                device.sendEffect(solidPayload(EffectType.OFF, 0, 0, 0))
            } finally {
                _patternTesting.value = false
            }
        }
    }

    fun stopPatternTest() {
        patternJob?.cancel()
        patternJob = null
        _patternTesting.value = false
        connectedOrNull()?.sendEffect(solidPayload(EffectType.OFF, 0, 0, 0))
    }

    fun setBroadcasting(enabled: Boolean) {
        val device = _connected.value ?: return
        prefs.setBroadcasting(device.mac, enabled)
        _broadcasting.value = enabled
    }

    fun setGroupCount(count: Int) {
        prefs.groupCount = count
        _groupCount.value = prefs.groupCount
    }

    /** 그룹 배정 방송: 연결 기기가 해당 그룹 색으로 BLINK 방송을 시작한다 */
    fun sendGroupSetting(groupId: Int) {
        val device = connectedOrNull() ?: return
        if (!_groupAllowed.value) {
            _message.value = Loc.str(R.string.ble_view_model_8)
            return
        }
        if (!device.sendGroupSetting(groupId)) _message.value = Loc.str(R.string.ble_view_model_9)
    }

    // ── 기기 관리: OTA ───────────────────────────────────────────

    /** 펌웨어 파일을 읽어 현재 버전과 비교한 결과를 [otaCheck] 에 올린다 */
    fun checkFirmware(uri: Uri) {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null) {
                _message.value = Loc.str(R.string.ble_view_model_10)
                return@launch
            }
            val deviceVersion = _info.value?.firmwareRevision.orEmpty().trim()
            val fileVersion = FirmwareVersionParser.parseFromBytes(bytes)
            pendingFirmware = bytes
            _otaCheck.value = OtaCheck(
                fileVersion = fileVersion,
                deviceVersion = deviceVersion,
                updateAvailable = fileVersion == null || FirmwareVersionParser.isNewerVersion(fileVersion, deviceVersion)
            )
        }
    }

    fun clearOtaCheck() {
        _otaCheck.value = null
    }

    fun startOta() {
        val device = connectedOrNull() ?: return
        val firmware = pendingFirmware ?: return
        pendingFirmware = null
        _otaCheck.value = null
        _otaProgress.value = 0
        val ok = device.startOta(
            firmware = firmware,
            onProgress = { _otaProgress.value = it },
            onResult = { r ->
                _otaProgress.value = null
                _message.value = if (r.isSuccess) Loc.str(R.string.ble_view_model_11)
                else Loc.str(R.string.ble_view_model_12, r.exceptionOrNull()?.message)
                refreshInfo()
            }
        )
        if (!ok) {
            _otaProgress.value = null
            _message.value = Loc.str(R.string.ble_view_model_13)
        }
    }

    fun abortOta() {
        _connected.value?.let { runCatching { it.abortOta() } }
        _otaProgress.value = null
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
            clearConnectionState()
            _message.value = Loc.str(R.string.ble_view_model_6, device.name ?: device.mac)
            return null
        }
        return device
    }

    override fun onCleared() {
        stopScan()
        super.onCleared()
    }
}
