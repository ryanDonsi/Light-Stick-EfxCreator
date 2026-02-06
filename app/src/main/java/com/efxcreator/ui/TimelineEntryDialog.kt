@file:OptIn(ExperimentalMaterial3Api::class)

package com.efxcreator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.efxcreator.ui.components.NumberTextField
import com.lightstick.types.Color
import com.lightstick.types.Colors
import com.lightstick.types.EffectType
import com.lightstick.types.LSEffectPayload
import com.lightstick.efx.EfxEntry

/**
 * ✅ 리팩토링: NumberTextField 컴포넌트 사용
 *
 * Timeline Entry 추가/편집 다이얼로그
 * - NumberTextField로 간결한 코드
 * - 포커스 시 "0" 자동 지우기
 * - 숫자 키보드 자동 표시
 */
@Composable
fun TimelineEntryDialog(
    entry: EfxEntry?,
    lastEntryTimestamp: Long = 0L,
    onDismiss: () -> Unit,
    onSave: (EfxEntry) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val initialTimestamp = entry?.timestampMs ?: lastEntryTimestamp
    val initialMinutes = (initialTimestamp / 60000).toInt()
    val initialSeconds = ((initialTimestamp % 60000) / 1000).toInt()
    val initialMillis = (initialTimestamp % 1000).toInt()

    // ✅ 간결해진 상태 변수 (String 불필요)
    var minutes by remember { mutableIntStateOf(initialMinutes) }
    var seconds by remember { mutableIntStateOf(initialSeconds) }
    var millis by remember { mutableIntStateOf(initialMillis) }

    var effectType by remember { mutableStateOf(entry?.payload?.effectType ?: EffectType.ON) }
    var period by remember { mutableIntStateOf(entry?.payload?.period ?: 0) }

    // Colors
    var colorR by remember { mutableIntStateOf(entry?.payload?.color?.r ?: 255) }
    var colorG by remember { mutableIntStateOf(entry?.payload?.color?.g ?: 255) }
    var colorB by remember { mutableIntStateOf(entry?.payload?.color?.b ?: 255) }
    var bgColorR by remember { mutableIntStateOf(entry?.payload?.backgroundColor?.r ?: 0) }
    var bgColorG by remember { mutableIntStateOf(entry?.payload?.backgroundColor?.g ?: 0) }
    var bgColorB by remember { mutableIntStateOf(entry?.payload?.backgroundColor?.b ?: 0) }

    // Advanced
    var spf by remember { mutableIntStateOf(entry?.payload?.spf ?: 100) }
    var fade by remember { mutableIntStateOf(entry?.payload?.fade ?: 100) }
    var randomColor by remember { mutableIntStateOf(entry?.payload?.randomColor ?: 0) }
    var randomDelay by remember { mutableIntStateOf(entry?.payload?.randomDelay ?: 0) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showFgColorPicker by remember { mutableStateOf(false) }
    var showBgColorPicker by remember { mutableStateOf(false) }

    // Effect Type 변경 시 period 기본값 설정
    LaunchedEffect(effectType) {
        if (entry == null) {
            period = when (effectType) {
                EffectType.ON, EffectType.OFF -> 0
                EffectType.STROBE -> 2
                EffectType.BLINK -> 5
                EffectType.BREATH -> 10
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Effect 편집") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ========== Timestamp ==========
                Text("Timestamp", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ✅ 간결해진 코드 - NumberTextField 사용
                    NumberTextField(
                        value = minutes,
                        onValueChange = { minutes = it },
                        label = "min",
                        modifier = Modifier.weight(1f)
                    )

                    NumberTextField(
                        value = seconds,
                        onValueChange = { seconds = it },
                        label = "sec",
                        range = 0..59,
                        modifier = Modifier.weight(1f)
                    )

                    NumberTextField(
                        value = millis,
                        onValueChange = { millis = it },
                        label = "ms",
                        range = 0..999,
                        modifier = Modifier.weight(1f)
                    )
                }

                // ========== Effect Type ==========
                Text("Effect Type", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = effectType == EffectType.ON,
                            onClick = { effectType = EffectType.ON },
                            label = { Text("ON") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = effectType == EffectType.OFF,
                            onClick = { effectType = EffectType.OFF },
                            label = { Text("OFF") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = effectType == EffectType.STROBE,
                            onClick = { effectType = EffectType.STROBE },
                            label = { Text("STROBE") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = effectType == EffectType.BLINK,
                            onClick = { effectType = EffectType.BLINK },
                            label = { Text("BLINK") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = effectType == EffectType.BREATH,
                            onClick = { effectType = EffectType.BREATH },
                            label = { Text("BREATH") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                // ========== Period / Transit ==========
                NumberTextField(
                    value = period,
                    onValueChange = { period = it },
                    label = if (effectType in listOf(EffectType.ON, EffectType.OFF))
                        "Transit (0-255)"
                    else
                        "Period (0-255)",
                    range = 0..255,
                    modifier = Modifier.fillMaxWidth()
                )

                // ========== Foreground Color ==========
                if (effectType != EffectType.OFF) {
                    Text("Foreground Color", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ComposeColor(colorR, colorG, colorB))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            )
                            Text("RGB($colorR, $colorG, $colorB)")
                        }
                        TextButton(onClick = { showFgColorPicker = true }) {
                            Text("변경")
                        }
                    }
                }

                // ========== Background Color ==========
                if (effectType in listOf(EffectType.BLINK, EffectType.STROBE, EffectType.BREATH)) {
                    Text("Background Color", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ComposeColor(bgColorR, bgColorG, bgColorB))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            )
                            Text("RGB($bgColorR, $bgColorG, $bgColorB)")
                        }
                        TextButton(onClick = { showBgColorPicker = true }) {
                            Text("변경")
                        }
                    }
                }

                HorizontalDivider()

                // ========== Advanced Parameters ==========
                Text("Advanced Parameters", style = MaterialTheme.typography.titleSmall)

                NumberTextField(
                    value = spf,
                    onValueChange = { spf = it },
                    label = "SPF (0-255)",
                    range = 0..255,
                    modifier = Modifier.fillMaxWidth()
                )

                NumberTextField(
                    value = fade,
                    onValueChange = { fade = it },
                    label = "Fade (0-255)",
                    range = 0..255,
                    modifier = Modifier.fillMaxWidth()
                )

                // Random Color Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Random Color")
                    Switch(
                        checked = randomColor != 0,
                        onCheckedChange = { randomColor = if (it) 1 else 0 }
                    )
                }

                NumberTextField(
                    value = randomDelay,
                    onValueChange = { randomDelay = it },
                    label = "Random Delay (0-255, x10ms)",
                    range = 0..255,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry != null && onDelete != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("삭제")
                    }
                }
                TextButton(onClick = {
                    val timestampMs = (minutes * 60000L) + (seconds * 1000L) + millis
                    val payload = LSEffectPayload(
                        effectType = effectType,
                        color = Color(colorR, colorG, colorB),
                        backgroundColor = Color(bgColorR, bgColorG, bgColorB),
                        period = period,
                        spf = spf,
                        fade = fade,
                        randomColor = randomColor,
                        randomDelay = randomDelay,
                        broadcasting = 1,
                        syncIndex = 0,
                        effectIndex = 0
                    )
                    onSave(EfxEntry(timestampMs, payload))
                }) {
                    Text("저장")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )

    // ========== Color Picker Dialogs ==========
    if (showFgColorPicker) {
        ColorPickerDialog(
            title = "전경색 선택",
            currentColor = Color(colorR, colorG, colorB),
            onColorSelected = { color ->
                colorR = color.r
                colorG = color.g
                colorB = color.b
                showFgColorPicker = false
            },
            onDismiss = { showFgColorPicker = false }
        )
    }

    if (showBgColorPicker) {
        ColorPickerDialog(
            title = "배경색 선택",
            currentColor = Color(bgColorR, bgColorG, bgColorB),
            onColorSelected = { color ->
                bgColorR = color.r
                bgColorG = color.g
                bgColorB = color.b
                showBgColorPicker = false
            },
            onDismiss = { showBgColorPicker = false }
        )
    }

    // ========== Delete Confirmation Dialog ==========
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Effect 삭제") },
            text = { Text("이 타임라인 엔트리를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete?.invoke()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }
}

/**
 * ✅ Color Picker Dialog (SDK Colors 사용)
 *
 * RGB 슬라이더와 프리셋 색상으로 색상 선택
 */
@Composable
fun ColorPickerDialog(
    title: String = "색상 선택",
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableIntStateOf(currentColor.r) }
    var green by remember { mutableIntStateOf(currentColor.g) }
    var blue by remember { mutableIntStateOf(currentColor.b) }

    // 현재 선택된 색상 (실시간)
    val selectedColor = Color(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ========== 색상 미리보기 ==========
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ComposeColor(red, green, blue))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(8.dp)
                        )
                )

                // ========== RGB 슬라이더 ==========
                CompactColorSlider(
                    label = "R",
                    value = red,
                    onValueChange = { red = it }
                )

                CompactColorSlider(
                    label = "G",
                    value = green,
                    onValueChange = { green = it }
                )

                CompactColorSlider(
                    label = "B",
                    value = blue,
                    onValueChange = { blue = it }
                )

                // ========== 프리셋 색상 ==========
                Text(
                    text = "프리셋",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // SDK Colors 전체 사용 (11개)
                val presetColors = listOf(
                    Color(153, 0, 255),
                    Color(73,134,232),
                    Color(255,64,0),
                    Color(255,153,0),
                    Colors.MAGENTA,
                    Colors.CYAN,
                    Colors.ORANGE,
                    Colors.PURPLE,
                    Colors.PINK,
                    Colors.WHITE,
                    Colors.BLACK
                )

                // 첫 번째 줄: 6개
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetColors.take(6).forEach { color ->
                        PresetColorButton(
                            color = color,
                            isSelected = selectedColor.r == color.r &&
                                    selectedColor.g == color.g &&
                                    selectedColor.b == color.b,
                            onClick = {
                                red = color.r
                                green = color.g
                                blue = color.b
                            }
                        )
                    }
                }

                // 두 번째 줄: 5개
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetColors.drop(6).forEach { color ->
                        PresetColorButton(
                            color = color,
                            isSelected = selectedColor.r == color.r &&
                                    selectedColor.g == color.g &&
                                    selectedColor.b == color.b,
                            onClick = {
                                red = color.r
                                green = color.g
                                blue = color.b
                            }
                        )
                    }
                    // 빈 공간 추가 (6개 맞추기 위해)
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onColorSelected(selectedColor)
            }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

/**
 * ✅ 컴팩트 RGB 슬라이더
 */
@Composable
private fun CompactColorSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(20.dp)
        )

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier
                .weight(1f)
                .height(20.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        )
                )
            },
            track = { sliderState ->
                val fraction = sliderState.value / 255f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                ) {
                    // Inactive track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .align(Alignment.Center)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(1.dp)
                            )
                    )
                    // Active track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(2.dp)
                            .align(Alignment.CenterStart)
                            .background(
                                MaterialTheme.colorScheme.onSurface,
                                RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        )

        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(32.dp),
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * ✅ 프리셋 색상 버튼
 */
@Composable
private fun RowScope.PresetColorButton(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(ComposeColor(color.r, color.g, color.b))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(6.dp)
            )
            .then(
                if (!isSelected) {
                    Modifier.border(
                        width = 1.dp,
                        color = ComposeColor.Black.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    )
                } else Modifier
            )
            .clickable(onClick = onClick)
    )
}