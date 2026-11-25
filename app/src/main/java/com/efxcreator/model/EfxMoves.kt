package com.efxcreator.model

import com.lightstick.efx.EfxEntry

/**
 * 타임라인에서 이펙트를 옮길 때의 계산. 드래그 중 미리보기와 손을 뗐을 때의 확정이 **같은 계산**을 쓴다.
 *
 * "삽입" 방식: 이펙트를 빼도 빠진 자리는 비워 두고(뒤 이펙트를 당겨오지 않음), 놓은 경계에 끼워 넣는다.
 * - 끼워 넣을 자리와 겹치는 뒤쪽 이펙트만 겹치는 만큼 뒤로 밀린다. 원래 자리 근처에 놓으면 아무것도 바뀌지 않는다.
 * - 다른 이펙트의 한가운데에 놓으면 쪼개지 않고 가까운 경계(그 이펙트의 시작 또는 끝)에 붙는다.
 *   이펙트가 없는 빈 구간에 놓으면 놓은 시각 그대로 들어간다.
 * - 옮긴 이펙트는 자기 duration(길이)을 유지하고, 여러 개를 옮기면 서로의 간격도 유지한다.
 */
object EfxMoves {

    /** [entries] 는 옮긴 뒤의 목록, [movedTimes] 는 옮겨진 이펙트들의 새 시각 */
    /**
     * [entries] 는 옮긴 뒤의 목록, [movedTimes] 는 옮겨진 이펙트들의 새 시각,
     * [origins] 는 [entries] 의 각 항목이 옮기기 전 목록에서 몇 번째였는지 (타임라인 애니메이션이 같은 이펙트를 추적하는 데 쓴다)
     */
    data class Result(val entries: List<EfxEntry>, val movedTimes: Set<Long>, val origins: List<Int> = emptyList())

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
        if (delta == 0L) return Result(list, movedOld.map { it.timestampMs }.toSet(), list.indices.toList())

        val songEnd = songEndMs?.takeIf { it > 0L }
        // 옮기는 묶음이 차지하던 구간의 길이: 처음~마지막 이펙트 간격 + 마지막 이펙트가 차지하던 구간
        val width = (oldLast - oldFirst) + slotMs(list, move.max(), songEnd)
        val restIdx = list.indices.filter { it !in move }
        val movedIdx = list.indices.filter { it in move }
        val rest = restIdx.map { list[it] }

        // 1) 빠진 자리는 그대로 비워 둔다 (뒤의 이펙트를 당겨오지 않는다)
        val closed = rest

        // 2) 놓은 지점
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

        // 4) 옮긴 이펙트가 [b, b+width) 를 차지한다. 겹치는 뒤쪽 이펙트만, 겹치는 만큼 한꺼번에 뒤로 민다 (서로의 간격은 유지)
        val firstAfter = closed.filter { it.timestampMs >= b }.minOfOrNull { it.timestampMs }
        val shift = if (firstAfter == null) 0L else maxOf(0L, b + width - firstAfter)
        val pushed = if (shift == 0L) closed else closed.map { e -> if (e.timestampMs >= b) e.copy(timestampMs = e.timestampMs + shift) else e }
        val moved = movedOld.map { it.copy(timestampMs = b + (it.timestampMs - oldFirst)) }
        return Result(pushed + moved, moved.map { it.timestampMs }.toSet(), restIdx + movedIdx)
    }

    /** 드래그 중 미리보기용: [move] 결과를 시각순으로 정렬하고 duration 을 정리해 타임라인에 바로 그릴 수 있게 한다 */
    fun preview(list: List<EfxEntry>, indices: Collection<Int>, deltaMs: Long, songEndMs: Long?): Result? {
        val r = move(list, indices, deltaMs, songEndMs) ?: return null
        val order = r.entries.indices.sortedBy { r.entries[it].timestampMs }
        val sorted = order.map { r.entries[it] }
        return Result(EfxDurations.clamp(EfxDurations.reconcile(list, sorted)), r.movedTimes, order.map { r.origins[it] })
    }
}
