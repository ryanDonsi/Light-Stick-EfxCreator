package com.efxcreator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.efxcreator.music.sets.RgbColor
import com.efxcreator.R
import com.efxcreator.core.Loc

private data class Hsv(val h: Float, val s: Float, val v: Float)

/**
 * 색상 선택: Light-Stick-Music 의 색상 선택 화면과 같은 세 개의 그라데이션 슬라이더
 * (색상 / 선명도 / 밝기)와, RGB 값을 직접 입력하는 칸.
 * 슬라이더를 움직이면 RGB 칸이 바뀌고, RGB 칸에 입력하면 슬라이더가 따라 움직인다.
 */
@Composable
fun HsvColorPicker(color: RgbColor, onChange: (RgbColor) -> Unit, modifier: Modifier = Modifier) {
    var hsv by remember { mutableStateOf(rgbToHsv(color)) }
    // 밖(RGB 입력, 빠른 선택, 슬롯 변경)에서 색이 바뀐 경우에만 슬라이더 위치를 다시 계산한다.
    // 회색처럼 색상(hue) 정보가 없는 색에서 슬라이더가 튀지 않도록 슬라이더가 만든 값은 그대로 둔다.
    LaunchedEffect(color) {
        if (hsvToRgb(hsv) != color) hsv = rgbToHsv(color)
    }

    fun update(next: Hsv) {
        hsv = next
        onChange(hsvToRgb(next))
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 미리보기
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(MaterialTheme.shapes.small)
                .background(Color(color.r, color.g, color.b))
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
        )

        SliderBlock(Loc.str(R.string.hsv_color_picker_1)) {
            CustomSlider(
                value = 360f - hsv.h,
                onValueChange = { update(hsv.copy(h = 360f - it)) },
                valueRange = 0f..360f,
                steps = 359,
                trackGradient = Brush.horizontalGradient(
                    listOf(
                        Color(0xFFFF0000), Color(0xFFFF00FF), Color(0xFF0000FF),
                        Color(0xFF00FFFF), Color(0xFF00FF00), Color(0xFFFFFF00), Color(0xFFFF0000)
                    )
                ),
                thumbColor = MaterialTheme.colorScheme.onSurface,
                trackHeight = 32.dp,
                thumbSize = 28.dp
            )
        }

        SliderBlock(Loc.str(R.string.hsv_color_picker_2)) {
            val gradient = remember(hsv.h, hsv.v) {
                Brush.horizontalGradient(listOf(hsvToColor(hsv.h, 0f, hsv.v), hsvToColor(hsv.h, 1f, hsv.v)))
            }
            CustomSlider(
                value = hsv.s,
                onValueChange = { update(hsv.copy(s = it)) },
                valueRange = 0f..1f,
                steps = 99,
                trackGradient = gradient,
                thumbColor = MaterialTheme.colorScheme.onSurface,
                trackHeight = 32.dp,
                thumbSize = 28.dp
            )
        }

        SliderBlock(Loc.str(R.string.hsv_color_picker_3)) {
            val gradient = remember(hsv.h, hsv.s) {
                Brush.horizontalGradient(listOf(hsvToColor(hsv.h, hsv.s, 0f), hsvToColor(hsv.h, hsv.s, 1f)))
            }
            CustomSlider(
                value = hsv.v,
                onValueChange = { update(hsv.copy(v = it)) },
                valueRange = 0f..1f,
                steps = 99,
                trackGradient = gradient,
                thumbColor = MaterialTheme.colorScheme.onSurface,
                trackHeight = 32.dp,
                thumbSize = 28.dp
            )
        }

        // RGB 직접 입력
        SliderBlock(Loc.str(R.string.hsv_color_picker_4)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                NumberTextField(
                    value = color.r, onValueChange = { onChange(color.copy(r = it)) },
                    label = "R", range = 0..255, modifier = Modifier.weight(1f)
                )
                NumberTextField(
                    value = color.g, onValueChange = { onChange(color.copy(g = it)) },
                    label = "G", range = 0..255, modifier = Modifier.weight(1f)
                )
                NumberTextField(
                    value = color.b, onValueChange = { onChange(color.copy(b = it)) },
                    label = "B", range = 0..255, modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SliderBlock(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

// ── HSV <-> RGB (Light-Stick-Music 의 ColorPickerDialog 와 같은 표준 공식) ──

private fun rgbToHsv(c: RgbColor): Hsv {
    val r = c.r.coerceIn(0, 255) / 255f
    val g = c.g.coerceIn(0, 255) / 255f
    val b = c.b.coerceIn(0, 255) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6)
        max == g -> 60f * (((b - r) / delta) + 2)
        else -> 60f * (((r - g) / delta) + 4)
    }.let { if (it < 0) it + 360f else it }
    return Hsv(hue, if (max == 0f) 0f else delta / max, max)
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val c = value * saturation
    val x = c * (1 - kotlin.math.abs((hue / 60f) % 2 - 1))
    val m = value - c
    val (r, g, b) = when {
        hue < 60 -> Triple(c, x, 0f)
        hue < 120 -> Triple(x, c, 0f)
        hue < 180 -> Triple(0f, c, x)
        hue < 240 -> Triple(0f, x, c)
        hue < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

private fun hsvToRgb(hsv: Hsv): RgbColor {
    val c = hsvToColor(hsv.h, hsv.s, hsv.v)
    return RgbColor(
        (c.red * 255f + 0.5f).toInt().coerceIn(0, 255),
        (c.green * 255f + 0.5f).toInt().coerceIn(0, 255),
        (c.blue * 255f + 0.5f).toInt().coerceIn(0, 255)
    )
}
