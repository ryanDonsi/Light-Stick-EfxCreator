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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.DialogProperties
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

/**
 * 비트 수 입력 박스 (소수점 입력). 시작 시간 박스와 같은 둥근 박스 모양이며, 옆의 '비트' 라벨은 호출하는 쪽에서 붙인다.
 */
@Composable
private fun BeatBox(
    text: String,
    onTextChange: (String) -> Unit,
    onFocusGained: () -> Unit,
    onFocusLost: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.titleMedium.copy(
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .width(88.dp)
            .height(40.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .onFocusChanged { st -> if (st.isFocused) onFocusGained() else onFocusLost() },
        decorationBox = { inner -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() } }
    )
}

/**
 * 작은 숫자 박스 (시작 시간 0.00.00 의 한 칸). 포커스하면 비우고 바로 입력, 벗어나면 범위로 보정하고 자릿수를 채운다.
 * @param minDigits 표시할 최소 자릿수 (모자라면 앞에 0 을 채운다)
 */
@Composable
private fun TimeBox(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    minDigits: Int,
    maxDigits: Int,
    modifier: Modifier = Modifier
) {
    fun fmt(v: Int) = v.toString().padStart(minDigits, '0')
    var focused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(fmt(value)) }
    // 입력 중에는 건드리지 않고(자릿수 채우기가 타이핑을 방해하지 않도록), 포커스가 없을 때만 값에 맞춰 표시한다
    LaunchedEffect(value, focused) { if (!focused) text = fmt(value) }
    val outline = MaterialTheme.colorScheme.outline
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            val f = raw.filter { it.isDigit() }.take(maxDigits)
            text = f
            f.toIntOrNull()?.let { if (it in range) onValueChange(it) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.titleMedium.copy(
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .width((16 + 14 * maxDigits).dp)
            .height(40.dp)
            .border(1.dp, outline, RoundedCornerShape(8.dp))
            .onFocusChanged { st ->
                if (st.isFocused) {
                    focused = true
                    text = ""
                } else {
                    focused = false
                    val v = text.toIntOrNull()?.coerceIn(range) ?: value
                    text = fmt(v)
                    onValueChange(v)
                }
            },
        decorationBox = { inner -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() } }
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
    var beatsText by remember { mutableStateOf("0") }
    fun fmtBeats(b: Double): String = BeatGrid.formatBeats(b)
    fun beatsOfTicks(ticks: Int): Double =
        if (!beatsAvailable || ticks == 0) 0.0
        else BeatGrid.beatsBetween(beatTimesMs, effectiveTs.toDouble(), effectiveTs + EfxDurations.ticksToMs(ticks).toDouble())
    fun ticksOfBeats(b: Double): Int {
        if (b <= 0.0 || !beatsAvailable) return 0
        val ms = BeatGrid.msForBeats(beatTimesMs, effectiveTs.toDouble(), b)
        return ((ms + 5) / 10).toInt().coerceIn(1, EfxDurations.MAX_TICKS)
    }
    val focusManager = LocalFocusManager.current
    fun setStart(ts: Long) {
        minutes = (ts / 60000).toInt()
        seconds = ((ts % 60000) / 1000).toInt()
        millis = (ts % 1000).toInt()
    }
    // 시작 시간 비트 조정: 스냅 ON 이면 다음/이전 비트로, OFF 면 0.1비트씩 (dir: +1 = 뒤로, -1 = 앞으로)
    fun stepStart(dir: Int) {
        if (!beatsAvailable) return
        val f = BeatGrid.indexAt(beatTimesMs, effectiveTs.toDouble())
        val next: Long = if (snapOn) {
            // 비트 위에 있으면 한 박, 비트 사이에 있으면 그 방향의 가장 가까운 비트로 (0.01박 여유로 부동소수 오차 흡수)
            val idx = if (dir > 0) kotlin.math.floor(f + 0.01) + 1 else kotlin.math.ceil(f - 0.01) - 1
            Math.round(BeatGrid.timeAt(beatTimesMs, idx))
        } else {
            Math.round(BeatGrid.timeAt(beatTimesMs, f + dir * 0.1))
        }
        setStart(next.coerceAtLeast(0L))
    }
    // 단위를 바꾸거나 시작 위치가 바뀌면 저장될 값 기준으로 비트 표시를 다시 계산한다
    LaunchedEffect(effectiveTs) { beatsText = fmtBeats(beatsOfTicks(duration)) }
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

    val wide = LocalConfiguration.current.screenWidthDp >= 720
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = if (wide) Modifier.width(820.dp) else Modifier,
        properties = DialogProperties(usePlatformDefaultWidth = !wide),
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                topContent?.invoke(this)

                // 넓은 화면(태블릿)에서는 좌우 두 단으로 나눠 높이를 줄인다
                // 왼쪽: 시작 시간 / 비트 스냅 / 지속 시간 / 덮어쓰기, 오른쪽: 종류 / 주기 / 색 / 고급 설정
                val sectionLeft: @Composable ColumnScope.() -> Unit = {
                // ========== Timestamp ==========
                if (showTimestamp) Text(Loc.str(R.string.label_timestamp), style = MaterialTheme.typography.labelMedium)
                if (showTimestamp) Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 0.00.00 = 분 . 초 . 1/100초 (10ms). 세 번째 칸은 바꿨을 때만 ms 에 반영해 원래 ms 값이 틀어지지 않게 한다
                    TimeBox(value = minutes, onValueChange = { minutes = it }, range = 0..99, minDigits = 1, maxDigits = 2)
                    Text(".", style = MaterialTheme.typography.titleMedium)
                    TimeBox(value = seconds, onValueChange = { seconds = it }, range = 0..59, minDigits = 2, maxDigits = 2)
                    Text(".", style = MaterialTheme.typography.titleMedium)
                    TimeBox(
                        value = millis / 10,
                        onValueChange = { if (it != millis / 10) millis = it * 10 },
                        range = 0..99, minDigits = 2, maxDigits = 2
                    )
                    if (beatsAvailable) {
                        Spacer(Modifier.width(8.dp))
                        // 비트 단위 조정: 스냅 ON 이면 1비트씩 다음/이전 비트로, OFF 면 0.1비트씩
                        OutlinedButton(
                            onClick = { focusManager.clearFocus(); stepStart(-1) },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(40.dp)
                        ) { Text(if (snapOn) "-1" else "-0.1") }
                        OutlinedButton(
                            onClick = { focusManager.clearFocus(); stepStart(+1) },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(40.dp)
                        ) { Text(if (snapOn) "+1" else "+0.1") }
                    }
                }
                if (showTimestamp) UnitHint(
                    Loc.str(R.string.dlg_time_units) +
                        if (beatsAvailable) "  ·  " + Loc.str(if (snapOn) R.string.dlg_step_beat_snap else R.string.dlg_step_beat_fine) else ""
                )

                if (showTimestamp && beatsAvailable) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(Loc.str(R.string.dlg_snap_label))
                            UnitHint(Loc.str(if (snapOn) R.string.dlg_snap_on_desc else R.string.dlg_snap_off_desc))
                        }
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
                    }
                }

                // 시작 시간 바로 아래: 지속 시간과 덮어쓰기/밀기 (서로 영향을 주는 값이라 한곳에 모은다)
                if (showTargetOptions) {
                // ── 지속시간: 비트 단위로만 입력 (둥근 박스 = 입력, 옆의 '비트' = 라벨). 0 = 다음 이펙트까지 ──
                Text(Loc.str(R.string.label_duration), style = MaterialTheme.typography.labelMedium)
                if (beatsAvailable) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BeatBox(
                            text = beatsText,
                            onTextChange = { raw ->
                                val f = raw.replace(',', '.')
                                if (Regex("""\d{0,4}(\.\d{0,2})?""").matches(f)) {
                                    beatsText = f
                                    duration = ticksOfBeats(f.toDoubleOrNull() ?: 0.0)
                                }
                            },
                            onFocusGained = { beatsText = "" },
                            // 포커스를 벗어나면 저장될 값 기준으로 다시 표시한다 (값 자체는 입력하는 동안 이미 반영됨)
                            onFocusLost = { beatsText = fmtBeats(beatsOfTicks(duration)) }
                        )
                        Text(Loc.str(R.string.dlg_beat_unit), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                val nb = ((beatsText.toDoubleOrNull() ?: 0.0) - 0.1).coerceAtLeast(0.0)
                                beatsText = fmtBeats(nb)
                                duration = ticksOfBeats(nb)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(40.dp)
                        ) { Text("-0.1") }
                        OutlinedButton(
                            onClick = {
                                focusManager.clearFocus()
                                val nb = (beatsText.toDoubleOrNull() ?: 0.0) + 0.1
                                beatsText = fmtBeats(nb)
                                duration = ticksOfBeats(nb)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(40.dp)
                        ) { Text("+0.1") }
                    }
                } else {
                    // 곡 분석(비트 정보)이 없으면 비트로 환산할 수 없어 10ms 단위로 입력한다
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
                    // 영향이 있을 때만 안내한다 (없으면 아무것도 표시하지 않는다)
                    val impactText: String? = when {
                        impact.removed > 0 || impact.trimmed > 0 -> Loc.str(R.string.dlg_overwrite_summary, impact.removed, impact.trimmed)
                        impact.pushed > 0 -> Loc.str(R.string.dlg_push_summary, impact.pushed, EfxDurations.formatMs(impact.pushedByMs))
                        else -> null
                    }
                    if (impactText != null) UnitHint(impactText)
                }
                }
                }
                val sectionRight: @Composable ColumnScope.() -> Unit = {
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

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberTextField(
                        value = spf,
                        onValueChange = { spf = it },
                        label = Loc.str(R.string.timeline_entry_dialog_6),
                        range = 0..255,
                        modifier = Modifier.weight(1f)
                    )
                    NumberTextField(
                        value = fade,
                        onValueChange = { fade = it },
                        label = Loc.str(R.string.timeline_entry_dialog_8),
                        range = 0..100,
                        modifier = Modifier.weight(1f)
                    )
                }
                UnitHint(TimelineMath.frameUnitLabel(spf) + Loc.str(R.string.timeline_entry_dialog_7))

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
                if (wide) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { sectionLeft() }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { sectionRight() }
                    }
                } else {
                    sectionLeft()
                    sectionRight()
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