package com.efxcreator.music.effect

/**
 * EffectMatchingEngine 라우터.
 * 포팅 범위에서는 EffectMatchingEngineV1(V8 기반 완전 구현)만 지원한다.
 */
object EffectMatchingEngineRouter {

    fun createEngine(): EffectMatchingEngine = EffectMatchingEngineV1()
}
