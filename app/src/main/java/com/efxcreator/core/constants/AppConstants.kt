package com.efxcreator.core.constants

/**
 * Light-Stick-Music domain/music 파이프라인 포팅에 필요한 최소 상수 모음.
 * com.efxcreator.core.util.Log 의 TAG 화이트리스트로 쓰인다.
 */
object AppConstants {

    const val LOG_ENABLED = true
    const val LOG_VERBOSE_ENABLED = false

    object Feature {
        const val AUTO_TIMELINE = "AutoTimeline"
    }

    val LOG_ENABLED_FEATURES: Set<String> = setOf(
        Feature.AUTO_TIMELINE,
    )
}
