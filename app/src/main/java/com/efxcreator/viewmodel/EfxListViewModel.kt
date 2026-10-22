package com.efxcreator.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.efxcreator.data.EfxRepository
import com.efxcreator.model.EfxProjectMetadata
import com.efxcreator.music.AutoEfxGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EfxListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EfxRepository(application)
    private val context = application

    private val _projects = MutableStateFlow<List<EfxProjectMetadata>>(emptyList())
    val projects: StateFlow<List<EfxProjectMetadata>> = _projects.asStateFlow()

    /** 음악 기반 자동 생성 진행 상태 */
    sealed class AutoGenState {
        object Idle : AutoGenState()
        data class Generating(val musicName: String) : AutoGenState()
        data class Done(val projectId: String) : AutoGenState()
        data class Error(val message: String) : AutoGenState()
    }

    private val _autoGenState = MutableStateFlow<AutoGenState>(AutoGenState.Idle)
    val autoGenState: StateFlow<AutoGenState> = _autoGenState.asStateFlow()

    private val autoEfxGenerator = AutoEfxGenerator()
    private var autoGenJob: Job? = null

    companion object {
        private const val TAG = "EfxListViewModel"
    }

    init {
        Log.d(TAG, "EfxListViewModel initialized")
        loadProjects()
        // 디버그: 저장된 파일 목록 출력
        repository.debugListFiles()
    }

    fun loadProjects() {
        viewModelScope.launch {
            Log.d(TAG, "Loading projects...")
            val loadedProjects = repository.loadProjectsMetadata()
            _projects.value = loadedProjects
            Log.d(TAG, "Loaded ${loadedProjects.size} projects")

            // 각 프로젝트 정보 출력
            loadedProjects.forEach { project ->
                Log.d(TAG, "Project: ${project.name} (${project.id})")
            }
        }
    }

    fun createNewProject(): String {
        val newMetadata = EfxProjectMetadata(
            name = "New EFX ${_projects.value.size + 1}"
        )

        Log.d(TAG, "Creating new project: ${newMetadata.name} (${newMetadata.id})")

        // 빈 EFX 생성 및 저장
        val newEfx = repository.createNewEfx()
        repository.saveEfx(newMetadata.id, newEfx)

        // 메타데이터 저장
        repository.saveProjectMetadata(newMetadata)

        // 즉시 리로드
        loadProjects()

        Log.d(TAG, "Project created successfully")
        return newMetadata.id
    }

    /**
     * 음악 파일을 분석해 이펙트 타임라인을 자동 생성하고 새 프로젝트로 저장한다.
     * 완료되면 [autoGenState] 가 [AutoGenState.Done] 으로 바뀌며, 이후 편집 화면(EfxEditScreen)에서
     * 기존 수동 편집 기능으로 추가 수정할 수 있다.
     */
    fun createProjectFromMusic(uri: Uri) {
        if (autoGenJob?.isActive == true) return
        val displayName = repository.getDisplayName(uri)
            ?: "New EFX ${_projects.value.size + 1}"
        _autoGenState.value = AutoGenState.Generating(displayName)

        autoGenJob = viewModelScope.launch {
            val metadata = EfxProjectMetadata(name = displayName)
            try {
                // 재생/분석은 앱 내부 사본을 사용한다 (SAF 권한 만료 방지)
                val musicFile = withContext(Dispatchers.IO) { repository.importMusic(metadata.id, uri) }
                val result = autoEfxGenerator.generate(musicFile)

                withContext(Dispatchers.IO) {
                    repository.saveEfx(metadata.id, result.efx)
                    result.analysis?.let { repository.saveAnalysis(metadata.id, it) }
                    repository.saveProjectMetadata(
                        metadata.copy(musicUriString = Uri.fromFile(musicFile).toString())
                    )
                }
                loadProjects()

                Log.d(TAG, "Auto-generated project: ${metadata.name} (${result.efx.body.entries.size} entries)")
                _autoGenState.value = AutoGenState.Done(metadata.id)
            } catch (e: CancellationException) {
                // cancelAutoGeneration() 이 이미 상태를 Idle 로 되돌렸다
                withContext(NonCancellable + Dispatchers.IO) { repository.deleteMusicFiles(metadata.id) }
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { repository.deleteMusicFiles(metadata.id) }
                Log.e(TAG, "Auto generation failed", e)
                _autoGenState.value = AutoGenState.Error(e.message ?: "자동 생성에 실패했습니다.")
            }
        }
    }

    fun cancelAutoGeneration() {
        autoGenJob?.cancel()
        _autoGenState.value = AutoGenState.Idle
    }

    /** Done/Error 상태를 UI 가 처리한 뒤 호출 */
    fun consumeAutoGenState() {
        if (_autoGenState.value !is AutoGenState.Generating) {
            _autoGenState.value = AutoGenState.Idle
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            Log.d(TAG, "Deleting project: $projectId")
            repository.deleteProject(projectId)
            loadProjects()
        }
    }

    /**
     * 프로젝트를 내보내기 (임시 파일 생성)
     * @param projectId 프로젝트 ID
     * @param projectName 프로젝트 이름
     * @return 내보낼 준비가 된 File 객체
     */
    fun prepareExportFile(projectId: String, projectName: String): File {
        Log.d(TAG, "Preparing export file for: $projectId as $projectName")
        return repository.exportEfx(projectId, projectName)
    }

    /**
     * 임시 파일을 사용자가 선택한 위치에 복사
     * @param sourceFile 소스 파일
     * @param destinationUri 목적지 URI
     */
    fun saveExportedFile(sourceFile: File, destinationUri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d(TAG, "File saved successfully to: $destinationUri")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving file", e)
            }
        }
    }

    fun getEntryCount(projectId: String): Int {
        val efx = repository.loadEfx(projectId)
        val count = efx?.body?.entries?.size ?: 0
        Log.d(TAG, "Project $projectId has $count entries")
        return count
    }

    fun getMusicId(projectId: String): Int {
        val efx = repository.loadEfx(projectId)
        val musicId = efx?.header?.musicId ?: 0
        Log.d(TAG, "Project $projectId has musicId: 0x${musicId.toUInt().toString(16).uppercase()}")
        return musicId
    }
}