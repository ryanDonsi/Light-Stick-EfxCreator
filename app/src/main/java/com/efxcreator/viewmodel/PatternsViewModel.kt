package com.efxcreator.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.efxcreator.data.PatternRepository
import com.efxcreator.model.BuiltInPatterns
import com.efxcreator.model.EffectPattern
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** 이펙트 라이브러리: 사용자가 만든 패턴(이펙트 묶음) 목록 */
class PatternsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PatternRepository(application)

    /** 사용자가 만든 이펙트 (파일에 저장되는 것은 이것뿐) */
    private var userPatterns: List<EffectPattern> = repository.load().filterNot { it.builtIn }

    private val _patterns = MutableStateFlow(BuiltInPatterns.ALL + userPatterns)
    /** 기본 제공 이펙트 + 사용자 이펙트 */
    val patterns: StateFlow<List<EffectPattern>> = _patterns.asStateFlow()

    private fun commit(list: List<EffectPattern>) {
        userPatterns = list
        repository.save(list)
        _patterns.value = BuiltInPatterns.ALL + list
    }

    fun newId(): String = UUID.randomUUID().toString()

    /** 기본 제공 이펙트는 저장하지 않는다 (복제해서 새 id 로 저장해야 한다) */
    fun save(pattern: EffectPattern) {
        if (pattern.builtIn) return
        val list = userPatterns.toMutableList()
        val i = list.indexOfFirst { it.id == pattern.id }
        if (i >= 0) list[i] = pattern else list.add(pattern)
        commit(list)
    }

    fun delete(id: String) = commit(userPatterns.filterNot { it.id == id })

    fun find(id: String?): EffectPattern? = id?.let { key -> _patterns.value.firstOrNull { it.id == key } }
}
