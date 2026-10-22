package com.efxcreator.music

import android.content.Context
import android.net.Uri
import android.util.Log
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

/**
 * 음악 파일(Uri) → 자동 분석 → [Efx] 변환.
 *
 * [AutoTimelineGeneratorBeat] 가 만든 (timestampMs, 20바이트 payload) 프레임을
 * Light-Stick-Music 의 EffectEngineController 와 동일하게
 * `EfxEntry(timestampMs, LSEffectPayload.fromByteArray(bytes))` 로 변환한다.
 * 분석 파이프라인은 파일 경로를 요구하므로 Uri 내용을 캐시 디렉토리에 임시 복사해서 사용한다.
 */
class AutoEfxGenerator(private val context: Context) {

    class GenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)

    companion object {
        private const val TAG = "AutoEfxGenerator"
    }

    /** CPU 집약적(수십 초 가능)이므로 Dispatchers.Default 에서 실행한다. */
    suspend fun generate(uri: Uri): Efx = withContext(Dispatchers.Default) {
        val musicId = try {
            MusicId.fromUri(context, uri)
        } catch (e: Exception) {
            throw GenerationException("음악 ID를 계산할 수 없습니다: ${e.message}", e)
        }

        val tempFile = copyToCache(uri)
        try {
            coroutineContext.ensureActive()
            val frames = try {
                AutoTimelineGeneratorBeat().generate(
                    musicPath = tempFile.absolutePath,
                    musicId = musicId,
                    paletteSize = AutoTimelineConfig.PALETTE_SIZE
                )
            } catch (e: Exception) {
                throw GenerationException("음악 분석에 실패했습니다: ${e.message}", e)
            }
            coroutineContext.ensureActive()

            val entries = framesToEntries(frames)
            if (entries.isEmpty()) {
                throw GenerationException("비트를 감지하지 못했습니다. 다른 음악 파일을 선택해 주세요.")
            }
            Log.d(TAG, "Generated ${entries.size} entries, musicId=0x${musicId.toUInt().toString(16)}")

            Efx(EfxHeader(musicId = musicId, entryCount = entries.size), EfxBody(entries))
        } finally {
            tempFile.delete()
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

    private fun copyToCache(uri: Uri): File {
        val dir = File(context.cacheDir, "auto_efx").apply { mkdirs() }
        val file = File.createTempFile("music_", ".tmp", dir)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw GenerationException("음악 파일을 열 수 없습니다.")
            input.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
        } catch (e: Exception) {
            file.delete()
            if (e is GenerationException) throw e
            throw GenerationException("음악 파일을 읽을 수 없습니다: ${e.message}", e)
        }
        return file
    }
}
