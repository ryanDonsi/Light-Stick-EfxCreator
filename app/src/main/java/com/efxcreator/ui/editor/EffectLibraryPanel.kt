package com.efxcreator.ui.editor

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
 * - "내 라이브러리": 사용자가 등록한 이펙트 [UserEffect.SLOT_COUNT] 칸 (7칸 x 3줄). 빈 칸을 누르면 추가,
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
    val presets = remember(category) { EffectPresets.byCategory(category) }

    Column(modifier = modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("이펙트 라이브러리", style = MaterialTheme.typography.titleSmall, color = Color.White)
            Spacer(Modifier.width(12.dp))
            Text(
                "탭 = 재생 위치에 추가 · 길게 눌러 끌기 = 원하는 위치에 놓기",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9A9AA5)
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
                        color = Color(0xFF9A9AA5)
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    "${userSlots.count { it != null }} / ${UserEffect.SLOT_COUNT}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFB0B0BA)
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

        if (category == EffectPreset.Category.USER) {
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(presets, key = { it.id }) { preset ->
                    PresetCard(
                        preset = preset,
                        drag = drag,
                        onTap = { onTapPreset(preset) },
                        onDrop = onDrop
                    )
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { gridOrigin = it.positionInWindow() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
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
                                    if (isTarget) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
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
                    .background(Color(0xE63A3A48), RoundedCornerShape(8.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                SlotContent(ghostPreset)
            }
        }
    }
}

private val SLOT_HEIGHT = 64.dp

@Composable
private fun EmptySlot(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(SLOT_HEIGHT)
            .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Icon(Icons.Default.Add, contentDescription = "이펙트 추가", tint = Color(0x88FFFFFF))
    }
}

@Composable
private fun SlotContent(preset: EffectPreset) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PresetSwatch(preset)
        Spacer(Modifier.height(4.dp))
        Text(
            preset.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
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
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(SLOT_HEIGHT)
            .background(Color(0xFF26262E).copy(alpha = if (dimmed) 0.35f else 1f), RoundedCornerShape(8.dp))
            .then(
                if (editMode) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
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
            .padding(horizontal = 4.dp, vertical = 6.dp)
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

@Composable
private fun PresetCard(
    preset: EffectPreset,
    drag: PresetDragState,
    onTap: () -> Unit,
    onDrop: () -> Unit
) {
    var originWindow by remember { mutableStateOf(Offset.Zero) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFF26262E), RoundedCornerShape(8.dp))
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
            .padding(horizontal = 10.dp)
    ) {
        PresetSwatch(preset)
        Spacer(Modifier.width(8.dp))
        Text(
            preset.label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1
        )
    }
}

/** 1색 이펙트는 원 하나, 2색 이펙트(BLINK/BREATH/STROBE)는 왼쪽 FG · 오른쪽 BG 로 반씩 나눈 원 */
@Composable
fun PresetSwatch(preset: EffectPreset, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .border(1.dp, Color(0x55FFFFFF), CircleShape)
    ) {
        if (preset.isTwoColor) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(preset.swatchColor()))
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(preset.swatchBgColor()))
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(preset.swatchColor()))
        }
    }
}
