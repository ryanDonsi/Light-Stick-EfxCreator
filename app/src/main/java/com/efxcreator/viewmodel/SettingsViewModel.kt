package com.efxcreator.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import androidx.lifecycle.AndroidViewModel
import com.efxcreator.data.EfxRepository
import com.efxcreator.data.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EfxRepository(application)
    private val prefs = PreferencesManager(application)
    private val context = application

    // EFX 저장 경로
    private val _efxStoragePath = MutableStateFlow(prefs.efxStoragePath)
    val efxStoragePath: StateFlow<String> = _efxStoragePath.asStateFlow()

    private val _efxStorageInfo = MutableStateFlow(repository.getEfxStorageInfo())
    val efxStorageInfo: StateFlow<String> = _efxStorageInfo.asStateFlow()

    // 음악 불러오기 경로
    private val _musicLoadPath = MutableStateFlow(prefs.musicLoadPath)
    val musicLoadPath: StateFlow<String> = _musicLoadPath.asStateFlow()

    private val _musicLoadInfo = MutableStateFlow(repository.getMusicLoadInfo())
    val musicLoadInfo: StateFlow<String> = _musicLoadInfo.asStateFlow()

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    /**
     * 일반 폴더(예: /storage/emulated/0/Effects)를 EFX 사본 저장 위치로 쓴다.
     * 폴더를 만들고 실제로 쓸 수 있는지 확인한 뒤에만 적용한다 (모든 파일 접근 권한이 있어야 한다).
     */
    fun changeEfxStoragePathToFolder(dir: File): Boolean = try {
        val writable = (dir.isDirectory || dir.mkdirs()) && runCatching {
            File(dir, ".glowsync_write_test").apply { writeText("ok"); delete() }
        }.isSuccess
        if (writable) {
            val path = dir.absolutePath
            Log.d(TAG, "Changing EFX storage to folder: $path")
            repository.changeEfxStoragePath(path)
            _efxStoragePath.value = path
            _efxStorageInfo.value = repository.getEfxStorageInfo()
            true
        } else {
            Log.e(TAG, "Cannot create/write folder: ${dir.absolutePath}")
            false
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error changing EFX path to folder", e)
        false
    }

    /**
     * 음악 불러오기 시작 위치를 경로로 바로 지정한다 ("default_music" 이면 기본 Music 폴더).
     * 이 값은 곡 추가 때 파일 선택기가 처음 열리는 위치(힌트)로만 쓰여서 권한이나 폴더 생성이 필요 없다.
     */
    fun setMusicLoadPath(path: String) {
        repository.changeMusicLoadPath(path)
        _musicLoadPath.value = path
        _musicLoadInfo.value = repository.getMusicLoadInfo()
    }

    /** EFX 저장 위치를 기본(Music/Effects)으로 되돌린다 */
    fun resetEfxStoragePath() {
        repository.changeEfxStoragePath(com.efxcreator.data.PreferencesManager.EFX_PATH_DEFAULT)
        _efxStoragePath.value = com.efxcreator.data.PreferencesManager.EFX_PATH_DEFAULT
        _efxStorageInfo.value = repository.getEfxStorageInfo()
    }

    /**
     * EFX 저장 경로를 URI로 변경
     */
    fun changeEfxStoragePathFromUri(uri: Uri) {
        try {
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            if (documentFile != null && documentFile.canWrite()) {
                val path = uri.toString()
                Log.d(TAG, "Changing EFX storage to URI: $path")

                repository.changeEfxStoragePath(path)
                _efxStoragePath.value = path
                _efxStorageInfo.value = repository.getEfxStorageInfo()
            } else {
                Log.e(TAG, "Cannot write to selected folder")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error changing EFX path from URI", e)
        }
    }

    /**
     * 음악 불러오기 경로를 URI로 변경
     */
    fun changeMusicLoadPathFromUri(uri: Uri) {
        try {
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            if (documentFile != null && documentFile.canRead()) {
                val path = uri.toString()
                Log.d(TAG, "Changing Music load path to URI: $path")

                repository.changeMusicLoadPath(path)
                _musicLoadPath.value = path
                _musicLoadInfo.value = repository.getMusicLoadInfo()
            } else {
                Log.e(TAG, "Cannot read from selected folder")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error changing Music path from URI", e)
        }
    }
}