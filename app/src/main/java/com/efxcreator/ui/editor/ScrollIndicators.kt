package com.efxcreator.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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
    val maxStart = (total - viewMs * 0.5f).coerceAtLeast(1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .pointerInput(total, viewMs) {
                detectTapGestures { p -> onScrollTo((p.x / size.width * total - viewMs / 2f).coerceIn(0f, maxStart)) }
            }
            .pointerInput(total, viewMs) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onScrollTo(
                        (viewStartMs + drag.x / size.width * total).coerceIn(0f, maxStart)
                    )
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

/** 세로 스크롤 중에만 나타나는 얇은 막대 (콘텐츠 위 오른쪽 가장자리에 겹쳐 그린다). */
@Composable
fun Modifier.slimVerticalScrollBar(state: ScrollState): Modifier {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val alpha by animateFloatAsState(
        targetValue = if (state.isScrollInProgress) 0.55f else 0f,
        animationSpec = tween(if (state.isScrollInProgress) 100 else 600),
        label = "scrollbarAlpha"
    )
    return drawWithContent {
        drawContent()
        if (alpha > 0f && state.maxValue > 0) {
            val thickness = THUMB_THICKNESS.toPx()
            val viewH = size.height
            val total = viewH + state.maxValue
            val len = (viewH / total * viewH).coerceAtLeast(THUMB_MIN.toPx())
            val y = state.value.toFloat() / state.maxValue * (viewH - len)
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(size.width - thickness - 2.dp.toPx(), y),
                size = Size(thickness, len),
                cornerRadius = CornerRadius(thickness / 2f)
            )
        }
    }
}
