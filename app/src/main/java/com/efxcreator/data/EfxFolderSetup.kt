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

    /**
     * 폴더를 (다시) 지정해야 하는가. 이 경우 EFX 는 앱 내부에만 저장되고 폴더 사본은 만들어지지 않는다.
     * - 아직 지정하지 않았거나
     * - 지정한 폴더에 쓸 수 있는 접근권이 없을 때: 앱을 다시 설치하거나 기기를 옮기면 자동 백업이 설정(폴더 주소)만 복원하고
     *   시스템이 따로 기억하는 접근권은 사라진다. 폴더를 지웠거나 저장소를 뺀 경우도 같다.
     */
    fun isUnset(context: Context): Boolean {
        val path = PreferencesManager(context).efxStoragePath
        return when {
            PreferencesManager.isDefaultEfxPath(path) -> true
            path == PreferencesManager.EFX_PATH_INTERNAL -> false
            path.startsWith("content://") -> !hasWriteAccess(context, path)
            else -> true   // 예전 방식의 절대 경로는 권한 없이 쓸 수 없으므로 다시 지정해야 한다
        }
    }

    /** 지정한 폴더(트리 주소)에 지금 실제로 쓸 수 있는가: 저장해 둔 쓰기 접근권이 있고, 폴더가 그대로 있어야 한다 */
    fun hasWriteAccess(context: Context, uriString: String): Boolean = runCatching {
        val uri = Uri.parse(uriString)
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission } &&
            DocumentFile.fromTreeUri(context, uri)?.canWrite() == true
    }.getOrDefault(false)

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
