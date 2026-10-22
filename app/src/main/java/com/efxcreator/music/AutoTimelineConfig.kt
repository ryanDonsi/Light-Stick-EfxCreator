package com.efxcreator.music

/**
 * 자동 이펙트 생성 파이프라인 설정.
 *
 * Light-Stick-Music 의 AutoTimelineConfig 에서 EfxCreator 포팅 범위(기본 경로)만 남긴 것이다.
 * 파이프라인: BeatDetector → SectionDetector → MusicStyleClassifier → EffectMatchingEngine
 */
object AutoTimelineConfig {

    /** 비트 감지기 버전. 1 = BeatDetectorV1, 그 외 = BeatDetectorV3 (Tempogram, 기본값). */
    const val BEAT_DETECTOR_VERSION = 3

    /** BeatDetector hopMs — V1/V3 모두 10ms. */
    fun beatDetectorHopMs(version: Int = BEAT_DETECTOR_VERSION): Long = 10L

    /** 섹션 감지기 버전. SectionDetectorRouter 는 현재 V3 만 지원한다. */
    const val SECTION_DETECTOR_VERSION = 3

    /** 이펙트 규칙 버전. EffectMatchingEngineRouter 는 현재 V1 만 지원한다. */
    const val EFFECT_RULE_VERSION = 1

    const val PALETTE_SIZE = 4

    /** 비트 감지기 공통 BPM 탐색 범위 */
    const val MIN_BEAT_MS = 375L   // ~160 BPM (BeatDetector 내부 하한과 일치)
    const val MAX_BEAT_MS = 1200L  // ~50 BPM
}
