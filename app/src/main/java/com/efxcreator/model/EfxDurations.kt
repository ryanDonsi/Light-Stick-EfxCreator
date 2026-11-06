package com.efxcreator.model

import com.lightstick.efx.EfxEntry
import com.lightstick.types.EffectType
import com.lightstick.types.LSEffectPayload

/**
 * SDK 2.1.0 `LSEffectPayload.duration` 규칙 (u16, **10ms 단위**).
 * - 0 = 다음 명령(다음 이펙트)까지 유지
 * - 1~65535 = 이 이펙트가 재생되는 시간. 지나면 LED 가 꺼진다 (다음 이펙트 시각보다 길 수 없다)
 *
 * 타임라인 블록 길이, 이펙트 상세 정보, 자동 생성, 이동/편집이 모두 이 규칙을 같이 쓴다.
 */
object EfxDurations {

    const val UNIT_MS = 10L
    const val MAX_TICKS = 65535

    /** 마지막 이펙트를 옮길 때 쓰는 기본 길이 */
    const val TAIL_MS = 2_000L

    /** Effect Index: 1부터 매기되 255 를 넘으면 0 부터 다시 시작한다 (SDK 는 0~255 밖이면 예외) */
    fun indexFor(position: Int): Int = (position + 1) % 256

    fun ticksToMs(ticks: Int): Long = ticks.toLong() * UNIT_MS

    /** ms → 10ms 단위(올림, 1~65535) */
    fun msToTicks(ms: Long): Int = ((ms + UNIT_MS - 1) / UNIT_MS).coerceIn(1L, MAX_TICKS.toLong()).toInt()

    /**
     * [i] 번째 이펙트가 실제로 재생되는 끝 시각.
     * duration>0 이면 min(시작+duration, 다음 이펙트 시작), 0 이면 다음 이펙트 시작. 뒤에 이펙트가 없고 duration=0 이면 null(곡 끝까지).
     */
    fun playEndMs(entries: List<EfxEntry>, i: Int): Long? {
        val e = entries[i]
        val d = e.payload.duration
        val next = entries.getOrNull(i + 1)?.timestampMs
        return when {
            d > 0 && next != null -> minOf(e.timestampMs + ticksToMs(d), next)
            d > 0 -> e.timestampMs + ticksToMs(d)
            else -> next
        }
    }

    /** 이펙트를 옮길 때 쓰는 길이(ms): duration>0 이면 그 값, 아니면 다음 이펙트까지의 간격(마지막이면 [TAIL_MS], 곡 끝이 가까우면 그만큼) */
    fun lengthMs(entries: List<EfxEntry>, i: Int, songEndMs: Long?): Long {
        val e = entries[i]
        val d = e.payload.duration
        val next = entries.getOrNull(i + 1)?.timestampMs
        return when {
            d > 0 -> ticksToMs(d).let { if (next != null) minOf(it, (next - e.timestampMs).coerceAtLeast(1L)) else it }
            next != null -> (next - e.timestampMs).coerceAtLeast(1L)
            else -> songEndMs?.let { (it - e.timestampMs).coerceIn(1L, TAIL_MS) } ?: TAIL_MS
        }
    }

    /** 자동 생성 결과용: 각 이펙트의 duration 을 "다음 이펙트까지의 간격"으로 채운다. OFF 와 마지막 이펙트는 0 */
    fun fillFull(entries: List<EfxEntry>): List<EfxEntry> {
        val sorted = entries.sortedBy { it.timestampMs }
        return sorted.mapIndexed { i, e ->
            val next = sorted.getOrNull(i + 1)
            val want = when {
                e.payload.effectType == EffectType.OFF || next == null -> 0
                else -> msToTicks(next.timestampMs - e.timestampMs)
            }
            e.copy(payload = e.payload.copy(duration = want, effectIndex = indexFor(i)))
        }
    }

    /** duration 이 다음 이펙트 시각을 넘지 않도록 줄인다 (늘리지는 않는다). OFF 는 0 */
    fun clamp(entries: List<EfxEntry>): List<EfxEntry> =
        entries.mapIndexed { i, e ->
            val p = e.payload
            val next = entries.getOrNull(i + 1)
            val want = when {
                p.effectType == EffectType.OFF -> 0
                next == null -> p.duration
                p.duration > 0 -> minOf(p.duration, msToTicks(next.timestampMs - e.timestampMs))
                else -> 0
            }
            if (want == p.duration) e else e.copy(payload = p.copy(duration = want))
        }

    private fun keyOf(e: EfxEntry): Pair<Long, LSEffectPayload> =
        e.timestampMs to e.payload.copy(duration = 0, effectIndex = 0)

    /**
     * 편집 전([old])에 "다음 이펙트까지 꽉 차 있던" 이펙트가 편집 후([new])에도 그 자리에 그대로 있으면,
     * 새 간격에 맞춰 duration 을 늘리거나 줄여 꽉 찬 상태를 유지한다 (이웃 이펙트를 지웠을 때 LED 가 일찍 꺼지지 않도록).
     * 옮겨졌거나 새로 만든 이펙트는 건드리지 않는다. [new] 는 시각순으로 정렬되어 있어야 한다.
     */
    fun reconcile(old: List<EfxEntry>, new: List<EfxEntry>): List<EfxEntry> {
        // 키 → 편집 전 duration. 사용자가 duration 을 직접 바꾼 이펙트(값이 달라진 것)는 건드리지 않는다
        val full = HashMap<Pair<Long, LSEffectPayload>, Int>()
        old.forEachIndexed { i, e ->
            val next = old.getOrNull(i + 1) ?: return@forEachIndexed
            val d = e.payload.duration
            if (d > 0 && e.timestampMs + ticksToMs(d) >= next.timestampMs) full[keyOf(e)] = d
        }
        if (full.isEmpty()) return new
        return new.mapIndexed { i, e ->
            val next = new.getOrNull(i + 1)
            if (next != null && e.payload.effectType != EffectType.OFF && full[keyOf(e)] == e.payload.duration) {
                val want = msToTicks(next.timestampMs - e.timestampMs)
                if (want == e.payload.duration) e else e.copy(payload = e.payload.copy(duration = want))
            } else e
        }
    }

    /**
     * SDK 2.0 으로 저장한 옛 파일 정리. 2.0 은 offset 18 에 effectIndex(1부터 순번)를 넣었고 2.1 은 그 자리를 duration 으로 읽는다.
     * 모든 항목의 duration 이 1,2,3 … 순번과 같으면 옛 파일로 보고 duration 을 0 으로 되돌리고 effectIndex 를 순번으로 다시 매긴다.
     */
    fun sanitizeLegacy(entries: List<EfxEntry>): List<EfxEntry> {
        if (entries.isEmpty()) return entries
        val legacy = entries.withIndex().all { (i, e) -> e.payload.duration == i + 1 }
        if (!legacy) return entries
        return entries.mapIndexed { i, e -> e.copy(payload = e.payload.copy(duration = 0, effectIndex = indexFor(i))) }
    }

    /** "다음 이펙트까지" 또는 "150 (1.5s)" 형태의 표시 문자열에 쓰는 초 단위 포맷 */
    fun formatMs(ms: Long): String =
        if (ms >= 1000L) "%.2fs".format(ms / 1000.0).replace(Regex("\\.?0+s$"), "s") else "${ms}ms"
}
