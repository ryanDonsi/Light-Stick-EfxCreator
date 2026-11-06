package com.efxcreator.music.section

import com.efxcreator.music.beat.BeatDetectorRouter

/**
 * SectionDetector 라우터.
 * 포팅 범위에서는 SectionDetectorV3(마디(bar) 단위 가변 길이 특징 윈도우)만 지원한다.
 * [version] 인자는 Music 앱과의 호출 시그니처 호환을 위해 남겨 두었다.
 */
object SectionDetectorRouter {

    fun detect(
        version: Int,
        lowEnv: List<Float>,
        midEnv: List<Float>,
        fullEnv: List<Float>,
        highEnv: List<Float>,
        beats: List<BeatDetectorRouter.BeatInfo.Beat>,
        beatMs: Long,
        durationMs: Long,
        hopMs: Long,
        beatsPerBar: Int = 4,
        downbeatMs: Long = 0L
    ): List<SectionDetector.AnnotatedBeat> = SectionDetectorV3().detect(
        lowEnv      = lowEnv,  midEnv   = midEnv,
        fullEnv     = fullEnv, highEnv  = highEnv,
        beats       = beats,   beatMs   = beatMs,
        durationMs  = durationMs, hopMs = hopMs,
        beatsPerBar = beatsPerBar, downbeatMs = downbeatMs
    )
}
