package com.efxcreator.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

/**
 * Material Design 3 모션 토큰 (easing / duration) 과 자주 쓰는 전환.
 * 참고: m3.material.io/styles/motion — Emphasized / Standard easing, Fade through, Shared axis.
 */
object EfxMotion {

    // Easing
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    // Duration (ms)
    const val SHORT = 100
    const val MEDIUM = 250
    const val LONG = 400

    /** 화면 전환 (Shared axis X) 에서 가로로 이동하는 거리 비율 (화면 폭의 1/12) */
    const val SLIDE_FRACTION = 12

    /**
     * Fade through: 나가는 콘텐츠가 먼저 사라지고(90ms), 잠시 뒤 들어오는 콘텐츠가 나타난다(210ms).
     * 서로 관련이 약한 콘텐츠(탭 전환 등) 교체에 쓴다.
     */
    fun fadeThrough(): ContentTransform =
        fadeIn(tween(210, delayMillis = 90, easing = StandardDecelerate)) togetherWith
            fadeOut(tween(90, easing = StandardAccelerate))
}
