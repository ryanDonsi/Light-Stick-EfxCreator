package com.efxcreator.ui.editor

import com.lightstick.efx.EfxEntry
import com.lightstick.types.Color
import com.lightstick.types.EffectType
import com.lightstick.types.LSEffectPayload

/** 라이브러리 카드 한 장 = 드래그&드롭/탭으로 타임라인에 넣을 수 있는 이펙트 프리셋 */
data class EffectPreset(
    val id: String,
    val label: String,
    val category: Category,
    val type: EffectType,
    val color: Color,
    val backgroundColor: Color = Color(0, 0, 0),
    /** 10ms 단위. ON/OFF 에서는 전환(transit) 시간, 그 외에는 이펙트 주기 */
    val period: Int = 0,
    val spf: Int = 100,
    val fade: Int = 100
) {
    enum class Category(val label: String) {
        BASIC("기본"), ON("ON"), BLINK("Blink"), BREATH("Breath"), STROBE("Strobe")
    }

    fun toEntry(timestampMs: Long): EfxEntry = EfxEntry(
        timestampMs,
        LSEffectPayload(
            effectType = type,
            color = color,
            backgroundColor = backgroundColor,
            period = period,
            spf = spf,
            fade = fade,
            randomColor = 0,
            randomDelay = 0,
            broadcasting = 1,
            effectIndex = 0   // 실제 Effect Index 는 EfxEditViewModel 이 저장 시 1부터 재부여한다
        )
    )
}

object EffectPresets {

    private val WHITE = Color(255, 255, 255)

    /** (이름, 색) — Light-Stick-Music 팔레트 색 계열 */
    private val COLORS = listOf(
        "Red" to Color(255, 0, 0),
        "Orange" to Color(255, 117, 0),
        "Yellow" to Color(255, 249, 0),
        "Green" to Color(0, 255, 36),
        "Cyan" to Color(0, 255, 255),
        "Blue" to Color(0, 0, 255),
        "Purple" to Color(164, 0, 255),
        "Pink" to Color(255, 0, 155)
    )

    private const val BLINK_PERIOD = 50    // 500ms
    private const val BREATH_PERIOD = 200  // 2s
    private const val STROBE_PERIOD = 8    // 80ms

    val all: List<EffectPreset> = buildList {
        add(EffectPreset("basic_on", "All ON", EffectPreset.Category.BASIC, EffectType.ON, WHITE))
        add(EffectPreset("basic_off", "All OFF", EffectPreset.Category.BASIC, EffectType.OFF, Color(0, 0, 0)))
        add(EffectPreset("basic_strobe", "Strobe", EffectPreset.Category.BASIC, EffectType.STROBE, WHITE, period = STROBE_PERIOD))
        add(EffectPreset("basic_blink", "Blink", EffectPreset.Category.BASIC, EffectType.BLINK, WHITE, period = BLINK_PERIOD))
        add(EffectPreset("basic_breath", "Breath", EffectPreset.Category.BASIC, EffectType.BREATH, WHITE, period = BREATH_PERIOD))

        for ((name, color) in COLORS) {
            add(EffectPreset("on_$name", "$name ON", EffectPreset.Category.ON, EffectType.ON, color))
        }
        for ((name, color) in COLORS) {
            add(EffectPreset("blink_$name", "$name Blink", EffectPreset.Category.BLINK, EffectType.BLINK, color, period = BLINK_PERIOD))
        }
        for ((name, color) in COLORS) {
            add(EffectPreset("breath_$name", "$name Breath", EffectPreset.Category.BREATH, EffectType.BREATH, color, period = BREATH_PERIOD))
        }
        for ((name, color) in COLORS) {
            add(EffectPreset("strobe_$name", "$name Strobe", EffectPreset.Category.STROBE, EffectType.STROBE, color, period = STROBE_PERIOD))
        }
    }

    fun byCategory(category: EffectPreset.Category): List<EffectPreset> = all.filter { it.category == category }
}
