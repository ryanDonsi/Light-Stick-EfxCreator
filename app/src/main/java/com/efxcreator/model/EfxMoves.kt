package com.efxcreator.model

import com.lightstick.efx.EfxEntry

/**
 * 타임라인에서 이펙트를 옮길 때의 계산. 드래그 중 미리보기와 손을 뗐을 때의 확정이 **같은 계산**을 쓴다.
 *
 * "순서 바꾸기" 방식: 이펙트를 빼내 빠진 자리를 뒤쪽이 당겨져 메우고, 놓은 경계에 끼워 넣으며 뒤쪽을 그만큼 다시 민다.
 * - 앞으로 옮기든 뒤로 옮기든 빠진 자리는 뒤의 이펙트가 앞으로 당겨져 메워진다 (앞 이펙트의 길이는 변하지 않는다).
 * - 다른 이펙트의 한가운데에 놓으면 쪼개지 않고 가까운 경계(그 이펙트의 시작 또는 끝)에 붙는다.
 *   이펙트가 없는 빈 구간에 놓으면 놓은 시각 그대로 들어간다.
 * - 옮긴 이펙트는 자기 duration(길이)을 유지하고, 여러 개를 옮기면 서로의 간격도 유지한다.
 * - 전체 길이는 변하지 않으며, 옮겼다가 되돌리면 원래대로 돌아온다. 원래 자리 근처에 놓으면 아무것도 바뀌지 않는다.
 */
object EfxMoves {

    /** [entries] 는 옮긴 뒤의 목록, [movedTimes] 는 옮겨진 이펙트들의 새 시각 */
    data class Result(val entries: List<EfxEntry>, val movedTimes: Set<Long>)

    /** 이펙트가 차지하는 구간: 다음 이펙트까지의 간격 (마지막이면 duration, 없으면 기본 길이) */
    private fun slotMs(list: List<EfxEntry>, i: Int, songEndMs: Long?): Long {
        val next = list.getOrNull(i + 1)?.timestampMs
        return if (next != null) (next - list[i].timestampMs).coerceAtLeast(1L) else EfxDurations.lengthMs(list, i, songEndMs)
    }

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
        // 옮기는 묶음이 차지하던 구간의 길이: 처음~마지막 이펙트 간격 + 마지막 이펙트가 차지하던 구간
        val width = (oldLast - oldFirst) + slotMs(list, move.max(), songEnd)
        val rest = list.filterIndexed { i, _ -> i !in move }

        // 1) 빠진 자리 메우기: 묶음 뒤의 이펙트는 width 만큼 앞으로 당겨진다
        val closed = rest.map { e ->
            if (e.timestampMs >= oldFirst + width) e.copy(timestampMs = e.timestampMs - width) else e
        }

        // 2) 놓은 지점. 옮긴 이펙트는 끼워 넣은 뒤에도 놓은 위치에서 시작하므로 메운 시간축에서도 같은 값을 그대로 쓴다
        //    (뒤쪽 이펙트는 끼워 넣은 뒤 width 만큼 다시 밀려 원래 자리로 돌아간다)
        val p = oldFirst + delta

        // 3) 끼워 넣을 경계: 다른 이펙트가 재생 중인 한가운데면 가까운 경계(시작 또는 끝)로 붙인다
        var b = p
        val k = closed.indexOfLast { it.timestampMs < p }
        if (k >= 0) {
            val a = closed[k]
            val playEnd = EfxDurations.playEndMs(closed, k) ?: (a.timestampMs + EfxDurations.lengthMs(closed, k, songEnd))
            if (p < playEnd) {
                val slotEnd = closed.getOrNull(k + 1)?.timestampMs ?: playEnd
                b = if (p - a.timestampMs <= slotEnd - p) a.timestampMs else slotEnd
            }
        }

        // 4) 경계 이후의 이펙트는 width 만큼 뒤로 밀리고, 옮긴 이펙트가 그 자리에 들어간다
        val pushed = closed.map { e -> if (e.timestampMs >= b) e.copy(timestampMs = e.timestampMs + width) else e }
        val moved = movedOld.map { it.copy(timestampMs = b + (it.timestampMs - oldFirst)) }
        return Result(pushed + moved, moved.map { it.timestampMs }.toSet())
    }

    /** 드래그 중 미리보기용: [move] 결과를 시각순으로 정렬하고 duration 을 정리해 타임라인에 바로 그릴 수 있게 한다 */
    fun preview(list: List<EfxEntry>, indices: Collection<Int>, deltaMs: Long, songEndMs: Long?): Result? {
        val r = move(list, indices, deltaMs, songEndMs) ?: return null
        val sorted = r.entries.sortedBy { it.timestampMs }
        return Result(EfxDurations.clamp(EfxDurations.reconcile(list, sorted)), r.movedTimes)
    }
}
