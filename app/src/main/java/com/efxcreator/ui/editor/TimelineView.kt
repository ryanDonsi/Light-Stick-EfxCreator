package com.efxcreator.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.efxcreator.music.MusicAnalysis
import com.lightstick.efx.EfxEntry
import com.lightstick.types.EffectType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.efxcreator.model.EfxMoves
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** 타임라인 뷰포트 (가로 스크롤 위치 + 줌). 화면과 [TimelineView] 가 공유한다. */
class TimelineState {
    /** 화면 왼쪽 끝이 가리키는 시각(ms) */
    var viewStartMs by mutableFloatStateOf(0f)
    /** 1ms 당 픽셀 수 (0.06 = 60px/초) */
    var pxPerMs by mutableFloatStateOf(0.06f)
    var viewWidthPx by mutableFloatStateOf(0f)
    /** 곡 길이(ms). 최대 축소 한계(전체 보기)를 정하는 데 쓴다. */
    var durationMs by mutableLongStateOf(0L)

    companion object {
        const val MIN_PX_PER_MS = 0.008f
        const val MAX_PX_PER_MS = 0.6f
    }

    /** 가장 많이 축소한 상태 = 곡 전체가 화면 너비에 딱 맞는 "전체 보기" */
    val minPxPerMs: Float
        get() = if (viewWidthPx > 0f && durationMs > 0L) {
            (viewWidthPx / durationMs).coerceAtMost(MAX_PX_PER_MS)
        } else MIN_PX_PER_MS

    /** 보이는 구간 길이(ms) */
    val viewMs: Float get() = if (pxPerMs > 0f) viewWidthPx / pxPerMs else 0f

    /** 스크롤 한계: 화면 오른쪽 끝이 곡의 끝에 닿는 위치 */
    val maxStartMs: Float get() = max(0f, durationMs - viewMs)

    /** 줌 범위 밖이면 범위 안으로 되돌린다 (곡이나 화면 크기가 바뀐 경우) */
    fun clampZoom() {
        val clamped = pxPerMs.coerceIn(minPxPerMs, MAX_PX_PER_MS)
        if (clamped != pxPerMs) {
            pxPerMs = clamped
        }
        viewStartMs = viewStartMs.coerceIn(0f, maxStartMs)
    }

    fun msAtX(x: Float): Float = viewStartMs + x / pxPerMs

    fun zoom(factor: Float, anchorX: Float = viewWidthPx / 2f) {
        val anchorMs = msAtX(anchorX)
        pxPerMs = (pxPerMs * factor).coerceIn(minPxPerMs, MAX_PX_PER_MS)
        viewStartMs = (anchorMs - anchorX / pxPerMs).coerceIn(0f, maxStartMs)
    }

    /**
     * 핀치 제스처: 이전 중심점(prevCentroidX) 아래에 있던 시각이 새 중심점(centroidX) 아래에
     * 오도록 줌과 스크롤을 함께 갱신한다 (손가락이 잡은 지점이 따라 움직임).
     */
    fun pinch(scale: Float, prevCentroidX: Float, centroidX: Float) {
        val anchorMs = msAtX(prevCentroidX)
        pxPerMs = (pxPerMs * scale).coerceIn(minPxPerMs, MAX_PX_PER_MS)
        viewStartMs = (anchorMs - centroidX / pxPerMs).coerceIn(0f, maxStartMs)
    }

    fun panByMs(deltaMs: Float, totalMs: Long) {
        viewStartMs = (viewStartMs + deltaMs).coerceIn(0f, max(0f, totalMs - viewMs))
    }
}

object SectionColors {
    fun of(type: String): Color = when (type) {
        "INTRO" -> Color(0xFF5C6BC0)
        "VERSE" -> Color(0xFF26A69A)
        "CHORUS" -> Color(0xFFEF5350)
        "BRIDGE" -> Color(0xFFFFA726)
        "CLIMAX" -> Color(0xFFAB47BC)
        "BREAK" -> Color(0xFF78909C)
        "OUTRO" -> Color(0xFF8D6E63)
        else -> Color(0xFF455A64) // END
    }
}

// 위 → 아래: 시간 / 파형 / 섹션 / 비트 / 이펙트 블록
private val RULER_H = 22.dp
private val WAVE_H = 96.dp
private val SECTION_H = 22.dp
private val BEAT_H = 22.dp
private val LANE_GAP = 6.dp
private val LANE_H = 72.dp
val TIMELINE_TOTAL_HEIGHT = RULER_H + WAVE_H + SECTION_H + BEAT_H + LANE_GAP + LANE_H

/**
 * 캔버스 오른쪽 끝에 걸친 라벨도 안전하게 그린다.
 * drawText 는 크기를 생략하면 (캔버스 폭 - x) 를 최대 폭으로 쓰는데, x 가 캔버스 밖이면 음수가 되어
 * "maxWidth(-n) must be >= than minWidth(0)" 로 크래시한다. 남은 폭이 너무 좁으면 그리지 않는다.
 */
