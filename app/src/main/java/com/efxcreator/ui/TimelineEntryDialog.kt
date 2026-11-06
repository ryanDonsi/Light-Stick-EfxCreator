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
import com.efxcreator.music.sets.RgbColor
import com.efxcreator.ui.components.HsvColorPicker
import com.efxcreator.ui.components.NumberTextField
import com.efxcreator.ui.device.activityBleViewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.efxcreator.model.EfxDurations
import com.efxcreator.model.EfxOverwrite
import com.efxcreator.ui.editor.BeatGrid
import com.efxcreator.ui.editor.TimelineMath
import com.lightstick.types.Color
import com.lightstick.types.Colors
import com.lightstick.types.EffectType
import com.lightstick.types.LSEffectPayload
import com.lightstick.efx.EfxEntry
import com.efxcreator.R
import com.efxcreator.core.Loc

/**
 * ✅ 리팩토링: NumberTextField 컴포넌트 사용
 *
 * Timeline Entry 추가/편집 다이얼로그
 * - NumberTextField로 간결한 코드
 * - 포커스 시 "0" 자동 지우기
 * - 숫자 키보드 자동 표시
 */
/** 입력칸 아래에 단위 환산 결과를 보여 주는 작은 안내 글 */
@Composable
private fun UnitHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun TimelineEntryDialog(
    entry: EfxEntry?,
    lastEntryTimestamp: Long = 0L,
    onDismiss: () -> Unit,
    onSave: (EfxEntry) -> Unit,
    onDelete: (() -> Unit)? = null,
    title: String = Loc.str(R.string.timeline_entry_dialog_1),
    deleteTitle: String = Loc.str(R.string.timeline_entry_dialog_2),
    deleteMessage: String = Loc.str(R.string.efx_edit_screen_14),
    /** false 면 Timestamp 입력을 숨긴다 (시간이 없는 내 라이브러리 편집용) */
    showTimestamp: Boolean = true,
    /** false 면 Random Color / Random Delay / 그룹 마스크를 숨긴다 (라이브러리에는 저장되지 않는 값) */
    showTargetOptions: Boolean = true,
    /** 분석된 비트 시각(ms). 2개 이상이면 비트 스냅/비트 단위 입력을 쓸 수 있다 */
    beatTimesMs: List<Long> = emptyList(),
    /** 비트 스냅 ON/OFF (편집 화면의 설정과 같이 쓰려면 [onSnapChange] 도 넘긴다) */
    snapToBeats: Boolean = true,
    onSnapChange: ((Boolean) -> Unit)? = null,
    /** 덮어쓰기/밀기 선택과 영향 안내를 보여 줄지 (편집 화면의 이펙트 편집에서만 true) */
    showOverwrite: Boolean = false,
    /** 편집 중인 이펙트를 뺀 나머지 이펙트 (덮어쓰기/밀기 영향 계산용) */
    otherEntries: List<EfxEntry> = emptyList(),
    /** [showOverwrite] 가 true 일 때 저장하면 [onSave] 대신 불린다: (이펙트, 덮어쓰기 여부) */
    onSaveOverwrite: ((EfxEntry, Boolean) -> Unit)? = null,
    /** 맨 위에 끼워 넣을 내용 (예: 라이브러리 이름 입력) */
    topContent: (@Composable ColumnScope.() -> Unit)? = null
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
    var fade by remember { mutableIntStateOf((entry?.payload?.fade ?: 100).coerceIn(0, 100)) }
    var randomColor by remember { mutableIntStateOf(entry?.payload?.randomColor ?: 0) }
    var randomDelay by remember { mutableIntStateOf(entry?.payload?.randomDelay ?: 0) }
    // Duration: 10ms 단위, 0 = 다음 이펙트까지 유지
    var duration by remember { mutableIntStateOf(entry?.payload?.duration ?: 0) }

    // 비트 스냅 / 덮어쓰기 / 비트 단위 입력. 기본값: 스냅 ON, 덮어쓰기 ON, 비트 단위(분석된 곡)
    val beatsAvailable = beatTimesMs.size >= 2
    var snapLocal by remember { mutableStateOf(snapToBeats) }
    val snapOn = if (onSnapChange != null) snapToBeats else snapLocal
    var overwrite by remember { mutableStateOf(true) }
    // 시작 시각은 사용자가 바꿨을 때만 비트에 붙인다 (다른 항목만 고쳐도 위치가 틀어지지 않도록)
    val typedTs = minutes * 60000L + seconds * 1000L + millis
    val tsChanged = typedTs != initialTimestamp
    val snappedBeat: Long? =
        if (showTimestamp && snapOn && beatsAvailable && tsChanged) TimelineMath.nearestBeat(typedTs, beatTimesMs) else null
    val effectiveTs = snappedBeat ?: typedTs
    var beatUnit by remember { mutableStateOf(beatsAvailable) }
    var beatsText by remember { mutableStateOf("0") }
    fun fmtBeats(b: Double): String =
        "%.2f".format(java.util.Locale.US, b).trimEnd('0').trimEnd('.').ifEmpty { "0" }
    fun beatsOfTicks(ticks: Int): Double =
        if (!beatsAvailable || ticks == 0) 0.0
        else BeatGrid.beatsBetween(beatTimesMs, effectiveTs.toDouble(), effectiveTs + EfxDurations.ticksToMs(ticks).toDouble())
    fun ticksOfBeats(b: Double): Int {
        if (b <= 0.0 || !beatsAvailable) return 0
        val ms = BeatGrid.msForBeats(beatTimesMs, effectiveTs.toDouble(), b)
        return ((ms + 5) / 10).toInt().coerceIn(1, EfxDurations.MAX_TICKS)
    }
    // 단위를 바꾸거나 시작 위치가 바뀌면 저장될 값 기준으로 비트 표시를 다시 계산한다
    LaunchedEffect(beatUnit, effectiveTs) { beatsText = fmtBeats(beatsOfTicks(duration)) }
    // 그룹 마스크: 그룹 번호 토글로 편집 (0 = 마스터 키/모든 그룹, bit(n-1) = 그룹 n)
    var groupMask by remember { mutableLongStateOf(entry?.payload?.groupMask ?: 0L) }

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
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                topContent?.invoke(this)

                // ========== Timestamp ==========
                if (showTimestamp) Text(Loc.str(R.string.label_timestamp), style = MaterialTheme.typography.labelMedium)
                if (showTimestamp) Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ✅ 간결해진 코드 - NumberTextField 사용
                    NumberTextField(
                        value = minutes,
                        onValueChange = { minutes = it },
                        label = Loc.str(R.string.label_minutes),
                        modifier = Modifier.weight(1f)
                    )

                    NumberTextField(
                        value = seconds,
                        onValueChange = { seconds = it },
                        label = Loc.str(R.string.label_seconds),
                        range = 0..59,
                        modifier = Modifier.weight(1f)
                    )

                    NumberTextField(
                        value = millis,
                        onValueChange = { millis = it },
                        label = Loc.str(R.string.label_millis),
                        range = 0..999,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (showTimestamp && beatsAvailable) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(Loc.str(R.string.dlg_snap_label), modifier = Modifier.weight(1f))
                        Switch(
                            checked = snapOn,
                            onCheckedChange = { if (onSnapChange != null) onSnapChange(it) else snapLocal = it }
                        )
                    }
                    if (snappedBeat != null) {
                        UnitHint(
                            Loc.str(
                                R.string.dlg_snap_target,
                                beatTimesMs.indexOf(snappedBeat) + 1,
                                TimelineMath.formatTime(snappedBeat, withTenths = true)
                            )
                        )
                    } else if (snapOn) {
                        UnitHint(Loc.str(R.string.dlg_snap_hint))
                    }
                }

                // 시작 시간 바로 아래: 지속 시간과 덮어쓰기/밀기 (서로 영향을 주는 값이라 한곳에 모은다)
                if (showTargetOptions) {
                // ── 지속시간: 비트 단위(소수점 2자리) 또는 10ms 단위로 입력. 0 = 다음 이펙트까지 ──
                Text(Loc.str(R.string.label_duration), style = MaterialTheme.typography.labelMedium)
                if (beatsAvailable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = beatUnit, onClick = { beatUnit = true }, label = { Text(Loc.str(R.string.dlg_beat_unit)) })
                        FilterChip(selected = !beatUnit, onClick = { beatUnit = false }, label = { Text(Loc.str(R.string.dlg_unit_10ms)) })
                    }
                }
                if (beatUnit && beatsAvailable) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = beatsText,
                            onValueChange = { raw ->
                                val f = raw.replace(',', '.')
                                if (Regex("""\d{0,4}(\.\d{0,2})?""").matches(f)) {
                                    beatsText = f
                                    duration = ticksOfBeats(f.toDoubleOrNull() ?: 0.0)
                                }
                            },
                            label = { Text(Loc.str(R.string.dlg_beat_unit)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = {
                            val nb = ((beatsText.toDoubleOrNull() ?: 0.0) - 0.1).coerceAtLeast(0.0)
                            beatsText = fmtBeats(nb)
                            duration = ticksOfBeats(nb)
                        }) { Text("-0.1") }
                        OutlinedButton(onClick = {
                            val nb = (beatsText.toDoubleOrNull() ?: 0.0) + 0.1
                            beatsText = fmtBeats(nb)
                            duration = ticksOfBeats(nb)
                        }) { Text("+0.1") }
                    }
                } else {
                    NumberTextField(
                        value = duration,
                        onValueChange = { duration = it },
                        label = Loc.str(R.string.label_duration),
                        range = 0..EfxDurations.MAX_TICKS,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // 실제로 들어가는 값 안내
                val durMs = EfxDurations.ticksToMs(duration)
                UnitHint(
                    when {
                        duration == 0 -> Loc.str(R.string.duration_until_next)
                        beatsAvailable -> Loc.str(R.string.dlg_duration_ref_beats, fmtBeats(beatsOfTicks(duration)), EfxDurations.formatMs(durMs), duration)
                        else -> Loc.str(R.string.dlg_duration_ref_ms, EfxDurations.formatMs(durMs), duration)
                    }
                )

                // ── 덮어쓰기(ON) / 밀기(OFF) ──
                if (showOverwrite) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(Loc.str(R.string.dlg_overwrite_label))
                            UnitHint(Loc.str(if (overwrite) R.string.dlg_overwrite_on_desc else R.string.dlg_overwrite_off_desc))
                        }
                        Switch(checked = overwrite, onCheckedChange = { overwrite = it })
                    }
                    val impact = EfxOverwrite.preview(otherEntries, effectiveTs, duration, overwrite)
                    UnitHint(
                        when {
                            impact.removed > 0 || impact.trimmed > 0 -> Loc.str(R.string.dlg_overwrite_summary, impact.removed, impact.trimmed)
                            impact.pushed > 0 -> Loc.str(R.string.dlg_push_summary, impact.pushed, EfxDurations.formatMs(impact.pushedByMs))
                            else -> Loc.str(R.string.dlg_overlap_none)
                        }
                    )
                }
                }

                // ========== Effect Type ==========
                Text(Loc.str(R.string.label_effect_type), style = MaterialTheme.typography.labelMedium)
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
                        Loc.str(R.string.timeline_entry_dialog_3)
                    else
                        Loc.str(R.string.timeline_entry_dialog_4),
                    range = 0..255,
                    modifier = Modifier.fillMaxWidth()
                )
                UnitHint("= ${TimelineMath.formatFrames(period, spf)}")

                // ========== Foreground Color ==========
                if (effectType != EffectType.OFF) {
                    Text(Loc.str(R.string.label_foreground_color), style = MaterialTheme.typography.labelMedium)
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
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(ComposeColor(colorR, colorG, colorB))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                            )
                            Text("RGB($colorR, $colorG, $colorB)")
                        }
                        TextButton(onClick = { showFgColorPicker = true }) {
                            Text(Loc.str(R.string.timeline_entry_dialog_5))
                        }
                    }
                }

                // ========== Background Color ==========
                if (effectType in listOf(EffectType.BLINK, EffectType.STROBE, EffectType.BREATH)) {
                    Text(Loc.str(R.string.label_background_color), style = MaterialTheme.typography.labelMedium)
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
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(ComposeColor(bgColorR, bgColorG, bgColorB))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                            )
                            Text("RGB($bgColorR, $bgColorG, $bgColorB)")
                        }
                        TextButton(onClick = { showBgColorPicker = true }) {
                            Text(Loc.str(R.string.timeline_entry_dialog_5))
                        }
                    }
                }

                HorizontalDivider()

                // ========== Advanced Parameters ==========
                Text(Loc.str(R.string.label_advanced_params), style = MaterialTheme.typography.titleSmall)

                NumberTextField(
                    value = spf,
                    onValueChange = { spf = it },
                    label = Loc.str(R.string.timeline_entry_dialog_6),
                    range = 0..255,
                    modifier = Modifier.fillMaxWidth()
                )
                UnitHint(TimelineMath.frameUnitLabel(spf) + Loc.str(R.string.timeline_entry_dialog_7))

                NumberTextField(
                    value = fade,
                    onValueChange = { fade = it },
                    label = Loc.str(R.string.timeline_entry_dialog_8),
                    range = 0..100,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showTargetOptions) {
                // Random Color Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(Loc.str(R.string.label_random_color))
                    Switch(
                        checked = randomColor != 0,
                        onCheckedChange = { randomColor = if (it) 1 else 0 }
                    )
                }

                NumberTextField(
                    value = randomDelay,
                    onValueChange = { randomDelay = it },
                    label = Loc.str(R.string.timeline_entry_dialog_9),
                    range = 0..255,
                    modifier = Modifier.fillMaxWidth()
                )
                UnitHint(if (randomDelay == 0) Loc.str(R.string.timeline_entry_dialog_10) else Loc.str(R.string.timeline_entry_dialog_11, TimelineMath.formatFrames(randomDelay, spf)))

                GroupMaskPicker(mask = groupMask, onChange = { groupMask = it })
                }
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
                        Text(Loc.str(R.string.efx_edit_screen_15))
                    }
                }
                TextButton(onClick = {
                    val timestampMs = effectiveTs
                    val payload = LSEffectPayload(
                        groupMask = groupMask,
                        effectType = effectType,
                        color = Color(colorR, colorG, colorB),
                        backgroundColor = Color(bgColorR, bgColorG, bgColorB),
                        period = period,
                        spf = spf,
                        fade = fade,
                        randomColor = randomColor,
                        randomDelay = randomDelay,
                        duration = duration,
                        effectIndex = 0
                    )
                    val saved = EfxEntry(timestampMs, payload)
                    if (showOverwrite && onSaveOverwrite != null) onSaveOverwrite(saved, overwrite) else onSave(saved)
                }) {
                    Text(Loc.str(R.string.efx_edit_screen_21))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.str(R.string.efx_edit_screen_16))
            }
        }
    )

    // ========== Color Picker Dialogs ==========
    if (showFgColorPicker) {
        ColorPickerDialog(
            title = Loc.str(R.string.timeline_entry_dialog_12),
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
            title = Loc.str(R.string.timeline_entry_dialog_13),
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
            title = { Text(deleteTitle) },
            text = { Text(deleteMessage) },
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
                    Text(Loc.str(R.string.efx_edit_screen_15))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(Loc.str(R.string.efx_edit_screen_16))
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
    title: String = Loc.str(R.string.timeline_entry_dialog_14),
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableIntStateOf(currentColor.r) }
    var green by remember { mutableIntStateOf(currentColor.g) }
    var blue by remember { mutableIntStateOf(currentColor.b) }

    // 현재 선택된 색상 (실시간)
    val selectedColor = Color(red, green, blue)

    // 연결된 디바이스에 편집 중인 색을 실시간으로 표시한다 (닫을 때 끈다)
    val bleVm = activityBleViewModel()
    val connectedDevice by bleVm.connected.collectAsState()
    val deviceConnected = connectedDevice != null
    LaunchedEffect(red, green, blue, deviceConnected) { bleVm.previewColor(red, green, blue) }
    DisposableEffect(Unit) { onDispose { bleVm.stopPreview() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 색상/선명도/밝기 슬라이더 + RGB 직접 입력 (팔레트 세트 편집과 같은 색상 선택)
                HsvColorPicker(
                    color = RgbColor(red, green, blue),
                    onChange = { red = it.r; green = it.g; blue = it.b }
                )
                Text(
                    if (deviceConnected) Loc.str(R.string.timeline_entry_dialog_15)
                    else Loc.str(R.string.timeline_entry_dialog_16),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (deviceConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ========== 프리셋 색상 ==========
                Text(
                    text = Loc.str(R.string.timeline_entry_dialog_17),
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
                Text(Loc.str(R.string.efx_edit_screen_19))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.str(R.string.efx_edit_screen_16))
            }
        }
    )
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
            .clip(MaterialTheme.shapes.extraSmall)
            .background(ComposeColor(color.r, color.g, color.b))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.extraSmall
            )
            .then(
                if (!isSelected) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.extraSmall
                    )
                } else Modifier
            )
            .clickable(onClick = onClick)
    )
}