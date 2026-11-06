package com.efxcreator.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("efx_creator_prefs", Context.MODE_PRIVATE)

    companion object {
        // EFX 저장 경로
        private const val KEY_EFX_STORAGE_PATH = "efx_storage_path"

        // 음악 불러오기 경로
        private const val KEY_MUSIC_LOAD_PATH = "music_load_path"

        // 기본값 (최초 실행 시)
        private const val DEFAULT_EFX_PATH = "default_internal"
        private const val DEFAULT_MUSIC_PATH = "default_music"
    }

    // EFX 저장 경로
    var efxStoragePath: String
        get() = prefs.getString(KEY_EFX_STORAGE_PATH, DEFAULT_EFX_PATH) ?: DEFAULT_EFX_PATH
        set(value) = prefs.edit().putString(KEY_EFX_STORAGE_PATH, value).apply()

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
