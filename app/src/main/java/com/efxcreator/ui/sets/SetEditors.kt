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
import com.efxcreator.ui.editor.formatBeatsLabel
import com.efxcreator.ui.components.NumberTextField
import com.efxcreator.music.sets.RuleCase
import com.efxcreator.music.effect.EffectMatchingEngine.FgEngine
import com.efxcreator.model.EffectPattern
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.padding
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
    val connectedDevice by bleVm.connected.collectAsState()
    val deviceConnected = connectedDevice != null
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
 * 이펙트 세트(섹션별 규칙) 추가/수정.
 * 섹션마다 "자동"(엔진 기본 동작)이거나, 직접 지정한 규칙 목록을 쓴다. 규칙은 조건(마디 에너지, 섹션 에너지, 곡 분위기)과
 * 이펙트(엔진 기본 패턴 또는 이펙트 라이브러리의 이펙트)로 이뤄지고, 위에서부터 조건이 맞는 첫 규칙이 적용된다.
 */
@Composable
fun EffectSetEditorDialog(
    initial: EffectRuleSet,
    isNew: Boolean,
    patterns: List<EffectPattern>,
    onSave: (EffectRuleSet) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    // 섹션 -> 규칙 목록 (비어 있으면 "자동")
    val sections = remember {
        mutableStateMapOf<String, List<RuleCase>>().apply {
            SetCatalog.EDITABLE_SECTIONS.forEach { (key, _) ->
                val list = initial.casesFor(key).map { legacyMoodToGenres(it) }.ifEmpty {
                    initial.rules[key]?.let { listOf(RuleCase(it)) }.orEmpty()
                }
                if (list.isNotEmpty()) put(key, list)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "이펙트 세트 추가" else "이펙트 세트 수정") },
        text = {
            Column(
                modifier = Modifier
                    .width(560.dp)
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
                Text(
                    "섹션마다 \"자동\"(아래에 표시된 기본 동작) 또는 직접 정한 규칙을 씁니다. " +
                        "규칙은 마디/섹션 에너지 같은 조건에 따라 이펙트를 고르며, 이펙트 라이브러리에서 만든 이펙트도 쓸 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SetCatalog.EDITABLE_SECTIONS.forEach { (key, label) ->
                    SectionRuleCard(
                        sectionKey = key,
                        sectionLabel = label,
                        cases = sections[key].orEmpty(),
                        patterns = patterns,
                        onChange = { list -> if (list.isEmpty()) sections.remove(key) else sections[key] = list }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    // 조건 없이 엔진 기본 패턴 하나뿐인 섹션은 예전 형식(rules)으로, 나머지는 조건부 규칙(cases)으로 저장한다
                    val rules = mutableMapOf<String, String>()
                    val cases = mutableMapOf<String, List<RuleCase>>()
                    sections.forEach { (key, list) ->
                        val only = list.singleOrNull()
                        if (only != null && !only.hasCondition && only.engine != null) rules[key] = only.effect
                        else cases[key] = list
                    }
                    onSave(initial.copy(name = name.trim(), rules = rules, cases = cases.ifEmpty { null }, builtIn = false))
                }
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/** 섹션 하나의 규칙 편집: 자동/직접 지정 전환, 기본 동작 표시, 규칙 목록 */
@Composable
private fun SectionRuleCard(
    sectionKey: String,
    sectionLabel: String,
    cases: List<RuleCase>,
    patterns: List<EffectPattern>,
    onChange: (List<RuleCase>) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sectionLabel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = cases.isEmpty(),
                    onClick = { onChange(emptyList()) },
                    label = { Text("자동") }
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = cases.isNotEmpty(),
                    onClick = {
                        if (cases.isEmpty()) {
                            val start = SetCatalog.DEFAULT_RULES[sectionKey] ?: FgEngine.BREATH.name
                            onChange(listOf(RuleCase(start)))
                        }
                    },
                    label = { Text("직접 지정") }
                )
            }
            // 자동일 때 엔진이 실제로 하는 동작
            Text(
                "자동: " + (SetCatalog.DEFAULT_BEHAVIOR[sectionKey] ?: "-"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (cases.isNotEmpty()) {
                cases.forEachIndexed { i, case ->
                    CaseEditor(
                        index = i,
                        case = case,
                        count = cases.size,
                        patterns = patterns,
                        onChange = { c -> onChange(cases.toMutableList().also { it[i] = c }) },
                        onMove = { delta ->
                            val j = i + delta
                            if (j in cases.indices) {
                                onChange(cases.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t })
                            }
                        },
                        onDelete = { onChange(cases.toMutableList().also { it.removeAt(i) }) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onChange(cases + RuleCase(FgEngine.BREATH.name)) }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("규칙 추가")
                    }
                    if (sectionKey == "VERSE") {
                        TextButton(onClick = { onChange(SetCatalog.DEFAULT_VERSE_CASES) }) { Text("기본 동작으로 채우기") }
                    }
                }
                Text(
                    "위에서부터 조건이 맞는 첫 규칙이 쓰입니다. 조건이 없는 규칙은 \"그 외 모두\"이고, 맞는 규칙이 없으면 자동 동작을 따릅니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CaseEditor(
    index: Int,
    case: RuleCase,
    count: Int,
    patterns: List<EffectPattern>,
    onChange: (RuleCase) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("규칙 ${index + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { onMove(-1) }, enabled = index > 0) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "위로")
                }
                IconButton(onClick = { onMove(1) }, enabled = index < count - 1) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "아래로")
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "규칙 삭제") }
            }
            EffectChoice(case.effect, patterns) { onChange(case.copy(effect = it)) }

            // 조건: 곡 분위기(장르). 아무것도 고르지 않은 "전체" 가 기본(그 외 모두)이고, 여러 장르를 함께 고를 수 있다
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("곡 분위기", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(76.dp))
                FilterChip(
                    selected = case.genres == null,
                    onClick = { onChange(case.copy(genres = null, mood = null)) },
                    label = { Text("전체") }
                )
                SetCatalog.GENRES.forEach { (key, label) ->
                    val selected = case.genres?.contains(key) == true
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val next = (case.genres.orEmpty()).let { if (selected) it - key else it + key }
                            onChange(case.copy(genres = next.ifEmpty { null }, mood = null))
                        },
                        label = { Text(label) }
                    )
                }
            }
            // 조건: 마디 에너지 / 섹션 에너지
            EnergyCondition(
                label = "마디 에너지",
                hint = "섹션 안에서의 순위 (100% = 가장 센 마디)",
                min = case.minBarEnergy, max = case.maxBarEnergy,
                onChange = { mn, mx -> onChange(case.copy(minBarEnergy = mn, maxBarEnergy = mx)) }
            )
            EnergyCondition(
                label = "섹션 에너지",
                hint = "곡 전체 섹션 중 상대 에너지 (100% = 가장 센 섹션)",
                min = case.minSectionEnergy, max = case.maxSectionEnergy,
                onChange = { mn, mx -> onChange(case.copy(minSectionEnergy = mn, maxSectionEnergy = mx)) }
            )
            Text(
                case.describe(patterns),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 에너지 범위 조건: 체크하면 이상/이하(%) 입력칸이 나타난다 */
@Composable
private fun EnergyCondition(label: String, hint: String, min: Int?, max: Int?, onChange: (Int?, Int?) -> Unit) {
    val enabled = min != null || max != null
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = enabled, onCheckedChange = { on -> if (on) onChange(50, null) else onChange(null, null) })
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (enabled) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 40.dp)
            ) {
                NumberTextField(
                    value = min ?: 0, onValueChange = { onChange(it, max) },
                    label = "이상 (%)", range = 0..100, modifier = Modifier.width(110.dp)
                )
                Text("~", style = MaterialTheme.typography.bodyMedium)
                NumberTextField(
                    value = max ?: 100, onValueChange = { onChange(min, it) },
                    label = "이하 (%)", range = 0..100, modifier = Modifier.width(110.dp)
                )
            }
        }
    }
}

