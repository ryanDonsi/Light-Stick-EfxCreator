package com.efxcreator.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.efxcreator.data.PatternRepository
import com.efxcreator.data.PreferencesManager
import com.efxcreator.model.BuiltInPatterns
import com.efxcreator.data.SetRepository
import com.efxcreator.music.sets.EffectRuleSet
import com.efxcreator.music.sets.GenerationOptions
import com.efxcreator.music.sets.PaletteSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** 이펙트 세트 / 팔레트 세트 관리 (내장 + 사용자) 와 마지막 선택 기억 */
class SetsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SetRepository(application)
    private val prefs = PreferencesManager(application)

    private val _effectSets = MutableStateFlow(repository.allEffectSets())
    val effectSets: StateFlow<List<EffectRuleSet>> = _effectSets.asStateFlow()

    private val _paletteSets = MutableStateFlow(repository.allPaletteSets())
    val paletteSets: StateFlow<List<PaletteSet>> = _paletteSets.asStateFlow()

    val lastEffectSetId: String get() = prefs.lastEffectSetId
    val lastPaletteSetId: String get() = prefs.lastPaletteSetId

    private fun reload() {
        _effectSets.value = repository.allEffectSets()
        _paletteSets.value = repository.allPaletteSets()
    }

    // ── 이펙트 세트 ─────────────────────────────────────────

    fun newEffectSetId(): String = UUID.randomUUID().toString()

    fun saveEffectSet(set: EffectRuleSet) {
        repository.upsertEffectSet(set.copy(builtIn = false))
        reload()
    }

    fun deleteEffectSet(id: String) {
        repository.deleteEffectSet(id)
        if (prefs.lastEffectSetId == id) prefs.lastEffectSetId = ""
        reload()
    }

    // ── 팔레트 세트 ─────────────────────────────────────────

    fun newPaletteSetId(): String = UUID.randomUUID().toString()

    fun savePaletteSet(set: PaletteSet) {
        repository.upsertPaletteSet(set.copy(builtIn = false))
        reload()
    }

    fun deletePaletteSet(id: String) {
        repository.deletePaletteSet(id)
        if (prefs.lastPaletteSetId == id) prefs.lastPaletteSetId = ""
        reload()
    }

    // ── 선택 ────────────────────────────────────────────────

    /** 선택을 기억하고 생성 옵션으로 변환한다. id 가 null/빈 문자열이면 자동. */
    fun rememberSelection(effectSetId: String?, paletteSetId: String?): GenerationOptions {
        prefs.lastEffectSetId = effectSetId.orEmpty()
        prefs.lastPaletteSetId = paletteSetId.orEmpty()
        // 조건부 규칙이 이펙트 라이브러리의 이펙트를 가리킬 수 있으므로 함께 넘긴다
        val patterns = BuiltInPatterns.ALL + PatternRepository(getApplication()).load().filterNot { it.builtIn }
        return repository.resolveOptions(effectSetId, paletteSetId).copy(patterns = patterns)
    }
}
