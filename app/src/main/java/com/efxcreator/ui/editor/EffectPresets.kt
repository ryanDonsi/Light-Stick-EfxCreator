package com.efxcreator.ui.editor

import com.efxcreator.model.EffectPattern
import com.efxcreator.model.PatternResolver
import com.efxcreator.model.PatternStep
import com.efxcreator.model.UserEffect
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
    /** 프레임 수 (1프레임 = [spf] ms). ON/OFF 에서는 전환(transit) 시간, 그 외에는 이펙트 주기 */
    val period: Int = 0,
    val spf: Int = 100,
    val fade: Int = 100,
    /** 이펙트 라이브러리(패턴)에서 온 프리셋이면 그 패턴의 id (타임라인에 넣을 때 여러 단계로 펼쳐진다) */
    val patternId: String? = null,
    /** 칸의 이름 아래에 한 줄 더 보여 줄 설명 (패턴이면 "1비트" 같은 길이) */
    val subLabel: String? = null
) {
    enum class Category(val label: String) {
        USER("내 라이브러리"), ON("ON"), OFF("OFF"), BLINK("BLINK"), BREATH("BREATH"), STROBE("STROBE")
    }

    /** 전경(FG)·배경(BG) 두 색을 쓰는 이펙트인가 */
    val isTwoColor: Boolean
        get() = type == EffectType.BLINK || type == EffectType.BREATH || type == EffectType.STROBE

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

    private val BLACK = Color(0, 0, 0)

    /** (이름, 색) — Light-Stick-Music 팔레트 색 계열 + 흰색 */
    private val COLORS = listOf(
        "White" to Color(255, 255, 255),
        "Red" to Color(255, 0, 0),
        "Orange" to Color(255, 117, 0),
        "Yellow" to Color(255, 249, 0),
        "Green" to Color(0, 255, 36),
        "Cyan" to Color(0, 255, 255),
        "Blue" to Color(0, 0, 255),
        "Purple" to Color(164, 0, 255),
        "Pink" to Color(255, 0, 155)
    )

    private fun color(name: String): Color = COLORS.first { it.first == name }.second

    /** 2색 이펙트용 FG/BG 조합: 각 색 + 검정 배경, 그리고 대비가 큰 색 조합 */
    private val PAIRS: List<Triple<String, Color, Color>> =
        COLORS.map { (n, c) -> Triple("$n / Black", c, BLACK) } + listOf(
            "Red" to "Blue", "Orange" to "Cyan", "Yellow" to "Purple", "Green" to "Pink",
            "Cyan" to "Red", "Blue" to "White", "Purple" to "Green", "Pink" to "Cyan"
        ).map { (fg, bg) -> Triple("$fg / $bg", color(fg), color(bg)) }

    private const val BLINK_PERIOD = 50    // 500ms
    private const val BREATH_PERIOD = 200  // 2s
    private const val STROBE_PERIOD = 8    // 80ms
    private const val FADE_OFF_PERIOD = 100 // 1s 페이드 아웃

    val all: List<EffectPreset> = buildList {
        // ON — 단색
        for ((name, c) in COLORS) {
            add(EffectPreset("on_$name", "$name ON", EffectPreset.Category.ON, EffectType.ON, c))
        }
        // OFF
        add(EffectPreset("off", "OFF", EffectPreset.Category.OFF, EffectType.OFF, BLACK))
        add(EffectPreset("off_fade", "OFF (1초 페이드)", EffectPreset.Category.OFF, EffectType.OFF, BLACK, period = FADE_OFF_PERIOD))
        // BLINK / BREATH / STROBE — FG + BG 2색
        for ((label, fg, bg) in PAIRS) {
            add(EffectPreset("blink_$label", "$label", EffectPreset.Category.BLINK, EffectType.BLINK, fg, bg, period = BLINK_PERIOD))
        }
        for ((label, fg, bg) in PAIRS) {
            add(EffectPreset("breath_$label", "$label", EffectPreset.Category.BREATH, EffectType.BREATH, fg, bg, period = BREATH_PERIOD))
        }
        for ((label, fg, bg) in PAIRS) {
            add(EffectPreset("strobe_$label", "$label", EffectPreset.Category.STROBE, EffectType.STROBE, fg, bg, period = STROBE_PERIOD))
        }
    }

    fun byCategory(category: EffectPreset.Category): List<EffectPreset> = all.filter { it.category == category }
}

/** 사용자 라이브러리 칸을 드래그&드롭/탭으로 쓸 수 있는 프리셋으로 변환 */
fun UserEffect.toPreset(): EffectPreset = EffectPreset(
    id = "user_$id",
    label = label,
    category = EffectPreset.Category.USER,
    type = runCatching { EffectType.valueOf(effectType) }.getOrDefault(EffectType.ON),
    color = fg.toLs(),
    backgroundColor = bg.toLs(),
    period = period,
    spf = spf,
    fade = fade,
    patternId = patternId
)

/** 설정의 이펙트 라이브러리(패턴)를 라이브러리 칸으로 쓸 수 있는 프리셋으로 변환 (첫 단계로 칸의 모양을 정한다) */
fun EffectPattern.toPreset(): EffectPreset {
    val first = steps.firstOrNull() ?: PatternStep()
    val payload = PatternResolver.payload(first, null)
    return EffectPreset(
        id = "pattern_$id",
        label = name,
        category = EffectPreset.Category.USER,
        type = first.effectType,
        color = payload.color,
        backgroundColor = payload.backgroundColor,
        period = first.frames,
        spf = first.spf,
        fade = first.fade,
        patternId = id,
        subLabel = formatBeatsLabel(totalBeatsTenths)
    )
}

/** 박자 길이를 "1비트", "1.5비트" 로 (소수점 한 자리, 정수면 소수점 생략) */
fun formatBeatsLabel(tenths: Int): String =
    if (tenths % 10 == 0) "${tenths / 10}비트" else "%.1f비트".format(java.util.Locale.US, tenths / 10f)
