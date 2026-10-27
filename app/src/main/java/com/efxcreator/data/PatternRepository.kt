package com.efxcreator.data

import android.content.Context
import android.util.Log
import com.efxcreator.model.EffectPattern
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** 이펙트 라이브러리(패턴) 저장소 (filesDir/effect_patterns.json) */
class PatternRepository(context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, "effect_patterns.json")

    @Synchronized
    fun load(): List<EffectPattern> {
        if (!file.exists()) return emptyList()
        return try {
            gson.fromJson<List<EffectPattern>>(file.readText(), object : TypeToken<List<EffectPattern>>() {}.type)
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading patterns", e)
            emptyList()
        }
    }

    @Synchronized
    fun save(patterns: List<EffectPattern>) {
        try {
            file.writeText(gson.toJson(patterns))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving patterns", e)
        }
    }

    companion object {
        private const val TAG = "PatternRepository"
    }
}
