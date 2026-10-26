package com.efxcreator.data

import android.content.Context
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

    companion object {
        private const val TAG = "ShowRepository"
    }
}
