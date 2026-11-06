package com.efxcreator.ui.editor

/**
 * 비트 시각 목록([beats], 오름차순 ms)을 "소수 비트 번호" 축으로 다룬다. 첫 비트가 0.
 * 템포가 바뀌는 곡에서도 맞도록 구간마다 실제 비트 간격으로 보간하고, 범위 밖은 첫/마지막 간격으로 외삽한다.
 */
object BeatGrid {

    /** [ms] 시각의 소수 비트 번호 */
    fun indexAt(beats: List<Long>, ms: Double): Double {
        val n = beats.size
        if (n < 2) return 0.0
        if (ms <= beats[0]) return (ms - beats[0]) / (beats[1] - beats[0]).coerceAtLeast(1L)
        if (ms >= beats[n - 1]) return (n - 1) + (ms - beats[n - 1]) / (beats[n - 1] - beats[n - 2]).coerceAtLeast(1L)
        var lo = 0
        var hi = n - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (beats[mid] <= ms) lo = mid else hi = mid
        }
        return lo + (ms - beats[lo]) / (beats[hi] - beats[lo]).coerceAtLeast(1L)
    }

    /** 소수 비트 번호 [index] 의 시각(ms) */
    fun timeAt(beats: List<Long>, index: Double): Double {
        val n = beats.size
        if (n < 2) return 0.0
        if (index <= 0.0) return beats[0] + index * (beats[1] - beats[0])
        if (index >= n - 1) return beats[n - 1] + (index - (n - 1)) * (beats[n - 1] - beats[n - 2])
        val i = index.toInt()
        return beats[i] + (index - i) * (beats[i + 1] - beats[i])
    }

    /**
     * 비트 수 표시: 정수에 0.03비트 이내로 가까우면 정수로 보여 준다 (11.98 → 12).
     * 길이는 10ms 단위로 저장되고 비트 시각도 ms 단위 분석값이라 정수 비트가 0.01~0.02 어긋나 보이는 것을 정리한다.
     * 그 밖에는 소수점 2자리까지 (끝의 0 제거).
     */
    fun formatBeats(beats: Double): String {
        val r = Math.round(beats)
        if (kotlin.math.abs(beats - r) <= 0.03) return r.toString()
        return "%.2f".format(java.util.Locale.US, beats).trimEnd('0').trimEnd('.').ifEmpty { "0" }
    }

    /** [startMs] ~ [endMs] 사이가 몇 비트인가 */
    fun beatsBetween(beats: List<Long>, startMs: Double, endMs: Double): Double =
        indexAt(beats, endMs) - indexAt(beats, startMs)

    /** [startMs] 에서 [count] 비트 뒤까지의 길이(ms) */
    fun msForBeats(beats: List<Long>, startMs: Double, count: Double): Long =
        Math.round(timeAt(beats, indexAt(beats, startMs) + count) - startMs)
}
