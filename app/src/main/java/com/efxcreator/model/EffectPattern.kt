package com.efxcreator.model

import com.efxcreator.music.sets.PaletteSet
import com.efxcreator.music.sets.RgbColor
import com.efxcreator.music.sets.SetCatalog
import com.lightstick.efx.EfxEntry
import com.lightstick.types.EffectType
import com.lightstick.types.LSEffectPayload

/**
 * 이펙트 라이브러리의 패턴 한 단계.
 * [beatsTenths] 는 길이를 박자의 1/10 단위로 센 값이다 (3 = 0.3박, 10 = 1박) — 소수점 한 자리를 오차 없이 다루기 위해 정수로 저장한다.
 * [fg]/[bg] 는 색 자체가 아니라 "화이트", "보조색1" 같은 **역할**이라서, 팔레트 세트를 바꿔 같은 패턴을 다른 색으로 쓸 수 있다.
 * [frames] 는 ON/OFF 에서는 Transit, 그 외에는 Period (프레임 수, 1프레임 = [spf] ms (SPF 100 → 100ms, SPF 10 → 10ms)).
 */
data class PatternStep(
    val type: String = "ON",
    val beatsTenths: Int = 10,
    val fg: String = PatternColors.WHITE,
    val bg: String = PatternColors.BLACK,
    val frames: Int = 0,
    val spf: Int = 100,
    val fade: Int = 100
) {
    val beats: Float get() = beatsTenths / 10f
    val effectType: EffectType get() = runCatching { EffectType.valueOf(type) }.getOrDefault(EffectType.ON)
}

/** 이름을 붙인 단계들의 묶음. 예) ON_PULSE = [ON 0.3박, ON 0.7박 (Transit 3)] */
data class EffectPattern(
    val id: String,
    val name: String,
    val steps: List<PatternStep>,
    /** 기본 제공 이펙트: 수정/삭제할 수 없고, 복제해서 고쳐 쓸 수 있다 */
    val builtIn: Boolean = false
) {
    val totalBeatsTenths: Int get() = steps.sumOf { it.beatsTenths }
}

/** 패턴 단계가 고를 수 있는 색 역할 */
object PatternColors {
    const val WHITE = "WHITE"
    const val BLACK = "BLACK"
    const val MAIN = "P0"
    const val SUB1 = "P1"
    const val SUB2 = "P2"
    const val SUB3 = "P3"

    /** (저장 값, 화면 이름) */
    val OPTIONS = listOf(
        WHITE to "화이트", BLACK to "검정",
        MAIN to "대표색", SUB1 to "보조색1", SUB2 to "보조색2", SUB3 to "보조색3"
    )

    fun label(ref: String): String = OPTIONS.firstOrNull { it.first == ref }?.second ?: ref

    /** [palette] 가 없으면 내장 첫 번째 팔레트 세트를 쓴다 */
    fun resolve(ref: String, palette: PaletteSet?): RgbColor {
        val p = palette ?: SetCatalog.BUILT_IN_PALETTE_SETS.first()
        return when (ref) {
            WHITE -> RgbColor(255, 255, 255)
            BLACK -> RgbColor(0, 0, 0)
            MAIN -> p.main
            SUB1 -> p.step1
            SUB2 -> p.step2
            SUB3 -> p.step3
            else -> RgbColor(255, 255, 255)
        }
    }
}

/** 패턴을 실제 이펙트(페이로드)로 바꾸는 계산 — 타임라인에 넣을 때와 디바이스 테스트가 함께 쓴다 */
object PatternResolver {

    /** 단계 하나의 페이로드. 색은 [palette] 로 풀고, [broadcasting] 은 기기의 Broadcasting Mode 값 */
    fun payload(step: PatternStep, palette: PaletteSet?, broadcasting: Int = 1): LSEffectPayload {
        val type = step.effectType
        val fg = if (type == EffectType.OFF) RgbColor(0, 0, 0) else PatternColors.resolve(step.fg, palette)
        val twoColor = type == EffectType.BLINK || type == EffectType.BREATH || type == EffectType.STROBE
        val bg = if (twoColor) PatternColors.resolve(step.bg, palette) else RgbColor(0, 0, 0)
        return LSEffectPayload(
            effectType = type, color = fg.toLs(), backgroundColor = bg.toLs(),
            period = step.frames, spf = step.spf, fade = step.fade,
            randomColor = 0, randomDelay = 0, broadcasting = broadcasting, effectIndex = 0
        )
    }

