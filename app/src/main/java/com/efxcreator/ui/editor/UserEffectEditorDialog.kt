package com.efxcreator.ui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.efxcreator.model.UserEffect
import com.efxcreator.music.sets.RgbColor
import com.efxcreator.ui.TimelineEntryDialog
import com.efxcreator.ui.sets.SetDropdown
import com.lightstick.efx.EfxEntry
import com.lightstick.types.EffectType

/**
 * "내 라이브러리" 한 칸을 추가/수정하는 다이얼로그.
 * 타임라인의 "선택한 이펙트" 편집창([TimelineEntryDialog])을 그대로 쓰고, 시간·Random·그룹 마스크처럼
 * 라이브러리에 저장되지 않는 항목만 숨긴다. 맨 위에 이름 입력과 기본 프리셋 불러오기가 추가된다.
 */
@Composable
fun UserEffectEditorDialog(
    slot: Int,
    initial: UserEffect?,
    newId: () -> String,
    onSave: (UserEffect) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var pickedPreset by remember { mutableStateOf("") }
    // 프리셋을 고르면 이 값이 바뀌고, 아래 key 로 편집창이 새 값으로 다시 시작된다
    var seed by remember { mutableStateOf(initial?.toEntry()) }
    var seedVersion by remember { mutableIntStateOf(0) }

    key(seedVersion) {
        TimelineEntryDialog(
            entry = seed,
            title = if (initial == null) "내 라이브러리 ${slot + 1}번 칸에 추가" else "내 라이브러리 ${slot + 1}번 칸 수정",
            deleteTitle = "라이브러리 삭제",
            deleteMessage = "이 칸의 이펙트를 삭제하시겠습니까?",
            showTimestamp = false,
            showTargetOptions = false,
            onDismiss = onDismiss,
            onDelete = onDelete,
            onSave = { e ->
                val p = e.payload
                val twoColor = p.effectType == EffectType.BLINK || p.effectType == EffectType.BREATH ||
                    p.effectType == EffectType.STROBE
                val fg = RgbColor(p.color.r, p.color.g, p.color.b)
                onSave(
                    UserEffect(
                        id = initial?.id ?: newId(), slot = slot,
                        label = label.trim().ifEmpty { "${p.effectType.name} #%02X%02X%02X".format(fg.r, fg.g, fg.b) },
                        effectType = p.effectType.name, fg = fg,
                        bg = if (twoColor) RgbColor(p.backgroundColor.r, p.backgroundColor.g, p.backgroundColor.b) else RgbColor(0, 0, 0),
                        period = p.period, spf = p.spf, fade = p.fade
                    )
                )
            },
            topContent = {
                SetDropdown(
                    label = "기본 프리셋에서 가져오기",
                    autoLabel = "직접 입력",
                    items = EffectPresets.all,
                    selectedId = pickedPreset,
                    idOf = { it.id },
                    nameOf = { "${it.category.label} · ${it.label}" },
                    onSelect = { id ->
                        pickedPreset = id
                        EffectPresets.all.firstOrNull { it.id == id }?.let { p ->
                            seed = p.toEntry(0L)
                            if (label.isBlank()) label = p.label
                            seedVersion++
                        }
                    }
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("이름 (비우면 자동)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}

private fun UserEffect.toEntry(): EfxEntry = toPreset().toEntry(0L)
