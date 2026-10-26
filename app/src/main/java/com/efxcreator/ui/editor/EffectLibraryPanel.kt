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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.efxcreator.model.UserEffect
import com.efxcreator.ui.theme.EfxMotion
import com.lightstick.types.EffectType

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
 * - "내 라이브러리": 사용자가 등록한 이펙트 [UserEffect.SLOT_COUNT] 칸 (6칸 x 4줄). 빈 칸을 누르면 추가,
 *   "편집"을 켜고 칸을 누르면 수정/삭제, 편집 중 칸을 길게 눌러 끌면 순서 변경(서로 바꾸기/빈 칸으로 이동).
 * - 나머지 탭: 기본 제공 프리셋.
 * - 카드 탭: 현재 재생 위치에 추가 / 길게 눌러 끌기: 타임라인의 원하는 위치에 드롭
 */
@Composable
fun EffectLibraryPanel(
    drag: PresetDragState,
    userSlots: List<UserEffect?>,
    onTapPreset: (EffectPreset) -> Unit,
    onDrop: () -> Unit,
    onUserSlotClick: (slot: Int) -> Unit,
    onMoveSlot: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var category by remember {
        mutableStateOf(
            if (userSlots.any { it != null }) EffectPreset.Category.USER else EffectPreset.Category.ON
        )
    }
    var editMode by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("이펙트 라이브러리", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(12.dp))
            Text(
                "탭 = 재생 위치에 추가 · 길게 눌러 끌기 = 원하는 위치에 놓기",
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
                if (editMode) {
                    Text(
                        "탭 = 수정 · 길게 눌러 끌기 = 순서 바꾸기",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    "${userSlots.count { it != null }} / ${UserEffect.SLOT_COUNT}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = editMode,
                    onClick = { editMode = !editMode },
                    label = { Text("편집") },
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
            if (cat == EffectPreset.Category.USER) {
                UserLibraryGrid(
                    slots = userSlots,
                    drag = drag,
                    editMode = editMode,
                    onTapPreset = onTapPreset,
                    onDrop = onDrop,
                    onSlotClick = onUserSlotClick,
                    onMoveSlot = onMoveSlot
                )
            } else {
                // "내 라이브러리" 와 같은 6칸 격자 + 같은 스크롤 막대
                val presetScroll = rememberScrollState()
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 14.dp)
                            .verticalScroll(presetScroll),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EffectPresets.byCategory(cat).chunked(UserEffect.COLUMNS).forEach { rowItems ->
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
                                repeat(UserEffect.COLUMNS - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    SlimVerticalScrollBar(presetScroll, Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    }
}

/** 7칸 x 3줄 고정 격자. 빈 칸은 "+" */
@Composable
private fun UserLibraryGrid(
    slots: List<UserEffect?>,
    drag: PresetDragState,
    editMode: Boolean,
    onTapPreset: (EffectPreset) -> Unit,
    onDrop: () -> Unit,
    onSlotClick: (Int) -> Unit,
    onMoveSlot: (Int, Int) -> Unit
) {
    val density = LocalDensity.current

    // ── 순서 바꾸기(편집 모드에서 칸을 길게 눌러 끌기) 상태 ──
    val slotBounds = remember { HashMap<Int, Rect>() }          // 칸별 윈도우 좌표 (레이아웃 때 갱신, 상태 아님)
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    var reorderFrom by remember { mutableIntStateOf(-1) }
    var reorderTarget by remember { mutableIntStateOf(-1) }
    var reorderOffset by remember { mutableStateOf(Offset.Zero) } // 끌기 시작점 대비 누적 이동량
    var pointerWindow by remember { mutableStateOf(Offset.Zero) }

    fun targetAt(p: Offset): Int =
        slotBounds.entries.firstOrNull { it.value.contains(p) }?.key ?: -1

    fun resetReorder() {
        reorderFrom = -1
        reorderTarget = -1
        reorderOffset = Offset.Zero
    }

    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { gridOrigin = it.positionInWindow() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 14.dp)   // 오른쪽 스크롤 막대 자리
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (row in 0 until UserEffect.ROWS) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until UserEffect.COLUMNS) {
                        val slot = row * UserEffect.COLUMNS + col
                        val effect = slots.getOrNull(slot)
                        val isSource = reorderFrom == slot
                        val isTarget = reorderTarget == slot && reorderFrom >= 0 && reorderFrom != slot

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .onGloballyPositioned { slotBounds[slot] = it.boundsInWindow() }
                                .then(
                                    if (isTarget) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                                    else Modifier
                                )
                        ) {
                            if (effect == null) {
                                EmptySlot(onClick = { onSlotClick(slot) })
                            } else {
                                val preset = remember(effect) { effect.toPreset() }
                                SlotTile(
                                    preset = preset,
                                    drag = drag,
                                    editMode = editMode,
                                    dimmed = isSource,
                                    onTap = { if (editMode) onSlotClick(slot) else onTapPreset(preset) },
                                    onDrop = onDrop,
                                    onReorderStart = { offset ->
                                        reorderFrom = slot
                                        reorderTarget = slot
                                        reorderOffset = Offset.Zero
                                        pointerWindow = (slotBounds[slot]?.topLeft ?: Offset.Zero) + offset
                                    },
                                    onReorderDrag = { amount ->
                                        reorderOffset += amount
                                        pointerWindow += amount
                                        reorderTarget = targetAt(pointerWindow)
                                    },
                                    onReorderEnd = {
                                        val from = reorderFrom
                                        val to = reorderTarget
                                        resetReorder()
                                        if (from >= 0 && to >= 0 && from != to) onMoveSlot(from, to)
                                    },
                                    onReorderCancel = { resetReorder() }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 끄는 동안 손가락을 따라다니는 칸 (원래 칸은 흐리게 남겨 둔다)
        val sourceEffect = slots.getOrNull(reorderFrom)
        val sourceBounds = slotBounds[reorderFrom]
        if (sourceEffect != null && sourceBounds != null) {
            val ghostPreset = remember(sourceEffect) { sourceEffect.toPreset() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (sourceBounds.left - gridOrigin.x + reorderOffset.x).roundToInt(),
                            (sourceBounds.top - gridOrigin.y + reorderOffset.y).roundToInt()
                        )
                    }
                    .size(
                        with(density) { sourceBounds.width.toDp() },
                        with(density) { sourceBounds.height.toDp() }
                    )
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
                    .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                contentAlignment = Alignment.CenterStart
            ) {
                SlotContent(ghostPreset)
            }
        }

        SlimVerticalScrollBar(scrollState, Modifier.align(Alignment.CenterEnd))
    }
}

private val SLOT_HEIGHT = 48.dp

@Composable
private fun EmptySlot(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(SLOT_HEIGHT)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
    ) {
        Icon(Icons.Default.Add, contentDescription = "이펙트 추가", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SlotContent(preset: EffectPreset) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PresetSwatch(preset)
        Spacer(Modifier.width(6.dp))
        // "Red / Black" 처럼 FG / BG 로 이루어진 이름은 두 줄(위 FG, 아래 BG)로 나눠 표시
        // 내 라이브러리 칸은 이름 대신 이펙트 종류와 색상을 보여 준다 (2색은 FG / BG 순서)
        val lines = when {
            preset.category == EffectPreset.Category.USER -> listOf(
                preset.type.name,
                if (preset.type == EffectType.OFF) "—"
                else if (preset.isTwoColor) "${EffectPresets.colorName(preset.color)}/${EffectPresets.colorName(preset.backgroundColor)}"
                else EffectPresets.colorName(preset.color)
            )
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

/**
 * 등록된 칸: 스와치 + 이름.
 * - 일반 모드: 탭 = 추가, 길게 눌러 끌기 = 타임라인에 드롭
 * - 편집 모드: 탭 = 수정 창, 길게 눌러 끌기 = 칸 순서 바꾸기
 */
@Composable
private fun SlotTile(
    preset: EffectPreset,
    drag: PresetDragState,
    editMode: Boolean,
    dimmed: Boolean,
    onTap: () -> Unit,
    onDrop: () -> Unit,
    onReorderStart: (Offset) -> Unit,
    onReorderDrag: (Offset) -> Unit,
    onReorderEnd: () -> Unit,
    onReorderCancel: () -> Unit
) {
    var originWindow by remember { mutableStateOf(Offset.Zero) }

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(SLOT_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (dimmed) 0.35f else 1f), MaterialTheme.shapes.small)
            .then(
                if (editMode) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                else Modifier
            )
            .onGloballyPositioned { originWindow = it.positionInWindow() }
            .pointerInput(preset.id) { detectTapGestures(onTap = { onTap() }) }
            .pointerInput(preset.id, editMode) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        if (editMode) {
                            onReorderStart(offset)
                        } else {
                            drag.preset = preset
                            drag.pointerWindow = originWindow + offset
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        if (editMode) onReorderDrag(amount) else drag.pointerWindow += amount
                    },
                    onDragEnd = { if (editMode) onReorderEnd() else onDrop() },
                    onDragCancel = { if (editMode) onReorderCancel() else drag.preset = null }
                )
            }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        SlotContent(preset)
        if (editMode) {
            Icon(
                Icons.Default.Edit, contentDescription = "수정",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
            )
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

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(SLOT_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
            .onGloballyPositioned { originWindow = it.positionInWindow() }
            .pointerInput(preset.id) { detectTapGestures(onTap = { onTap() }) }
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
                    onDragEnd = { onDrop() },
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
