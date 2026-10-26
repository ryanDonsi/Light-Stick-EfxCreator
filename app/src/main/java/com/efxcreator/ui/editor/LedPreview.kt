package com.efxcreator.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

fun LedColor.toComposeColor(): Color = Color(
    red = (r / 255f).coerceIn(0f, 1f),
    green = (g / 255f).coerceIn(0f, 1f),
    blue = (b / 255f).coerceIn(0f, 1f)
)

/**
 * 응원봉 상단을 위에서 본 프리뷰.
 * 실제 응원봉처럼 **바깥쪽 원(링)** 이 켜지고, 안쪽 원은 켜지지 않는 어두운 면으로 둔다.
 */
@Composable
fun LedPreview(color: LedColor, modifier: Modifier = Modifier) {
    val led = color.toComposeColor()
    val brightness = maxOf(color.r, color.g, color.b) / 255f

    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        val ringRadius = radius * 0.82f      // 링 중심선 반지름
        val ringWidth = radius * 0.26f       // 링 두께 (0.69r ~ 0.95r)
        val innerRadius = radius * 0.64f     // 켜지지 않는 안쪽 원

        // 몸체
        drawCircle(Color(0xFF1E1E24), radius = radius, center = center)

        // 링 둘레로 번지는 빛 (링 바깥/안쪽으로 부드럽게 퍼짐)
        if (brightness > 0.02f) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    0.55f to Color.Transparent,
                    0.82f to led.copy(alpha = 0.50f * brightness),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        // 링: 꺼져 있으면 어두운 회색, 켜지면 LED 색
        val ringColor = if (brightness > 0.02f) led else Color(0xFF34343C)
        drawCircle(ringColor, radius = ringRadius, center = center, style = Stroke(width = ringWidth))
        // 링 윗면 하이라이트
        if (brightness > 0.02f) {
            drawCircle(
                Color.White.copy(alpha = 0.22f * brightness),
                radius = ringRadius + ringWidth * 0.18f, center = center,
                style = Stroke(width = ringWidth * 0.22f)
            )
        }

        // 안쪽 원: 점등되지 않는 면
        drawCircle(Color(0xFF15151A), radius = innerRadius, center = center)
        drawCircle(
            Color(0xFF2A2A32), radius = innerRadius, center = center,
            style = Stroke(width = radius * 0.02f)
        )
    }
}
