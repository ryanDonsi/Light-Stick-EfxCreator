package com.efxcreator.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract
import com.efxcreator.data.PreferencesManager

/**
 * 설정의 "음악 불러오기 위치" 에서 시작하는 시스템 파일 선택기 (음악 파일 선택).
 * 입력으로 시작 폴더를 받는다 → [MusicFolder.initialUri]. [multiple] 이 true 면 여러 곡을 고를 수 있다.
 * 시작 폴더는 선택기에 주는 "힌트" 라서, 기기나 Android 버전에 따라 무시될 수 있다.
 */
class OpenMusicDocuments(private val multiple: Boolean = true) : ActivityResultContract<Uri, List<Uri>>() {

    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        if (resultCode != android.app.Activity.RESULT_OK || intent == null) return emptyList()
        val clip = intent.clipData
        return if (clip != null) {
            (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        } else {
            listOfNotNull(intent.data)
        }
    }
}

/** 설정의 "음악 불러오기 위치" 를 파일 선택기의 시작 위치(문서 URI)로 바꾼다 */
object MusicFolder {

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val INTERNAL_ROOT = "/storage/emulated/0/"

    fun initialUri(context: Context): Uri {
        val path = PreferencesManager(context).musicLoadPath
        return when {
            // 설정에서 폴더를 직접 고른 경우 (트리 URI → 그 폴더의 문서 URI)
            path.startsWith("content://") -> runCatching {
                val tree = Uri.parse(path)
                DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            }.getOrDefault(fromFilePath(defaultMusicPath()))
            path.isEmpty() || path == "default_music" -> fromFilePath(defaultMusicPath())
            else -> fromFilePath(path)
        }
    }

    private fun defaultMusicPath(): String =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath

    /** "/storage/emulated/0/Music" → content://…externalstorage.documents/document/primary%3AMusic */
    private fun fromFilePath(path: String): Uri {
        val relative = if (path.startsWith(INTERNAL_ROOT)) path.removePrefix(INTERNAL_ROOT) else "Music"
        return DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:$relative")
    }
}
