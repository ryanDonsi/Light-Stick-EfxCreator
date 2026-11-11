package com.efxcreator.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * EFX 사본을 저장할 폴더를 시스템 폴더 선택기(SAF)로 지정하는 공통 동작.
 * 권한 선언 없이, 사용자가 고른 폴더에 대한 접근권만 받아 둔다.
 */
object EfxFolderSetup {

    /** 선택기를 처음 열 위치: Music/Effects (없으면 시스템이 다른 위치에서 연다) */
    fun pickerStartUri(): Uri =
        DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Music/Effects")

    /** 아직 폴더가 지정되지 않았는가 (이 경우 EFX 는 앱 내부에만 저장된다) */
    fun isUnset(context: Context): Boolean =
        PreferencesManager.isDefaultEfxPath(PreferencesManager(context).efxStoragePath)

    /** 선택한 폴더에 읽기/쓰기 접근권을 영구 저장하고 EFX 저장 위치로 지정한다. 성공하면 true */
    fun apply(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        if (DocumentFile.fromTreeUri(context, uri)?.canWrite() == true) {
            EfxRepository(context).changeEfxStoragePath(uri.toString())
            true
        } else false
    } catch (e: Exception) {
        false
    }
}
