package com.efxcreator.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.efxcreator.model.EffectPattern
import com.efxcreator.ui.theme.EfxMotion
import com.lightstick.types.EffectType
import com.efxcreator.R
import com.efxcreator.core.Loc

/** 라이브러리 카드를 타임라인으로 끌고 가는 동안의 드래그 상태 (윈도우 좌표) */
class PresetDragState {
    var preset by mutableStateOf<EffectPreset?>(null)
    var pointerWindow by mutableStateOf(Offset.Zero)
}

fun EffectPreset.swatchColor(): Color =
    if (type == EffectType.OFF) Color(0xFF2A2A30) else Color(color.r, color.g, color.b)

fun EffectPreset.swatchBgColor(): Color = Color(backgroundColor.r, backgroundColor.g, backgroundColor.b)

/**
 * 이펙트 라이브러리.
 * - "내 라이브러리": 설정의 "이펙트 라이브러리" 에서 만든 이펙트만 보여 준다 (이름 아래에 몇 비트짜리인지 표기).
 *   이펙트는 설정에서 추가/수정/삭제한다.
 * - 나머지 탭: 기본 제공 프리셋.
 * - 카드 탭: 현재 재생 위치에 추가 / 길게 눌러 끌기: 타임라인의 원하는 위치에 드롭
 */
@Composable
fun EffectLibraryPanel(
    drag: PresetDragState,
    patterns: List<EffectPattern>,
    onTapPreset: (EffectPreset) -> Unit,
    onDrop: () -> Unit,
    onManageEffects: () -> Unit,
    modifier: Modifier = Modifier
) {
    var category by remember {
        mutableStateOf(
            if (patterns.isNotEmpty()) EffectPreset.Category.USER else EffectPreset.Category.ON
        )
    }
    val patternPresets = remember(patterns) { patterns.map { it.toPreset() } }

    Column(modifier = modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Loc.str(R.string.settings_screen_17), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(12.dp))
            Text(
                Loc.str(R.string.effect_library_panel_1),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EffectPreset.Category.entries.forEach { c ->
                    FilterChip(
                        selected = c == category,
                        onClick = { category = c },
                        label = { Text(c.label) }
                    )
                }
            }
            if (category == EffectPreset.Category.USER) {
                Spacer(Modifier.weight(1f))
                Text(
                    Loc.str(R.string.effect_library_panel_2, patterns.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                // 이펙트의 추가/수정/삭제는 설정의 "이펙트 라이브러리" 에서 한다
                AssistChip(
                    onClick = onManageEffects,
                    label = { Text(Loc.str(R.string.effect_library_panel_3)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // 탭을 바꿀 때 Material 3 fade through 로 내용을 교체한다
        AnimatedContent(
            targetState = category,
            transitionSpec = { EfxMotion.fadeThrough() },
            modifier = Modifier.fillMaxSize(),
            label = "libraryTab"
        ) { cat ->
            val presets = if (cat == EffectPreset.Category.USER) patternPresets else EffectPresets.byCategory(cat)
            val scroll = rememberScrollState()
            Box(modifier = Modifier.fillMaxSize()) {
                if (presets.isEmpty()) {
                    Text(
                        Loc.str(R.string.effect_library_panel_4),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 14.dp)   // 오른쪽 스크롤 막대 자리
                        .verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.chunked(COLUMNS).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { preset ->
                                Box(modifier = Modifier.weight(1f)) {
                                    PresetCard(
                                        preset = preset,
                                        drag = drag,
                                        onTap = { onTapPreset(preset) },
                                        onDrop = onDrop
                                    )
                                }
                            }
                            repeat(COLUMNS - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                SlimVerticalScrollBar(scroll, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

/** 한 줄에 놓는 칸 수 */
private const val COLUMNS = 6

private val SLOT_HEIGHT = 48.dp

@Composable
private fun SlotContent(preset: EffectPreset) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PresetSwatch(preset)
        Spacer(Modifier.width(6.dp))
        // "Red / Black" 처럼 FG / BG 로 이루어진 이름은 두 줄(위 FG, 아래 BG)로 나눠 표시
        // 내 라이브러리 칸은 이름 대신 이펙트 종류만 보여 준다 (색은 왼쪽 원으로 구분)
        val lines = when {
            // 이펙트 라이브러리(패턴): 1줄 제목 + 아래에 몇 비트짜리인지
            preset.subLabel != null -> listOf(preset.label, preset.subLabel)
            preset.isTwoColor && " / " in preset.label -> preset.label.split(" / ", limit = 2)
            else -> listOf(preset.label)
        }
        Column(modifier = Modifier.weight(1f)) {
            lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 기본 프리셋 칸: "내 라이브러리" 칸과 같은 크기/모양 */
@Composable
private fun PresetCard(
    preset: EffectPreset,
    drag: PresetDragState,
    onTap: () -> Unit,
    onDrop: () -> Unit
) {
    var originWindow by remember { mutableStateOf(Offset.Zero) }
    // pointerInput 블록은 키가 바뀔 때만 다시 시작되므로, 편집 모드 같은 최신 상태를 읽도록 항상 최신 람다를 참조한다
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDrop by rememberUpdatedState(onDrop)

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(SLOT_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
            .onGloballyPositioned { originWindow = it.positionInWindow() }
            .pointerInput(preset.id) { detectTapGestures(onTap = { currentOnTap() }) }
            .pointerInput(preset.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        drag.preset = preset
                        drag.pointerWindow = originWindow + offset
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag.pointerWindow += amount
                    },
                    onDragEnd = { currentOnDrop() },
                    onDragCancel = { drag.preset = null }
                )
            }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        SlotContent(preset)
    }
}

/**
 * 스와치 원: 1색 이펙트는 단색, BLINK/STROBE 는 왼쪽 FG · 오른쪽 BG 로 반씩,
 * BREATH 도 반씩 나누되 경계 부분만 FG → BG 그라데이션으로 부드럽게 이어 준다
 */
@Composable
fun PresetSwatch(preset: EffectPreset, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    ) {
        when {
            preset.type == EffectType.BREATH -> {
                val fg = preset.swatchColor()
                val bg = preset.swatchBgColor()
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0f to fg, 0.3f to fg, 0.7f to bg, 1f to bg)
                    )
                )
            }
            preset.isTwoColor -> Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(preset.swatchColor()))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(preset.swatchBgColor()))
            }
            else -> Box(modifier = Modifier.fillMaxSize().background(preset.swatchColor()))
        }
    }
}
