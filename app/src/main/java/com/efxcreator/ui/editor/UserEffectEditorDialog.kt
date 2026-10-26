package com.efxcreator.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.efxcreator.model.UserEffect
import com.efxcreator.music.sets.RgbColor
import com.efxcreator.ui.sets.RgbPicker
import com.efxcreator.ui.sets.SetDropdown
import com.lightstick.types.EffectType
import kotlin.math.roundToInt

private val TYPE_ORDER = listOf(EffectType.ON, EffectType.OFF, EffectType.BLINK, EffectType.BREATH, EffectType.STROBE)

private fun defaultPeriod(type: EffectType) = when (type) {
    EffectType.BLINK -> 50
    EffectType.BREATH -> 200
    EffectType.STROBE -> 8
    else -> 0
}

/**
 * "내 라이브러리" 한 칸을 추가/수정하는 다이얼로그.
 * 기본 프리셋을 불러와 고치거나 처음부터 직접 만들 수 있다. [onDelete] 가 있으면 삭제 버튼이 표시된다.
 */
@Composable
fun UserEffectEditorDialog(
    slot: Int,
    initial: UserEffect?,
    newId: () -> String,
    onSave: (UserEffect) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var type by remember {
        mutableStateOf(initial?.let { runCatching { EffectType.valueOf(it.effectType) }.getOrNull() } ?: EffectType.ON)
    }
    var fg by remember { mutableStateOf(initial?.fg ?: RgbColor(255, 255, 255)) }
    var bg by remember { mutableStateOf(initial?.bg ?: RgbColor(0, 0, 0)) }
    var period by remember { mutableIntStateOf(initial?.period ?: 0) }
    var spf by remember { mutableIntStateOf(initial?.spf ?: 100) }
    var fade by remember { mutableIntStateOf(initial?.fade ?: 100) }
    var pickedPreset by remember { mutableStateOf("") }

    val twoColor = type == EffectType.BLINK || type == EffectType.BREATH || type == EffectType.STROBE
    val periodLabel = TimelineMath.periodLabel(type)   // ON/OFF = Transit, 그 외 = Period

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "내 라이브러리 ${slot + 1}번 칸에 추가" else "내 라이브러리 ${slot + 1}번 칸 수정") },
        text = {
            Column(
                modifier = Modifier
                    .width(480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SetDropdown(
                    label = "기본 프리셋에서 가져오기",
                    autoLabel = "직접 입력",
                    items = EffectPresets.all,
                    selectedId = pickedPreset,
                    idOf = { it.id },
                    nameOf = { "${it.category.label} · ${it.label}" },
                    onSelect = { id ->
                        pickedPreset = id
                        EffectPresets.all.firstOrNull { it.id == id }?.let { p ->
                            type = p.type
                            fg = RgbColor(p.color.r, p.color.g, p.color.b)
                            bg = RgbColor(p.backgroundColor.r, p.backgroundColor.g, p.backgroundColor.b)
                            period = p.period; spf = p.spf; fade = p.fade
                            if (label.isBlank()) label = p.label
                        }
                    }
                )

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("이름 (비우면 자동)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("종류", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TYPE_ORDER.forEach { t ->
                        FilterChip(
                            selected = t == type,
                            onClick = {
                                type = t
                                period = defaultPeriod(t)
                            },
                            label = { Text(t.name) }
                        )
                    }
                }

                if (type != EffectType.OFF) {
                    RgbPicker("FG(전경) 색", fg, { fg = it })
                }
                if (twoColor) {
                    RgbPicker("BG(배경) 색", bg, { bg = it })
                }

                ParamSlider("$periodLabel (${period * 10}ms)", period) { period = it }
                ParamSlider("SPF ($spf)", spf) { spf = it }
                ParamSlider("Fade ($fade)", fade) { fade = it }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    PresetSwatch(
                        EffectPreset(
                            id = "preview", label = "", category = EffectPreset.Category.USER, type = type,
                            color = fg.toLs(), backgroundColor = bg.toLs(), period = period, spf = spf, fade = fade
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("미리보기", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val finalLabel = label.trim().ifEmpty { "${type.name} #%02X%02X%02X".format(fg.r, fg.g, fg.b) }
                onSave(
                    UserEffect(
                        id = initial?.id ?: newId(), slot = slot, label = finalLabel,
                        effectType = type.name, fg = fg, bg = if (twoColor) bg else RgbColor(0, 0, 0),
                        period = period, spf = spf, fade = fade
                    )
                )
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        }
    )
}

@Composable
private fun ParamSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.height(30.dp)
        )
    }
}