private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    style: TextStyle,
    maxWidth: Float = Float.MAX_VALUE
) {
    val avail = minOf(maxWidth, size.width - x)
    val availH = size.height - y
    if (avail < 12f || availH < 8f) return
    drawText(
        measurer, text,
        topLeft = Offset(x, y),
        style = style,
        softWrap = false,
        size = Size(avail, availH)
    )
}

/**
 * 이펙트 종류별 블록 표현 (위 → 아래):
 * - ON      : FG 단색
 * - OFF     : 어두운 회색
 * - STROBE  : FG 3 : BG 7
 * - BLINK   : FG 5 : BG 5
 * - BREATH  : FG 5 : BG 5 로 반씩, 가운데 경계만 FG → BG 그라데이션
 */
private fun DrawScope.drawEffectBlock(
    offBlock: Color, type: EffectType, fg: Color, bg: Color,
    left: Float, top: Float, width: Float, height: Float, alpha: Float
) {
    val f = fg.copy(alpha = alpha)
    val b = bg.copy(alpha = alpha)
    val size = { h: Float -> Size(width, h) }

    when (type) {
        EffectType.OFF -> drawRect(offBlock.copy(alpha = alpha), Offset(left, top), size(height))
        EffectType.ON -> drawRect(f, Offset(left, top), size(height))
        EffectType.STROBE -> {
            val h1 = height * 0.3f
            drawRect(f, Offset(left, top), size(h1))
            drawRect(b, Offset(left, top + h1), size(height - h1))
        }
        EffectType.BLINK -> {
            val h1 = height * 0.5f
            drawRect(f, Offset(left, top), size(h1))
            drawRect(b, Offset(left, top + h1), size(height - h1))
        }
        EffectType.BREATH -> {
            // 위 절반 FG · 아래 절반 BG 로 나누고, 가운데(30%~70%)만 FG → BG 그라데이션 (이펙트 라이브러리 스와치와 같은 표현)
            drawRect(
                Brush.verticalGradient(
                    0f to f, 0.3f to f, 0.7f to b, 1f to b,
                    startY = top, endY = top + height
                ),
                Offset(left, top), size(height)
            )
        }
    }
    // 배경색이 레인 색과 비슷해도 블록 경계가 보이도록 얇은 테두리
    drawRect(
        Color.White.copy(alpha = 0.22f * alpha), Offset(left, top), size(height),
        style = Stroke(width = 1f)
    )
}

private enum class DragMode { NONE, SEEK, PAN, MOVE, GROUP, MARQUEE }

/**
 * 타임라인 블록마다 "지금 화면에 보이는 위치"를 들고 있다가 목표 위치로 부드럽게 다가가게 한다.
 * 이펙트 이동(드래그)과 놓았을 때의 정렬 애니메이션에 쓴다.
 */
private class BlockAnim {
    var curStart = FloatArray(0)
    var curEnd = FloatArray(0)
    /** 끌고 있거나 막 놓은 이펙트들 (원래 목록 기준 인덱스) */
    var movedSet: Set<Int> = emptySet()
    /** 놓으면 자리 잡을 구간 (점선 표시용) */
    var ghostStart = 0f
    var ghostEnd = 0f
    /** 이동이 확정되면 바뀔 목록에서 각 항목이 원래 몇 번째였는지. 목록이 바뀌는 순간 위치를 이어받는 데 쓴다 */
    var pendingOrigins: List<Int>? = null
    /** 이동 확정(목록 갱신)을 기다리는 동안 애니메이션을 멈춰 둔다 */
    var freeze = false

    fun snapTo(blocks: List<TimelineBlock>) {
        curStart = FloatArray(blocks.size) { blocks[it].startMs.toFloat() }
        curEnd = FloatArray(blocks.size) { blocks[it].endMs.toFloat() }
        clearHighlight()
    }

    fun clearHighlight() {
        movedSet = emptySet()
        ghostEnd = ghostStart
    }

    /** 목록이 [origins] 대로 바뀌었다: 같은 이펙트의 지금 위치를 그대로 이어받는다 */
    fun remap(origins: List<Int>) {
        val s = FloatArray(origins.size) { curStart[origins[it]] }
        val e = FloatArray(origins.size) { curEnd[origins[it]] }
        movedSet = origins.withIndex().filter { it.value in movedSet }.map { it.index }.toSet()
        curStart = s
        curEnd = e
    }

    /** 한 프레임 진행. 목표에 거의 닿으면 딱 맞추고, 아직 움직이는 블록이 있으면 true */
    fun step(tStart: FloatArray, tEnd: FloatArray, fast: BooleanArray, dtMs: Float): Boolean {
        var moving = false
        val kSlow = 1f - exp(-dtMs / 70f)   // 이웃/정렬: 부드럽게
        val kFast = 1f - exp(-dtMs / 22f)   // 끌고 있는 블록: 손가락을 거의 바로 따라감
        for (i in curStart.indices) {
            val k = if (fast[i]) kFast else kSlow
            curStart[i] += (tStart[i] - curStart[i]) * k
            curEnd[i] += (tEnd[i] - curEnd[i]) * k
            if (abs(tStart[i] - curStart[i]) < 0.5f && abs(tEnd[i] - curEnd[i]) < 0.5f) {
                curStart[i] = tStart[i]
                curEnd[i] = tEnd[i]
            } else moving = true
        }
        return moving
    }
}

