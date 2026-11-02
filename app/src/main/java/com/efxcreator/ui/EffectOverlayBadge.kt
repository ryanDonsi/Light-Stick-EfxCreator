package com.efxcreator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.efxcreator.model.EfxEntry
import com.efxcreator.ui.editor.LedSimulator

/**
 * 앨범 아트 위 실시간 이펙트 배지 (Light-Stick-Music 의 EffectOverlayBadge 와 같은 모양).
 * 왼쪽에 지금 적용 중인 이펙트 이름, 오른쪽에 그 순간의 LED 색 원을 보여 준다.
 */
@Composable
fun EffectOverlayBadge(
    entries: List<EfxEntry>,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    val index = LedSimulator.indexAt(entries, positionMs)
    val entry = entries.getOrNull(index) ?: return
    val led = LedSimulator.stateAt(entries, positionMs)
    val dot = Color(
        led.r.toInt().coerceIn(0, 255),
        led.g.toInt().coerceIn(0, 255),
        led.b.toInt().coerceIn(0, 255)
    )
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = entry.payload.effectType.name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontSize = 9.sp,
            maxLines = 1,
            softWrap = false
        )
        Box(Modifier.size(10.dp).background(dot, CircleShape))
    }
}
