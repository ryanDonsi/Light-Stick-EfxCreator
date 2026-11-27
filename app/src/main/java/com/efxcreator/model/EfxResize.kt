package com.efxcreator.model

import com.efxcreator.ui.editor.TimelineMath
import com.lightstick.efx.EfxEntry
import com.lightstick.types.EffectType

/**
 * 타임라인에서 선택한 이펙트의 좌/우 끝을 끌어 길이를 바꿀 때의 계산. 드래그 중 미리보기와 손을 뗐을 때의 확정이 같은 계산을 쓴다.
 *
 * "덮어쓰기" 방식: 늘린 구간에 다른 이펙트가 있으면 지우지 않고 겹친 만큼만 덮어쓴다.
 * - 완전히 덮인 이펙트는 삭제된다.
 * - 일부만 덮인 이펙트는 덮인 앞부분만 잘려 나가고(시작 시각이 뒤로 밀리고 duration 이 줄어듦) 끝은 그대로다.
 * - 오른쪽 끝: 시작 시각은 그대로, duration 이 바뀐다. 왼쪽 끝: 끝 시각은 그대로, 시작 시각과 duration 이 바뀐다.
 * - 줄일 때는 이웃을 건드리지 않는다.
 */
object EfxResize {

    /** 이 이하로는 줄일 수 없다 */
    const val MIN_MS = 100L

    /** [entries] 는 바뀐 목록(시각순), [index] 는 크기를 바꾼 이펙트의 새 위치, [startMs] 는 그 이펙트의 새 시작 시각 */
    data class Result(val entries: List<EfxEntry>, val index: Int, val startMs: Long, val endMs: Long)

    /** 길이를 조정할 수 있는 이펙트인가 (OFF 는 duration 이 없다) */
    fun resizable(entry: EfxEntry): Boolean = entry.payload.effectType != EffectType.OFF

    /**
     * [index] 이펙트의 왼쪽([left]=true) 또는 오른쪽 끝을 [edgeMs] 로 옮긴 결과. 바뀐 것이 없으면 null.
     * [songEndMs] 가 있으면 오른쪽 끝은 곡 끝을 넘지 못한다.
     */
    fun resize(list: List<EfxEntry>, index: Int, left: Boolean, edgeMs: Long, songEndMs: Long?): Result? {
        if (index !in list.indices || !resizable(list[index])) return null
        val songEnd = songEndMs?.takeIf { it > 0L }
        val blocks = TimelineMath.blocks(list, songEnd ?: 0L)
        val b = blocks[index]
        val e = list[index]
        val out = ArrayList<EfxEntry>(list.size)

        return if (!left) {
            val maxEnd = minOf(b.startMs + EfxDurations.ticksToMs(EfxDurations.MAX_TICKS), songEnd ?: Long.MAX_VALUE)
            val end = ((edgeMs + 5) / 10 * 10).coerceIn(b.startMs + MIN_MS, maxOf(b.startMs + MIN_MS, maxEnd))
            val ticks = EfxDurations.msToTicks(end - b.startMs)
            if (ticks == e.payload.duration && end == b.endMs) return null
            for ((j, x) in list.withIndex()) {
                when {
                    j < index -> out += x
                    j == index -> out += x.copy(payload = x.payload.copy(duration = ticks))
                    x.timestampMs >= end -> out += x
                    blocks[j].endMs <= end -> Unit                         // 완전히 덮임: 삭제
                    else -> {                                              // 앞부분만 덮임: 시작을 end 로, 끝은 그대로
                        val d = x.payload.duration
                        val keep = if (d > 0) EfxDurations.msToTicks(blocks[j].endMs - end) else 0
                        out += x.copy(timestampMs = end, payload = x.payload.copy(duration = keep))
                    }
                }
            }
            finish(out, index, b.startMs, end)
        } else {
            val start = ((edgeMs + 5) / 10 * 10).coerceIn(0L, maxOf(0L, b.endMs - MIN_MS))
            if (start == b.startMs) return null
            val d = e.payload.duration
            val ticks = if (d > 0) EfxDurations.msToTicks(b.endMs - start) else 0
            var newIndex = 0
            for ((j, x) in list.withIndex()) {
                when {
                    j < index && x.timestampMs >= start -> Unit            // 완전히 덮임: 삭제
                    j < index -> {
                        // 줄여서 틈이 생길 때, 바로 앞의 '다음까지 유지(0)' 이펙트가 틈까지 늘어나지 않게 기존 끝을 고정한다
                        val fix = j == index - 1 && start > b.startMs && x.payload.duration == 0 && x.payload.effectType != EffectType.OFF
                        out += if (fix) x.copy(payload = x.payload.copy(duration = EfxDurations.msToTicks(b.startMs - x.timestampMs))) else x
                    }
                    j == index -> { newIndex = out.size; out += x.copy(timestampMs = start, payload = x.payload.copy(duration = ticks)) }
                    else -> out += x
                }
            }
            finish(out, newIndex, start, b.endMs)
        }
    }

    private fun finish(list: List<EfxEntry>, index: Int, startMs: Long, endMs: Long): Result {
        val sorted = list.sortedBy { it.timestampMs }   // 안정 정렬: 같은 시각이면 기존 순서 유지
        val clamped = EfxDurations.clamp(sorted)
        return Result(clamped, sorted.indexOfFirst { it === list[index] }.coerceAtLeast(0), startMs, endMs)
    }
}
