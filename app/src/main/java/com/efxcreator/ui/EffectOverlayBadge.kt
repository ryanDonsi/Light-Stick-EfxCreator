package com.efxcreator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
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
import com.lightstick.efx.EfxEntry
import com.efxcreator.ui.editor.LedSimulator

/**
 * 앨범 아트 위 실시간 이펙트 배지 (Light-Stick-Music 의 EffectOverlayBadge 와 같은 모양).
 * 왼쪽에 지금 적용 중인 이펙트 이름, 오른쪽에 그 순간의 LED 색 원을 보여 준다.
 * 곡 목록 카드와 같은 규격: 이름은 [maxLabelLength] 글자까지, 폭 고정(원이 흔들리지 않음), 앨범 아트 오른쪽 아래.
 */
@Composable
fun EffectOverlayBadge(
    entries: List<EfxEntry>,
    positionMs: Long,
    modifier: Modifier = Modifier,
    maxLabelLength: Int = 2
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
            .width((14 * maxLabelLength + 24).dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = entry.payload.effectType.name.take(maxLabelLength),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontSize = 10.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.size(10.dp).background(dot, CircleShape))
    }
}
