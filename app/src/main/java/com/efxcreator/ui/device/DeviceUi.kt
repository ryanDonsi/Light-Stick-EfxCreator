package com.efxcreator.ui.device

import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efxcreator.viewmodel.BleViewModel
import com.lightstick.device.Device
import com.lightstick.types.GroupPalette
import kotlin.math.roundToInt

/**
 * 디바이스(응원봉) 연결/관리 UI — 메인 화면 상단, 에디터 상단, 설정의 "디바이스 관리" 가 함께 쓴다.
 * 관리 메뉴(디바이스 정보 / FIND / 그룹 배정 / OTA)는 Light-Stick-Music 의 디바이스 상세 화면을 가져왔다.
 */

/** 화면이 바뀌어도 연결 상태가 같도록 Activity 범위의 [BleViewModel] 을 쓴다 */
@Composable
fun activityBleViewModel(): BleViewModel {
    var ctx = LocalContext.current
    while (ctx is ContextWrapper && ctx !is ComponentActivity) ctx = ctx.baseContext
    return viewModel(viewModelStoreOwner = ctx as ComponentActivity)
}

/** 상단 바의 디바이스 버튼이 여는 팝업 */
enum class DevicePopup { NONE, CONNECT, MANAGE }

/** 연결돼 있으면 관리 팝업, 아니면 연결 팝업 */
fun devicePopupFor(connected: Device?): DevicePopup =
    if (connected != null) DevicePopup.MANAGE else DevicePopup.CONNECT

/** 상단 바용 디바이스 연결 정보 버튼: 연결 시 기기 이름과 배터리, 미연결 시 "디바이스 연결" */
@Composable
fun DeviceStatusButton(bleVm: BleViewModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val connected by bleVm.connected.collectAsState()
    val battery by bleVm.battery.collectAsState()
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        leadingIcon = {
            Icon(
                if (connected != null) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                contentDescription = null,
                tint = if (connected != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                connected?.let { d ->
                    (d.name ?: d.mac) + (battery?.let { " · $it%" } ?: "")
                } ?: "디바이스 연결"
            )
        }
    )
}

/** [popup] 에 맞는 팝업을 띄우고, 기기 관련 안내 메시지를 토스트로 보여 준다 */
@Composable
fun DevicePopups(bleVm: BleViewModel, popup: DevicePopup, onDismiss: () -> Unit) {
    DeviceMessageToasts(bleVm)
    when (popup) {
        DevicePopup.CONNECT -> DeviceConnectDialog(bleVm, onDismiss)
        DevicePopup.MANAGE -> DeviceManageDialog(bleVm, onDismiss)
        DevicePopup.NONE -> Unit
    }
}

