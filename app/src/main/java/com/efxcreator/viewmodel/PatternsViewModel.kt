package com.efxcreator.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.efxcreator.data.PatternRepository
import com.efxcreator.model.EffectPattern
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** 이펙트 라이브러리: 사용자가 만든 패턴(이펙트 묶음) 목록 */
class PatternsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PatternRepository(application)

    private val _patterns = MutableStateFlow(repository.load())
    val patterns: StateFlow<List<EffectPattern>> = _patterns.asStateFlow()

    private fun commit(list: List<EffectPattern>) {
        repository.save(list)
        _patterns.value = list
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun save(pattern: EffectPattern) {
        val list = _patterns.value.toMutableList()
        val i = list.indexOfFirst { it.id == pattern.id }
        if (i >= 0) list[i] = pattern else list.add(pattern)
        commit(list)
    }

    fun delete(id: String) = commit(_patterns.value.filterNot { it.id == id })

    fun find(id: String?): EffectPattern? = id?.let { key -> _patterns.value.firstOrNull { it.id == key } }
}
