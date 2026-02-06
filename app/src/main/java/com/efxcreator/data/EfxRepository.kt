package com.efxcreator.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.efxcreator.model.EfxProjectMetadata
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lightstick.efx.Efx
import com.lightstick.efx.EfxBody
import com.lightstick.efx.EfxEntry
import com.lightstick.efx.EfxHeader
import com.lightstick.efx.MusicId
import com.lightstick.types.Color
import com.lightstick.types.EffectType
import com.lightstick.types.LSEffectPayload
import java.io.File

class EfxRepository(private val context: Context) {

    private val gson = Gson()
    private val prefs = PreferencesManager(context)

    companion object {
        private const val TAG = "EfxRepository"
    }

    // 메타데이터 파일 (항상 내부 저장소)
    private val metadataFile = File(context.filesDir, "efx_projects_metadata.json")

    /**
     * EFX 저장 디렉토리 반환
     */
    private fun getEfxDirectory(): File {
        val path = prefs.efxStoragePath

        return when {
            path == "default_internal" || path.isEmpty() -> {
                // 기본: 내부 저장소
                context.filesDir
            }
            path.startsWith("content://") -> {
                // DocumentFile URI: 내부 저장소를 임시 사용
                // TODO: DocumentFile API를 통한 파일 작업 구현 필요
                context.filesDir
            }
            else -> {
                // 일반 경로
                File(path).also {
                    if (!it.exists()) {
                        it.mkdirs()
                        Log.d(TAG, "Created EFX directory: ${it.absolutePath}")
                    }
                }
            }
        }
    }

    /**
     * 음악 불러오기 디렉토리 반환
     */
    fun getMusicDirectory(): File {
        val path = prefs.musicLoadPath

        return when {
            path == "default_music" || path.isEmpty() -> {
                // 기본: Music 폴더
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            }
            path.startsWith("content://") -> {
                // DocumentFile URI: 기본 Music 폴더
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            }
            else -> {
                // 일반 경로
                File(path).also {
                    if (!it.exists()) {
                        it.mkdirs()
                        Log.d(TAG, "Created Music directory: ${it.absolutePath}")
                    }
                }
            }
        }
    }

    /**
     * EFX 파일 경로 반환
     */
    private fun getEfxFile(projectId: String): File {
        val directory = getEfxDirectory()
        return File(directory, "$projectId.efx")
    }

    /**
     * 프로젝트 메타데이터 목록 로드
     */
    fun loadProjectsMetadata(): List<EfxProjectMetadata> {
        if (!metadataFile.exists()) {
            Log.d(TAG, "Metadata file does not exist, returning empty list")
            return emptyList()
        }

        return try {
            val json = metadataFile.readText()
            Log.d(TAG, "Loading metadata: $json")
            val type = object : TypeToken<List<EfxProjectMetadata>>() {}.type
            val projects: List<EfxProjectMetadata> = gson.fromJson(json, type) ?: emptyList()
            Log.d(TAG, "Loaded ${projects.size} projects")
            projects
        } catch (e: Exception) {
            Log.e(TAG, "Error loading metadata", e)
            emptyList()
        }
    }

    /**
     * 프로젝트 메타데이터 저장
     */
    fun saveProjectsMetadata(metadata: List<EfxProjectMetadata>) {
        try {
            val json = gson.toJson(metadata)
            metadataFile.writeText(json)
            Log.d(TAG, "Saved ${metadata.size} projects to ${metadataFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving metadata", e)
        }
    }

    /**
     * 단일 프로젝트 메타데이터 추가/업데이트
     */
    fun saveProjectMetadata(metadata: EfxProjectMetadata) {
        val projects = loadProjectsMetadata().toMutableList()
        val index = projects.indexOfFirst { it.id == metadata.id }

        if (index >= 0) {
            projects[index] = metadata
            Log.d(TAG, "Updated project: ${metadata.id}")
        } else {
            projects.add(metadata)
            Log.d(TAG, "Added new project: ${metadata.id}")
        }

        saveProjectsMetadata(projects)
    }

