package com.efxcreator.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.efxcreator.data.ShowRepository
import com.efxcreator.model.Show
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 공연 목록과 공연별 곡 구성 */
class ShowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ShowRepository(application)

    private val _shows = MutableStateFlow(repository.load())
    val shows: StateFlow<List<Show>> = _shows.asStateFlow()

    private fun commit(list: List<Show>) {
        repository.save(list)
        _shows.value = list
    }

    /** @return 새 공연의 id */
    fun createShow(name: String): String {
        val show = Show(name = name.trim().ifEmpty { "New Show ${_shows.value.size + 1}" })
        commit(_shows.value + show)
        return show.id
    }

    fun renameShow(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        commit(_shows.value.map { if (it.id == id) it.copy(name = n) else it })
    }

    /** 공연만 지운다. 곡(프로젝트)은 지워지지 않고 "미분류" 로 돌아간다. */
    fun deleteShow(id: String) = commit(_shows.value.filterNot { it.id == id })

    fun addSong(showId: String, projectId: String) {
        commit(_shows.value.map {
            if (it.id == showId && projectId !in it.songIds) it.copy(songIds = it.songIds + projectId) else it
        })
    }

    /** 곡이 프로젝트에서 삭제될 때 모든 공연에서 뺀다 */
    fun removeSongEverywhere(projectId: String) {
        if (_shows.value.none { projectId in it.songIds }) return
        commit(_shows.value.map { it.copy(songIds = it.songIds - projectId) })
    }

    /** 곡을 다른 공연으로 옮긴다 (한 곡은 하나의 공연에만 속한다) */
    fun moveSong(projectId: String, toShowId: String) {
        commit(_shows.value.map {
            when {
                it.id == toShowId -> if (projectId in it.songIds) it else it.copy(songIds = it.songIds + projectId)
                projectId in it.songIds -> it.copy(songIds = it.songIds - projectId)
                else -> it
            }
        })
    }
}
