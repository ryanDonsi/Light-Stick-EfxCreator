package com.efxcreator.data

import android.content.Context
import android.util.Log
import com.efxcreator.music.sets.EffectRuleSet
import com.efxcreator.music.sets.GenerationOptions
import com.efxcreator.music.sets.PaletteSet
import com.efxcreator.music.sets.SetCatalog
import com.google.gson.Gson
import java.io.File
import com.efxcreator.R
import com.efxcreator.core.Loc

/**
 * 사용자 정의 이펙트 세트/팔레트 세트 저장소 (filesDir/user_sets.json).
 * 내장 세트는 코드에 있으며 읽기 전용이라 저장하지 않는다.
 */
class SetRepository(context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, "user_sets.json")

    private class Store(
        val effectSets: List<EffectRuleSet> = emptyList(),
        val paletteSets: List<PaletteSet> = emptyList()
    )

    companion object {
        private const val TAG = "SetRepository"
    }

    @Synchronized
    private fun load(): Store {
        if (!file.exists()) return Store()
        return try {
            gson.fromJson(file.readText(), Store::class.java) ?: Store()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user sets", e)
            Store()
        }
    }

    @Synchronized
    private fun save(store: Store) {
        try {
            file.writeText(gson.toJson(store))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user sets", e)
        }
    }

    // ── 조회 ─────────────────────────────────────────────────

    fun userEffectSets(): List<EffectRuleSet> = load().effectSets
    fun userPaletteSets(): List<PaletteSet> = load().paletteSets

    /** 내장 + 사용자 세트 */
    fun allEffectSets(): List<EffectRuleSet> = SetCatalog.BUILT_IN_RULE_SETS + userEffectSets()
    fun allPaletteSets(): List<PaletteSet> = SetCatalog.BUILT_IN_PALETTE_SETS + userPaletteSets()

    fun findEffectSet(id: String?): EffectRuleSet? =
        if (id.isNullOrEmpty()) null else allEffectSets().firstOrNull { it.id == id }

    fun findPaletteSet(id: String?): PaletteSet? =
        if (id.isNullOrEmpty()) null else allPaletteSets().firstOrNull { it.id == id }

    /** 저장된 선택 id(빈 문자열 = 자동) → 생성 옵션. 삭제돼 없는 id 는 자동으로 취급한다. */
    fun resolveOptions(effectSetId: String?, paletteSetId: String?) = GenerationOptions(
        ruleSet = findEffectSet(effectSetId),
        paletteSet = findPaletteSet(paletteSetId)
    )

    // ── 추가/수정/삭제 (내장 세트는 건드리지 않는다) ───────────────

    @Synchronized
    fun upsertEffectSet(set: EffectRuleSet) {
        require(!set.builtIn) { Loc.str(R.string.set_repository_1) }
        val store = load()
        val list = store.effectSets.toMutableList()
        val i = list.indexOfFirst { it.id == set.id }
        if (i >= 0) list[i] = set else list.add(set)
        save(Store(list, store.paletteSets))
    }

    @Synchronized
    fun deleteEffectSet(id: String) {
        val store = load()
        save(Store(store.effectSets.filterNot { it.id == id }, store.paletteSets))
    }

    @Synchronized
    fun upsertPaletteSet(set: PaletteSet) {
        require(!set.builtIn) { Loc.str(R.string.set_repository_1) }
        val store = load()
        val list = store.paletteSets.toMutableList()
        val i = list.indexOfFirst { it.id == set.id }
        if (i >= 0) list[i] = set else list.add(set)
        save(Store(store.effectSets, list))
    }

    @Synchronized
    fun deletePaletteSet(id: String) {
        val store = load()
        save(Store(store.effectSets, store.paletteSets.filterNot { it.id == id }))
    }
}
