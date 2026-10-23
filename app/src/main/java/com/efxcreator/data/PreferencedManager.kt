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

    // 마지막으로 선택한 이펙트/팔레트 세트 id (빈 문자열 = 자동)
    var lastEffectSetId: String
        get() = prefs.getString("last_effect_set_id", "") ?: ""
        set(value) = prefs.edit().putString("last_effect_set_id", value).apply()

    var lastPaletteSetId: String
        get() = prefs.getString("last_palette_set_id", "") ?: ""
        set(value) = prefs.edit().putString("last_palette_set_id", value).apply()
}