    /** 단계의 길이(ms). 10ms 단위로 반올림한다 */
    fun durationMs(step: PatternStep, beatMs: Long): Long =
        Math.round(step.beatsTenths / 10.0 * beatMs / 10.0) * 10L

    /** 패턴 전체 길이(ms) = 단계 길이의 합 */
    fun totalDurationMs(pattern: EffectPattern, beatMs: Long): Long =
        pattern.steps.sumOf { durationMs(it, beatMs) }

    /** 시작 시각 [startMs] 부터 단계마다 하나씩 타임라인 엔트리를 만든다 */
    fun expand(pattern: EffectPattern, startMs: Long, beatMs: Long, palette: PaletteSet?): List<EfxEntry> {
        var t = startMs
        return pattern.steps.map { step ->
            EfxEntry(t, payload(step, palette)).also { t += durationMs(step, beatMs) }
        }
    }
}

/**
 * 기본 제공 이펙트 — 자동 생성 엔진(EffectMatchingEngineV1)이 박자마다 만드는 패턴을 1박 기준 단계로 옮긴 것.
 * 색은 팔레트 세트의 역할이라 팔레트를 바꾸면 색이 따라 바뀐다.
 * 엔진은 곡의 BPM 에 맞춰 Period 를 계산하지만 여기서는 120BPM(1박 500ms) 기준 값을 쓴다.
 */
object BuiltInPatterns {
    val ALL: List<EffectPattern> = listOf(
        // 박자 시작에 대표색을 짧게(0.3박) 켰다가, 나머지 0.7박 동안 검정으로 Transit 3 만에 꺼지며 강조
        EffectPattern(
            id = "builtin_on_pulse", name = "ON_PULSE", builtIn = true,
            steps = listOf(
                PatternStep(type = "ON", beatsTenths = 3, fg = PatternColors.MAIN, frames = 0),
                PatternStep(type = "ON", beatsTenths = 7, fg = PatternColors.BLACK, frames = 3)
            )
        ),
        // 대표색 ↔ 보조색1 로 박자에 맞춰 깜빡임 (Period 5 = 50ms 한 사이클 @SPF 100)
        EffectPattern(
            id = "builtin_blink", name = "BLINK", builtIn = true,
            steps = listOf(
                PatternStep(type = "BLINK", beatsTenths = 10, fg = PatternColors.MAIN, bg = PatternColors.SUB1, frames = 5)
            )
        ),
        // 대표색이 부드럽게 밝아졌다 어두워짐 (Period 25 = 250ms 한 사이클 @SPF 100)
        EffectPattern(
            id = "builtin_breath", name = "BREATH", builtIn = true,
            steps = listOf(
                PatternStep(type = "BREATH", beatsTenths = 10, fg = PatternColors.MAIN, bg = PatternColors.SUB1, frames = 25)
            )
        ),
        // 박자마다 대표색 → 보조색1 → 2 → 3 으로 색이 바뀐다. Transit 0 = 즉시 바뀌는 ON_ROTATE
        EffectPattern(
            id = "builtin_on_rotate", name = "ON_ROTATE", builtIn = true,
            steps = listOf(
                PatternStep(type = "ON", beatsTenths = 10, fg = PatternColors.MAIN, frames = 0),
                PatternStep(type = "ON", beatsTenths = 10, fg = PatternColors.SUB1, frames = 0),
                PatternStep(type = "ON", beatsTenths = 10, fg = PatternColors.SUB2, frames = 0),
                PatternStep(type = "ON", beatsTenths = 10, fg = PatternColors.SUB3, frames = 0)
            )
        ),
        // 같은 색 순서이지만 Transit 2 로 부드럽게 넘어가는 전환
        EffectPattern(
            id = "builtin_on_transit_rotate", name = "ON_TRANSIT_ROTATE", builtIn = true,
            steps = listOf(
                PatternStep(type = "ON", beatsTenths = 10, fg = PatternColors.MAIN, frames = 2),
                PatternStep(type = "ON", beatsTenths = 10, fg = PatternColors.SUB1, frames = 2),
                PatternStep(type = "ON", beatsTenths = 10, fg = PatternColors.SUB2, frames = 2),
                PatternStep(type = "ON", beatsTenths = 10, fg = PatternColors.SUB3, frames = 2)
            )
        )
    )
}