@Composable
fun DeviceMessageToasts(bleVm: BleViewModel) {
    val context = LocalContext.current
    val message by bleVm.message.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            bleVm.consumeMessage()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 연결 팝업 (검색 + 연결)
// ─────────────────────────────────────────────────────────────

/** 응원봉을 검색해 목록에서 골라 연결한다. 연결되면 자동으로 닫힌다. */
@Composable
fun DeviceConnectDialog(bleVm: BleViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scanning by bleVm.scanning.collectAsState()
    val found by bleVm.found.collectAsState()
    val connected by bleVm.connected.collectAsState()
    val connectingMac by bleVm.connectingMac.collectAsState()
    val hasScanned by bleVm.hasScanned.collectAsState()
    var scanAfterPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            if (scanAfterPermission) bleVm.startScan()
        } else {
            Toast.makeText(context, "블루투스 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
        scanAfterPermission = false
    }

    fun requestScan() {
        if (bleVm.hasPermissions()) {
            bleVm.startScan()
        } else {
            scanAfterPermission = true
            permissionLauncher.launch(BleViewModel.requiredPermissions())
        }
    }

    // 연결이 끝나면 팝업을 닫는다
    LaunchedEffect(connected) { if (connected != null) onDismiss() }
    // 처음 열 때 바로 검색을 시작한다
    LaunchedEffect(Unit) { requestScan() }
    DisposableEffect(Unit) { onDispose { bleVm.stopScan() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("디바이스 연결") },
        text = {
            Column(modifier = Modifier.width(380.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { requestScan() }, enabled = !scanning) {
                        Text(if (scanning) "검색 중…" else "기기 검색")
                    }
                    if (scanning) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (hasScanned && !scanning && found.isEmpty()) {
                    Text(
                        "검색된 응원봉이 없습니다. 아래를 확인해 주세요.\n" +
                            "• 응원봉 전원이 켜져 있고 연결 대기(광고) 상태인지\n" +
                            "• 다른 앱(Light Stick Music 등)이나 다른 기기에 이미 연결돼 있지 않은지 — 연결된 응원봉은 검색되지 않습니다\n" +
                            "• 기기 이름이 LS / GL / RL 로 끝나는지\n" +
                            "• 블루투스가 켜져 있는지 (Android 11 이하는 위치도)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(found, key = { it.mac }) { device ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = connectingMac == null) { bleVm.connect(device) }
                                .padding(vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(device.name ?: "(이름 없음)", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    device.mac,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (connectingMac == device.mac) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                device.rssi?.let { Text("$it dBm", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

// ─────────────────────────────────────────────────────────────
// 관리 팝업 / 관리 메뉴
// ─────────────────────────────────────────────────────────────

/** 연결된 기기의 관리 팝업: 기기 정보 + 해제 + 관리 메뉴 */
@Composable
fun DeviceManageDialog(bleVm: BleViewModel, onDismiss: () -> Unit) {
    val connected by bleVm.connected.collectAsState()
    // 연결이 끊어지면 팝업을 닫는다
    LaunchedEffect(connected) { if (connected == null) onDismiss() }
    val device = connected ?: return

    var confirmDisconnect by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("디바이스 관리") },
        text = {
            Column(
                modifier = Modifier.width(420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DeviceInfoHeader(bleVm, device)
                Button(
                    onClick = { confirmDisconnect = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("연결 해제") }
                HorizontalDivider()
                DeviceManageMenu(bleVm)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )

    if (confirmDisconnect) {
        DisconnectConfirmDialog(
            deviceName = device.name ?: device.mac,
            onDismiss = { confirmDisconnect = false },
            onConfirm = {
                confirmDisconnect = false
                bleVm.disconnect()
            }
        )
    }
}

@Composable
fun DisconnectConfirmDialog(deviceName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text("$deviceName 기기 연결을 해제할까요?") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/** 기기 이름 · 배터리 · MAC · RSSI */
@Composable
fun DeviceInfoHeader(bleVm: BleViewModel, device: Device, modifier: Modifier = Modifier) {
    val battery by bleVm.battery.collectAsState()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                (device.name ?: "").ifEmpty { "Unknown Device" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            battery?.let {
                Text(
                    "$it%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        InfoLine("MAC", device.mac)
        device.rssi?.let { InfoLine("RSSI", "$it dBm") }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(34.dp))
        Text("|", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 관리 메뉴: 디바이스 정보 / FIND / 그룹 배정 / OTA (Light-Stick-Music 의 연결된 기기 화면과 같은 구성) */
@Composable
fun DeviceManageMenu(bleVm: BleViewModel, modifier: Modifier = Modifier) {
    val info by bleVm.info.collectAsState()
    val connected by bleVm.connected.collectAsState()
    val groupAllowed by bleVm.groupAllowed.collectAsState()
    val groupCount by bleVm.groupCount.collectAsState()
    val broadcasting by bleVm.broadcasting.collectAsState()
    val otaProgress by bleVm.otaProgress.collectAsState()
    val otaCheck by bleVm.otaCheck.collectAsState()

    var showInfo by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var showGroup by remember { mutableStateOf(false) }

    val firmwarePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) bleVm.checkFirmware(uri)
    }
    val device = connected ?: return
    val otaRunning = otaProgress != null

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MenuRow(
            label = "디바이스 정보",
            description = "기기의 정보를 확인",
            onClick = { bleVm.refreshInfo(); showInfo = true }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Broadcasting Mode", style = MaterialTheme.typography.titleSmall) },
            supportingContent = {
                Text("연결된 주변 응원봉에 신호를 전파해 함께 동작", style = MaterialTheme.typography.bodySmall)
            },
            trailingContent = { Switch(checked = broadcasting, onCheckedChange = { bleVm.setBroadcasting(it) }) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        HorizontalDivider()
        MenuRow(
            label = "FIND",
            description = "연결한 기기에 특정 이펙트를 전달하여 기기 찾기",
            onClick = { showFind = true }
        )
        HorizontalDivider()
        MenuRow(
            label = "그룹 배정",
            description = if (groupAllowed) "그룹 색상을 방송하여 응원봉을 그룹에 배정"
            else "BLE Only 모드에서는 지원하지 않는 기능입니다",
            enabled = groupAllowed,
            onClick = { showGroup = true }
        )
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("OTA", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (otaRunning) "펌웨어 업데이트 중 (${otaProgress}%)" else "펌웨어 업데이트를 진행",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (otaRunning) {
                    FilledTonalButton(
                        onClick = { bleVm.abortOta() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text("OTA 중지") }
                } else {
                    FilledTonalButton(onClick = { firmwarePicker.launch("*/*") }) { Text("업데이트") }
                }
            }
            otaProgress?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()) }
        }
    }

    if (showInfo) {
        DeviceInfoDialog(
            name = info?.deviceName ?: device.name ?: "-",
            model = info?.modelNumber ?: "-",
            firmware = info?.firmwareRevision ?: "-",
            manufacturer = info?.manufacturer ?: "-",
            onDismiss = { showInfo = false }
        )
    }
    if (showFind) {
        AlertDialog(
            onDismissRequest = { showFind = false },
            text = { Text("기기에 이펙트를 전송할까요?") },
            confirmButton = { TextButton(onClick = { showFind = false; bleVm.sendFind() }) { Text("확인") } },
            dismissButton = { TextButton(onClick = { showFind = false }) { Text("취소") } }
        )
    }
    if (showGroup) {
        GroupAssignDialog(
            groupCount = groupCount,
            onGroupCountChange = { bleVm.setGroupCount(it) },
            onDismiss = { showGroup = false },
            onConfirm = { bleVm.sendGroupSetting(it) }
        )
    }
    otaCheck?.let { check ->
        val name = device.name ?: device.mac
        if (check.fileVersion != null && !check.updateAvailable) {
            AlertDialog(
                onDismissRequest = { bleVm.clearOtaCheck() },
                text = { Text("$name(${check.deviceVersion})은\n최신 버전입니다.") },
                confirmButton = { TextButton(onClick = { bleVm.clearOtaCheck() }) { Text("확인") } }
            )
        } else {
            AlertDialog(
                onDismissRequest = { bleVm.clearOtaCheck() },
                text = {
                    Text(
                        "$name\n현재 버전: ${check.deviceVersion.ifEmpty { "-" }} → 새 버전: " +
                            "${check.fileVersion ?: "확인 불가"}\n업데이트를 진행하시겠습니까?"
                    )
                },
                confirmButton = { TextButton(onClick = { bleVm.startOta() }) { Text("확인") } },
                dismissButton = { TextButton(onClick = { bleVm.clearOtaCheck() }) { Text("취소") } }
            )
        }
    }
}

@Composable
private fun MenuRow(label: String, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(description, style = MaterialTheme.typography.bodySmall) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (enabled) Modifier else Modifier.background(Color.Transparent))
    )
}

@Composable
fun DeviceInfoDialog(name: String, model: String, firmware: String, manufacturer: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("디바이스 정보") },
        text = {
            Column(modifier = Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("기기명" to name, "모델명" to model, "펌웨어 버전" to firmware, "제조사" to manufacturer)
                    .forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } }
    )
}

/**
 * 그룹 배정 방송 다이얼로그 (Light-Stick-Music 에서 가져옴).
 * 전체 그룹 수(1~20)를 정하고 그룹을 탭하면 그 그룹 색으로 BLINK 방송을 보낸다.
 * 방송을 받는 동안 응원봉에서 지정 버튼을 누르면 그 응원봉이 선택한 그룹으로 저장된다.
 */
@Composable
fun GroupAssignDialog(
    groupCount: Int,
    onGroupCountChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (groupId: Int) -> Unit
) {
    var lastSent by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("그룹 배정") },
        text = {
            Column(
                modifier = Modifier.width(380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "그룹을 탭하면 즉시 전송됩니다. 방송을 받는 동안\n버튼을 누른 응원봉이 해당 그룹으로 저장됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text("전체 그룹 수 (${groupCount}개)", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = groupCount.toFloat(),
                        onValueChange = { onGroupCountChange(it.roundToInt()) },
                        valueRange = GroupPalette.MIN_GROUP_ID.toFloat()..GroupPalette.MAX_GROUP_ID.toFloat(),
                        steps = GroupPalette.MAX_GROUP_ID - GroupPalette.MIN_GROUP_ID - 1
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..groupCount).chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { id ->
                                GroupSwatch(
                                    groupId = id,
                                    selected = id == lastSent,
                                    onClick = { lastSent = id; onConfirm(id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

@Composable
private fun GroupSwatch(groupId: Int, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = GroupPalette.colorFor(groupId)
    val color = Color(c.r, c.g, c.b)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(MaterialTheme.shapes.small)
            .background(color)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, MaterialTheme.shapes.small)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            groupId.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (color.luminance() > 0.5f) Color.Black else Color.White
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 설정 > 디바이스 관리
// ─────────────────────────────────────────────────────────────

/**
 * 설정의 "디바이스 관리" 내용.
 * 위: 연결 상태 + 검색 및 연결 / 연결 해제 버튼 (미연결이면 연결 팝업으로 검색·연결).
 * 아래: 연결돼 있을 때만 관리 메뉴 일체.
 */
@Composable
fun DeviceSettingsContent(bleVm: BleViewModel, modifier: Modifier = Modifier) {
    val connected by bleVm.connected.collectAsState()
    var popup by remember { mutableStateOf(DevicePopup.NONE) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var confirmReconnect by remember { mutableStateOf(false) }

    DevicePopups(bleVm, if (popup == DevicePopup.MANAGE) DevicePopup.NONE else popup) { popup = DevicePopup.NONE }

    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val device = connected
                if (device != null) {
                    DeviceInfoHeader(bleVm, device)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.BluetoothDisabled, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column {
                            Text("연결된 디바이스 없음", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "검색해서 응원봉을 연결하면 관리 메뉴가 표시됩니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { if (connected != null) confirmReconnect = true else popup = DevicePopup.CONNECT },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (connected != null) "다른 디바이스 검색 및 연결" else "디바이스 검색 및 연결")
                    }
                    OutlinedButton(
                        onClick = { confirmDisconnect = true },
                        enabled = connected != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("연결 해제") }
                }
            }
        }

        if (connected != null) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    DeviceManageMenu(bleVm)
                }
            }
        }
    }

    if (confirmDisconnect) {
        DisconnectConfirmDialog(
            deviceName = connected?.let { it.name ?: it.mac } ?: "",
            onDismiss = { confirmDisconnect = false },
            onConfirm = {
                confirmDisconnect = false
                bleVm.disconnect()
            }
        )
    }
    if (confirmReconnect) {
        AlertDialog(
            onDismissRequest = { confirmReconnect = false },
            title = { Text("연결") },
            text = {
                Text(
                    "현재 ${connected?.let { it.name ?: it.mac } ?: ""} 기기가 연결되어 있습니다.\n" +
                        "이 기기의 연결을 해제 후\n새로운 기기를 연결하시겠습니까?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReconnect = false
                    bleVm.disconnect()
                    popup = DevicePopup.CONNECT
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { confirmReconnect = false }) { Text("취소") } }
        )
    }
}
