package com.efxcreator.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
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