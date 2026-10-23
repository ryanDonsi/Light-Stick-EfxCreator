package com.efxcreator.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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

    companion object {
        const val MIN_PX_PER_MS = 0.008f
        const val MAX_PX_PER_MS = 0.6f
    }

    fun msAtX(x: Float): Float = viewStartMs + x / pxPerMs

    fun zoom(factor: Float, anchorX: Float = viewWidthPx / 2f) {
        val anchorMs = msAtX(anchorX)
        pxPerMs = (pxPerMs * factor).coerceIn(MIN_PX_PER_MS, MAX_PX_PER_MS)
        viewStartMs = (anchorMs - anchorX / pxPerMs).coerceAtLeast(0f)
    }

    /**
     * 핀치 제스처: 이전 중심점(prevCentroidX) 아래에 있던 시각이 새 중심점(centroidX) 아래에
     * 오도록 줌과 스크롤을 함께 갱신한다 (손가락이 잡은 지점이 따라 움직임).
     */
    fun pinch(scale: Float, prevCentroidX: Float, centroidX: Float) {
        val anchorMs = msAtX(prevCentroidX)
        pxPerMs = (pxPerMs * scale).coerceIn(MIN_PX_PER_MS, MAX_PX_PER_MS)
        viewStartMs = (anchorMs - centroidX / pxPerMs).coerceAtLeast(0f)
    }

    fun panByMs(deltaMs: Float, totalMs: Long) {
        val viewMs = if (pxPerMs > 0f) viewWidthPx / pxPerMs else 0f
        val maxStart = max(0f, totalMs - viewMs * 0.5f)
        viewStartMs = (viewStartMs + deltaMs).coerceIn(0f, maxStart)
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

private val BG = Color(0xFF16161A)
private val RULER_BG = Color(0xFF202027)
private val LANE_BG = Color(0xFF1D1D23)
private val GRID = Color(0xFF3A3A44)
private val TEXT_DIM = Color(0xFFB0B0BA)
private val WAVE_PLAYED = Color(0xFF64B5F6)
private val WAVE_UNPLAYED = Color(0xFF2F6FA8)
private val PLAYHEAD = Color(0xFFFFEB3B)
private val DROP_LINE = Color(0xFF26C6DA)
private val OFF_BLOCK = Color(0xFF2A2A30)

private val RULER_H = 22.dp
private val SECTION_H = 22.dp
private val WAVE_H = 80.dp
private val LANE_GAP = 6.dp
private val LANE_H = 64.dp
val TIMELINE_TOTAL_HEIGHT = RULER_H + SECTION_H + WAVE_H + LANE_GAP + LANE_H

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

private enum class DragMode { NONE, SEEK, PAN, MOVE }

private class DragHolder {
    var mode = DragMode.NONE
    var originMs = 0L
}

/**
 * 음악 파형 + 섹션 + 비트 + 이펙트 블록(다음 이펙트까지 구간)을 그리는 타임라인.
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
    dropPreviewMs: Long?,
    snapToBeats: Boolean,
    followPlayhead: Boolean,
    isPlaying: Boolean,
    emptyHint: String,
    onSeek: (Long) -> Unit,
    onEntryClick: (Int) -> Unit,
    onEntryMove: (Int, Long) -> Unit,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val rulerH = with(density) { RULER_H.toPx() }
    val sectionH = with(density) { SECTION_H.toPx() }
    val waveH = with(density) { WAVE_H.toPx() }
    val laneGap = with(density) { LANE_GAP.toPx() }
    val laneH = with(density) { LANE_H.toPx() }
    val laneTop = rulerH + sectionH + waveH + laneGap

    val currentEntries by rememberUpdatedState(entries)
    val currentAnalysis by rememberUpdatedState(analysis)
    val currentDuration by rememberUpdatedState(durationMs)
    val currentSnap by rememberUpdatedState(snapToBeats)
    val onSeekState by rememberUpdatedState(onSeek)
    val onEntryClickState by rememberUpdatedState(onEntryClick)
    val onEntryMoveState by rememberUpdatedState(onEntryMove)

    val drag = remember { DragHolder() }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragDeltaMs by remember { mutableFloatStateOf(0f) }

    // 재생 중 플레이헤드가 화면을 벗어나면 따라간다
    LaunchedEffect(isPlaying, followPlayhead) {
        if (!isPlaying || !followPlayhead) return@LaunchedEffect
        while (isActive) {
            val p = positionProvider().toFloat()
            val viewMs = state.viewWidthPx / state.pxPerMs
            if (p < state.viewStartMs || p > state.viewStartMs + viewMs * 0.85f) {
                state.viewStartMs = max(0f, p - viewMs * 0.15f)
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

    Canvas(
        modifier = modifier
            .height(TIMELINE_TOTAL_HEIGHT)
            .clipToBounds()
            .onGloballyPositioned {
                state.viewWidthPx = it.size.width.toFloat()
                onBoundsChanged(it.boundsInWindow())
            }
            // 두 손가락 핀치 줌 — Initial 패스에서 먼저 보고, 두 손가락이 닿으면 변화를 소비해
            // 아래의 탭/드래그 감지기가 (이동·시크 도중이던 것도) 취소되게 한다.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
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
                    if (o.y >= laneTop) {
                        onEntryClickState(blockAt(o.x))
                    } else {
                        onSeekState(state.msAtX(o.x).toLong().coerceAtLeast(0L))
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o ->
                        if (o.y >= laneTop) {
                            val idx = blockAt(o.x)
                            if (idx >= 0) {
                                drag.mode = DragMode.MOVE
                                drag.originMs = currentEntries[idx].timestampMs
                                dragIndex = idx
                                dragDeltaMs = 0f
                                onEntryClickState(idx)
                            } else {
                                drag.mode = DragMode.PAN
                            }
                        } else {
                            drag.mode = DragMode.SEEK
                            onSeekState(state.msAtX(o.x).toLong().coerceAtLeast(0L))
                        }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        when (drag.mode) {
                            DragMode.SEEK ->
                                onSeekState(state.msAtX(change.position.x).toLong().coerceAtLeast(0L))
                            DragMode.PAN -> state.panByMs(-amount.x / state.pxPerMs, currentDuration)
                            DragMode.MOVE -> dragDeltaMs += amount.x / state.pxPerMs
                            DragMode.NONE -> Unit
                        }
                    },
                    onDragEnd = {
                        if (drag.mode == DragMode.MOVE && dragIndex >= 0) {
                            val newMs = snapMs(drag.originMs + dragDeltaMs.toLong())
                            if (newMs != drag.originMs) onEntryMoveState(dragIndex, newMs)
                        }
                        drag.mode = DragMode.NONE
                        dragIndex = -1
                        dragDeltaMs = 0f
                    },
                    onDragCancel = {
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

        drawRect(BG)

        // ── 눈금 ─────────────────────────────────────────────
        drawRect(RULER_BG, Offset.Zero, Size(w, rulerH))
        val stepMs = (TimelineMath.rulerStepSec(pxPerMs) * 1000f).toLong()
        var t = (startMs.toLong().coerceAtLeast(0L) / stepMs) * stepMs
        while (t <= endMs) {
            val x = xOf(t.toFloat())
            drawLine(GRID, Offset(x, rulerH * 0.5f), Offset(x, laneTop + laneH), strokeWidth = 1f)
            drawLabel(
                textMeasurer, TimelineMath.formatTime(t), x + 4f, 2f,
                TextStyle(fontSize = 10.sp, color = TEXT_DIM)
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
                Offset(x0, rulerH + 1f), Size(x1 - x0 - 1f, sectionH - 2f)
            )
            if (x1 - x0 > 46f) {
                drawLabel(
                    textMeasurer, s.type, x0 + 5f, rulerH + 3f,
                    TextStyle(fontSize = 10.sp, color = Color.White),
                    maxWidth = x1 - x0 - 8f
                )
            }
        }

        // ── 파형 ──────────────────────────────────────────────
        val waveTop = rulerH + sectionH
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
                            if (ms <= position) WAVE_PLAYED else WAVE_UNPLAYED,
                            Offset(px, waveMid - h), Offset(px, waveMid + h), strokeWidth = 2f
                        )
                    }
                }
                px += 2f
            }

            // 비트 눈금
            if (pxPerMs * 400f > 5f) {
                for (b in analysis.beatTimesMs) {
                    if (b < startMs || b > endMs) continue
                    val x = xOf(b.toFloat())
                    drawLine(Color(0x66FFFFFF), Offset(x, waveTop + waveH - 8f), Offset(x, waveTop + waveH), strokeWidth = 1f)
                }
            }
        } else {
            drawLabel(
                textMeasurer, emptyHint, 16f, waveMid - 8f,
                TextStyle(fontSize = 12.sp, color = TEXT_DIM)
            )
        }

        // ── 이펙트 레인 ───────────────────────────────────────
        drawRect(LANE_BG, Offset(0f, laneTop), Size(w, laneH))
        val blocks = TimelineMath.blocks(entries, durationMs)
        for (b in blocks) {
            val shift = if (b.index == dragIndex) dragDeltaMs else 0f
            val x0 = xOf(b.startMs + shift)
            val x1 = xOf(b.endMs + shift)
            if (x1 < 0f || x0 > w) continue

            val p = b.entry.payload
            val base = if (p.effectType == EffectType.OFF) OFF_BLOCK
            else Color(p.color.r, p.color.g, p.color.b)
            val left = max(-2f, x0)
            val width = max(2f, min(x1, w + 2f) - left - 1f)
            val alpha = if (b.index == dragIndex) 0.7f else 1f

            drawRect(base.copy(alpha = alpha), Offset(left, laneTop + 3f), Size(width, laneH - 6f))
            val bgc = p.backgroundColor
            if (bgc.r + bgc.g + bgc.b > 0) {
                drawRect(Color(bgc.r, bgc.g, bgc.b), Offset(left, laneTop + laneH - 9f), Size(width, 6f))
            }
            if (b.index == selectedIndex) {
                drawRect(
                    Color.White, Offset(left, laneTop + 3f), Size(width, laneH - 6f),
                    style = Stroke(width = 3f)
                )
            }
            if (width > 44f) {
                val luminance = (0.299f * base.red + 0.587f * base.green + 0.114f * base.blue)
                drawLabel(
                    textMeasurer, p.effectType.name, left + 6f, laneTop + 8f,
                    TextStyle(fontSize = 11.sp, color = if (luminance > 0.55f) Color.Black else Color.White),
                    maxWidth = width - 8f
                )
            }
        }

        // ── 드롭 위치 미리보기 ────────────────────────────────
        if (dropPreviewMs != null) {
            val x = xOf(dropPreviewMs.toFloat())
            drawLine(
                DROP_LINE, Offset(x, 0f), Offset(x, laneTop + laneH), strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            )
            drawLabel(
                textMeasurer, TimelineMath.formatTime(dropPreviewMs, withTenths = true), x + 6f, laneTop - 16f,
                TextStyle(fontSize = 11.sp, color = DROP_LINE)
            )
        }

        // ── 플레이헤드 ────────────────────────────────────────
        val px = xOf(position.toFloat())
        if (px in 0f..w) {
            drawLine(PLAYHEAD, Offset(px, 0f), Offset(px, laneTop + laneH), strokeWidth = 2f)
            drawCircle(PLAYHEAD, radius = 6f, center = Offset(px, 6f))
        }
    }
}
