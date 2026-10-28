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
import com.efxcreator.R
import com.efxcreator.core.Loc

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
    return Loc.str(R.string.pattern_panel_1, index + 1, type, "%.1f".format(Locale.US, beats), colors, frame)
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
            Text(Loc.str(R.string.settings_screen_17), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = { editing = newPattern() to true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(Loc.str(R.string.pattern_panel_2))
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
                            Text(Loc.str(R.string.pattern_panel_3), style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                Loc.str(R.string.pattern_panel_4),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (patterns.none { !it.builtIn }) {
                    item {
                        Text(
                            Loc.str(R.string.pattern_panel_5),
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
                                        Text(Loc.str(R.string.pattern_panel_6), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                if (pattern.description.isNotEmpty()) {
                                    Text(
                                        pattern.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    Loc.str(R.string.pattern_panel_7, pattern.steps.size, formatBeats(pattern.totalBeatsTenths)),
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
                                Icon(Icons.Default.PlayArrow, contentDescription = Loc.str(R.string.pattern_panel_8))
                            }
                            if (!pattern.builtIn) {
                                IconButton(onClick = { editing = pattern to false }) {
                                    Icon(Icons.Default.Edit, contentDescription = Loc.str(R.string.pattern_panel_9))
                                }
                            }
                            IconButton(onClick = {
                                editing = pattern.copy(id = patternsVm.newId(), name = Loc.str(R.string.pattern_panel_10, pattern.name), builtIn = false) to true
                            }) { Icon(Icons.Default.ContentCopy, contentDescription = Loc.str(R.string.pattern_panel_11)) }
                            if (!pattern.builtIn) {
                                IconButton(onClick = { deleting = pattern }) {
                                    Icon(Icons.Default.Delete, contentDescription = Loc.str(R.string.efx_edit_screen_15))
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
            title = { Text(Loc.str(R.string.pattern_panel_12, pattern.name)) },
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
            confirmButton = { TextButton(onClick = { testing = null }) { Text(Loc.str(R.string.efx_edit_screen_6)) } }
        )
    }
    deleting?.let { pattern ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(Loc.str(R.string.pattern_panel_13)) },
            text = {
                Text(
                    Loc.str(R.string.pattern_panel_14, pattern.name)
                )
            },
            confirmButton = {
                TextButton(onClick = { patternsVm.delete(pattern.id); deleting = null }) {
                    Text(Loc.str(R.string.efx_edit_screen_15), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(Loc.str(R.string.efx_edit_screen_16)) } }
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
        title = { Text(if (isNew) Loc.str(R.string.pattern_panel_2) else Loc.str(R.string.pattern_panel_15)) },
        text = {
            Column(
                modifier = Modifier.width(600.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(Loc.str(R.string.pattern_panel_16)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    Loc.str(R.string.pattern_panel_17, steps.size, formatBeats(total)),
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
                    Text(Loc.str(R.string.pattern_panel_18))
                }
                HorizontalDivider()
                Text(Loc.str(R.string.pattern_panel_8), style = MaterialTheme.typography.titleSmall)
                PatternTester(steps.toList(), paletteSets, bleVm)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && steps.isNotEmpty(),
                onClick = { onSave(initial.copy(name = name.trim(), steps = steps.toList())) }
            ) { Text(Loc.str(R.string.efx_edit_screen_21)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Loc.str(R.string.efx_edit_screen_16)) } }
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
                Text(Loc.str(R.string.pattern_panel_19, index + 1), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = Loc.str(R.string.pattern_panel_20))
                }
                IconButton(onClick = { onMove(1) }, enabled = index < count - 1) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = Loc.str(R.string.pattern_panel_21))
                }
                IconButton(onClick = onDelete, enabled = count > 1) {
                    Icon(Icons.Default.Delete, contentDescription = Loc.str(R.string.pattern_panel_22))
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
                Text(Loc.str(R.string.pattern_panel_23), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { onChange(step.copy(beatsTenths = (step.beatsTenths - 1).coerceAtLeast(1))) }) {
                    Icon(Icons.Default.Remove, contentDescription = Loc.str(R.string.pattern_panel_24))
                }
                BeatsField(step.beatsTenths) { onChange(step.copy(beatsTenths = it)) }
                IconButton(onClick = { onChange(step.copy(beatsTenths = (step.beatsTenths + 1).coerceAtMost(MAX_BEATS_TENTHS))) }) {
                    Icon(Icons.Default.Add, contentDescription = Loc.str(R.string.pattern_panel_25))
                }
                Text(Loc.str(R.string.pattern_panel_26), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (step.type != "OFF") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorRefDropdown(Loc.str(R.string.pattern_panel_27), step.fg) { onChange(step.copy(fg = it)) }
                    if (isTwoColor(step.type)) {
                        ColorRefDropdown(Loc.str(R.string.efx_tablet_editor_screen_40), step.bg) { onChange(step.copy(bg = it)) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NumberTextField(
                    value = step.frames,
                    onValueChange = { onChange(step.copy(frames = it)) },
                    label = if (step.type == "ON" || step.type == "OFF") Loc.str(R.string.pattern_panel_28) else Loc.str(R.string.pattern_panel_29),
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
                    label = Loc.str(R.string.pattern_panel_30),
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
            label = Loc.str(R.string.settings_screen_21),
            autoLabel = Loc.str(R.string.pattern_panel_31),
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
            Text(Loc.str(R.string.pattern_panel_32), style = MaterialTheme.typography.labelMedium)
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
                label = Loc.str(R.string.pattern_panel_33), range = MIN_BEAT_MS..MAX_BEAT_MS, modifier = Modifier.width(120.dp)
            )
            Checkbox(checked = loop, onCheckedChange = { loop = it })
            Text(Loc.str(R.string.pattern_panel_34), style = MaterialTheme.typography.labelLarge)
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
                    Text(Loc.str(R.string.pattern_panel_35))
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
                    Text(Loc.str(R.string.pattern_panel_36))
                }
            }
            if (connected == null) {
                Text(
                    Loc.str(R.string.pattern_panel_37),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
