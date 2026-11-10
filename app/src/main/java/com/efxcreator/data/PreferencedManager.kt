package com.efxcreator.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("efx_creator_prefs", Context.MODE_PRIVATE)

    companion object {
        /** EFX 저장 위치 기본값: Music/Effects 에 `공연명/곡명.efx` 사본을 둔다 (모든 파일 접근이 허용돼야 만들어짐) */
        const val EFX_PATH_DEFAULT = "default_efx"
        /** 앱 내부에만 저장 (사본 없음) */
        const val EFX_PATH_INTERNAL = "default_internal"

        /** Music/Effects 폴더 */
        fun defaultEfxDir(): java.io.File = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "Effects"
        )

        fun isDefaultEfxPath(path: String): Boolean = path.isEmpty() || path == EFX_PATH_DEFAULT

        /**
         * 기본 위치(Music/Effects)를 쓰는 중이고 권한이 허용돼 있으면 폴더를 만든다.
         * 권한이 없거나 기본 위치를 쓰지 않으면 아무것도 하지 않는다. 폴더가 준비돼 있으면 true.
         */
        fun ensureDefaultEfxDir(context: Context): Boolean {
            if (!isDefaultEfxPath(PreferencesManager(context).efxStoragePath) || !canWriteFolders(context)) return false
            val dir = defaultEfxDir()
            return dir.isDirectory || dir.mkdirs()
        }

        /** 폴더 경로로 직접 쓰기 위한 권한 (Android 11+ 모든 파일 접근, 그 이전은 쓰기 권한) */
        fun canWriteFolders(context: Context): Boolean =
            if (android.os.Build.VERSION.SDK_INT >= 30) android.os.Environment.isExternalStorageManager()
            else androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        // EFX 저장 경로
        private const val KEY_EFX_STORAGE_PATH = "efx_storage_path"

        // 음악 불러오기 경로
        private const val KEY_MUSIC_LOAD_PATH = "music_load_path"

        // 기본값 (최초 실행 시)
        private const val DEFAULT_EFX_PATH = EFX_PATH_DEFAULT
        private const val DEFAULT_MUSIC_PATH = "default_music"
    }

    // EFX 저장 경로
    var efxStoragePath: String
        get() = prefs.getString(KEY_EFX_STORAGE_PATH, DEFAULT_EFX_PATH) ?: DEFAULT_EFX_PATH
        set(value) = prefs.edit().putString(KEY_EFX_STORAGE_PATH, value).apply()

    // 기본 저장 위치에 필요한 권한 안내를 이미 한 번 보여줬는지 (거부해도 다시 묻지 않는다)
    var storagePermissionPromptShown: Boolean
        get() = prefs.getBoolean("storage_perm_prompt_shown", false)
        set(value) = prefs.edit().putBoolean("storage_perm_prompt_shown", value).apply()

    // 음악 불러오기 경로
    var musicLoadPath: String
        get() = prefs.getString(KEY_MUSIC_LOAD_PATH, DEFAULT_MUSIC_PATH) ?: DEFAULT_MUSIC_PATH
        set(value) = prefs.edit().putString(KEY_MUSIC_LOAD_PATH, value).apply()

    // 이번 행사의 전체 그룹 개수 (그룹 배정 방송에 사용, 1~20)
    var groupCount: Int
        get() = prefs.getInt("group_count", 20).coerceIn(1, 20)
        set(value) = prefs.edit().putInt("group_count", value.coerceIn(1, 20)).apply()

    // 기기별 Broadcasting Mode (연결된 기기가 주변 응원봉에 신호를 전파할지). 기본 ON
    fun getBroadcasting(mac: String): Boolean = prefs.getBoolean("broadcasting_$mac", true)

    fun setBroadcasting(mac: String, enabled: Boolean) =
        prefs.edit().putBoolean("broadcasting_$mac", enabled).apply()

    // 기기별 자동 연결: 앱 시작 시 자동으로 연결하고 앱 종료 시 해제할 기기 (mac → 이름). 등록 순서를 유지한다
    fun getAutoConnectDevices(): List<Pair<String, String?>> =
        (prefs.getString("auto_connect_macs", "") ?: "").split(",").filter { it.isNotBlank() }
            .map { it to prefs.getString("auto_connect_name_$it", null) }

    fun isAutoConnect(mac: String): Boolean = getAutoConnectDevices().any { it.first == mac }

    fun setAutoConnect(mac: String, name: String?, enabled: Boolean) {
        val macs = getAutoConnectDevices().map { it.first }.filter { it != mac }.toMutableList()
        if (enabled) macs += mac
        prefs.edit().apply {
            putString("auto_connect_macs", macs.joinToString(","))
            if (enabled && name != null) putString("auto_connect_name_$mac", name) else remove("auto_connect_name_$mac")
        }.apply()
    }

    // 마지막으로 선택한 이펙트/팔레트 세트 id (빈 문자열 = 자동)
    var lastEffectSetId: String
        get() = prefs.getString("last_effect_set_id", "") ?: ""
        set(value) = prefs.edit().putString("last_effect_set_id", value).apply()

    // 곡별로 마지막에 적용한 팔레트 세트 id (없으면 null)
    fun getSongPaletteSetId(projectId: String): String? = prefs.getString("song_palette_$projectId", null)

    fun setSongPaletteSetId(projectId: String, paletteSetId: String) =
        prefs.edit().putString("song_palette_$projectId", paletteSetId).apply()

    fun getSongEffectSetId(projectId: String): String? = prefs.getString("song_effect_set_$projectId", null)

    fun setSongEffectSetId(projectId: String, effectSetId: String) =
        prefs.edit().putString("song_effect_set_$projectId", effectSetId).apply()

    var lastPaletteSetId: String
        get() = prefs.getString("last_palette_set_id", "") ?: ""
        set(value) = prefs.edit().putString("last_palette_set_id", value).apply()
}
