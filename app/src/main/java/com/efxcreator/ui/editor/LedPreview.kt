package com.efxcreator.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
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

/** 안쪽 원 : 바깥 링 = 4 : 6 (반지름 방향 비율, 전체 반지름을 10 으로 봤을 때) */
private const val INNER_RATIO = 0.4f

/**
 * 응원봉 상단을 위에서 본 프리뷰.
 * **바깥쪽 링** 이 켜지고 안쪽 원은 켜지지 않는 어두운 면이다.
 * 반지름을 10 으로 보면 안쪽 원 4, 바깥 링 6 (링 두께 = 전체 반지름의 60%).
 */
@Composable
fun LedPreview(color: LedColor, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val bodyColor = cs.surfaceContainerHighest
    val offRing = cs.outlineVariant
    val innerColor = cs.surfaceContainerLowest
    val innerEdge = cs.surfaceBright
    val led = color.toComposeColor()
    val brightness = maxOf(color.r, color.g, color.b) / 255f

    Canvas(modifier = modifier.aspectRatio(1f)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        // 빛 번짐이 들어갈 여백을 남기고 몸체를 그린다
        val body = radius * 0.92f
        val innerRadius = body * INNER_RATIO
        val ringWidth = body - innerRadius
        val ringMid = innerRadius + ringWidth / 2f

        // 몸체
        drawCircle(bodyColor, radius = body, center = center)

        // 링 둘레로 번지는 빛
        if (brightness > 0.02f) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    (innerRadius / radius) to Color.Transparent,
                    (ringMid / radius) to led.copy(alpha = 0.45f * brightness),
                    (body / radius) to led.copy(alpha = 0.30f * brightness),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        // 링: 꺼져 있으면 어두운 회색, 켜지면 LED 색
        val ringColor = if (brightness > 0.02f) led else offRing
        drawCircle(ringColor, radius = ringMid, center = center, style = Stroke(width = ringWidth))
        if (brightness > 0.02f) {
            drawCircle(
                Color.White.copy(alpha = 0.20f * brightness),
                radius = ringMid + ringWidth * 0.30f, center = center,
                style = Stroke(width = ringWidth * 0.14f)
            )
        }

        // 안쪽 원: 점등되지 않는 면
        drawCircle(innerColor, radius = innerRadius, center = center)
        drawCircle(
            innerEdge, radius = innerRadius, center = center,
            style = Stroke(width = radius * 0.02f)
        )
    }
}
