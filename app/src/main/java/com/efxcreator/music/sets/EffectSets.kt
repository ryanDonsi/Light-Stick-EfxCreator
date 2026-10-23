package com.efxcreator.music.sets

import com.efxcreator.music.effect.EffectMatchingEngine.FgEngine
import com.efxcreator.music.effect.EffectMatchingEngineV1
import com.lightstick.types.Color as LSColor

/** Gson 으로 저장할 수 있는 단순 RGB */
data class RgbColor(val r: Int, val g: Int, val b: Int) {
    fun toLs(): LSColor = LSColor(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

    companion object {
        fun of(c: LSColor) = RgbColor(c.r, c.g, c.b)
    }
}

/**
 * 팔레트 세트 — 엔진이 역할별로 쓰는 4색(대표색 + 단계색 3개).
 * 어두운 톤(deep)과 흰/검정은 엔진이 대표색에서 자동으로 파생한다.
 */
data class PaletteSet(
    val id: String,
    val name: String,
    val main: RgbColor,
    val step1: RgbColor,
    val step2: RgbColor,
    val step3: RgbColor,
    val builtIn: Boolean = false
) {
    val colors: List<RgbColor> get() = listOf(main, step1, step2, step3)

    companion object {
        val COLOR_ROLES = listOf("대표색", "단계색 1", "단계색 2", "단계색 3")
    }
}

/**
 * 이펙트 세트(엔진) — 섹션 종류별로 사용할 패턴을 고르는 규칙 묶음.
 * [rules] 의 key = SectionType 이름, value = [FgEngine] 이름. 규칙이 없는 섹션은 기본 동작(자동)을 따른다.
 */
data class EffectRuleSet(
    val id: String,
    val name: String,
    val rules: Map<String, String>,
    val builtIn: Boolean = false
) {
    fun patternFor(sectionType: String): FgEngine? =
        rules[sectionType]?.let { runCatching { FgEngine.valueOf(it) }.getOrNull() }
}

/** 자동 생성/세트 적용 시 선택한 세트. null 이면 기본 동작(자동). */
data class GenerationOptions(
    val ruleSet: EffectRuleSet? = null,
    val paletteSet: PaletteSet? = null
)

/** 편집 가능한 패턴(FgEngine) 설명 */
data class PatternInfo(val engine: FgEngine, val label: String, val description: String)

object SetCatalog {

    /** 규칙을 편집할 수 있는 섹션 (CLIMAX 는 분석 단계에서 검출되지 않아 제외) */
    val EDITABLE_SECTIONS = listOf(
        "INTRO" to "인트로",
        "VERSE" to "벌스",
        "CHORUS" to "코러스",
        "BRIDGE" to "브릿지",
        "BREAK" to "브레이크(무음)",
        "OUTRO" to "아웃트로",
        "END" to "엔딩"
    )

    val PATTERNS = listOf(
        PatternInfo(FgEngine.ON_PULSE, "펄스", "박자마다 짧게 켜졌다 돌아오는 강조"),
        PatternInfo(FgEngine.BLINK, "블링크", "박자에 맞춰 깜빡임"),
        PatternInfo(FgEngine.BREATH, "호흡", "부드럽게 밝아졌다 어두워짐"),
        PatternInfo(FgEngine.ON_TRANSIT_ROTATE, "색 회전", "마디마다 색이 바뀌며 전환"),
        PatternInfo(FgEngine.FREEZE, "고정", "특정 색을 켠 채 유지"),
        PatternInfo(FgEngine.OFF_TRANSIT, "끄기", "이 섹션은 이펙트 없이 꺼 둠")
    )

    fun patternLabel(engineName: String?): String =
        PATTERNS.firstOrNull { it.engine.name == engineName }?.label ?: "자동"

    /** 엔진의 기본 동작과 같은 규칙 (새 세트를 만들 때의 출발점) */
    val DEFAULT_RULES: Map<String, String> = mapOf(
        "INTRO" to FgEngine.BREATH.name,
        "VERSE" to FgEngine.ON_PULSE.name,
        "CHORUS" to FgEngine.ON_TRANSIT_ROTATE.name,
        "BRIDGE" to FgEngine.BREATH.name,
        "BREAK" to FgEngine.FREEZE.name,
        "OUTRO" to FgEngine.OFF_TRANSIT.name,
        "END" to FgEngine.OFF_TRANSIT.name
    )

    val BUILT_IN_RULE_SETS: List<EffectRuleSet> = listOf(
        EffectRuleSet(
            "builtin_calm", "차분 (호흡 위주)",
            DEFAULT_RULES + mapOf(
                "VERSE" to FgEngine.BREATH.name,
                "CHORUS" to FgEngine.BREATH.name
            ),
            builtIn = true
        ),
        EffectRuleSet(
            "builtin_power", "파워 (펄스·회전 위주)",
            DEFAULT_RULES + mapOf(
                "INTRO" to FgEngine.ON_PULSE.name,
                "BRIDGE" to FgEngine.ON_PULSE.name,
                "VERSE" to FgEngine.ON_PULSE.name,
                "CHORUS" to FgEngine.ON_TRANSIT_ROTATE.name
            ),
            builtIn = true
        ),
        EffectRuleSet(
            "builtin_blink", "블링크 위주",
            DEFAULT_RULES + mapOf(
                "VERSE" to FgEngine.BLINK.name,
                "CHORUS" to FgEngine.BLINK.name,
                "BRIDGE" to FgEngine.BLINK.name
            ),
            builtIn = true
        )
    )

    /** 엔진에 내장된 색 계열을 읽기 전용 팔레트 세트로 노출 */
    val BUILT_IN_PALETTE_SETS: List<PaletteSet> by lazy {
        EffectMatchingEngineV1.familyColors().map { (name, colors) ->
            PaletteSet(
                id = "builtin_${name.lowercase()}",
                name = familyDisplayName(name),
                main = RgbColor.of(colors[0]),
                step1 = RgbColor.of(colors[1]),
                step2 = RgbColor.of(colors[2]),
                step3 = RgbColor.of(colors[3]),
                builtIn = true
            )
        }
    }

    private fun familyDisplayName(name: String) = when (name) {
        "PURPLE" -> "퍼플 계열"
        "RED" -> "레드 계열"
        "ORANGE_GOLD" -> "오렌지·골드 계열"
        "TEAL" -> "틸·그린 계열"
        "BLUE" -> "블루 계열"
        else -> name
    }
}
