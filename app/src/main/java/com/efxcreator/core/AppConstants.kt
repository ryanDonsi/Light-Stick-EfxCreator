package com.efxcreator.core

/** 포팅된 음악 분석 파이프라인용 상수. */
object AppConstants {
    const val LOG_ENABLED = true
    const val LOG_VERBOSE_ENABLED = false

    object Feature {
        const val AUTO_TIMELINE = "AutoTimeline"
    }

    val LOG_ENABLED_FEATURES: Set<String> = setOf(Feature.AUTO_TIMELINE)
}
