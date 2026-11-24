package com.efxcreator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.efxcreator.data.PreferencesManager
import com.lightstick.LSBluetooth

/**
 * 설정 > BLE 전송 설정 (디버그 빌드 전용 — [SettingsSection.BLE_DEBUG] 이 디버그에서만 보인다).
 *
 * SDK 의 GATT 명령 큐 튜닝 값을 바꾼다. 값은 기기 전체에 즉시 적용되고(연결된 기기 포함),
 * SDK 는 저장하지 않으므로 [PreferencesManager] 에 저장해 두었다가 앱 시작 때 다시 적용한다
 * ([com.efxcreator.EfxCreatorApplication]).
 *
 * - 전송 간격: 한 명령이 끝난 뒤 다음 명령까지의 최소 간격 (0~1000ms, 기본 50)
 * - 큐 크기: 기기별 대기 명령 최대 개수 (1~256, 기본 8). 초과하면 가장 오래된 대기 명령이 버려진다
 */
@Composable
fun BleDebugSettingsContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }

    var intervalText by remember { mutableStateOf(LSBluetooth.getCommandIntervalMs().toString()) }
    var queueText by remember { mutableStateOf(LSBluetooth.getCommandQueueSize().toString()) }
    var logEnabled by remember { mutableStateOf(prefs.bleDebugLogEnabled) }
    var status by remember {
        mutableStateOf("현재 적용 값: ${LSBluetooth.getCommandIntervalMs()} ms / 큐 ${LSBluetooth.getCommandQueueSize()}")
    }

    val interval = intervalText.toLongOrNull()
    val queue = queueText.toIntOrNull()
    val intervalOk = interval != null && interval in 0L..LSBluetooth.MAX_COMMAND_INTERVAL_MS
    val queueOk = queue != null && queue in 1..LSBluetooth.MAX_COMMAND_QUEUE_SIZE

    fun applyValues(intervalMs: Long, queueSize: Int) {
        LSBluetooth.setCommandIntervalMs(intervalMs)
        LSBluetooth.setCommandQueueSize(queueSize)
        prefs.bleCommandIntervalMs = intervalMs
        prefs.bleCommandQueueSize = queueSize
        // 실제로 SDK 에 적용된 값을 다시 읽어 보여 준다
        intervalText = LSBluetooth.getCommandIntervalMs().toString()
        queueText = LSBluetooth.getCommandQueueSize().toString()
        status = "적용됨: ${LSBluetooth.getCommandIntervalMs()} ms / 큐 ${LSBluetooth.getCommandQueueSize()}"
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "SDK 의 GATT 명령 큐 값입니다. 기기별 큐 하나를 FF02 이펙트뿐 아니라 읽기·CCCD·게임·OTA 등 " +
                "모든 GATT 작업이 공유하며, 연결된 기기에도 즉시 적용됩니다. 값은 이 앱에 저장되어 다음 실행 때 복원됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = intervalText,
            onValueChange = { intervalText = it.filter(Char::isDigit).take(4) },
            label = { Text("전송 간격 (ms)") },
            supportingText = { Text("0~${LSBluetooth.MAX_COMMAND_INTERVAL_MS}, 기본 ${LSBluetooth.DEFAULT_COMMAND_INTERVAL_MS}. 0 = 간격 없음") },
            isError = !intervalOk,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = queueText,
            onValueChange = { queueText = it.filter(Char::isDigit).take(3) },
            label = { Text("큐 크기 (대기 명령 수)") },
            supportingText = { Text("1~${LSBluetooth.MAX_COMMAND_QUEUE_SIZE}, 기본 ${LSBluetooth.DEFAULT_COMMAND_QUEUE_SIZE}. 초과 시 가장 오래된 대기 명령이 버려짐") },
            isError = !queueOk,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        if (interval != null && queue != null && intervalOk && queueOk) {
            Text(
                "예상 최대 지연 ≈ 큐 × 간격 = ${queue * interval} ms",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = intervalOk && queueOk,
                onClick = { applyValues(interval!!, queue!!) }
            ) { Text("적용") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = {
                    applyValues(LSBluetooth.DEFAULT_COMMAND_INTERVAL_MS, LSBluetooth.DEFAULT_COMMAND_QUEUE_SIZE)
                }
            ) { Text("기본값으로 초기화") }
        }

        Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("SDK 진단 로그 (태그 'Lightstick')", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = logEnabled,
                onCheckedChange = {
                    logEnabled = it
                    prefs.bleDebugLogEnabled = it
                    LSBluetooth.setDebugLoggingEnabled(it)
                }
            )
        }
    }
}
