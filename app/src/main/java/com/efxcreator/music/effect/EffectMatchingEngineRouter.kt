package com.efxcreator.music.effect

import com.efxcreator.model.EffectPattern
import com.efxcreator.music.sets.EffectRuleSet

/**
 * EffectMatchingEngine 라우터.
 * 포팅 범위에서는 EffectMatchingEngineV1(V8 기반 완전 구현)만 지원한다.
 *
 * @param ruleSet 사용자 이펙트 세트(섹션별 패턴 규칙). null 이면 기본 동작(자동).
 */
object EffectMatchingEngineRouter {

    fun createEngine(ruleSet: EffectRuleSet? = null, patterns: List<EffectPattern> = emptyList()): EffectMatchingEngine =
        EffectMatchingEngineV1(ruleSet, patterns)
}
