package com.efxcreator.ui.sets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.efxcreator.music.sets.EffectRuleSet
import com.efxcreator.music.sets.PaletteSet
import com.efxcreator.music.sets.RgbColor
import com.efxcreator.music.sets.SetCatalog
import kotlin.math.roundToInt

fun RgbColor.toComposeColor(): Color = Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

/** 팔레트 4색을 나란히 보여주는 띠 */
@Composable
fun PaletteStrip(colors: List<RgbColor>, modifier: Modifier = Modifier, swatchSize: Int = 28) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        colors.forEach { c ->
            Box(
                modifier = Modifier
                    .size(swatchSize.dp)
                    .background(c.toComposeColor(), MaterialTheme.shapes.extraSmall)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.extraSmall)
            )
        }
    }
}

/** 섹션별 규칙 한 줄 요약 */
fun EffectRuleSet.summary(): String =
    SetCatalog.EDITABLE_SECTIONS.joinToString("  ·  ") { (key, label) ->
        "$label ${SetCatalog.patternLabel(rules[key])}"
    }

/**
 * 세트 선택 드롭다운. [selectedId] 가 빈 문자열이면 [autoLabel] (자동) 이 선택된 것이다.
 */
@Composable
fun <T> SetDropdown(
    label: String,
    autoLabel: String,
    items: List<T>,
    selectedId: String,
    idOf: (T) -> String,
    nameOf: (T) -> String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentName = items.firstOrNull { idOf(it) == selectedId }?.let(nameOf) ?: autoLabel

    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(currentName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(autoLabel) },
                    onClick = {
                        onSelect("")
                        expanded = false
                    }
                )
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(nameOf(item)) },
                        onClick = {
                            onSelect(idOf(item))
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * 이펙트 세트 + 팔레트 세트를 고르는 공통 다이얼로그.
 * 자동 생성 전 선택과, 편집 화면의 "세트 적용" 에서 함께 쓴다. 추가 옵션은 [extraContent] 로 넣는다.
 */
@Composable
fun SetSelectionDialog(
    title: String,
    confirmLabel: String,
    effectSets: List<EffectRuleSet>,
    paletteSets: List<PaletteSet>,
    initialEffectId: String,
    initialPaletteId: String,
    onConfirm: (effectSetId: String, paletteSetId: String) -> Unit,
    onManageSets: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    var effectId by remember { mutableStateOf(initialEffectId) }
    var paletteId by remember { mutableStateOf(initialPaletteId) }
    val selectedEffect = effectSets.firstOrNull { it.id == effectId }
    val selectedPalette = paletteSets.firstOrNull { it.id == paletteId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .width(440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
                    SetDropdown(
                        label = "이펙트 세트",
                        autoLabel = "자동 (기본 규칙)",
                        items = effectSets,
                        selectedId = effectId,
                        idOf = { it.id },
                        nameOf = { it.name },
                        onSelect = { effectId = it }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        selectedEffect?.summary() ?: "곡의 섹션·에너지에 따라 기본 규칙으로 패턴을 고릅니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column {
                    SetDropdown(
                        label = "팔레트 세트",
                        autoLabel = "자동 (장르·곡 기반)",
                        items = paletteSets,
                        selectedId = paletteId,
                        idOf = { it.id },
                        nameOf = { it.name },
                        onSelect = { paletteId = it }
                    )
                    Spacer(Modifier.height(6.dp))
                    if (selectedPalette != null) {
                        PaletteStrip(selectedPalette.colors)
                    } else {
                        Text(
                            "장르에 어울리는 색 계열을 곡마다 자동으로 고릅니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                extraContent()
                TextButton(onClick = onManageSets) { Text("세트 관리…") }
            }
        },
        confirmButton = {
            TextButton(enabled = confirmEnabled, onClick = { onConfirm(effectId, paletteId) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

private val PICKER_QUICK_COLORS = listOf(
    RgbColor(255, 255, 255), RgbColor(255, 0, 0), RgbColor(255, 117, 0), RgbColor(255, 249, 0),
    RgbColor(0, 255, 36), RgbColor(0, 255, 255), RgbColor(0, 0, 255), RgbColor(164, 0, 255),
    RgbColor(255, 0, 155), RgbColor(0, 0, 0)
)

/** RGB 슬라이더 3개 + 빠른 색 선택으로 색 하나를 고르는 공용 컴포넌트 */
@Composable
fun RgbPicker(label: String, color: RgbColor, onChange: (RgbColor) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color.toComposeColor(), MaterialTheme.shapes.extraSmall)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
            )
            Spacer(Modifier.width(8.dp))
            Text("$label  RGB(${color.r}, ${color.g}, ${color.b})", style = MaterialTheme.typography.labelMedium)
        }
        PickerChannel("R", color.r) { onChange(color.copy(r = it)) }
        PickerChannel("G", color.g) { onChange(color.copy(g = it)) }
        PickerChannel("B", color.b) { onChange(color.copy(b = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PICKER_QUICK_COLORS.forEach { q ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(q.toComposeColor(), MaterialTheme.shapes.extraSmall)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.extraSmall)
                        .clickable { onChange(q) }
                )
            }
        }
    }
}

@Composable
private fun PickerChannel(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(18.dp), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f).height(26.dp)
        )
        Text(value.toString(), modifier = Modifier.width(32.dp), style = MaterialTheme.typography.labelSmall)
    }
}
