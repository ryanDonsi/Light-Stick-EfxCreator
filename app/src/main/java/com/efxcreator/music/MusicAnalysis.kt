package com.efxcreator.music

import com.efxcreator.music.section.SectionMeta

/**
 * 에디터 타임라인 표시용 음악 분석 결과 (파형 + 섹션 + 비트).
 * Gson 으로 프로젝트별 사이드카 JSON 에 저장하므로 단순 타입만 사용한다.
 */
data class MusicAnalysis(
    val durationMs: Long,
    /** [waveform] 한 칸이 나타내는 시간(ms) */
    val waveformHopMs: Int,
    /** 0..1 로 정규화된 전체 에너지 envelope */
    val waveform: List<Float>,
    val beatTimesMs: List<Long>,
    val sections: List<AnalysisSection>,
    val musicStyle: String? = null,
    /**
     * 이펙트 세트/팔레트 세트를 바꿔 다시 생성하기 위한 엔진 입력.
     * 이 필드가 생기기 전에 저장된 분석 결과에는 없다(null) — 그 경우 다시 분석해야 한다.
     */
    val engineInput: EngineInput? = null
) {
    companion object {
        const val WAVEFORM_HOP_MS = 50

        /** envelope(hopMs 단위)를 [WAVEFORM_HOP_MS] 단위로 줄이고(구간 최대값) 0..1 로 정규화한다. */
        fun downsampleWaveform(fullEnv: List<Float>, envHopMs: Long): List<Float> {
            if (fullEnv.isEmpty()) return emptyList()
            val bin = (WAVEFORM_HOP_MS / envHopMs).toInt().coerceAtLeast(1)
            val out = ArrayList<Float>(fullEnv.size / bin + 1)
            var i = 0
            while (i < fullEnv.size) {
                var m = 0f
                for (k in i until minOf(i + bin, fullEnv.size)) m = maxOf(m, fullEnv[k])
                out += m
                i += bin
            }
            val peak = out.max().takeIf { it > 0f } ?: 1f
            return out.map { it / peak }
        }
    }
}

data class AnalysisSection(
    val startMs: Long,
    val endMs: Long,
    /** SectionDetector.SectionType.name (INTRO/VERSE/CHORUS/...) */
    val type: String
)

/** [AutoTimelineGeneratorBeat.generateAnalyzed] 결과 */
data class GenerationResult(
    val frames: List<Pair<Long, ByteArray>>,
    val sections: List<SectionMeta>,
    /** 분석 실패(비트/envelope 없음) 시 null */
    val analysis: MusicAnalysis?
)
