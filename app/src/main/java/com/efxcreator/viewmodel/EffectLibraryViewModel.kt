package com.efxcreator.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.efxcreator.data.EffectLibraryRepository
import com.efxcreator.model.UserEffect
import com.efxcreator.music.sets.RgbColor
import com.lightstick.efx.EfxEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** "내 라이브러리": 사용자가 등록해 둔 이펙트 [UserEffect.SLOT_COUNT] 칸 */
class EffectLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EffectLibraryRepository(application)

    private val _slots = MutableStateFlow(repository.load())
    val slots: StateFlow<List<UserEffect?>> = _slots.asStateFlow()

    val usedCount: Int get() = _slots.value.count { it != null }

    private fun commit(newSlots: List<UserEffect?>) {
        repository.save(newSlots)
        _slots.value = newSlots
    }

    fun newId(): String = UUID.randomUUID().toString()

    /** [slot] 칸에 저장(추가 또는 덮어쓰기) */
    fun save(slot: Int, effect: UserEffect) {
        require(slot in 0 until UserEffect.SLOT_COUNT) { "slot out of range: $slot" }
        val list = _slots.value.toMutableList()
        list[slot] = effect.copy(slot = slot)
        commit(list)
    }

    fun delete(slot: Int) {
        if (slot !in 0 until UserEffect.SLOT_COUNT) return
        val list = _slots.value.toMutableList()
        list[slot] = null
        commit(list)
    }

    /**
     * 타임라인의 엔트리를 가장 앞쪽 빈 칸에 저장한다.
     * @return 저장된 칸 번호, 라이브러리가 가득 차면 -1
     */
    fun addFromEntry(entry: EfxEntry): Int {
        val slot = _slots.value.indexOfFirst { it == null }
        if (slot < 0) return -1
        val p = entry.payload
        val hex = "#%02X%02X%02X".format(p.color.r, p.color.g, p.color.b)
        save(
            slot,
            UserEffect(
                id = newId(), slot = slot,
                label = "${p.effectType.name} $hex",
                effectType = p.effectType.name,
                fg = RgbColor(p.color.r, p.color.g, p.color.b),
                bg = RgbColor(p.backgroundColor.r, p.backgroundColor.g, p.backgroundColor.b),
                period = p.period, spf = p.spf, fade = p.fade
            )
        )
        return slot
    }
}
