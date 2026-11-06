package com.efxcreator.model

import com.lightstick.efx.EfxEntry

/**
 * 이펙트 편집 다이얼로그에서 시작 시각/지속시간을 바꿨을 때 다른 이펙트와 겹치는 부분을 처리한다.
 * 저장 전 안내(미리보기)와 실제 저장이 같은 계산을 쓴다.
 *
 * - 덮어쓰기 ON : 편집한 이펙트가 차지하는 구간 [시작, 시작+지속시간) 에서 시작하는 다른 이펙트는 삭제하고,
 *                 구간 끝을 넘어 이어지던 이펙트는 시작을 구간 끝으로 줄여(지속시간도 그만큼 줄임) 남은 부분을 살린다.
 * - 덮어쓰기 OFF: 겹치는 만큼 뒤의 이펙트들이 함께 뒤로 밀린다 (서로의 간격은 유지).
 * - 지속시간이 0("다음 이펙트까지")이면 길이가 없으므로 같은 시각의 이펙트만 교체한다.
 */
object EfxOverwrite {

    data class Result(
        val entries: List<EfxEntry>,
        val removed: Int = 0,
        val trimmed: Int = 0,
        val pushed: Int = 0,
        val pushedByMs: Long = 0L
    )

    /** [others] (편집한 이펙트 제외)에 [edited] 를 적용한 전체 목록 (정렬 전) */
    fun apply(others: List<EfxEntry>, edited: EfxEntry, overwrite: Boolean): Result {
        val r = compute(others, edited.timestampMs, edited.payload.duration, overwrite)
        return r.copy(entries = r.entries + edited)
    }

    /** 다이얼로그 안내용: 편집한 이펙트를 뺀 나머지 목록에 생기는 변화 */
    fun preview(others: List<EfxEntry>, startMs: Long, ticks: Int, overwrite: Boolean): Result =
        compute(others, startMs, ticks, overwrite)

    private fun compute(others: List<EfxEntry>, t: Long, ticks: Int, overwrite: Boolean): Result {
        val sorted = others.sortedBy { it.timestampMs }
        val len = EfxDurations.ticksToMs(ticks)
        val end = t + len

        // 길이가 없으면 같은 시각의 이펙트만 교체한다
        if (len == 0L) {
            val kept = sorted.filter { it.timestampMs != t }
            return Result(kept, removed = sorted.size - kept.size)
        }

        if (overwrite) {
            var removed = 0
            var trimmed = 0
            val kept = ArrayList<EfxEntry>(sorted.size)
            sorted.forEachIndexed { i, o ->
                val ts = o.timestampMs
                when {
                    ts == t -> removed++
                    ts > t && ts < end -> {
                        val oEnd = EfxDurations.playEndMs(sorted, i)
                        if (oEnd != null && oEnd <= end) {
                            removed++
                        } else {
                            val remain = if (o.payload.duration > 0 && oEnd != null) EfxDurations.msToTicks(oEnd - end) else 0
                            kept += o.copy(timestampMs = end, payload = o.payload.copy(duration = remain))
                            trimmed++
                        }
                    }
                    else -> kept += o
                }
            }
            return Result(kept, removed = removed, trimmed = trimmed)
        }

        // 덮어쓰기 OFF: 시작 시각 이후의 이펙트는 겹치는 만큼 통째로 뒤로 민다
        val first = sorted.firstOrNull { it.timestampMs >= t } ?: return Result(sorted)
        val shift = (end - first.timestampMs).coerceAtLeast(0L)
        if (shift == 0L) return Result(sorted)
        val shifted = sorted.map { if (it.timestampMs >= t) it.copy(timestampMs = it.timestampMs + shift) else it }
        return Result(shifted, pushed = sorted.count { it.timestampMs >= t }, pushedByMs = shift)
    }
}
