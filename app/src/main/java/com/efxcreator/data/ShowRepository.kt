package com.efxcreator.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.efxcreator.model.Show
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** 공연 목록 저장소 (filesDir/shows.json) */
class ShowRepository(context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, "shows.json")

    @Synchronized
    fun load(): List<Show> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<Show>>() {}.type
            gson.fromJson<List<Show>>(file.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading shows", e)
            emptyList()
        }
    }

    @Synchronized
    fun save(shows: List<Show>) {
        try {
            file.writeText(gson.toJson(shows))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving shows", e)
        }
    }

    private val imageDir = File(context.filesDir, "show_images").apply { mkdirs() }
    private val resolver = context.contentResolver

    /**
     * 선택한 이미지를 앱 내부에 복사한다 (SAF 권한이 만료돼도 보이도록).
     * 같은 공연의 이미지를 바꿀 때 캐시가 남지 않게 파일 이름에 시각을 붙인다.
     * @return 복사한 파일 경로, 실패하면 null
     */
    fun importImage(showId: String, uri: Uri): String? = try {
        deleteImages(showId)
        val dest = File(imageDir, "${showId}_${System.currentTimeMillis()}.img")
        resolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
        if (dest.length() > 0) dest.absolutePath else null
    } catch (e: Exception) {
        Log.e(TAG, "Error importing show image", e)
        null
    }

    fun deleteImages(showId: String) {
        imageDir.listFiles { f -> f.name.startsWith("${showId}_") }?.forEach { it.delete() }
    }

    companion object {
        private const val TAG = "ShowRepository"
    }
}
