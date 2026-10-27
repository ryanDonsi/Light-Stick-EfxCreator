package com.efxcreator.ui.sets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.efxcreator.model.EffectPattern
import com.efxcreator.model.PatternColors
import com.efxcreator.model.PatternResolver
import com.efxcreator.model.PatternStep
import com.efxcreator.ui.activityViewModel
import com.efxcreator.ui.components.NumberTextField
import com.efxcreator.ui.device.activityBleViewModel
import com.efxcreator.ui.editor.SlimVerticalScrollBar
import com.efxcreator.ui.editor.TimelineMath
import com.efxcreator.viewmodel.BleViewModel
import com.efxcreator.viewmodel.PatternsViewModel
import com.efxcreator.viewmodel.SetsViewModel
import com.efxcreator.music.sets.PaletteSet
import com.lightstick.types.EffectType
import java.util.Locale

private val STEP_TYPES = listOf("ON", "OFF", "BLINK", "BREATH", "STROBE")
private fun isTwoColor(type: String) = type == "BLINK" || type == "BREATH" || type == "STROBE"

private const val MAX_BEATS_TENTHS = 320   // 32박
private const val MAX_STEPS = 32
private const val MIN_BEAT_MS = 250   // 240 BPM
private const val MAX_BEAT_MS = 1500  // 40 BPM

/** 한 단계를 "1. ON 0.3박 · 화이트" 처럼 한 줄로 요약한다 */
fun PatternStep.summary(index: Int): String {
    val colors = when {
        type == "OFF" -> ""
        isTwoColor(type) -> " · ${PatternColors.label(fg)} / ${PatternColors.label(bg)}"
        else -> " · ${PatternColors.label(fg)}"
    }
    val frame = if (frames > 0) " · ${if (type == "ON" || type == "OFF") "Transit" else "Period"} ${frames}f" else ""
    return "${index + 1}. $type ${"%.1f".format(Locale.US, beats)}박$colors$frame"
}

private fun formatBeats(tenths: Int) = "%.1f".format(Locale.US, tenths / 10f)

/**
 * 이펙트 라이브러리 관리 패널 (설정 화면의 오른쪽).
 * 단계(이펙트 종류 + 박자 길이 + 색 역할)를 이어 붙여 이름 있는 이펙트를 만든다.
 * 만든 패턴은 내 라이브러리의 칸에 연결해 타임라인에 넣을 수 있고, 연결된 디바이스로 테스트할 수 있다.
 */
