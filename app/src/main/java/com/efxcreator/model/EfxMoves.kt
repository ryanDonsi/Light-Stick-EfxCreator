package com.efxcreator.model

import com.lightstick.efx.EfxEntry

/**
 * 타임라인에서 이펙트를 옮길 때의 계산. 드래그 중 미리보기와 손을 뗐을 때의 확정이 **같은 계산**을 쓴다.
 *
 * 놓은 자리에 끼워 넣고, 그 자리의 기존 이펙트는 그 뒤에 이어서 배치된다.
 * - 옮긴 이펙트는 자기 duration(길이)을 유지한다. 여러 개를 옮기면 서로의 간격도 유지한다.
 * - 놓은 지점에서 재생 중이던 이펙트 A 는 거기서 끊기고, 옮긴 이펙트 뒤에서 남은 길이만큼 이어서 재생된다(복제 항목).
 * - 놓은 자리 이후의 기존 이펙트는 옮긴 이펙트 길이만큼 뒤로 밀린다. (앞으로 옮길 때는 놓은 자리부터 원래 자리 앞까지만 밀려 비워진 자리를 채운다.)
 */
object EfxMoves {

    /** [entries] 는 옮긴 뒤의 목록, [movedTimes] 는 옮겨진 이펙트들의 새 시각 */
    data class Result(val entries: List<EfxEntry>, val movedTimes: Set<Long>)

    /**
     * [indices] 의 이펙트를 [deltaMs] 만큼 옮긴 결과(정렬 전). 0ms 아래로는 내려가지 않는다.
     * 옮길 것이 없으면 null.
     */
    fun move(list: List<EfxEntry>, indices: Collection<Int>, deltaMs: Long, songEndMs: Long?): Result? {
        val move = indices.toSet()
        val movedOld = list.filterIndexed { i, _ -> i in move }
        if (movedOld.isEmpty()) return null
        val oldFirst = movedOld.minOf { it.timestampMs }
        val oldLast = movedOld.maxOf { it.timestampMs }
        val delta = maxOf(deltaMs, -oldFirst)
        if (delta == 0L) return Result(list, movedOld.map { it.timestampMs }.toSet())

        val songEnd = songEndMs?.takeIf { it > 0L }
        // 옮기는 묶음이 차지하던 길이: 처음~마지막 이펙트 간격 + 마지막 이펙트의 길이(duration, 없으면 다음 이펙트까지)
        val span = (oldLast - oldFirst) + EfxDurations.lengthMs(list, move.max(), songEnd)
        val firstNew = oldFirst + delta
        val moved = movedOld.map { it.copy(timestampMs = it.timestampMs + delta) }
        val rest = list.filterIndexed { i, _ -> i !in move }

        // 놓은 지점에서 재생 중이던 이펙트 A: 끊기고, 옮긴 이펙트 뒤에서 남은 길이만큼 이어서 재생한다
        var tail: EfxEntry? = null
        val k = rest.indexOfLast { it.timestampMs < firstNew }
        if (k >= 0) {
            val a = rest[k]
            val aEnd = EfxDurations.playEndMs(rest, k)
            val playingAtDrop = aEnd == null || aEnd > firstNew
            if (playingAtDrop && (songEnd == null || firstNew < songEnd)) {
                val remain = if (a.payload.duration > 0 && aEnd != null) EfxDurations.msToTicks(aEnd - firstNew) else 0
                tail = a.copy(timestampMs = firstNew + span, payload = a.payload.copy(duration = remain))
            }
        }
        val pushed = rest.map { e ->
            val t = e.timestampMs
            val push = when {
                // 앞으로 옮기면: 놓은 자리부터 원래 자리 앞까지의 이펙트가 뒤로 밀려 비워진 자리를 채운다
                firstNew < oldFirst -> t >= firstNew && t < oldFirst
                // 뒤로 옮기면: 놓은 자리 이후의 이펙트가 밀린다
                else -> t >= firstNew
            }
            if (push) e.copy(timestampMs = t + span) else e
        }
        return Result(pushed + listOfNotNull(tail) + moved, moved.map { it.timestampMs }.toSet())
    }

    /** 드래그 중 미리보기용: [move] 결과를 시각순으로 정렬하고 duration 을 정리해 타임라인에 바로 그릴 수 있게 한다 */
    fun preview(list: List<EfxEntry>, indices: Collection<Int>, deltaMs: Long, songEndMs: Long?): Result? {
        val r = move(list, indices, deltaMs, songEndMs) ?: return null
        val sorted = r.entries.sortedBy { it.timestampMs }
        return Result(EfxDurations.clamp(EfxDurations.reconcile(list, sorted)), r.movedTimes)
    }
}
