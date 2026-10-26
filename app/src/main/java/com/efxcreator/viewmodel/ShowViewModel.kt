package com.efxcreator.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.efxcreator.data.ShowRepository
import com.efxcreator.model.Show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    fun createShow(name: String, performanceAt: Long? = null, imageUri: Uri? = null): String {
        val show = Show(
            name = name.trim().ifEmpty { "New Show ${_shows.value.size + 1}" },
            performanceAt = performanceAt
        )
        commit(_shows.value + show)
        if (imageUri != null) setImage(show.id, imageUri)
        return show.id
    }

    /** 이름/일시를 바꾸고, [newImage] 가 있으면 이미지를 교체, [removeImage] 면 기본 이미지로 되돌린다 */
    fun updateShow(id: String, name: String, performanceAt: Long?, newImage: Uri?, removeImage: Boolean) {
        val n = name.trim()
        commit(_shows.value.map {
            if (it.id == id) it.copy(name = n.ifEmpty { it.name }, performanceAt = performanceAt) else it
        })
        when {
            newImage != null -> setImage(id, newImage)
            removeImage -> {
                viewModelScope.launch(Dispatchers.IO) { repository.deleteImages(id) }
                commit(_shows.value.map { if (it.id == id) it.copy(imagePath = null) else it })
            }
        }
    }

    private fun setImage(id: String, uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { repository.importImage(id, uri) } ?: return@launch
            commit(_shows.value.map { if (it.id == id) it.copy(imagePath = path) else it })
        }
    }

    /** 공연만 지운다. 곡(프로젝트)은 지워지지 않고 "미분류" 로 돌아간다. */
    fun deleteShow(id: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteImages(id) }
        commit(_shows.value.filterNot { it.id == id })
    }

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
