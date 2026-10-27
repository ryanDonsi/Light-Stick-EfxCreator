package com.efxcreator.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * Light-Stick-Music 의 CustomSlider 를 가져왔다 (색상 선택 슬라이더와 같은 모양: 둥근 그라데이션 트랙 + 원형 손잡이).
 */

/**
 *  CustomSlider - Float 버전
 *
 * Float 값을 사용하는 슬라이더 (정밀한 색상 조절 등에 유용)
 *
 * ## 사용 예시
 * ```kotlin
 * CustomSlider(
 *     value = 0.5f,
 *     onValueChange = { value = it },
 *     valueRange = 0f..1f,
 *     trackColor = Color.Red
 * )
 * ```
 */
@Composable
fun CustomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enableHaptic: Boolean = true,
    trackColor: Color = Color.White,
    trackGradient: Brush? = null,
    thumbColor: Color = Color.White,
    trackHeight: Dp = 8.dp,
    thumbSize: Dp = 24.dp,
    thumbShadowElevation: Dp = 2.dp
) {
    val view = LocalView.current
    val density = LocalDensity.current

    var sliderWidth by remember { mutableIntStateOf(0) }
    var lastHapticStep by remember { mutableIntStateOf(-1) }

    val normalizedValue = ((value - valueRange.start) /
            (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    val thumbRadius = with(density) { thumbSize.toPx() / 2 }
    val availableWidth = (sliderWidth - with(density) { thumbSize.toPx() }).coerceAtLeast(0f)
    val thumbPosition = if (availableWidth > 0f) {
        thumbRadius + (normalizedValue * availableWidth).coerceIn(0f, availableWidth)
    } else {
        thumbRadius
    }

    fun calculateValue(offsetX: Float): Float {
        if (sliderWidth <= 0) return value

        val thumbSizePx = with(density) { thumbSize.toPx() }
        val availableWidth = (sliderWidth - thumbSizePx).coerceAtLeast(0f)

        if (availableWidth <= 0f) return value

        val adjustedOffset = (offsetX - thumbSizePx / 2).coerceIn(0f, availableWidth)
        val newNormalizedValue = adjustedOffset / availableWidth

        val rawValue = valueRange.start +
                (newNormalizedValue * (valueRange.endInclusive - valueRange.start))

        return if (steps > 0) {
            val stepSize = (valueRange.endInclusive - valueRange.start) / (steps + 1)
            val steppedValue = (rawValue / stepSize).roundToInt() * stepSize
            steppedValue.coerceIn(valueRange)
        } else {
            rawValue.coerceIn(valueRange)
        }
    }

    fun updateValue(newValue: Float) {
        if (enableHaptic && steps > 0) {
            val currentStep = ((newValue - valueRange.start) /
                    ((valueRange.endInclusive - valueRange.start) / (steps + 1))).roundToInt()
            if (currentStep != lastHapticStep) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                lastHapticStep = currentStep
            }
        }
        onValueChange(newValue)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbSize)
            .onSizeChanged { size ->
                sliderWidth = size.width
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newValue = calculateValue(offset.x)
                    updateValue(newValue)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val newValue = calculateValue(change.position.x)
                    updateValue(newValue)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(trackHeight / 2))
                .then(
                    if (trackGradient != null) {
                        Modifier.background(trackGradient)
                    } else {
                        Modifier.background(trackColor)
                    }
                )
        )

        Box(
            modifier = Modifier
                .size(thumbSize)
                .offset(x = with(density) { (thumbPosition - thumbSize.toPx() / 2).toDp() })
                .align(Alignment.CenterStart)
                .shadow(
                    elevation = thumbShadowElevation,
                    shape = CircleShape,
                    clip = false,
                    spotColor = Color.Black.copy(alpha = 0.6f)
                )
                .background(thumbColor, CircleShape)
        )
    }
}