@Composable
fun EffectPatternsPanel(
    modifier: Modifier = Modifier,
    patternsVm: PatternsViewModel = activityViewModel(),
    setsVm: SetsViewModel = viewModel(),
    bleVm: BleViewModel = activityBleViewModel()
) {
    val patterns by patternsVm.patterns.collectAsState()
    val paletteSets by setsVm.paletteSets.collectAsState()
    var editing by remember { mutableStateOf<Pair<EffectPattern, Boolean>?>(null) }
    var testing by remember { mutableStateOf<EffectPattern?>(null) }
    var deleting by remember { mutableStateOf<EffectPattern?>(null) }
    val listState = rememberLazyListState()

    fun newPattern() = EffectPattern(patternsVm.newId(), "", listOf(PatternStep()))

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("이펙트 라이브러리", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = { editing = newPattern() to true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("이펙트 추가")
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("이펙트 묶음 만들기", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "예) ON_PULSE = ① ON 0.3박(화이트) → ② ON 0.7박(보조색1, Transit 3). " +
                                    "길이는 박자 단위(소수점 한 자리)이고, 색은 팔레트 세트의 역할로 지정해 팔레트마다 다른 색으로 쓸 수 있습니다. " +
                                    "기본 제공 이펙트는 복제해서 고쳐 쓸 수 있고, 모든 이펙트는 에디터의 내 라이브러리에 나타납니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (patterns.none { !it.builtIn }) {
                    item {
                        Text(
                            "아직 직접 만든 이펙트가 없습니다. \"이펙트 추가\" 로 만들어 보세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                items(patterns, key = { it.id }) { pattern ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(pattern.name, style = MaterialTheme.typography.titleSmall)
                                    if (pattern.builtIn) {
                                        Spacer(Modifier.width(8.dp))
                                        Text("기본 제공", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text(
                                    "${pattern.steps.size}단계 · 총 ${formatBeats(pattern.totalBeatsTenths)}박",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                pattern.steps.forEachIndexed { i, step ->
                                    Text(
                                        step.summary(i),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { testing = pattern }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "디바이스 테스트")
                            }
                            if (!pattern.builtIn) {
                                IconButton(onClick = { editing = pattern to false }) {
                                    Icon(Icons.Default.Edit, contentDescription = "수정")
                                }
                            }
                            IconButton(onClick = {
                                editing = pattern.copy(id = patternsVm.newId(), name = "${pattern.name} 복사본", builtIn = false) to true
                            }) { Icon(Icons.Default.ContentCopy, contentDescription = "복제") }
                            if (!pattern.builtIn) {
                                IconButton(onClick = { deleting = pattern }) {
                                    Icon(Icons.Default.Delete, contentDescription = "삭제")
                                }
                            }
                        }
                    }
                }
            }
            SlimVerticalScrollBar(listState, Modifier.align(Alignment.CenterEnd))
        }
    }

    editing?.let { (pattern, isNew) ->
        PatternEditorDialog(
            initial = pattern,
            isNew = isNew,
            paletteSets = paletteSets,
            bleVm = bleVm,
            onSave = {
                patternsVm.save(it)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
    testing?.let { pattern ->
        AlertDialog(
            onDismissRequest = { testing = null },
            title = { Text("테스트 · ${pattern.name}") },
            text = {
                Column(
                    modifier = Modifier.width(480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pattern.steps.forEachIndexed { i, step ->
                        Text(step.summary(i), style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                    PatternTester(pattern.steps, paletteSets, bleVm)
                }
            },
            confirmButton = { TextButton(onClick = { testing = null }) { Text("닫기") } }
        )
    }
    deleting?.let { pattern ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("이펙트 삭제") },
            text = {
                Text(
                    "'${pattern.name}' 을(를) 삭제할까요? 내 라이브러리에 연결된 칸은 타임라인에 넣을 수 없게 됩니다(칸에서 연결을 바꿔 주세요). " +
                        "이미 타임라인에 넣은 이펙트에는 영향이 없습니다."
                )
            },
            confirmButton = {
                TextButton(onClick = { patternsVm.delete(pattern.id); deleting = null }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } }
        )
    }
}

/** 이펙트(패턴) 추가/수정 */
@Composable
fun PatternEditorDialog(
    initial: EffectPattern,
    isNew: Boolean,
    paletteSets: List<PaletteSet>,
    bleVm: BleViewModel,
    onSave: (EffectPattern) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    val steps = remember { initial.steps.toMutableStateList() }
    val total = steps.sumOf { it.beatsTenths }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "이펙트 추가" else "이펙트 수정") },
        text = {
            Column(
                modifier = Modifier.width(600.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름 (예: ON_PULSE)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "단계 ${steps.size}개 · 총 ${formatBeats(total)}박",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                steps.forEachIndexed { i, step ->
                    StepEditor(
                        index = i,
                        step = step,
                        count = steps.size,
                        onChange = { steps[i] = it },
                        onMove = { delta ->
                            val j = i + delta
                            if (j in steps.indices) { val t = steps[i]; steps[i] = steps[j]; steps[j] = t }
                        },
                        onDelete = { if (steps.size > 1) steps.removeAt(i) }
                    )
                }
                OutlinedButton(
                    onClick = { steps.add(steps.lastOrNull()?.copy() ?: PatternStep()) },
                    enabled = steps.size < MAX_STEPS
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("단계 추가")
                }
                HorizontalDivider()
                Text("디바이스 테스트", style = MaterialTheme.typography.titleSmall)
                PatternTester(steps.toList(), paletteSets, bleVm)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && steps.isNotEmpty(),
                onClick = { onSave(initial.copy(name = name.trim(), steps = steps.toList())) }
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun StepEditor(
    index: Int,
    step: PatternStep,
    count: Int,
    onChange: (PatternStep) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}단계", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "위로")
                }
                IconButton(onClick = { onMove(1) }, enabled = index < count - 1) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "아래로")
                }
                IconButton(onClick = onDelete, enabled = count > 1) {
                    Icon(Icons.Default.Delete, contentDescription = "단계 삭제")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                STEP_TYPES.forEach { t ->
                    FilterChip(
                        selected = step.type == t,
                        onClick = { onChange(step.copy(type = t)) },
                        label = { Text(t) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("길이", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { onChange(step.copy(beatsTenths = (step.beatsTenths - 1).coerceAtLeast(1))) }) {
                    Icon(Icons.Default.Remove, contentDescription = "0.1박 줄이기")
                }
                BeatsField(step.beatsTenths) { onChange(step.copy(beatsTenths = it)) }
                IconButton(onClick = { onChange(step.copy(beatsTenths = (step.beatsTenths + 1).coerceAtMost(MAX_BEATS_TENTHS))) }) {
                    Icon(Icons.Default.Add, contentDescription = "0.1박 늘리기")
                }
                Text("박 (0.1 단위)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (step.type != "OFF") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorRefDropdown("색 (FG)", step.fg) { onChange(step.copy(fg = it)) }
                    if (isTwoColor(step.type)) {
                        ColorRefDropdown("배경 (BG)", step.bg) { onChange(step.copy(bg = it)) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NumberTextField(
                    value = step.frames,
                    onValueChange = { onChange(step.copy(frames = it)) },
                    label = if (step.type == "ON" || step.type == "OFF") "Transit (프레임)" else "Period (프레임)",
                    range = 0..255,
                    modifier = Modifier.weight(1f)
                )
                NumberTextField(
                    value = step.spf,
                    onValueChange = { onChange(step.copy(spf = it)) },
                    label = "SPF",
                    range = 1..255,
                    modifier = Modifier.weight(1f)
                )
                NumberTextField(
                    value = step.fade,
                    onValueChange = { onChange(step.copy(fade = it)) },
                    label = "Fade (밝기 %)",
                    range = 0..100,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "= ${TimelineMath.formatFrames(step.frames, step.spf)}  ·  ${TimelineMath.frameUnitLabel(step.spf)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 박자 길이 입력: 소수점 한 자리 (0.1 ~ 32.0). 입력 중 "0." 같은 값도 허용한다 */
@Composable
private fun BeatsField(tenths: Int, onChange: (Int) -> Unit) {
    var text by remember(tenths) { mutableStateOf(formatBeats(tenths)) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() || it == '.' }
            val dot = filtered.indexOf('.')
            val limited = if (dot >= 0) filtered.take(dot + 2) else filtered   // 소수점 아래 한 자리
            text = limited
            limited.toFloatOrNull()?.let { v ->
                val t = Math.round(v * 10f).coerceIn(1, MAX_BEATS_TENTHS)
                if (t != tenths) onChange(t)
            }
        },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(96.dp)
    )
}

@Composable
private fun ColorRefDropdown(label: String, value: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(PatternColors.label(value))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                PatternColors.OPTIONS.forEach { (key, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(key); open = false })
                }
            }
        }
    }
}

/**
 * 연결된 디바이스로 패턴을 테스트한다.
 * 팔레트 세트를 골라 "보조색1" 같은 역할이 어떤 색으로 나오는지 확인하고, BPM 으로 박자 길이를 정한다.
 */
@Composable
fun PatternTester(steps: List<PatternStep>, paletteSets: List<PaletteSet>, bleVm: BleViewModel) {
    val connected by bleVm.connected.collectAsState()
    val testing by bleVm.patternTesting.collectAsState()
    var paletteId by remember { mutableStateOf("") }
    // 박자 길이(ms)를 기준 값으로 두고, BPM 과 서로 환산해 어느 쪽으로든 수정할 수 있게 한다
    var beatMs by remember { mutableIntStateOf(500) }
    val bpm = Math.round(60000f / beatMs.coerceAtLeast(1))
    var loop by remember { mutableStateOf(true) }
    val palette = paletteSets.firstOrNull { it.id == paletteId }

    DisposableEffect(Unit) { onDispose { bleVm.stopPatternTest() } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SetDropdown(
            label = "팔레트 세트",
            autoLabel = "기본 팔레트 (내장 첫 번째)",
            items = paletteSets,
            selectedId = paletteId,
            idOf = { it.id },
            nameOf = { it.name },
            onSelect = { paletteId = it }
        )
        // 선택한 팔레트의 색 역할 (대표색, 보조색1~3)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(PatternColors.MAIN, PatternColors.SUB1, PatternColors.SUB2, PatternColors.SUB3).forEach { ref ->
                val c = PatternColors.resolve(ref, palette)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(Color(c.r, c.g, c.b))
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                    )
                    Text(PatternColors.label(ref), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        // 이 팔레트에서 각 단계 색이 어떻게 풀리는지 미리 보여 준다
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("색 미리보기", style = MaterialTheme.typography.labelMedium)
            steps.forEach { s ->
                val fg = PatternColors.resolve(s.fg, palette)
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(if (s.type == "OFF") Color.Black else Color(fg.r, fg.g, fg.b))
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberTextField(
                value = bpm, onValueChange = { beatMs = Math.round(60000f / it).coerceIn(MIN_BEAT_MS, MAX_BEAT_MS) },
                label = "BPM", range = 40..240, modifier = Modifier.width(110.dp)
            )
            NumberTextField(
                value = beatMs, onValueChange = { beatMs = it },
                label = "1박 (ms)", range = MIN_BEAT_MS..MAX_BEAT_MS, modifier = Modifier.width(120.dp)
            )
            Checkbox(checked = loop, onCheckedChange = { loop = it })
            Text("반복", style = MaterialTheme.typography.labelLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (testing) {
                Button(
                    onClick = { bleVm.stopPatternTest() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("정지")
                }
            } else {
                Button(
                    enabled = connected != null && steps.isNotEmpty(),
                    onClick = {
                        bleVm.startPatternTest(
                            steps.map { PatternResolver.payload(it, palette) to PatternResolver.durationMs(it, beatMs.toLong()) },
                            loop
                        )
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("디바이스에서 테스트")
                }
            }
            if (connected == null) {
                Text(
                    "디바이스를 연결하면 테스트할 수 있습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
