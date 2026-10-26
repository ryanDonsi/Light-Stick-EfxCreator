package com.efxcreator.ui.editor

import com.lightstick.efx.EfxEntry
import com.lightstick.types.EffectType
import kotlin.math.abs

/** 타임라인의 "다음 이펙트까지" 구간 블록 */
data class TimelineBlock(val index: Int, val startMs: Long, val endMs: Long, val entry: EfxEntry)

object TimelineMath {

    /** 마지막 엔트리 뒤에 곡 길이 정보가 없을 때 보여줄 최소 블록 길이 */
    const val TAIL_BLOCK_MS = 2_000L

    /** 엔트리마다 [시작, 다음 엔트리 시작) 블록을 만든다. 마지막은 곡 끝(없으면 +[TAIL_BLOCK_MS])까지. */
    fun blocks(entries: List<EfxEntry>, durationMs: Long): List<TimelineBlock> =
        entries.mapIndexed { i, e ->
            val end = when {
                i + 1 < entries.size -> entries[i + 1].timestampMs
                durationMs > e.timestampMs -> durationMs
                else -> e.timestampMs + TAIL_BLOCK_MS
            }
            TimelineBlock(i, e.timestampMs, end, e)
        }

    /** 가장 가까운 비트(허용 오차 [toleranceMs] 이내)로 스냅하고, 없으면 10ms 단위로 반올림한다. */
    fun snap(ms: Long, beatsMs: List<Long>, toleranceMs: Long, snapToBeats: Boolean): Long {
        val clamped = ms.coerceAtLeast(0L)
        if (snapToBeats && beatsMs.isNotEmpty()) {
            val nearest = nearestBeat(clamped, beatsMs)
            if (nearest != null && abs(nearest - clamped) <= toleranceMs) return nearest
        }
        return (clamped + 5) / 10 * 10
    }

    /** 정렬된 beatsMs 에서 ms 에 가장 가까운 값 */
    fun nearestBeat(ms: Long, beatsMs: List<Long>): Long? {
        if (beatsMs.isEmpty()) return null
        var lo = 0
        var hi = beatsMs.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (beatsMs[mid] < ms) lo = mid + 1 else hi = mid
        }
        val after = beatsMs[lo]
        val before = if (lo > 0) beatsMs[lo - 1] else after
        return if (abs(ms - before) <= abs(after - ms)) before else after
    }

    /** 눈금 라벨 간격(초): 라벨 사이가 최소 [minPx] 픽셀이 되도록 고른다. */
    fun rulerStepSec(pxPerMs: Float, minPx: Float = 90f): Float {
        val candidates = floatArrayOf(0.5f, 1f, 2f, 5f, 10f, 15f, 30f, 60f, 120f, 300f)
        return candidates.firstOrNull { it * 1000f * pxPerMs >= minPx } ?: candidates.last()
    }

    /**
     * 같은 패킷 필드(period)를 이벤트에 따라 다르게 부른다:
     * ON/OFF 는 색 전환 시간(Transit), BLINK/STROBE/BREATH 는 이펙트 주기(Period). 단위는 모두 10ms.
     */
    fun periodLabel(type: EffectType): String =
        if (type == EffectType.ON || type == EffectType.OFF) "Transit" else "Period"

    /**
     * 그룹 마스크 표시:
     * - 0           = 마스터 키 (모든 그룹, 그룹 소속과 무관하게 적용)
     * - 0xFFFFFFFF = 32개 그룹을 전부 지정 (결과는 모든 그룹이지만 마스터(0)와는 약간 다름)
     * - 그 외       = 켜진 비트의 GRPn 목록 (bit(n-1) = 그룹 n)
     */
    fun formatGroupMask(mask: Long): String = when (mask) {
        0L -> "마스터 · 모든 그룹 (0)"
        0xFFFFFFFFL -> "전체 그룹 지정 (0xFFFFFFFF)"
        else -> {
            val groups = (0 until 32).filter { ((mask shr it) and 1L) == 1L }.map { "GRP${it + 1}" }
            "${groups.joinToString(", ")} (0x%08X)".format(mask)
        }
    }

    fun formatTime(ms: Long, withTenths: Boolean = false): String {
        val total = ms.coerceAtLeast(0L)
        val m = total / 60_000
        val s = (total % 60_000) / 1000
        return if (withTenths) "%d:%02d.%d".format(m, s, (total % 1000) / 100) else "%d:%02d".format(m, s)
    }
}
