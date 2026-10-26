package com.efxcreator.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

/**
 * 음악 파일 여러 개를 고르는 시스템 파일 선택기.
 * 내장 저장공간의 Music 폴더에서 시작한다 (폴더가 없으면 선택기가 기본 위치를 연다).
 */
class OpenMusicDocuments : ActivityResultContract<Unit, List<Uri>>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, MUSIC_FOLDER_URI)
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

    companion object {
        /** 내장 저장공간(primary)의 Music 폴더 */
        private val MUSIC_FOLDER_URI: Uri =
            Uri.parse("content://com.android.externalstorage.documents/document/primary%3AMusic")
    }
}
