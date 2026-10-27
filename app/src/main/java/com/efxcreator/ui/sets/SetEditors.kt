package com.efxcreator.ui.sets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.efxcreator.ui.components.HsvColorPicker
import com.efxcreator.ui.device.activityBleViewModel
import com.efxcreator.music.sets.EffectRuleSet
import com.efxcreator.music.sets.PaletteSet
import com.efxcreator.music.sets.RgbColor
import com.efxcreator.music.sets.SetCatalog
import kotlin.math.roundToInt

/** 색 빠른 선택용 (Light-Stick-Music 팔레트 색 계열) */
private val QUICK_COLORS = listOf(
    RgbColor(255, 0, 0), RgbColor(255, 117, 0), RgbColor(255, 249, 0), RgbColor(0, 255, 36),
    RgbColor(0, 255, 255), RgbColor(0, 0, 255), RgbColor(164, 0, 255), RgbColor(255, 0, 155),
    RgbColor(255, 255, 255)
)

/**
 * 팔레트 세트 추가/수정. [initial] 의 id 로 저장되므로 새 세트는 새 id 를 가진 값을 넘긴다.
 */
@Composable
fun PaletteSetEditorDialog(
    initial: PaletteSet,
    isNew: Boolean,
    onSave: (PaletteSet) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    val colors = remember { initial.colors.toMutableStateList() }
    var selectedSlot by remember { mutableIntStateOf(0) }
    val current = colors[selectedSlot]

    // 연결된 디바이스에 편집 중인 색을 실시간으로 표시한다 (닫을 때 끈다)
    val bleVm = activityBleViewModel()
    val deviceConnected by bleVm.connected.collectAsState()
    LaunchedEffect(current, deviceConnected) { bleVm.previewColor(current.r, current.g, current.b) }
    DisposableEffect(Unit) { onDispose { bleVm.stopPreview() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "팔레트 세트 추가" else "팔레트 세트 수정") },
        text = {
            Column(
                modifier = Modifier
                    .width(460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("색 슬롯 (눌러서 선택)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colors.forEachIndexed { i, c ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(c.toComposeColor(), MaterialTheme.shapes.large)
                                    .border(
                                        if (i == selectedSlot) 3.dp else 1.dp,
                                        if (i == selectedSlot) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        MaterialTheme.shapes.large
                                    )
                                    .clickable { selectedSlot = i }
                            )
                            Text(PaletteSet.COLOR_ROLES[i], style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Text(
                    "${PaletteSet.COLOR_ROLES[selectedSlot]}: RGB(${current.r}, ${current.g}, ${current.b})",
                    style = MaterialTheme.typography.labelMedium
                )
                HsvColorPicker(color = current, onChange = { colors[selectedSlot] = it })
                Text(
                    if (deviceConnected) "● 디바이스에 색상이 실시간으로 표시됩니다."
                    else "디바이스를 연결하면 색상이 실시간으로 표시됩니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (deviceConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("빠른 선택", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_COLORS.forEach { q ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(q.toComposeColor(), MaterialTheme.shapes.extraSmall)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.extraSmall)
                                .clickable { colors[selectedSlot] = q }
                        )
                    }
                }
                Text(
                    "대표색은 곡 전체의 기준색이고, 단계색 3개는 마디·섹션이 바뀔 때 번갈아 쓰입니다. " +
                        "어두운 톤은 대표색에서 자동으로 만들어집니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            main = colors[0], step1 = colors[1], step2 = colors[2], step3 = colors[3],
                            builtIn = false
                        )
                    )
                }
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun ChannelSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(20.dp), style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f)
        )
        Text(value.toString(), modifier = Modifier.width(36.dp), style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * 이펙트 세트(섹션별 규칙) 추가/수정. 규칙을 지정하지 않은 섹션("자동")은 기본 동작을 따른다.
 */
@Composable
fun EffectSetEditorDialog(
    initial: EffectRuleSet,
    isNew: Boolean,
    onSave: (EffectRuleSet) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    val rules = remember { mutableStateMapOf<String, String>().apply { putAll(initial.rules) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "이펙트 세트 추가" else "이펙트 세트 수정") },
        text = {
            Column(
                modifier = Modifier
                    .width(460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "섹션마다 사용할 패턴을 고르세요. 곡의 비트·섹션 분석 결과 위에 이 규칙이 적용됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SetCatalog.EDITABLE_SECTIONS.forEach { (key, label) ->
                    PatternRow(
                        sectionLabel = label,
                        patternName = rules[key],
                        onSelect = { selected ->
                            if (selected == null) rules.remove(key) else rules[key] = selected
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(initial.copy(name = name.trim(), rules = rules.toMap(), builtIn = false)) }
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun PatternRow(sectionLabel: String, patternName: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val description = SetCatalog.PATTERNS.firstOrNull { it.engine.name == patternName }?.description
        ?: "기본 규칙을 따릅니다."

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(sectionLabel, modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
        Column(modifier = Modifier.weight(1f)) {
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(SetCatalog.patternLabel(patternName), modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("자동 (기본)") }, onClick = {
                        onSelect(null)
                        expanded = false
                    })
                    SetCatalog.PATTERNS.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.label} — ${p.description}") },
                            onClick = {
                                onSelect(p.engine.name)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
