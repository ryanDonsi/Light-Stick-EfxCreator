package com.efxcreator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lightstick.efx.EfxEntry
import com.lightstick.types.Color
import com.lightstick.types.Colors
import com.lightstick.types.EffectType
import com.lightstick.types.LSEffectPayload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineEntryDialog(
    entry: EfxEntry?,
    lastEntryTimestamp: Long = 0L,
    onDismiss: () -> Unit,
    onSave: (EfxEntry) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    // Timestamp (분:초:밀리초)
    val initialTimestamp = entry?.timestampMs ?: lastEntryTimestamp
    var minutes by remember { mutableIntStateOf((initialTimestamp / 60000).toInt()) }
    var seconds by remember { mutableIntStateOf(((initialTimestamp % 60000) / 1000).toInt()) }
    var millis by remember { mutableIntStateOf((initialTimestamp % 1000).toInt()) }

    // Effect Type
    var effectType by remember { mutableStateOf(entry?.payload?.effectType ?: EffectType.ON) }

    // Period
    var period by remember {
        mutableIntStateOf(
            entry?.payload?.period ?: when (effectType) {
                EffectType.ON, EffectType.OFF -> 0
                EffectType.STROBE -> 2
                EffectType.BLINK -> 5
                EffectType.BREATH -> 10
            }
        )
    }

    // Color
    var colorR by remember { mutableIntStateOf(entry?.payload?.color?.r ?: 255) }
    var colorG by remember { mutableIntStateOf(entry?.payload?.color?.g ?: 255) }
    var colorB by remember { mutableIntStateOf(entry?.payload?.color?.b ?: 255) }

    // Background Color
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
                // Timestamp
                Text("Timestamp", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = minutes.toString(),
                        onValueChange = { minutes = it.toIntOrNull()?.coerceAtLeast(0) ?: 0 },
                        label = { Text("min") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = seconds.toString(),
                        onValueChange = { seconds = it.toIntOrNull()?.coerceIn(0, 59) ?: 0 },
                        label = { Text("sec") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = millis.toString(),
                        onValueChange = { millis = it.toIntOrNull()?.coerceIn(0, 999) ?: 0 },
                        label = { Text("ms") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Effect Type
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

                // Period (범위 표시 추가)
                OutlinedTextField(
                    value = period.toString(),
                    onValueChange = { period = it.toIntOrNull() ?: 0 },
                    label = {
                        Text(
                            if (effectType in listOf(EffectType.ON, EffectType.OFF))
                                "Transit (0-255)"
                            else
                                "Period (0-255)"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Foreground Color (OFF Effect는 제외)
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

                // Background Color
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

                Text("Advanced Parameters", style = MaterialTheme.typography.titleSmall)

                // SPF (약어 제거, 범위 추가)
                OutlinedTextField(
                    value = spf.toString(),
                    onValueChange = { spf = it.toIntOrNull()?.coerceIn(0, 255) ?: 100 },
                    label = { Text("SPF (0-255)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Fade (범위 추가)
                OutlinedTextField(
                    value = fade.toString(),
                    onValueChange = { fade = it.toIntOrNull()?.coerceIn(0, 255) ?: 100 },
                    label = { Text("Fade (0-255)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Random Color (0 or 1)
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

                // Random Delay (이미 범위 표시 있음)
                OutlinedTextField(
                    value = randomDelay.toString(),
                    onValueChange = { randomDelay = it.toIntOrNull()?.coerceIn(0, 255) ?: 0 },
                    label = { Text("Random Delay (0-255)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("취소")
                }
                TextButton(
                    onClick = {
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
                    }
                ) {
                    Text("저장")
                }
            }
        }
    )

    // Foreground Color Picker
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

    // Background Color Picker
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

    // 삭제 확인 다이얼로그
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Entry 삭제") },
            text = { Text("이 타임라인 엔트리를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete?.invoke()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
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
 * Color Picker Dialog (SDK Colors 전체 사용)
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
                // 색상 미리보기
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

                // RGB 슬라이더
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

                // 프리셋 색상 (SDK Colors 전체)
                Text(
                    text = "프리셋",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // SDK Colors 전체 사용 (11개)
                val presetColors = listOf(
                    Colors.RED,
                    Colors.GREEN,
                    Colors.BLUE,
                    Colors.YELLOW,
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

                // 두 번째 줄: 5개 + 빈 공간
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
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onColorSelected(Color(red, green, blue))
            }) {
                Text("선택")
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
 * 프리셋 색상 버튼
 */
@Composable
fun PresetColorButton(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ComposeColor(color.r, color.g, color.b))
            .border(
                width = if (isSelected) 2.dp else 0.5.dp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = ComposeColor.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 간결한 컬러 슬라이더 (회색 통일, 컴팩트한 크기)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactColorSlider(
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
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(24.dp),
            fontWeight = FontWeight.Medium
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