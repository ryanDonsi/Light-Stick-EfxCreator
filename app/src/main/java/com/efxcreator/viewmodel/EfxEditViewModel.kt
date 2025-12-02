package com.efxcreator.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.efxcreator.data.EfxRepository
import com.efxcreator.model.EfxProjectMetadata
import com.lightstick.efx.Efx
import com.lightstick.efx.EfxBody
import com.lightstick.efx.EfxEntry
import com.lightstick.efx.EfxHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EfxEditViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EfxRepository(application)
    private val context = application

    private val _metadata = MutableStateFlow<EfxProjectMetadata?>(null)
    val metadata: StateFlow<EfxProjectMetadata?> = _metadata.asStateFlow()

    private val _efx = MutableStateFlow<Efx?>(null)
    val efx: StateFlow<Efx?> = _efx.asStateFlow()

    private val _editingEntry = MutableStateFlow<Pair<Int, EfxEntry>?>(null)
    val editingEntry: StateFlow<Pair<Int, EfxEntry>?> = _editingEntry.asStateFlow()

    private val _suggestedName = MutableStateFlow<String?>(null)
    val suggestedName: StateFlow<String?> = _suggestedName.asStateFlow()

    companion object {
        private const val TAG = "EfxEditViewModel"
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Loading project: $projectId")

            val loadedMetadata = repository.loadProjectMetadata(projectId)
            _metadata.value = loadedMetadata

            val loadedEfx = repository.loadEfx(projectId)
            _efx.value = loadedEfx

            Log.d(TAG, "Loaded project: ${loadedMetadata?.name}")
        }
    }

    fun updateProjectName(newName: String) {
        viewModelScope.launch {
            val currentMetadata = _metadata.value ?: return@launch

            val updatedMetadata = currentMetadata.copy(
                name = newName,
                updatedAt = System.currentTimeMillis()  // ✅ modifiedAt → updatedAt
            )

            repository.saveProjectMetadata(updatedMetadata)
            _metadata.value = updatedMetadata

            // 제안 이름 초기화
            _suggestedName.value = null

            Log.d(TAG, "Updated project name to: $newName")
        }
    }

    fun setMusicFile(uri: Uri?) {
        viewModelScope.launch {
            val currentMetadata = _metadata.value ?: return@launch
            val currentEfx = _efx.value ?: return@launch

            if (uri == null) {
                // 음악 제거
                val newHeader = currentEfx.header.copy(musicId = 0)
                val updatedEfx = Efx(newHeader, currentEfx.body)

                repository.saveEfx(currentMetadata.id, updatedEfx)
                _efx.value = updatedEfx

                // 메타데이터에서 음악 URI 제거
                val updatedMetadata = currentMetadata.copy(
                    musicUriString = null,
                    updatedAt = System.currentTimeMillis()
                )
                repository.saveProjectMetadata(updatedMetadata)
                _metadata.value = updatedMetadata

                // 제안 이름 초기화
                _suggestedName.value = null

                Log.d(TAG, "Music removed")
            } else {
                // 음악 ID 계산 (SDK 사용)
                val musicId = repository.calculateMusicId(uri)

                val newHeader = currentEfx.header.copy(musicId = musicId)
                val updatedEfx = Efx(newHeader, currentEfx.body)

                repository.saveEfx(currentMetadata.id, updatedEfx)
                _efx.value = updatedEfx

                // 메타데이터에 음악 URI 저장
                val updatedMetadata = currentMetadata.copy(
                    musicUriString = uri.toString(),
                    updatedAt = System.currentTimeMillis()
                )
                repository.saveProjectMetadata(updatedMetadata)
                _metadata.value = updatedMetadata

                // 음악 파일명 추출 및 제안
                val fileName = getMusicFileName(uri)
                if (fileName != null) {
                    _suggestedName.value = fileName
                }

                Log.d(TAG, "Music set with ID: 0x${musicId.toUInt().toString(16).uppercase()}")
            }
        }
    }

    private fun getMusicFileName(uri: Uri): String? {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val fileName = it.getString(nameIndex)
                        // 확장자 제거
                        fileName.substringBeforeLast(".")
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting music file name", e)
            null
        }
    }

    fun applySuggestedName() {
        _suggestedName.value?.let { suggested ->
            updateProjectName(suggested)
        }
    }

    fun dismissSuggestion() {
        _suggestedName.value = null
    }

    fun addTimelineEntry(entry: EfxEntry) {
        viewModelScope.launch {
            val currentMetadata = _metadata.value ?: return@launch
            val currentEfx = _efx.value ?: return@launch

            val newEntries = currentEfx.body.entries + entry
            val sortedEntries = newEntries.sortedBy { it.timestampMs }

            // Effect Index 자동 부여 (1부터 시작)
            val entriesWithIndex = sortedEntries.mapIndexed { index, efxEntry ->
                val updatedPayload = efxEntry.payload.copy(effectIndex = index + 1)
                efxEntry.copy(payload = updatedPayload)
            }

            val newBody = currentEfx.body.copy(entries = entriesWithIndex)
            val newHeader = currentEfx.header.copy(entryCount = entriesWithIndex.size)
            val updatedEfx = Efx(newHeader, newBody)

            repository.saveEfx(currentMetadata.id, updatedEfx)
            _efx.value = updatedEfx

            // 메타데이터 업데이트
            val updatedMetadata = currentMetadata.copy(
                updatedAt = System.currentTimeMillis()
            )
            repository.saveProjectMetadata(updatedMetadata)
            _metadata.value = updatedMetadata

            Log.d(TAG, "Added timeline entry with auto Effect Index")
        }
    }

    fun updateTimelineEntry(index: Int, updatedEntry: EfxEntry) {
        viewModelScope.launch {
            val currentMetadata = _metadata.value ?: return@launch
            val currentEfx = _efx.value ?: return@launch

            val newEntries = currentEfx.body.entries.toMutableList()
            newEntries[index] = updatedEntry

            val sortedEntries = newEntries.sortedBy { it.timestampMs }

            // Effect Index 재계산 (1부터 시작)
            val entriesWithIndex = sortedEntries.mapIndexed { idx, efxEntry ->
                val updatedPayload = efxEntry.payload.copy(effectIndex = idx + 1)
                efxEntry.copy(payload = updatedPayload)
            }

            val newBody = currentEfx.body.copy(entries = entriesWithIndex)
            val newHeader = currentEfx.header.copy(entryCount = entriesWithIndex.size)
            val updatedEfx = Efx(newHeader, newBody)

            repository.saveEfx(currentMetadata.id, updatedEfx)
            _efx.value = updatedEfx
            _editingEntry.value = null

            // 메타데이터 업데이트
            val updatedMetadata = currentMetadata.copy(
                updatedAt = System.currentTimeMillis()
            )
            repository.saveProjectMetadata(updatedMetadata)
            _metadata.value = updatedMetadata

            Log.d(TAG, "Updated timeline entry with recalculated Effect Index")
        }
    }

    fun deleteTimelineEntry(index: Int) {
        viewModelScope.launch {
            val currentMetadata = _metadata.value ?: return@launch
            val currentEfx = _efx.value ?: return@launch

            val newEntries = currentEfx.body.entries.toMutableList()
            newEntries.removeAt(index)

            val sortedEntries = newEntries.sortedBy { it.timestampMs }

            // Effect Index 재계산 (1부터 시작)
            val entriesWithIndex = sortedEntries.mapIndexed { idx, efxEntry ->
                val updatedPayload = efxEntry.payload.copy(effectIndex = idx + 1)
                efxEntry.copy(payload = updatedPayload)
            }

            val newBody = currentEfx.body.copy(entries = entriesWithIndex)
            val newHeader = currentEfx.header.copy(entryCount = entriesWithIndex.size)
            val updatedEfx = Efx(newHeader, newBody)

            repository.saveEfx(currentMetadata.id, updatedEfx)
            _efx.value = updatedEfx
            _editingEntry.value = null

            // 메타데이터 업데이트
            val updatedMetadata = currentMetadata.copy(
                updatedAt = System.currentTimeMillis()
            )
            repository.saveProjectMetadata(updatedMetadata)
            _metadata.value = updatedMetadata

            Log.d(TAG, "Deleted timeline entry with recalculated Effect Index")
        }
    }

    fun startEditingEntry(index: Int, entry: EfxEntry) {
        _editingEntry.value = Pair(index, entry)
    }

    fun cancelEditingEntry() {
        _editingEntry.value = null
    }
}