package com.efxcreator.ui.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.dp

/**
 * macOS 2011 스타일 스크롤 표시: 화살표·트랙 없이 얇은 둥근 막대 하나만 보여 준다.
 */
private val THUMB_THICKNESS = 4.dp
private val THUMB_MIN = 32.dp

/** 가로 스크롤 막대: 막대를 끌거나 빈 곳을 탭하면 [onScrollTo](viewStartMs) 로 보이는 구간이 이동한다. */
@Composable
fun SlimHorizontalScrollBar(
    viewStartMs: Float,
    viewMs: Float,
    durationMs: Long,
    onScrollTo: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val total = durationMs.toFloat().coerceAtLeast(1f)
    val maxStart = (total - viewMs).coerceAtLeast(0f)
    // 제스처 람다는 다시 시작되지 않으므로, 계속 바뀌는 값은 항상 최신 값을 읽도록 한다
    val currentStart by rememberUpdatedState(viewStartMs)
    val currentScrollTo by rememberUpdatedState(onScrollTo)
    val currentMax by rememberUpdatedState(maxStart)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(total, viewMs) {
                detectTapGestures { p ->
                    currentScrollTo((p.x / size.width * total - viewMs / 2f).coerceIn(0f, currentMax))
                }
            }
            .pointerInput(total, viewMs) {
                detectDragGestures { change, drag ->
                    change.consume()
                    currentScrollTo((currentStart + drag.x / size.width * total).coerceIn(0f, currentMax))
                }
            }
    ) {
        val thickness = THUMB_THICKNESS.toPx()
        val minLen = THUMB_MIN.toPx()
        val len = (viewMs / total * size.width).coerceIn(minLen, size.width)
        val x = if (total > 0f) (viewStartMs / total * size.width).coerceIn(0f, size.width - len) else 0f
        drawRoundRect(
            color = color.copy(alpha = 0.55f),
            topLeft = Offset(x, (size.height - thickness) / 2f),
            size = Size(len, thickness),
            cornerRadius = CornerRadius(thickness / 2f)
        )
    }
}

/**
 * 세로 스크롤 막대: 가로 막대와 같은 모양(얇은 둥근 막대)이고, 스크롤할 내용이 있을 때만 표시된다.
 * 막대를 끌거나 빈 곳을 탭하면 [state] 가 따라 움직인다. 스크롤 영역과 같은 크기의 Box 안에서
 * `Modifier.align(Alignment.CenterEnd)` 로 겹쳐 놓는다.
 */
@Composable
fun SlimVerticalScrollBar(state: ScrollState, modifier: Modifier = Modifier) {
    if (state.maxValue <= 0) return
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val scope = rememberCoroutineScope()
    val currentState by rememberUpdatedState(state)

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(16.dp)
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val st = currentState
                    val target = (p.y / size.height * (st.maxValue + size.height) - size.height / 2f)
                        .coerceIn(0f, st.maxValue.toFloat())
                    scope.launch { st.animateScrollTo(target.toInt()) }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val st = currentState
                    st.dispatchRawDelta(drag.y * (st.maxValue + size.height) / size.height)
                }
            }
    ) {
        val thickness = THUMB_THICKNESS.toPx()
        val viewH = size.height
        val total = viewH + state.maxValue
        val len = (viewH / total * viewH).coerceIn(THUMB_MIN.toPx(), viewH)
        val y = state.value.toFloat() / state.maxValue * (viewH - len)
        drawRoundRect(
            color = color.copy(alpha = 0.55f),
            topLeft = Offset((size.width - thickness) / 2f, y),
            size = Size(thickness, len),
            cornerRadius = CornerRadius(thickness / 2f)
        )
    }
}
