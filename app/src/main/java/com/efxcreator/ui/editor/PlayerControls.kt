package com.efxcreator.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Material 3 [Slider] 기반 재생 바.
 *
 * 위: 현재 시간(좌) / 전체 시간(우), 아래: 슬라이더.
 * 드래그 중에는 진행 바와 현재 시간이 실시간으로 바뀌고, 손가락을 뗄 때 [onSeekTo] 로 위치가 확정된다.
 */
@Composable
fun PlayerSeekBar(
    currentPosition: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseProgress = if (duration > 0) (currentPosition / duration.toFloat()).coerceIn(0f, 1f) else 0f

    var dragProgress by remember { mutableStateOf<Float?>(null) }
    val displayProgress = dragProgress ?: baseProgress
    val displayMs = dragProgress?.let { (duration * it).toLong() } ?: currentPosition

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                TimelineMath.formatTime(displayMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                TimelineMath.formatTime(duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = displayProgress,
            onValueChange = { dragProgress = it },
            onValueChangeFinished = {
                dragProgress?.let { if (duration > 0) onSeekTo((duration * it).toLong()) }
                dragProgress = null
            },
            enabled = duration > 0
        )
    }
}

/** 이전 / 재생·일시정지 / 다음 — Material 3 아이콘 버튼 (재생은 Filled, 나머지는 Standard). */
@Composable
fun PlayerButtons(
    isPlaying: Boolean,
    enabled: Boolean,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev, enabled = enabled) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "이전")
        }
        FilledIconButton(onClick = onPlayPause, enabled = enabled, modifier = Modifier.size(52.dp)) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "일시정지" else "재생",
                modifier = Modifier.size(30.dp)
            )
        }
        IconButton(onClick = onNext, enabled = enabled) {
            Icon(Icons.Default.SkipNext, contentDescription = "다음")
        }
    }
}