    /**
     * 프로젝트 삭제 (메타데이터와 EFX 파일 모두)
     */
    fun deleteProject(projectId: String) {
        val projects = loadProjectsMetadata().toMutableList()
        val project = projects.find { it.id == projectId }

        // EFX 파일 삭제
        project?.let {
            val efxFile = getEfxFile(it.id)
            if (efxFile.exists()) {
                val deleted = efxFile.delete()
                Log.d(TAG, "Deleted EFX file for $projectId: $deleted")
            }
        }

        // 메타데이터에서 제거
        val removed = projects.removeAll { it.id == projectId }
        Log.d(TAG, "Removed project from metadata: $removed")
        saveProjectsMetadata(projects)
    }

    /**
     * EFX 파일 로드 (SDK 사용)
     */
    fun loadEfx(projectId: String): Efx? {
        return try {
            val efxFile = getEfxFile(projectId)
            Log.d(TAG, "Loading EFX file: ${efxFile.absolutePath}")
            Log.d(TAG, "EFX file exists: ${efxFile.exists()}, size: ${efxFile.length()} bytes")

            if (efxFile.exists()) {
                val efx = Efx.read(efxFile)
                val major = (efx.header.version shr 8) and 0xFF
                val minor = efx.header.version and 0xFF
                Log.d(TAG, "Loaded EFX with version: v$major.$minor (0x${efx.header.version.toString(16).padStart(4, '0').uppercase()})")
                Log.d(TAG, "Loaded EFX with ${efx.body.entries.size} entries")
                efx
            } else {
                Log.w(TAG, "EFX file does not exist for project: $projectId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading EFX file for $projectId", e)
            null
        }
    }

    /**
     * EFX 파일 저장 (SDK 사용)
     */
    /**
     * EFX 파일 저장 (SDK 사용) - 디버깅 강화
     */
    fun saveEfx(projectId: String, efx: Efx) {
        try {
            val efxFile = getEfxFile(projectId)

            // ✅ 디버깅: 저장 전 상태 확인
            Log.d(TAG, "═══════════════════════════════════════")
            Log.d(TAG, "🔍 Saving EFX file...")
            Log.d(TAG, "   ├─ Project ID: $projectId")
            Log.d(TAG, "   ├─ File path: ${efxFile.absolutePath}")
            Log.d(TAG, "   ├─ Directory exists: ${efxFile.parentFile?.exists()}")
            Log.d(TAG, "   ├─ Directory writable: ${efxFile.parentFile?.canWrite()}")
            Log.d(TAG, "   └─ File exists (before): ${efxFile.exists()}")

            // ✅ 디버깅: Efx 객체 상태 확인
            Log.d(TAG, "🔍 EFX Object State:")
            Log.d(TAG, "   ├─ Header:")
            Log.d(TAG, "   │   ├─ magic: ${efx.header.magic}")
            Log.d(TAG, "   │   ├─ version: 0x${efx.header.version.toString(16).uppercase()}")
            Log.d(TAG, "   │   ├─ musicId: 0x${efx.header.musicId.toUInt().toString(16).uppercase().padStart(8, '0')}")
            Log.d(TAG, "   │   └─ entryCount: ${efx.header.entryCount}")
            Log.d(TAG, "   └─ Body:")
            Log.d(TAG, "       └─ entries.size: ${efx.body.entries.size}")

            // ✅ 디버깅: 각 엔트리 확인
            efx.body.entries.take(3).forEachIndexed { index, entry ->
                Log.d(TAG, "       [$index] ts=${entry.timestampMs}ms, effectType=${entry.payload.effectType}")
            }
            if (efx.body.entries.size > 3) {
                Log.d(TAG, "       ... and ${efx.body.entries.size - 3} more entries")
            }

            // ✅ entryCount와 실제 entries 개수 불일치 체크
            if (efx.header.entryCount != efx.body.entries.size) {
                Log.e(TAG, "❌ WARNING: entryCount mismatch!")
                Log.e(TAG, "   header.entryCount = ${efx.header.entryCount}")
                Log.e(TAG, "   body.entries.size = ${efx.body.entries.size}")

                // 수정: header를 실제 entries 개수로 업데이트
                val correctedHeader = efx.header.copy(entryCount = efx.body.entries.size)
                val correctedEfx = Efx(correctedHeader, efx.body)

                Log.d(TAG, "✅ Corrected header.entryCount to ${correctedHeader.entryCount}")

                // 수정된 Efx 사용
                correctedEfx.write(efxFile)
            } else {
                // 정상적으로 저장
                efx.write(efxFile)
            }

            // ✅ 디버깅: 저장 후 파일 확인
            Log.d(TAG, "🔍 After write():")
            Log.d(TAG, "   ├─ File exists: ${efxFile.exists()}")
            Log.d(TAG, "   ├─ File size: ${efxFile.length()} bytes")
            Log.d(TAG, "   └─ File readable: ${efxFile.canRead()}")

            // ✅ 0바이트 파일 에러 체크
            if (efxFile.exists() && efxFile.length() == 0L) {
                Log.e(TAG, "❌ ERROR: EFX file is 0 bytes!")
                Log.e(TAG, "   Attempting to read back the file...")

                try {
                    val readBack = Efx.read(efxFile)
                    Log.e(TAG, "   Read back succeeded (unexpected)")
                } catch (e: Exception) {
                    Log.e(TAG, "   Read back failed: ${e.message}")
                }
            } else if (efxFile.exists()) {
                Log.d(TAG, "✅ EFX file saved successfully: ${efxFile.length()} bytes")

                // ✅ 검증: 파일을 다시 읽어서 확인
                try {
                    val verification = Efx.read(efxFile)
                    Log.d(TAG, "✅ Verification: Read back ${verification.body.entries.size} entries")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Verification failed: ${e.message}")
                }
            } else {
                Log.e(TAG, "❌ ERROR: EFX file was not created!")
            }

            Log.d(TAG, "═══════════════════════════════════════")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving EFX file for $projectId", e)
            e.printStackTrace()
        }
    }

    /**
     * 새 빈 EFX 생성 (SDK 사용)
     */
    fun createNewEfx(): Efx {
        Log.d(TAG, "Creating new EFX")

        val defaultPayload = LSEffectPayload(
            effectIndex = 1,  // 첫 번째 Entry는 Index 1
            effectType = EffectType.ON,
            color = Color(255, 255, 255),
            backgroundColor = Color(0, 0, 0),
            period = 0,
            spf = 100,
            fade = 100,
            randomColor = 0,
            randomDelay = 0,
            broadcasting = 1,
            syncIndex = 0
        )

        val defaultEntry = EfxEntry(
            timestampMs = 0,
            payload = defaultPayload
        )

        val header = EfxHeader(entryCount = 1)

        val body = EfxBody(
            entries = listOf(defaultEntry)
        )

        return Efx(header, body)
    }

    /**
     * Music ID 계산 (SDK 사용)
     */
    fun calculateMusicId(uri: Uri): Int {
        return try {
            val musicId = MusicId.fromUri(context, uri)
            Log.d(TAG, "Calculated Music ID: 0x${musicId.toUInt().toString(16).uppercase()} for URI: $uri")
            musicId
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating Music ID for $uri", e)
            0
        }
    }

    /**
     * .efx 파일을 외부로 익스포트
     */
    fun exportEfx(projectId: String, exportName: String): File {
        val outputDir = File(context.getExternalFilesDir(null), "EfxExports")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
            Log.d(TAG, "Created export directory: ${outputDir.absolutePath}")
        }

        val sourceFile = getEfxFile(projectId)
        val outputFile = File(outputDir, "$exportName.efx")

        sourceFile.copyTo(outputFile, overwrite = true)
        Log.d(TAG, "Exported EFX to: ${outputFile.absolutePath}")

        return outputFile
    }

