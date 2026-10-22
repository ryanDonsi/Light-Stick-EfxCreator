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

/** 응원봉 상단을 위에서 본 원형 LED 프리뷰 */
@Composable
fun LedPreview(color: LedColor, modifier: Modifier = Modifier) {
    val led = color.toComposeColor()
    val brightness = maxOf(color.r, color.g, color.b) / 255f

    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        // 바깥 케이스
        drawCircle(Color(0xFF2B2B30), radius = radius, center = center)
        drawCircle(Color(0xFF3A3A42), radius = radius, center = center, style = Stroke(width = radius * 0.04f))

        // 발광 번짐
        if (brightness > 0.02f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(led.copy(alpha = 0.55f * brightness), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        // LED 본체
        drawCircle(Color(0xFF111114), radius = radius * 0.72f, center = center)
        drawCircle(led, radius = radius * 0.66f, center = center)

        // 하이라이트
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.18f * brightness), Color.Transparent),
                center = Offset(center.x - radius * 0.2f, center.y - radius * 0.25f),
                radius = radius * 0.5f
            ),
            radius = radius * 0.66f,
            center = center
        )
    }
}
