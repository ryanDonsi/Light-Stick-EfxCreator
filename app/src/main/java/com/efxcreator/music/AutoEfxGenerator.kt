package com.efxcreator.music

import android.util.Log
import com.efxcreator.music.sets.GenerationOptions
import com.lightstick.efx.Efx
import com.lightstick.efx.EfxBody
import com.lightstick.efx.EfxEntry
import com.lightstick.efx.EfxHeader
import com.lightstick.efx.MusicId
import com.lightstick.types.LSEffectPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import com.efxcreator.R
import com.efxcreator.core.Loc

/**
 * 음악 파일 → 자동 분석 → [Efx] + [MusicAnalysis].
 *
 * [AutoTimelineGeneratorBeat] 가 만든 (timestampMs, 20바이트 payload) 프레임을
 * Light-Stick-Music 의 EffectEngineController 와 동일하게
 * `EfxEntry(timestampMs, LSEffectPayload.fromByteArray(bytes))` 로 변환한다.
 * 분석 파이프라인은 파일 경로를 요구하므로 앱 내부 저장소에 보관된 음악 파일을 입력으로 받는다.
 */
class AutoEfxGenerator {

    class GenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class Result(val efx: Efx, val analysis: MusicAnalysis?)

    companion object {
        private const val TAG = "AutoEfxGenerator"
    }

    /** 이펙트 타임라인까지 자동 생성. CPU 집약적(수십 초 가능)이므로 Dispatchers.Default 에서 실행한다. */
    suspend fun generate(
        musicFile: File, options: GenerationOptions = GenerationOptions()
    ): Result = withContext(Dispatchers.Default) {
        val musicId = computeMusicId(musicFile)
        val r = runPipeline(musicFile, musicId, options)
        coroutineContext.ensureActive()

        val entries = framesToEntries(r.frames)
        if (entries.isEmpty()) {
            throw GenerationException(Loc.str(R.string.auto_efx_generator_1))
        }
        Log.d(TAG, "Generated ${entries.size} entries, musicId=0x${musicId.toUInt().toString(16)}")

        Result(Efx(EfxHeader(musicId = musicId, entryCount = entries.size), EfxBody(entries)), r.analysis)
    }

    /** 이펙트는 만들지 않고 파형/섹션/비트 분석 결과만 필요할 때 사용한다. */
    suspend fun analyze(musicFile: File): MusicAnalysis = withContext(Dispatchers.Default) {
        val r = runPipeline(musicFile, computeMusicId(musicFile))
        r.analysis ?: throw GenerationException(Loc.str(R.string.auto_efx_generator_2))
    }

    fun computeMusicId(musicFile: File): Int = try {
        MusicId.fromFile(musicFile)
    } catch (e: Exception) {
        throw GenerationException(Loc.str(R.string.auto_efx_generator_3, e.message), e)
    }

    /**
     * 저장된 분석 결과([MusicAnalysis.engineInput])에 이펙트/팔레트 세트를 적용해 엔트리를 다시 만든다.
     * 느린 분석을 건너뛰므로 보통 1초 안팎으로 끝난다.
     */
    suspend fun render(
        analysis: MusicAnalysis, musicId: Int, options: GenerationOptions
    ): List<EfxEntry> = withContext(Dispatchers.Default) {
        val input = analysis.engineInput
            ?: throw GenerationException(Loc.str(R.string.auto_efx_generator_4))
        val r = try {
            AutoTimelineGeneratorBeat().render(input, musicId, options)
        } catch (e: Exception) {
            throw GenerationException(Loc.str(R.string.auto_efx_generator_5, e.message), e)
        }
        framesToEntries(r.frames)
    }

    private suspend fun runPipeline(
        musicFile: File, musicId: Int, options: GenerationOptions = GenerationOptions()
    ): GenerationResult {
        coroutineContext.ensureActive()
        return try {
            AutoTimelineGeneratorBeat().generateAnalyzed(
                musicPath = musicFile.absolutePath,
                musicId = musicId,
                paletteSize = AutoTimelineConfig.PALETTE_SIZE,
                options = options
            )
        } catch (e: Exception) {
            throw GenerationException(Loc.str(R.string.auto_efx_generator_6, e.message), e)
        }
    }

    internal fun framesToEntries(frames: List<Pair<Long, ByteArray>>): List<EfxEntry> =
        frames.mapNotNull { (timestampMs, bytes) ->
            try {
                EfxEntry(timestampMs, LSEffectPayload.fromByteArray(bytes))
            } catch (e: Exception) {
                Log.w(TAG, "Frame parse failed at ${timestampMs}ms: ${e.message}")
                null
            }
        }.sortedBy { it.timestampMs }
}
