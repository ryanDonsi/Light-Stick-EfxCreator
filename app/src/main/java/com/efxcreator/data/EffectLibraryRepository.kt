package com.efxcreator.data

import android.content.Context
import android.util.Log
import com.efxcreator.model.UserEffect
import com.google.gson.Gson
import java.io.File

/** 사용자 이펙트 라이브러리(최대 [UserEffect.SLOT_COUNT] 칸) 저장소 — filesDir/user_effect_library.json */
class EffectLibraryRepository(context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, "user_effect_library.json")

    private class Store(val items: List<UserEffect> = emptyList())

    companion object {
        private const val TAG = "EffectLibraryRepository"
    }

    /** 길이 [UserEffect.SLOT_COUNT] 의 칸 목록 (빈 칸은 null) */
    @Synchronized
    fun load(): List<UserEffect?> {
        val slots = arrayOfNulls<UserEffect>(UserEffect.SLOT_COUNT)
        if (!file.exists()) return slots.toList()
        try {
            val store = gson.fromJson(file.readText(), Store::class.java)
            store?.items?.forEach { e ->
                if (e.slot in slots.indices) slots[e.slot] = e
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error loading user effect library", ex)
        }
        return slots.toList()
    }

    @Synchronized
    fun save(slots: List<UserEffect?>) {
        try {
            file.writeText(gson.toJson(Store(slots.filterNotNull())))
        } catch (ex: Exception) {
            Log.e(TAG, "Error saving user effect library", ex)
        }
    }
}
