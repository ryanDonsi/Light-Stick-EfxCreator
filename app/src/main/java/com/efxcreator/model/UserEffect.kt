package com.efxcreator.model

import com.efxcreator.music.sets.RgbColor

/**
 * 사용자가 "내 라이브러리"에 등록해 둔 이펙트 한 칸.
 * 라이브러리는 [COLUMNS] x [ROWS] = [SLOT_COUNT] 칸이고, [slot] 은 0 부터 왼쪽 위 → 오른쪽 아래 순서다.
 */
data class UserEffect(
    val id: String,
    val slot: Int,
    val label: String,
    /** com.lightstick.types.EffectType 이름 */
    val effectType: String,
    val fg: RgbColor,
    val bg: RgbColor,
    /** 프레임 수 (ON/OFF 는 전환 시간, 그 외는 이펙트 주기). 1프레임 = 1000/[spf] ms */
    val period: Int,
    val spf: Int,
    val fade: Int,
    /** 이펙트 라이브러리(패턴)와 연결된 칸이면 그 패턴의 id. 이때 칸을 타임라인에 넣으면 패턴의 모든 단계가 들어간다 */
    val patternId: String? = null
) {
    companion object {
        const val COLUMNS = 6
        const val ROWS = 4
        const val SLOT_COUNT = COLUMNS * ROWS
    }
}
