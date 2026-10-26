package com.efxcreator.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.efxcreator.R

/** Light-Stick-Music 재생 바와 같은 색 (그라데이션 채움, 트랙, 시간 글자) */
private val SEEK_GRADIENT_START = Color(0xFF9D79BC)
private val SEEK_GRADIENT_END = Color(0xFF8A40C4)
private val SEEK_TRACK = Color(0xFF424242)
private val SEEK_TEXT = Color(0xFF9CA3AF)
private val SEEK_TIME_STYLE = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 17.sp)

/**
 * Light-Stick-Music 의 MusicSeekBar 와 같은 재생 바.
 *
 * 위: 현재 시간(좌) / 전체 시간(우), 아래: 4dp 그라데이션 진행 바 (터치 영역 16dp).
 * 탭/드래그 중에는 진행 바와 현재 시간이 실시간으로 바뀌고, 손가락을 뗄 때 [onSeekTo] 로 위치가 확정된다.
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

    val gradient = Brush.horizontalGradient(listOf(SEEK_GRADIENT_START, SEEK_GRADIENT_END))

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(TimelineMath.formatTime(displayMs), style = SEEK_TIME_STYLE, color = SEEK_TEXT)
            Text(TimelineMath.formatTime(duration), style = SEEK_TIME_STYLE, color = SEEK_TEXT)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .pointerInput(duration) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Main)
                        val startPercent = (down.position.x / size.width).coerceIn(0f, 1f)
                        dragProgress = startPercent

                        drag(down.id) { change ->
                            change.consume()
                            dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        }

                        if (duration > 0) {
                            onSeekTo((duration * (dragProgress ?: startPercent)).toLong())
                        }
                        dragProgress = null
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(9999.dp))
                        .background(SEEK_TRACK)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(displayProgress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9999.dp))
                        .background(brush = gradient)
                )
            }
        }
    }
}

/** Light-Stick-Music 재생 버튼: 평소/눌림 이미지가 다른 원형 이미지 버튼 */
@Composable
fun PlayerIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    normalIcon: Int,
    pressedIcon: Int,
    contentDescription: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = if (isPressed && enabled) pressedIcon else normalIcon),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

/** 이전 / 재생·일시정지 / 다음 — Music 앱과 같은 이미지와 비율(48 / 64 / 48dp)을 [scale] 배로 그린다. */
@Composable
fun PlayerButtons(
    isPlaying: Boolean,
    enabled: Boolean,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    Row(
        modifier = modifier.height((64 * scale).dp),
        horizontalArrangement = Arrangement.spacedBy((16 * scale).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerIconButton(
            onClick = onPrev, enabled = enabled,
            normalIcon = R.drawable.ic_player_back_n, pressedIcon = R.drawable.ic_player_back_p,
            contentDescription = "이전", size = (48 * scale).dp
        )
        PlayerIconButton(
            onClick = onPlayPause, enabled = enabled,
            normalIcon = if (isPlaying) R.drawable.ic_player_pause_n else R.drawable.ic_player_play_n,
            pressedIcon = if (isPlaying) R.drawable.ic_player_pause_p else R.drawable.ic_player_play_p,
            contentDescription = if (isPlaying) "일시정지" else "재생", size = (64 * scale).dp
        )
        PlayerIconButton(
            onClick = onNext, enabled = enabled,
            normalIcon = R.drawable.ic_player_forward_n, pressedIcon = R.drawable.ic_player_forward_p,
            contentDescription = "다음", size = (48 * scale).dp
        )
    }
}
