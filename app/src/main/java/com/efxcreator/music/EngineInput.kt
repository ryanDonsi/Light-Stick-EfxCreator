package com.efxcreator.music

import com.efxcreator.music.section.SectionDetector

/** [SectionDetector.AnnotatedBeat] 의 JSON 저장용 사본 (sectionType 은 이름 문자열) */
data class EngineBeat(
    val timeMs: Long,
    val confidence: Float,
    val sectionType: String,
    val localEnergy: Float,
    val onsetStrength: Float,
    val lowRatio: Float,
    val highRatio: Float,
    val beatInBar: Int,
    val sectionEnergy: Float,
    val sectionPeakEnergy: Float,
    val sectionLowRatio: Float,
    val sectionMidRatio: Float,
    val sectionHighRatio: Float,
    val sectionOnsetDensity: Float,
    val sectionPeriodicity: Float
) {
    fun toAnnotated() = SectionDetector.AnnotatedBeat(
        timeMs = timeMs, confidence = confidence,
        sectionType = SectionDetector.SectionType.valueOf(sectionType),
        localEnergy = localEnergy, onsetStrength = onsetStrength,
        lowRatio = lowRatio, highRatio = highRatio, beatInBar = beatInBar,
        sectionEnergy = sectionEnergy, sectionPeakEnergy = sectionPeakEnergy,
        sectionLowRatio = sectionLowRatio, sectionMidRatio = sectionMidRatio,
        sectionHighRatio = sectionHighRatio, sectionOnsetDensity = sectionOnsetDensity,
        sectionPeriodicity = sectionPeriodicity
    )

    companion object {
        fun of(b: SectionDetector.AnnotatedBeat) = EngineBeat(
            b.timeMs, b.confidence, b.sectionType.name, b.localEnergy, b.onsetStrength,
            b.lowRatio, b.highRatio, b.beatInBar, b.sectionEnergy, b.sectionPeakEnergy,
            b.sectionLowRatio, b.sectionMidRatio, b.sectionHighRatio, b.sectionOnsetDensity,
            b.sectionPeriodicity
        )
    }
}

/**
 * 이펙트 엔진이 프레임을 만들 때 필요한 분석 결과 전부.
 * 느린 분석(비트/섹션 검출)은 한 번만 하고 이 값을 저장해 두면, 이펙트·팔레트 세트를 바꿔
 * 다시 생성할 때는 엔진 단계만 빠르게 다시 돌릴 수 있다.
 */
data class EngineInput(
    val durationMs: Long,
    val hopMs: Long,
    val beatTimesMs: List<Long>,
    val downbeatMs: Long,
    val beatsPerBar: Int,
    val globalBeatMs: Long,
    val finalOffMs: Long,
    /** hopMs(10ms) 단위 전체 에너지 envelope */
    val fullEnv: List<Float>,
    val beats: List<EngineBeat>,
    /** MusicStyleClassifier.MusicStyle 이름 */
    val musicStyle: String
)
