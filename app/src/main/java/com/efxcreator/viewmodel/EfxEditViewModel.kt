package com.efxcreator.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.efxcreator.data.EfxRepository
import com.efxcreator.model.EfxProjectMetadata
import com.efxcreator.music.AutoEfxGenerator
import com.efxcreator.music.MusicAnalysis
import com.lightstick.efx.Efx
import com.lightstick.efx.EfxBody
import com.lightstick.efx.EfxEntry
import com.lightstick.efx.EfxHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    /** 에디터 타임라인용 파형/섹션/비트 분석 결과 (음악이 없거나 분석 전이면 null) */
    private val _analysis = MutableStateFlow<MusicAnalysis?>(null)
    val analysis: StateFlow<MusicAnalysis?> = _analysis.asStateFlow()

    private val _analyzing = MutableStateFlow(false)
    val analyzing: StateFlow<Boolean> = _analyzing.asStateFlow()

    /** 재생/분석에 사용하는 앱 내부 음악 사본 */
    private val _musicFile = MutableStateFlow<File?>(null)
    val musicFile: StateFlow<File?> = _musicFile.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val autoEfxGenerator = AutoEfxGenerator()

    companion object {
        private const val TAG = "EfxEditViewModel"
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Loading project: $projectId")

            val loadedMetadata = repository.loadProjectMetadata(projectId)
            _metadata.value = loadedMetadata

            val loadedEfx = repository.loadEfx(projectId)
            _efx.value = loadedEfx
            if (loadedEfx == null && loadedMetadata != null) {
                _message.value = "EFX 파일을 읽을 수 없습니다. SDK 1.4.x로 만든 이전 파일은 2.0.0과 " +
                    "형식이 달라 열 수 없습니다. 프로젝트를 삭제하고 새로 만들어 주세요."
            }

            Log.d(TAG, "Loaded project: ${loadedMetadata?.name}")

            loadMusicAndAnalysis(loadedMetadata)
        }
    }

    /**
     * 앱 내부에 보관된 음악 사본과 분석 결과를 불러온다.
     * 이전 버전 프로젝트(SAF Uri 만 저장)는 가능하면 내부 사본으로 이관하고,
     * 분석 결과가 없으면 백그라운드에서 분석한다.
     */
    private suspend fun loadMusicAndAnalysis(metadata: EfxProjectMetadata?) {
        metadata ?: return
        var file = withContext(Dispatchers.IO) { repository.getStoredMusicFile(metadata.id) }

        val legacyUri = metadata.musicUri
        if (file == null && legacyUri != null) {
            file = withContext(Dispatchers.IO) {
                runCatching { repository.importMusic(metadata.id, legacyUri) }.getOrNull()
            }
            if (file != null) {
                val migrated = metadata.copy(musicUriString = Uri.fromFile(file).toString())
                repository.saveProjectMetadata(migrated)
                _metadata.value = migrated
            } else {
                _message.value = "음악 파일에 접근할 수 없습니다. 음악을 다시 선택해 주세요."
            }
        }
        _musicFile.value = file
        _analysis.value = withContext(Dispatchers.IO) { repository.loadAnalysis(metadata.id) }

        if (file != null && _analysis.value == null) runAnalysis(metadata.id, file)
    }

    private suspend fun runAnalysis(projectId: String, file: File) {
        _analyzing.value = true
        try {
            val result = autoEfxGenerator.analyze(file)
            withContext(Dispatchers.IO) { repository.saveAnalysis(projectId, result) }
            _analysis.value = result
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Analysis failed", e)
            _message.value = e.message ?: "음악 분석에 실패했습니다."
        } finally {
            _analyzing.value = false
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

                // 보관된 음악/분석 결과 삭제
                repository.deleteMusicFiles(currentMetadata.id)
                repository.deleteAnalysis(currentMetadata.id)
                _musicFile.value = null
                _analysis.value = null

                // 제안 이름 초기화
                _suggestedName.value = null

                Log.d(TAG, "Music removed")
            } else {
                // 앱 내부로 복사 후 그 사본으로 음악 ID 계산 (SDK 사용)
                val file = try {
                    withContext(Dispatchers.IO) { repository.importMusic(currentMetadata.id, uri) }
                } catch (e: Exception) {
                    Log.e(TAG, "Music import failed", e)
                    _message.value = "음악 파일을 읽을 수 없습니다: ${e.message}"
                    return@launch
                }
                val musicId = try {
                    withContext(Dispatchers.IO) { autoEfxGenerator.computeMusicId(file) }
                } catch (e: Exception) {
                    _message.value = e.message ?: "음악 ID를 계산할 수 없습니다."
                    return@launch
                }
                repository.deleteAnalysis(currentMetadata.id)
                _analysis.value = null
                _musicFile.value = file

                val newHeader = currentEfx.header.copy(musicId = musicId)
                val updatedEfx = Efx(newHeader, currentEfx.body)

                repository.saveEfx(currentMetadata.id, updatedEfx)
                _efx.value = updatedEfx

                // 메타데이터에 음악 URI 저장
                val updatedMetadata = currentMetadata.copy(
                    musicUriString = Uri.fromFile(file).toString(),
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

                runAnalysis(currentMetadata.id, file)
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