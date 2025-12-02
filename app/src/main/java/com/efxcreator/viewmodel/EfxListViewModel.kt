package com.efxcreator.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.efxcreator.data.EfxRepository
import com.efxcreator.model.EfxProjectMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class EfxListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EfxRepository(application)
    private val context = application

    private val _projects = MutableStateFlow<List<EfxProjectMetadata>>(emptyList())
    val projects: StateFlow<List<EfxProjectMetadata>> = _projects.asStateFlow()

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