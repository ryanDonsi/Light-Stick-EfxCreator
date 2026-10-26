package com.efxcreator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.efxcreator.ui.editor.TimelineMath

/** 그룹 마스크 상수 (SDK Group 과 같은 값) */
object GroupMasks {
    const val GROUP_COUNT = 32
    const val NONE = 0L                 // 단일: 그룹과 무관
    const val ALL_GROUPS = 0xFFFFFFFFL  // 모든 그룹

    /** 그룹 번호(1..32) 의 비트가 켜져 있는가 */
    fun isOn(mask: Long, group: Int): Boolean = ((mask shr (group - 1)) and 1L) == 1L

    /** 그룹 번호(1..32) 의 비트를 반전한다 */
    fun toggle(mask: Long, group: Int): Long = (mask xor (1L shl (group - 1))) and ALL_GROUPS
}

/**
 * 그룹 마스크를 32개의 그룹 번호 토글로 편집한다.
 * 번호 N 을 켜면 bit(N-1) 이 1 이 되고, 아무것도 켜지 않으면 0 = 단일(그룹과 무관)이다.
 * 아래에 현재 값(16진수와 GRPn 목록)을 보여 준다.
 */
@Composable
fun GroupMaskPicker(
    mask: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val columns = 8
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("그룹 마스크", style = MaterialTheme.typography.labelLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange(GroupMasks.NONE) }) { Text("단일") }
            OutlinedButton(onClick = { onChange(GroupMasks.ALL_GROUPS) }) { Text("모든 그룹") }
        }

        for (row in 0 until GroupMasks.GROUP_COUNT / columns) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until columns) {
                    val group = row * columns + col + 1
                    val on = GroupMasks.isOn(mask, group)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onChange(GroupMasks.toggle(mask, group)) }
                    ) {
                        Text(
                            group.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        Text(
            TimelineMath.formatGroupMask(mask),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
