package com.efxcreator.ui.editor

import com.lightstick.efx.EfxEntry
import com.lightstick.types.EffectType
import kotlin.math.PI
import kotlin.math.cos

/** Preview 용 LED 색 (각 0..255 실수) */
data class LedColor(val r: Float, val g: Float, val b: Float) {
    companion object {
        val OFF = LedColor(0f, 0f, 0f)
    }

    fun lerp(to: LedColor, f: Float): LedColor {
        val t = f.coerceIn(0f, 1f)
        return LedColor(r + (to.r - r) * t, g + (to.g - g) * t, b + (to.b - b) * t)
    }
}

/**
 * Efx 엔트리 목록으로부터 임의 시각의 LED 색을 근사 계산한다 (실제 펌웨어 동작의 근사치).
 *
 * - ON/OFF : period 를 전환(transit, 10ms 단위)으로 보고 이전 색에서 보간
 * - BLINK  : period*10ms 주기, 앞 절반은 color, 뒤 절반은 backgroundColor
 * - STROBE : period*10ms 주기, 앞 20% 만 color, 나머지는 backgroundColor
 * - BREATH : period*10ms 주기의 코사인 곡선으로 backgroundColor ↔ color 보간
 */
object LedSimulator {

    private const val DEFAULT_CYCLE_MS = 500L
    private const val MIN_CYCLE_MS = 20L
    private const val MAX_CHAIN_DEPTH = 3

    /** entries 는 timestampMs 오름차순이어야 한다. 첫 엔트리 이전은 꺼진 상태. */
    fun stateAt(entries: List<EfxEntry>, positionMs: Long): LedColor =
        stateAt(entries, indexAt(entries, positionMs), positionMs, 0)

    /** positionMs 시점에 유효한 엔트리의 인덱스 (없으면 -1) */
    fun indexAt(entries: List<EfxEntry>, positionMs: Long): Int {
        var lo = 0
        var hi = entries.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (entries[mid].timestampMs <= positionMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    private fun stateAt(entries: List<EfxEntry>, index: Int, positionMs: Long, depth: Int): LedColor {
        if (index < 0) return LedColor.OFF
        val entry = entries[index]
        val p = entry.payload
        val t = (positionMs - entry.timestampMs).coerceAtLeast(0L)
        val fg = LedColor(p.color.r.toFloat(), p.color.g.toFloat(), p.color.b.toFloat())
        val bg = LedColor(p.backgroundColor.r.toFloat(), p.backgroundColor.g.toFloat(), p.backgroundColor.b.toFloat())
        val cycle = if (p.period == 0) DEFAULT_CYCLE_MS else (p.period * 10L).coerceAtLeast(MIN_CYCLE_MS)

        return when (p.effectType) {
            EffectType.ON, EffectType.OFF -> {
                val target = if (p.effectType == EffectType.ON) fg else LedColor.OFF
                val transitMs = p.period * 10L
                if (transitMs > 0 && t < transitMs && depth < MAX_CHAIN_DEPTH) {
                    val prev = stateAt(entries, index - 1, entry.timestampMs, depth + 1)
                    prev.lerp(target, t.toFloat() / transitMs)
                } else {
                    target
                }
            }
            EffectType.BLINK -> if (t % cycle < cycle / 2) fg else bg
            EffectType.STROBE -> if (t % cycle < (cycle / 5).coerceAtLeast(1L)) fg else bg
            EffectType.BREATH -> {
                val phase = (t % cycle).toFloat() / cycle
                val brightness = ((1.0 - cos(2.0 * PI * phase)) / 2.0).toFloat()
                bg.lerp(fg, brightness)
            }
        }
    }
}
