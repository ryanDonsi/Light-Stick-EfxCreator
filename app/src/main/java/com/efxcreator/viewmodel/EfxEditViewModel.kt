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
import com.efxcreator.music.sets.GenerationOptions
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

    /** 이펙트/팔레트 세트 적용(재생성) 진행 중 */
    private val _applying = MutableStateFlow(false)
    val applying: StateFlow<Boolean> = _applying.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val autoEfxGenerator = AutoEfxGenerator()

    // ── 임시 작업본(draft) + Undo/Redo ─────────────────────────────
    // [efx] 는 화면에 보이는 "작업본"이다. 타임라인 편집은 작업본만 바꾸고, [save] 를 눌러야 파일에 기록된다.
    // 저장된 내용은 [savedEfx] 에 따로 들고 있어 변경 여부(dirty)를 비교한다.

    /** 마지막으로 저장된(디스크와 같은) 내용 */
    private var savedEfx: Efx? = null

    private val undoStack = ArrayDeque<List<EfxEntry>>()
    private val redoStack = ArrayDeque<List<EfxEntry>>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /** 저장되지 않은 변경이 있는가 */
    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    companion object {
        private const val TAG = "EfxEditViewModel"
        /** Undo 로 되돌릴 수 있는 최대 편집 횟수 */
        private const val MAX_HISTORY = 100
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
            savedEfx = loadedEfx
            _efx.value = loadedEfx
            undoStack.clear()
            redoStack.clear()
            refreshHistoryFlags()
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

        // 분석 결과가 없거나, 세트 적용에 필요한 엔진 입력이 없는 예전 결과면 다시 분석한다
        if (file != null && _analysis.value?.engineInput == null) runAnalysis(metadata.id, file)
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
                persistMusicId(currentMetadata.id, 0)

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

                persistMusicId(currentMetadata.id, musicId)

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

    /**
     * 이펙트 세트/팔레트 세트를 적용해 타임라인을 다시 생성한다.
     * 저장된 분석 결과를 쓰므로 느린 분석 없이 빠르게 끝난다.
     *
     * @param sectionIndex null 이면 곡 전체, 아니면 [MusicAnalysis.sections] 의 해당 섹션 구간만.
     *   적용 범위 안의 기존 엔트리는 새로 만든 것으로 교체되고, 범위 밖은 그대로 둔다.
     */
    fun applySets(options: GenerationOptions, sectionIndex: Int?) {
        if (_applying.value) return
        viewModelScope.launch {
            val meta = _metadata.value ?: return@launch
            val currentEfx = _efx.value ?: run {
                _message.value = "EFX 파일을 읽을 수 없어 적용할 수 없습니다."
                return@launch
            }
            val analysis = _analysis.value
            if (analysis == null || analysis.engineInput == null) {
                _message.value = if (_analyzing.value) "음악 분석 중입니다. 완료 후 다시 시도해 주세요."
                else "음악을 선택해 분석한 뒤 적용할 수 있습니다."
                return@launch
            }
            val range: LongRange = if (sectionIndex == null) {
                0L..Long.MAX_VALUE
            } else {
                val sec = analysis.sections.getOrNull(sectionIndex) ?: run {
                    _message.value = "선택한 섹션을 찾을 수 없습니다."
                    return@launch
                }
                sec.startMs until sec.endMs
            }

            _applying.value = true
            try {
                val generated = autoEfxGenerator.render(analysis, currentEfx.header.musicId, options)
                val inRange = generated.filter { it.timestampMs in range }
                replaceRange(currentEfx, range, inRange)
                Log.d(TAG, "Applied sets: range=$range new=${inRange.size}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Apply sets failed", e)
                _message.value = e.message ?: "세트 적용에 실패했습니다."
            } finally {
                _applying.value = false
            }
        }
    }

    /** [range] 안의 기존 엔트리를 [newEntries] 로 교체한다 (작업본에만 반영 — 저장은 [save]). */
    private fun replaceRange(currentEfx: Efx, range: LongRange, newEntries: List<EfxEntry>) {
        val kept = currentEfx.body.entries.filter { it.timestampMs !in range }
        commitDraft(kept + newEntries)
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
        val current = _efx.value ?: return
        commitDraft(current.body.entries + entry)
    }

    fun updateTimelineEntry(index: Int, updatedEntry: EfxEntry) {
        val current = _efx.value ?: return
        val list = current.body.entries.toMutableList()
        if (index !in list.indices) return
        list[index] = updatedEntry
        _editingEntry.value = null
        commitDraft(list)
    }

    fun deleteTimelineEntry(index: Int) {
        val current = _efx.value ?: return
        val list = current.body.entries.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        _editingEntry.value = null
        commitDraft(list)
    }

    // ── Undo / Redo / 저장 ──────────────────────────────────────

    /** 시각순으로 정렬하고 Effect Index 를 1부터 다시 매긴다 */
    private fun normalize(entries: List<EfxEntry>): List<EfxEntry> =
        entries.sortedBy { it.timestampMs }.mapIndexed { idx, e ->
            e.copy(payload = e.payload.copy(effectIndex = idx + 1))
        }

    private fun setDraftEntries(entries: List<EfxEntry>) {
        val current = _efx.value ?: return
        val normalized = normalize(entries)
        _efx.value = Efx(current.header.copy(entryCount = normalized.size), current.body.copy(entries = normalized))
    }

    /** 편집 한 번 = 히스토리 한 칸. 새 편집은 Redo 기록을 지운다. */
    private fun commitDraft(newEntries: List<EfxEntry>) {
        val current = _efx.value ?: return
        val normalized = normalize(newEntries)
        if (normalized == current.body.entries) return
        undoStack.addLast(current.body.entries)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        setDraftEntries(normalized)
        refreshHistoryFlags()
    }

    private fun refreshHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        _dirty.value = _efx.value?.body?.entries != savedEfx?.body?.entries
    }

    fun undo() {
        val current = _efx.value ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current.body.entries)
        setDraftEntries(previous)
        _editingEntry.value = null
        refreshHistoryFlags()
    }

    fun redo() {
        val current = _efx.value ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current.body.entries)
        setDraftEntries(next)
        _editingEntry.value = null
        refreshHistoryFlags()
    }

    /** 작업본을 실제 파일에 저장한다. 완료되면 [onSaved] 호출. */
    fun save(onSaved: () -> Unit = {}) {
        if (_saving.value) return
        val meta = _metadata.value ?: return
        val draft = _efx.value ?: return
        viewModelScope.launch {
            _saving.value = true
            try {
                val updatedMeta = meta.copy(updatedAt = System.currentTimeMillis())
                withContext(Dispatchers.IO) {
                    repository.saveEfx(meta.id, draft)
                    repository.saveProjectMetadata(updatedMeta)
                }
                savedEfx = draft
                _metadata.value = updatedMeta
                refreshHistoryFlags()
                _message.value = "저장했습니다."
                onSaved()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Save failed", e)
                _message.value = "저장에 실패했습니다: ${e.message}"
            } finally {
                _saving.value = false
            }
        }
    }

    /**
     * 음악 ID 는 프로젝트 설정이라 즉시 저장한다. 디스크(저장본)와 작업본의 헤더만 바꾸고
     * 작업본의 엔트리(미저장 편집)는 건드리지 않는다.
     */
    private suspend fun persistMusicId(projectId: String, musicId: Int) {
        val saved = savedEfx ?: return
        val newSaved = Efx(saved.header.copy(musicId = musicId), saved.body)
        withContext(Dispatchers.IO) { repository.saveEfx(projectId, newSaved) }
        savedEfx = newSaved
        _efx.value?.let { d -> _efx.value = Efx(d.header.copy(musicId = musicId), d.body) }
    }

    fun startEditingEntry(index: Int, entry: EfxEntry) {
        _editingEntry.value = Pair(index, entry)
    }

    fun cancelEditingEntry() {
        _editingEntry.value = null
    }
}