private class DragHolder {
    var mode = DragMode.NONE
    var originMs = 0L
}

/**
 * 음악 파형 + 섹션 + 비트 + 이펙트 블록(다음 이펙트까지 구간)을 그리는 타임라인.
 *
 * 이펙트 블록은 종류별로 FG/BG 를 나눠 그린다 ([drawEffectBlock] 참고).
 *
 * 제스처: 위쪽(눈금/섹션/파형) 탭·드래그 = 재생 위치 이동, 이펙트 레인의 블록 탭 = 선택,
 * 블록 드래그 = 시간 이동(스냅), 레인의 빈 곳 드래그 = 좌우 스크롤, 두 손가락 핀치 = 줌.
 */
@Composable
fun TimelineView(
    state: TimelineState,
    entries: List<EfxEntry>,
    analysis: MusicAnalysis?,
    durationMs: Long,
    positionProvider: () -> Long,
    selectedIndex: Int,
    /** 선택 모드(복수 선택): 켜져 있으면 [selectedIndices] 가 선택 표시와 이동 대상이 된다 */
    selectMode: Boolean = false,
    selectedIndices: Set<Int> = emptySet(),
    dropPreviewMs: Long?,
    snapToBeats: Boolean,
    showBeats: Boolean = true,
    followPlayhead: Boolean,
    isPlaying: Boolean,
    emptyHint: String,
    onSeek: (Long) -> Unit,
    /** 눈금자를 탭하거나 드래그를 놓아 위치가 확정됐다 (드래그 중에는 [onSeek] 만 불린다) */
    onSeekFinished: (Long) -> Unit = {},
    onEntryClick: (Int) -> Unit,
    onEntryMove: (Int, Long) -> Unit,
    onEntryToggle: (Int) -> Unit = {},
    onSelectionChange: (Set<Int>) -> Unit = {},
    /** 선택한 이펙트들을 함께 옮긴다: (인덱스들, 이동량 ms) */
    onEntriesMove: (List<Int>, Long) -> Unit = { _, _ -> },
    /** 이펙트 행을 길게 눌렀다: 누른 위치의 블록 인덱스 (빈 곳이면 -1) */
    onLaneLongPress: (Int) -> Unit = {},
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    SideEffect {
        state.durationMs = durationMs
        state.clampZoom()
    }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // 색은 Material 컬러 롤에서 가져온다 (구간 색은 데이터 구분용이라 SectionColors 유지)
    val cs = MaterialTheme.colorScheme
    val bgColor = cs.surfaceContainerLowest
    val rulerBg = cs.surfaceContainerHigh
    val laneBg = cs.surfaceContainerLow
    val gridColor = cs.outlineVariant
    val textDim = cs.onSurfaceVariant
    val wavePlayed = cs.primary
    val waveUnplayed = cs.primaryContainer
    val playheadColor = cs.tertiary
    val dropLine = cs.secondary
    val offBlock = cs.surfaceContainerHighest
    val beatLight = cs.onSurface

    val rulerH = with(density) { RULER_H.toPx() }
    val sectionH = with(density) { SECTION_H.toPx() }
    val waveH = with(density) { WAVE_H.toPx() }
    val beatH = with(density) { BEAT_H.toPx() }
    val laneGap = with(density) { LANE_GAP.toPx() }
    val laneH = with(density) { LANE_H.toPx() }
    val waveTop = rulerH
    val sectionTop = waveTop + waveH
    val beatTop = sectionTop + sectionH
    val laneTop = beatTop + beatH + laneGap

    // 마디(bar) 표시용: 박자 수와, 강박(downbeat)에 해당하는 비트 인덱스 (엔진 입력이 없으면 마디 구분 없이 비트만)
    val beatList = analysis?.beatTimesMs.orEmpty()
    val beatsPerBar = analysis?.engineInput?.beatsPerBar ?: 0
    val globalBeatMs = analysis?.engineInput?.globalBeatMs ?: 500L
    val downbeatIdx = remember(analysis) {
        val inp = analysis?.engineInput
        if (inp == null || inp.beatTimesMs.isEmpty()) 0
        else inp.beatTimesMs.indexOf(TimelineMath.nearestBeat(inp.downbeatMs, inp.beatTimesMs) ?: 0L).coerceAtLeast(0)
    }

    val currentEntries by rememberUpdatedState(entries)
    val currentAnalysis by rememberUpdatedState(analysis)
    val currentDuration by rememberUpdatedState(durationMs)
    val currentSnap by rememberUpdatedState(snapToBeats)
    val onSeekState by rememberUpdatedState(onSeek)
    val onSeekFinishedState by rememberUpdatedState(onSeekFinished)
    val lastSeekMs = remember { longArrayOf(0L) }
    val onEntryClickState by rememberUpdatedState(onEntryClick)
    val onEntryMoveState by rememberUpdatedState(onEntryMove)
    val selectModeState by rememberUpdatedState(selectMode)
    val selectedIndicesState by rememberUpdatedState(selectedIndices)
    val onEntryToggleState by rememberUpdatedState(onEntryToggle)
    val onSelectionChangeState by rememberUpdatedState(onSelectionChange)
    val onEntriesMoveState by rememberUpdatedState(onEntriesMove)
    val onLaneLongPressState by rememberUpdatedState(onLaneLongPress)
    val haptic = LocalHapticFeedback.current
    var suppressNextTap by remember { mutableStateOf(false) }

    val drag = remember { DragHolder() }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragDeltaMs by remember { mutableFloatStateOf(0f) }
    // 선택 모드의 범위 선택(마키): 시작/끝 시각(ms), 없으면 -1
    var marqueeA by remember { mutableFloatStateOf(-1f) }
    var marqueeB by remember { mutableFloatStateOf(-1f) }

    // 재생 중 플레이헤드가 화면을 벗어나면 따라간다
    LaunchedEffect(isPlaying, followPlayhead) {
        if (!isPlaying || !followPlayhead) return@LaunchedEffect
        while (isActive) {
            val p = positionProvider().toFloat()
            val viewMs = state.viewWidthPx / state.pxPerMs
            if (p < state.viewStartMs || p > state.viewStartMs + viewMs * 0.85f) {
                state.viewStartMs = (p - viewMs * 0.15f).coerceIn(0f, state.maxStartMs)
            }
            delay(50)
        }
    }

    /** x 위치의 이펙트 블록 인덱스 (없으면 -1) */
    fun blockAt(x: Float): Int {
        val list = currentEntries
        val ms = state.msAtX(x).toLong()
        val idx = LedSimulator.indexAt(list, ms)
        if (idx < 0) return -1
        val end = TimelineMath.blocks(list, currentDuration)[idx].endMs
        return if (ms <= end) idx else -1
    }

    fun snapMs(ms: Long): Long {
        val tolerance = max(120L, (14f / state.pxPerMs).toLong())
        return TimelineMath.snap(ms, currentAnalysis?.beatTimesMs.orEmpty(), tolerance, currentSnap)
    }

    // ── 이펙트 이동 애니메이션: 끌고 있는 블록은 손가락을 부드럽게 따라가고, 이웃은 자리를 비켜 미끄러지며,
    //    놓으면 정해진 자리로 스르륵 가서 정렬된다. 블록마다 "지금 보이는 위치"가 목표 위치로 다가간다.
    val anim = remember { BlockAnim() }
    var animTick by remember { mutableIntStateOf(0) }
    var settling by remember { mutableStateOf(false) }
    val animActive = dragIndex >= 0 || settling

    /** 한 프레임 진행. 아직 움직이는 블록이 있으면 true */
    fun stepAnimation(dtMs: Float): Boolean {
        if (anim.freeze) return true
        val blocks0 = TimelineMath.blocks(currentEntries, currentDuration)
        val n = blocks0.size
        if (anim.curStart.size != n) anim.snapTo(blocks0)
        val tStart = FloatArray(n) { blocks0[it].startMs.toFloat() }
        val tEnd = FloatArray(n) { blocks0[it].endMs.toFloat() }
        val fast = BooleanArray(n)
        if (dragIndex in 0 until n && (drag.mode == DragMode.MOVE || drag.mode == DragMode.GROUP)) {
            val idxs: List<Int> =
                if (drag.mode == DragMode.GROUP) (selectedIndicesState + dragIndex).toList() else listOf(dragIndex)
            val newMs = snapMs(drag.originMs + dragDeltaMs.toLong())
            val pv = EfxMoves.preview(currentEntries, idxs, newMs - drag.originMs, currentDuration)
            if (pv != null && pv.origins.size == n) {
                val pb = TimelineMath.blocks(pv.entries, currentDuration)
                var gs = Float.MAX_VALUE
                var ge = -Float.MAX_VALUE
                pb.forEachIndexed { j, b ->
                    val o = pv.origins[j]
                    tStart[o] = b.startMs.toFloat()
                    tEnd[o] = b.endMs.toFloat()
                    if (o in idxs) {
                        gs = min(gs, b.startMs.toFloat())
                        ge = max(ge, b.endMs.toFloat())
                    }
                }
                anim.ghostStart = gs
                anim.ghostEnd = ge
                anim.movedSet = idxs.toSet()
                // 끌고 있는 이펙트는 손가락 위치를 따라간다 (놓으면 위에서 정한 자리로 가서 정렬)
                for (i in idxs) {
                    if (i !in 0 until n) continue
                    tStart[i] = blocks0[i].startMs + dragDeltaMs
                    tEnd[i] = blocks0[i].endMs + dragDeltaMs
                    fast[i] = true
                }
            }
        }
        return anim.step(tStart, tEnd, fast, dtMs)
    }

    /** 놓는 순간: 확정될 배치를 기억해 두었다가, 목록이 바뀌면 같은 이펙트의 현재 위치를 이어받아 자리로 간다 */
    fun prepareSettle(idxs: List<Int>, delta: Long) {
        val pv = EfxMoves.preview(currentEntries, idxs, delta, currentDuration)
        if (pv != null && pv.origins.size == currentEntries.size) {
            anim.pendingOrigins = pv.origins
            anim.freeze = true
        }
    }

    // 이펙트 목록이 바뀌면: 이동 확정이면 같은 이펙트의 위치를 이어받고, 그 밖의 편집이면 바로 새 배치로 맞춘다
    LaunchedEffect(entries) {
        val blocksNow = TimelineMath.blocks(entries, durationMs)
        val po = anim.pendingOrigins
        if (po != null && po.size == blocksNow.size && anim.curStart.size == po.size) anim.remap(po)
        else anim.snapTo(blocksNow)
        anim.pendingOrigins = null
        anim.freeze = false
        animTick++
    }
    LaunchedEffect(animActive) {
        if (!animActive) return@LaunchedEffect
        var last = 0L
        var frozenFrames = 0
        while (isActive) {
            val moving = withFrameNanos { t ->
                val dt = if (last == 0L) 16f else ((t - last) / 1_000_000f).coerceIn(1f, 50f)
                last = t
                stepAnimation(dt)
            }
            animTick++
            if (anim.freeze) {
                // 이동이 실제로 확정되지 않았다면(변화 없음) 잠시 뒤 풀어 원래 자리로 돌아가게 한다
                frozenFrames++
                if (frozenFrames > 20) { anim.freeze = false; anim.pendingOrigins = null; frozenFrames = 0 }
            } else frozenFrames = 0
            if (!moving && dragIndex < 0 && !anim.freeze) {
                settling = false
                anim.clearHighlight()
                animTick++
                break
            }
        }
    }

    Canvas(
        modifier = modifier
            // Canvas 는 고유 크기가 없어 폭을 지정하지 않으면 0 이 된다 — 반드시 가로를 채워야 한다
            .fillMaxWidth()
            .height(TIMELINE_TOTAL_HEIGHT)
            .clipToBounds()
            .onGloballyPositioned {
                state.viewWidthPx = it.size.width.toFloat()
                state.clampZoom()
                onBoundsChanged(it.boundsInWindow())
            }
            // 두 손가락 핀치 줌 — Initial 패스에서 먼저 보고, 두 손가락이 닿으면 변화를 소비해
            // 아래의 탭/드래그 감지기가 (이동·시크 도중이던 것도) 취소되게 한다.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    suppressNextTap = false
                    var gestureOver = false
                    // 이펙트 행을 길게 누르면 선택 모드로 들어간다 (이벤트는 소비하지 않아 탭/드래그 감지기는 그대로 동작한다)
                    if (down.position.y >= laneTop) {
                        val held = withTimeoutOrNull<Unit>(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val c = ev.changes.firstOrNull { it.id == down.id }
                                if (c == null || !c.pressed) { gestureOver = true; return@withTimeoutOrNull }
                                if (ev.changes.count { it.pressed } >= 2 ||
                                    (c.position - down.position).getDistance() > viewConfiguration.touchSlop
                                ) return@withTimeoutOrNull
                            }
                        }
                        if (held == null) {
                            suppressNextTap = true   // 손을 뗄 때 탭으로도 처리되어 방금 한 선택이 뒤집히지 않게 한다
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLaneLongPressState(blockAt(down.position.x))
                        }
                    }
                    if (gestureOver) return@awaitEachGesture
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.filter { it.pressed && !it.changedToUp() }
                        if (pressed.size >= 2) {
                            val a = pressed[0]
                            val b = pressed[1]
                            val prevDist = (a.previousPosition - b.previousPosition).getDistance()
                            val dist = (a.position - b.position).getDistance()
                            if (prevDist > 0f && dist > 0f) {
                                val prevCentroidX = (a.previousPosition.x + b.previousPosition.x) / 2f
                                val centroidX = (a.position.x + b.position.x) / 2f
                                state.pinch(dist / prevDist, prevCentroidX, centroidX)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    if (suppressNextTap) { suppressNextTap = false; return@detectTapGestures }
                    if (o.y >= laneTop) {
                        val idx = blockAt(o.x)
                        if (selectModeState) { if (idx >= 0) onEntryToggleState(idx) } else onEntryClickState(idx)
                    } else {
                        val ms = state.msAtX(o.x).toLong().coerceAtLeast(0L)
                        onSeekState(ms)
                        onSeekFinishedState(ms)
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o ->
                        if (o.y >= laneTop) {
                            val idx = blockAt(o.x)
                            if (selectModeState && idx < 0) {
                                // 선택 모드에서 빈 곳을 끌면 범위 선택
                                drag.mode = DragMode.MARQUEE
                                marqueeA = state.msAtX(o.x)
                                marqueeB = marqueeA
                            } else if (idx >= 0) {
                                drag.mode = if (selectModeState) DragMode.GROUP else DragMode.MOVE
                                if (selectModeState && idx !in selectedIndicesState) onSelectionChangeState(setOf(idx))
                                drag.originMs = currentEntries[idx].timestampMs
                                dragIndex = idx
                                dragDeltaMs = 0f
                                onEntryClickState(idx)
                            } else {
                                drag.mode = DragMode.PAN
                            }
                        } else {
                            drag.mode = DragMode.SEEK
                            lastSeekMs[0] = state.msAtX(o.x).toLong().coerceAtLeast(0L)
                            onSeekState(lastSeekMs[0])
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        when (drag.mode) {
                            DragMode.SEEK -> {
                                lastSeekMs[0] = state.msAtX(change.position.x).toLong().coerceAtLeast(0L)
                                onSeekState(lastSeekMs[0])
                            }
                            DragMode.PAN -> state.panByMs(-amount.x / state.pxPerMs, currentDuration)
                            DragMode.MOVE, DragMode.GROUP -> dragDeltaMs += amount.x / state.pxPerMs
                            DragMode.MARQUEE -> marqueeB = state.msAtX(change.position.x)
                            DragMode.NONE -> Unit
                        }
                    },
                    onDragEnd = {
                        if (drag.mode == DragMode.SEEK) onSeekFinishedState(lastSeekMs[0])
                        if (drag.mode == DragMode.MOVE || drag.mode == DragMode.GROUP) settling = true
                        if (drag.mode == DragMode.MOVE && dragIndex >= 0) {
                            val newMs = snapMs(drag.originMs + dragDeltaMs.toLong())
                            if (newMs != drag.originMs) {
                                prepareSettle(listOf(dragIndex), newMs - drag.originMs)
                                onEntryMoveState(dragIndex, newMs)
                            }
                        }
                        if (drag.mode == DragMode.GROUP && dragIndex >= 0) {
                            val newMs = snapMs(drag.originMs + dragDeltaMs.toLong())
                            val delta = newMs - drag.originMs
                            val targets = (selectedIndicesState + dragIndex).sorted()
                            if (delta != 0L) {
                                prepareSettle(targets, delta)
                                onEntriesMoveState(targets, delta)
                            }
                        }
                        if (drag.mode == DragMode.MARQUEE && marqueeA >= 0f) {
                            val lo = minOf(marqueeA, marqueeB).toLong()
                            val hi = maxOf(marqueeA, marqueeB).toLong()
                            val picked = TimelineMath.blocks(currentEntries, currentDuration)
                                .filter { it.endMs >= lo && it.startMs <= hi }
                                .map { it.index }.toSet()
                            onSelectionChangeState(picked)
                        }
                        marqueeA = -1f
                        marqueeB = -1f
                        drag.mode = DragMode.NONE
                        dragIndex = -1
                        dragDeltaMs = 0f
                    },
                    onDragCancel = {
                        if (drag.mode == DragMode.SEEK) onSeekFinishedState(lastSeekMs[0])
                        if (drag.mode == DragMode.MOVE || drag.mode == DragMode.GROUP) settling = true
                        marqueeA = -1f
                        marqueeB = -1f
                        drag.mode = DragMode.NONE
                        dragIndex = -1
                        dragDeltaMs = 0f
                    }
                )
            }
    ) {
        val w = size.width
        val startMs = state.viewStartMs
        val pxPerMs = state.pxPerMs
        val endMs = startMs + w / pxPerMs
        val position = positionProvider()

        fun xOf(ms: Float) = (ms - startMs) * pxPerMs

        /** 보이는 범위의 비트(가는 선)와 마디 시작(굵은 선)을 [yTop, yBottom] 에 그린다. */
        fun drawBeats(
            yTop: Float, yBottom: Float, beatColor: Color, barColor: Color, withNumbers: Boolean,
            beatInset: Float = 0f
        ) {
            if (!showBeats || beatList.isEmpty()) return
            val beatPx = pxPerMs * globalBeatMs
            val barsOnly = beatPx < 5f && beatsPerBar > 0   // 너무 촘촘하면 마디 시작만
            if (beatPx < 5f && beatsPerBar <= 0) return
            var i = beatList.binarySearch(startMs.toLong()).let { if (it >= 0) it else -it - 1 }
            i = i.coerceAtLeast(0)
            while (i < beatList.size) {
                val t = beatList[i]
                if (t > endMs) break
                val rel = i - downbeatIdx
                val isBar = beatsPerBar > 0 && Math.floorMod(rel, beatsPerBar) == 0
                if (!barsOnly || isBar) {
                    val x = xOf(t.toFloat())
                    drawLine(if (isBar) barColor else beatColor, Offset(x, if (isBar) yTop else yTop + beatInset),
                        Offset(x, yBottom), strokeWidth = if (isBar) 2f else 1f)
                    if (withNumbers && isBar && rel >= 0 && beatPx * beatsPerBar > 36f) {
                        drawLabel(
                            textMeasurer, (Math.floorDiv(rel, beatsPerBar) + 1).toString(), x + 3f, yTop + 2f,
                            TextStyle(fontSize = 9.sp, color = beatLight.copy(alpha = 0.6f))
                        )
                    }
                }
                i++
            }
        }

        drawRect(bgColor)

        // ── 눈금 ─────────────────────────────────────────────
        drawRect(rulerBg, Offset.Zero, Size(w, rulerH))
        val stepMs = (TimelineMath.rulerStepSec(pxPerMs) * 1000f).toLong()
        var t = (startMs.toLong().coerceAtLeast(0L) / stepMs) * stepMs
        while (t <= endMs) {
            val x = xOf(t.toFloat())
            drawLine(gridColor, Offset(x, rulerH * 0.5f), Offset(x, laneTop + laneH), strokeWidth = 1f)
            drawLabel(
                textMeasurer, TimelineMath.formatTime(t), x + 4f, 2f,
                TextStyle(fontSize = 10.sp, color = textDim)
            )
            t += stepMs
        }

        // ── 섹션 띠 ───────────────────────────────────────────
        val sections = analysis?.sections.orEmpty()
        for (s in sections) {
            if (s.endMs < startMs || s.startMs > endMs) continue
            val x0 = max(0f, xOf(s.startMs.toFloat()))
            val x1 = min(w, xOf(s.endMs.toFloat()))
            if (x1 <= x0) continue
            drawRect(
                SectionColors.of(s.type).copy(alpha = 0.9f),
                Offset(x0, sectionTop + 1f), Size(x1 - x0 - 1f, sectionH - 2f)
            )
            if (x1 - x0 > 46f) {
                drawLabel(
                    textMeasurer, s.type, x0 + 5f, sectionTop + 3f,
                    TextStyle(fontSize = 10.sp, color = Color.White),
                    maxWidth = x1 - x0 - 8f
                )
            }
        }

        // ── 파형 ──────────────────────────────────────────────
        val waveMid = waveTop + waveH / 2f
        val wf = analysis?.waveform
        if (analysis != null && !wf.isNullOrEmpty()) {
            val hop = analysis.waveformHopMs.toFloat()
            var px = 0f
            while (px < w) {
                val ms = startMs + px / pxPerMs
                if (ms >= 0f) {
                    val i0 = (ms / hop).toInt()
                    val i1 = ((ms + 2f / pxPerMs) / hop).toInt()
                    var amp = 0f
                    var i = i0
                    while (i <= i1 && i < wf.size && i <= i0 + 64) {
                        if (wf[i] > amp) amp = wf[i]
                        i++
                    }
                    if (i0 < wf.size) {
                        val h = amp.coerceIn(0f, 1f).pow(0.6f) * waveH * 0.46f
                        drawLine(
                            if (ms <= position) wavePlayed else waveUnplayed,
                            Offset(px, waveMid - h), Offset(px, waveMid + h), strokeWidth = 2f
                        )
                    }
                }
                px += 2f
            }
        } else {
            drawLabel(
                textMeasurer, emptyHint, 16f, waveMid - 8f,
                TextStyle(fontSize = 12.sp, color = textDim)
            )
        }

        // 파형 위 가이드선(아주 연하게) — 비트 위치를 파형과 대조하기 위함
        drawBeats(waveTop, waveTop + waveH, beatLight.copy(alpha = 0.08f), beatLight.copy(alpha = 0.19f), withNumbers = false)

        // ── 비트 행 ───────────────────────────────────────────
        drawRect(rulerBg, Offset(0f, beatTop), Size(w, beatH))
        drawBeats(
            beatTop, beatTop + beatH, beatLight.copy(alpha = 0.67f), beatLight,
            withNumbers = true, beatInset = beatH * 0.5f
        )

        // ── 이펙트 레인 ───────────────────────────────────────
        drawRect(laneBg, Offset(0f, laneTop), Size(w, laneH))
        drawBeats(laneTop, laneTop + laneH, beatLight.copy(alpha = 0.09f), beatLight.copy(alpha = 0.25f), withNumbers = false)
        // 블록마다 애니메이션이 만든 "지금 보이는 위치"로 그린다 (끌 때 이웃이 비켜 미끄러지고, 놓으면 자리로 정렬)
        @Suppress("UNUSED_VARIABLE") val animFrame = animTick
        val blocks = TimelineMath.blocks(entries, durationMs)
        val animOk = anim.curStart.size == blocks.size
        for (b in blocks) {
            val moving = b.index in anim.movedSet
            val x0 = xOf(if (animOk) anim.curStart[b.index] else b.startMs.toFloat())
            val x1 = xOf(if (animOk) anim.curEnd[b.index] else b.endMs.toFloat())
            if (x1 < 0f || x0 > w) continue

            val p = b.entry.payload
            val left = max(-2f, x0)
            val width = max(2f, min(x1, w + 2f) - left - 1f)
            val isPicked = if (anim.movedSet.isNotEmpty()) moving else if (selectMode) b.index in selectedIndices else b.index == selectedIndex
            // 선택 모드에서는 선택되지 않은 블록을 흐리게 해서 선택된 것이 도드라지게 한다
            val alpha = when {
                moving -> 0.7f
                selectMode && !isPicked -> 0.4f
                else -> 1f
            }

            drawEffectBlock(offBlock,
                p.effectType,
                fg = Color(p.color.r, p.color.g, p.color.b),
                bg = Color(p.backgroundColor.r, p.backgroundColor.g, p.backgroundColor.b),
                left = left, top = laneTop + 3f, width = width, height = laneH - 6f, alpha = alpha
            )
            if (isPicked) {
                drawRect(
                    if (selectMode) cs.primary else Color.White,
                    Offset(left, laneTop + 3f), Size(width, laneH - 6f),
                    style = Stroke(width = if (selectMode) 4f else 3f)
                )
                if (selectMode && width > 26f) {
                    // 선택됨 표시: 블록 오른쪽 위의 체크 원
                    val r = 9f
                    val c = Offset(left + width - r - 4f, laneTop + 3f + r + 4f)
                    drawCircle(cs.primary, r, c)
                    drawLine(Color.White, Offset(c.x - 4f, c.y), Offset(c.x - 1f, c.y + 3.5f), strokeWidth = 2.2f)
                    drawLine(Color.White, Offset(c.x - 1f, c.y + 3.5f), Offset(c.x + 4.5f, c.y - 3.5f), strokeWidth = 2.2f)
                }
            }
            if (width > 44f) {
                // 블록 안의 색이 위치마다 달라서, 어떤 색 위에서도 읽히도록 흰 글자 + 검은 그림자
                drawLabel(
                    textMeasurer, p.effectType.name, left + 6f, laneTop + 8f,
                    TextStyle(
                        fontSize = 11.sp, color = Color.White,
                        shadow = Shadow(Color.Black, Offset(1f, 1f), blurRadius = 4f)
                    ),
                    maxWidth = width - 8f
                )
            }
        }

        // ── 끌고 있는 동안: 놓으면 자리 잡을 곳을 점선으로 보여 준다 ──
        if (dragIndex >= 0 && anim.ghostEnd > anim.ghostStart) {
            val gx0 = xOf(anim.ghostStart)
            val gx1 = xOf(anim.ghostEnd)
            drawRect(
                dropLine, Offset(gx0, laneTop + 3f), Size(max(2f, gx1 - gx0), laneH - 6f),
                style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            )
        }

        // ── 선택 모드 표시: 이펙트 행 테두리 ──────────────────
        if (selectMode) {
            drawRect(cs.primary, Offset(1.5f, laneTop), Size(w - 3f, laneH), style = Stroke(width = 3f))
        }

        // ── 범위 선택(마키) ───────────────────────────────────
        if (marqueeA >= 0f) {
            val xa = xOf(minOf(marqueeA, marqueeB))
            val xb = xOf(maxOf(marqueeA, marqueeB))
            drawRect(cs.primary.copy(alpha = 0.22f), Offset(xa, laneTop), Size(xb - xa, laneH))
            drawRect(cs.primary, Offset(xa, laneTop), Size(xb - xa, laneH), style = Stroke(width = 2f))
        }

        // ── 드롭 위치 미리보기 ────────────────────────────────
        if (dropPreviewMs != null) {
            val x = xOf(dropPreviewMs.toFloat())
            drawLine(
                dropLine, Offset(x, 0f), Offset(x, laneTop + laneH), strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )
            drawLabel(
                textMeasurer, TimelineMath.formatTime(dropPreviewMs, withTenths = true), x + 6f, laneTop - 16f,
                TextStyle(fontSize = 11.sp, color = dropLine)
            )
        }

        // ── 플레이헤드 ────────────────────────────────────────
        val px = xOf(position.toFloat())
        if (px in 0f..w) {
            drawLine(playheadColor, Offset(px, 0f), Offset(px, laneTop + laneH), strokeWidth = 2f)
            drawCircle(playheadColor, radius = 6f, center = Offset(px, 6f))

            // 선택한(재생) 위치의 시각을 세로선 옆 박스로 보여 준다. 예: 0:09.7
            val label = textMeasurer.measure(
                TimelineMath.formatTime(position, withTenths = true),
                TextStyle(fontSize = 11.sp, color = cs.onTertiary)
            )
            val padX = 6f
            val padY = 3f
            val boxW = label.size.width + padX * 2
            val boxH = label.size.height + padY * 2
            // 기본은 선 오른쪽, 화면 오른쪽 끝에 닿으면 왼쪽에 둔다
            val boxX = if (px + 6f + boxW <= w) px + 6f else px - 6f - boxW
            val boxY = rulerH + 4f
            drawRoundRect(
                playheadColor, Offset(boxX, boxY), Size(boxW, boxH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
            drawText(label, topLeft = Offset(boxX + padX, boxY + padY))
        }
    }
}