    /**
     * EFX 저장 경로 변경
     */
    fun changeEfxStoragePath(newPath: String) {
        val oldPath = prefs.efxStoragePath

        if (oldPath == newPath) return

        Log.d(TAG, "Changing EFX storage from $oldPath to $newPath")

        // 기존 디렉토리
        val oldDir = getEfxDirectory()

        // 경로 변경
        prefs.efxStoragePath = newPath

        // 새 디렉토리
        val newDir = getEfxDirectory()

        // URI 경로인 경우 파일 이동 스킵 (DocumentFile API 필요)
        if (newPath.startsWith("content://")) {
            Log.d(TAG, "New path is URI, skipping file migration")
            return
        }

        // 모든 EFX 파일 이동
        val projects = loadProjectsMetadata()

        projects.forEach { project ->
            try {
                val oldFile = File(oldDir, "${project.id}.efx")
                val newFile = File(newDir, "${project.id}.efx")

                if (oldFile.exists() && oldFile != newFile) {
                    oldFile.copyTo(newFile, overwrite = true)
                    oldFile.delete()
                    Log.d(TAG, "Moved ${project.id}.efx from ${oldFile.path} to ${newFile.path}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error moving EFX file for ${project.id}", e)
            }
        }

        Log.d(TAG, "EFX storage path changed successfully")
    }

    /**
     * 음악 불러오기 경로 변경
     */
    fun changeMusicLoadPath(newPath: String) {
        Log.d(TAG, "Changing Music load path to: $newPath")
        prefs.musicLoadPath = newPath
    }

    /**
     * 현재 EFX 저장 위치 정보
     */
    fun getEfxStorageInfo(): String {
        val path = prefs.efxStoragePath

        return when {
            path == "default_internal" || path.isEmpty() -> {
                "기본: ${context.filesDir.absolutePath}"
            }
            path.startsWith("content://") -> {
                try {
                    val uri = Uri.parse(path)
                    val documentFile = DocumentFile.fromTreeUri(context, uri)
                    documentFile?.name?.let { "사용자 지정: $it" } ?: "사용자 지정"
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting DocumentFile name", e)
                    "사용자 지정"
                }
            }
            else -> {
                "사용자 지정: $path"
            }
        }
    }

    /**
     * 현재 음악 불러오기 위치 정보
     */
    fun getMusicLoadInfo(): String {
        val path = prefs.musicLoadPath

        return when {
            path == "default_music" || path.isEmpty() -> {
                "기본: ${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath}"
            }
            path.startsWith("content://") -> {
                try {
                    val uri = Uri.parse(path)
                    val documentFile = DocumentFile.fromTreeUri(context, uri)
                    documentFile?.name?.let { "사용자 지정: $it" } ?: "사용자 지정"
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting DocumentFile name", e)
                    "사용자 지정"
                }
            }
            else -> {
                "사용자 지정: $path"
            }
        }
    }

    /**
     * 단일 프로젝트 메타데이터 로드
     */
    fun loadProjectMetadata(projectId: String): EfxProjectMetadata? {
        val projects = loadProjectsMetadata()
        val project = projects.find { it.id == projectId }
        Log.d(TAG, "Loaded metadata for project $projectId: ${project?.name}")
        return project
    }

    /**
     * 경로 유효성 검사
     */
    fun isPathValid(path: String): Boolean {
        return try {
            if (path.startsWith("content://")) {
                // URI 형식인 경우 항상 유효하다고 가정
                true
            } else {
                // 일반 경로인 경우
                val file = File(path)
                file.mkdirs()
                file.canWrite()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Invalid path: $path", e)
            false
        }
    }

    /**
     * 디버그: 저장된 모든 파일 목록 출력
     */
    fun debugListFiles() {
        Log.d(TAG, "=== EFX Storage Path: ${prefs.efxStoragePath} ===")
        Log.d(TAG, "=== EFX Storage Info: ${getEfxStorageInfo()} ===")
        Log.d(TAG, "=== Music Load Path: ${prefs.musicLoadPath} ===")
        Log.d(TAG, "=== Music Load Info: ${getMusicLoadInfo()} ===")
        Log.d(TAG, "=== Files in EFX directory ===")

        val efxDir = getEfxDirectory()
        Log.d(TAG, "Directory: ${efxDir.absolutePath}")

        efxDir.listFiles()?.forEach { file ->
            Log.d(TAG, "File: ${file.name}, Size: ${file.length()} bytes")
        } ?: Log.d(TAG, "No files found or directory is empty")

        Log.d(TAG, "=============================")
    }
}