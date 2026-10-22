package com.efxcreator.automusic

import com.lightstick.efx.EfxEntry
import com.lightstick.types.LSEffectPayload

/**
 * Light-Stick-Music의 BeatDetector → SectionDetector → MusicStyleClassifier →
 * EffectMatchingEngine 파이프라인(AutoTimelineGeneratorBeat)을 돌려 음악 파일로부터
 * 이펙트 타임라인을 자동 생성하고, EfxCreator가 쓰는 [EfxEntry] 리스트로 변환한다.
 *
 * frames(List<Pair<Long, ByteArray>>) → EfxEntry 변환은 Light-Stick-Music의
 * EffectEngineController.loadTimelineFromFrames()와 동일한 방식(LSEffectPayload.fromByteArray)을 쓴다.
 */
object AutoEfxGenerator {

    /**
     * @param musicPath 로컬 파일 절대 경로 (MediaExtractor가 content:// Uri를 직접 지원하지 않으므로
     *                  호출 측에서 먼저 임시 파일로 복사해 전달해야 한다)
     * @param musicId   SDK MusicId.fromUri/fromFile 로 계산한 값 — 팔레트 시드로 쓰인다
     */
    fun generate(musicPath: String, musicId: Int): List<EfxEntry> {
        val (frames, _) = AutoTimelineGeneratorBeat().generateWithSections(
            musicPath,
            musicId,
            AutoTimelineConfig.PALETTE_SIZE
        )

        return frames
            .mapNotNull { (timestampMs, bytes) ->
                runCatching { EfxEntry(timestampMs, LSEffectPayload.fromByteArray(bytes)) }.getOrNull()
            }
            .sortedBy { it.timestampMs }
            .mapIndexed { index, entry ->
                // EfxCreator 편집 화면과 동일한 Effect Index 규칙(1부터 시작)에 맞춘다.
                entry.copy(payload = entry.payload.copy(effectIndex = index + 1))
            }
    }
}
