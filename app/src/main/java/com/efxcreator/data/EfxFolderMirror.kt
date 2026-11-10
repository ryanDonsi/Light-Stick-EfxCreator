package com.efxcreator.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * 곡의 .efx 파일을 저장소 설정 폴더 아래에 `공연명/곡명.efx` 로도 만들어 두는 미러.
 *
 * 앱이 쓰는 작업 파일(`<id>.efx`)은 그대로 두고, 사용자가 폴더에서 바로 볼 수 있는 사본만 관리한다.
 * 곡을 만들거나 저장할 때, 곡 이름이나 소속 공연이 바뀔 때 다시 맞추고, 이전 위치의 사본은 지운다.
 * 저장소가 기본(앱 내부)이면 사용자가 볼 수 없는 위치라 만들지 않는다.
 */
class EfxFolderMirror(private val context: Context) {

    private val prefs = PreferencesManager(context)
    private val spf = context.getSharedPreferences("efx_mirror_paths", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "EfxFolderMirror"
        private const val MIME = "application/octet-stream"

        /** 파일/폴더 이름에 쓸 수 없는 문자를 바꾼다 */
        fun safeName(name: String, fallback: String): String {
            val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.')
            return cleaned.ifEmpty { fallback }
        }
    }

    /** 사본을 둘 루트. 내부 저장소(기본)이면 null */
    private sealed class Root {
        class Tree(val dir: DocumentFile) : Root()
        class Path(val dir: File) : Root()
    }

    private fun resolveRoot(): Root? {
        val path = prefs.efxStoragePath
        return when {
            path == PreferencesManager.EFX_PATH_INTERNAL -> null
            // 폴더 미지정(기본값): 사본을 만들지 않고 앱 내부 저장소로만 동작한다
            path.isEmpty() || path == PreferencesManager.EFX_PATH_DEFAULT -> null
            path.startsWith("content://") -> DocumentFile.fromTreeUri(context, Uri.parse(path))
                ?.takeIf { it.canWrite() }?.let { Root.Tree(it) }
            else -> File(path).takeIf { it.isDirectory || it.mkdirs() }?.let { Root.Path(it) }
        }
    }

    /** 이 곡의 사본을 현재 이름/공연에 맞는 위치에 만든다 (이미 있으면 덮어쓴다) */
    fun sync(projectId: String) {
        try {
            val repo = EfxRepository(context)
            val root = resolveRoot() ?: return
            val metas = repo.loadProjectsMetadata()
            val meta = metas.firstOrNull { it.id == projectId } ?: return
            val source = repo.getEfxFile(projectId).takeIf { it.exists() && it.length() > 0 } ?: return

            val shows = ShowRepository(context).load()
            val show = shows.firstOrNull { projectId in it.songIds }
            if (show == null) {
                // 공연에 속하지 않은 곡은 폴더 사본을 두지 않는다 (공연명 폴더가 없으므로)
                remove(projectId)
                return
            }
            val folderName = safeName(show.name, "show")

            // 같은 공연 안에 같은 이름의 곡이 있으면 id 일부를 붙여 구분한다
            val siblings = show.songIds.filter { it != projectId }
            val baseName = safeName(meta.name, "song")
            val clash = metas.any { it.id in siblings && safeName(it.name, "song") == baseName }
            val fileName = (if (clash) "${baseName}_${projectId.take(4)}" else baseName) + ".efx"

            val target = "$folderName/$fileName"
            val previous = spf.getString(projectId, null)
            if (previous != null && previous != target) deleteCopy(root, previous)

            writeCopy(root, folderName, fileName, source)
            spf.edit().putString(projectId, target).apply()
            Log.d(TAG, "Mirrored $projectId → $target")
        } catch (e: Exception) {
            Log.e(TAG, "Mirror sync failed for $projectId", e)
        }
    }

    /** 이 곡의 사본을 지운다 (곡 삭제 시) */
    fun remove(projectId: String) {
        try {
            val previous = spf.getString(projectId, null) ?: return
            resolveRoot()?.let { deleteCopy(it, previous) }
            spf.edit().remove(projectId).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Mirror remove failed for $projectId", e)
        }
    }

    /** 저장소 위치가 바뀌었을 때 등, 모든 곡의 사본을 다시 만든다 */
    fun syncAll() {
        EfxRepository(context).loadProjectsMetadata().forEach { sync(it.id) }
    }

    private fun writeCopy(root: Root, folderName: String, fileName: String, source: File) {
        when (root) {
            is Root.Path -> {
                val dir = File(root.dir, folderName).also { it.mkdirs() }
                source.copyTo(File(dir, fileName), overwrite = true)
            }
            is Root.Tree -> {
                val dir = root.dir.findFile(folderName)?.takeIf { it.isDirectory }
                    ?: root.dir.createDirectory(folderName)
                    ?: throw java.io.IOException("cannot create folder $folderName")
                val existing = dir.findFile(fileName)
                val file = existing ?: dir.createFile(MIME, fileName)
                    ?: throw java.io.IOException("cannot create file $fileName")
                // 이름이 중복돼 제공자가 이름을 바꿔 버린 경우를 피하려고, 있으면 덮어쓰기("wt")로 쓴다
                context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                } ?: throw java.io.IOException("cannot open ${file.uri}")
            }
        }
    }

    private fun deleteCopy(root: Root, relative: String) {
        val folder = relative.substringBefore('/')
        val name = relative.substringAfter('/')
        when (root) {
            is Root.Path -> {
                val dir = File(root.dir, folder)
                File(dir, name).delete()
                // 비어 있는 공연 폴더는 정리한다
                if (dir.isDirectory && dir.list().isNullOrEmpty()) dir.delete()
            }
            is Root.Tree -> {
                val dir = root.dir.findFile(folder)?.takeIf { it.isDirectory } ?: return
                dir.findFile(name)?.delete()
                if (dir.listFiles().isEmpty()) dir.delete()
            }
        }
    }
}