/** 이펙트 선택: 엔진 기본 패턴 + 이펙트 라이브러리의 이펙트 */
@Composable
private fun EffectChoice(effect: String, patterns: List<EffectPattern>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(effectLabel(effect, patterns), modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                "엔진 기본 패턴", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            SetCatalog.PATTERNS.forEach { info ->
                DropdownMenuItem(
                    text = { Column { Text(info.label); Text(info.description, style = MaterialTheme.typography.labelSmall) } },
                    onClick = { onSelect(info.engine.name); expanded = false }
                )
            }
            if (patterns.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    "이펙트 라이브러리", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                patterns.forEach { p ->
                    DropdownMenuItem(
                        text = { Column { Text(p.name); Text("${p.steps.size}단계 · ${formatBeatsLabel(p.totalBeatsTenths)}", style = MaterialTheme.typography.labelSmall) } },
                        onClick = { onSelect(RuleCase.library(p.id)); expanded = false }
                    )
                }
            }
        }
    }
}

private fun effectLabel(effect: String, patterns: List<EffectPattern>): String {
    if (effect.startsWith(RuleCase.PATTERN_PREFIX)) {
        val id = effect.removePrefix(RuleCase.PATTERN_PREFIX)
        return patterns.firstOrNull { it.id == id }?.let { "라이브러리 · ${it.name}" } ?: "(삭제된 이펙트)"
    }
    return SetCatalog.patternLabel(effect)
}

/** "마디 에너지 80% 이상 · 비발라드 → BLINK" */
fun RuleCase.describe(patterns: List<EffectPattern>): String {
    val conds = buildList {
        if (minBarEnergy != null || maxBarEnergy != null) add("마디 에너지 ${rangeText(minBarEnergy, maxBarEnergy)}")
        if (minSectionEnergy != null || maxSectionEnergy != null) add("섹션 에너지 ${rangeText(minSectionEnergy, maxSectionEnergy)}")
        genres?.let { add(it.joinToString("/") { g -> SetCatalog.genreLabel(g) }) }
        when (mood) { "BALLAD" -> add("발라드·힙합/R&B"); "NON_BALLAD" -> add("발라드 외") }
    }
    val target = effectLabel(effect, patterns)
    return if (conds.isEmpty()) "그 외 모두 → $target" else conds.joinToString(" · ") + " → $target"
}

private fun rangeText(min: Int?, max: Int?): String = when {
    min != null && max != null -> "$min~$max%"
    min != null -> "$min% 이상"
    else -> "$max% 이하"
}

/** 이전 버전의 "발라드 / 발라드 외" 조건을 장르 선택으로 바꿔 편집할 수 있게 한다 */
private fun legacyMoodToGenres(case: RuleCase): RuleCase = when (case.mood) {
    "BALLAD" -> case.copy(mood = null, genres = SetCatalog.BALLAD_LIKE_GENRES)
    "NON_BALLAD" -> case.copy(mood = null, genres = SetCatalog.GENRES.map { it.first } - SetCatalog.BALLAD_LIKE_GENRES.toSet())
    else -> case
}
