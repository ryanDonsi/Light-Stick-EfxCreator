package com.efxcreator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.efxcreator.R
import com.efxcreator.core.Loc
import com.efxcreator.model.GameMode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import androidx.compose.foundation.Canvas

// ─── Wand color constants ─────────────────────────────────────────────────────

private val WandOff    = Color(0xFF444466)
private val WandWhite  = Color(0xFFFFFFFF)
private val WandGreen  = Color(0xFF4ADE80)
private val WandRed    = Color(0xFFF87171)
private val WandBlue   = Color(0xFF60A5FA)
private val WandGrip   = Color(0xFF23233A)
private val WandNeck   = Color(0xFF2A2A4A)
private val WandStripe = Color(0xFF33334A)

private fun mode1Steps() = listOf(Loc.str(R.string.game_tut_1), "② LED ON", Loc.str(R.string.game_tut_2), Loc.str(R.string.game_tut_3), Loc.str(R.string.game_tut_4))
private fun mode2Steps() = listOf("① ON", Loc.str(R.string.game_tut_5), "③ OFF", Loc.str(R.string.game_tut_6), Loc.str(R.string.game_tut_7))
private fun mode3Steps() = listOf(Loc.str(R.string.game_tut_8), Loc.str(R.string.game_tut_9), Loc.str(R.string.game_tut_10), Loc.str(R.string.game_tut_11), Loc.str(R.string.game_tut_12))
private fun mode4Steps() = listOf(Loc.str(R.string.game_tut_13), Loc.str(R.string.game_tut_14), Loc.str(R.string.game_tut_15), Loc.str(R.string.game_tut_16), Loc.str(R.string.game_tut_12))

private val StageBackground = Color(0xFF0D0D1A)
// 어두운 무대 배경 위의 글자 색 (앱 테마와 무관하게 읽혀야 한다)
private val StageText = Color(0xFFB8B8CC)
private val StageSubText = Color(0xFF8888A0)
private val OverlayBackground = Color(0xCC0A0A1A)

// ─── Entry point ─────────────────────────────────────────────────────────────

@Composable
fun GameTutorialDialog(mode: GameMode, onDismiss: () -> Unit) {
    var playKey by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                // 태블릿처럼 넓은 화면에서는 폰 대화상자 폭 정도로 제한하고 가운데에 둔다 (Light-Stick-Music 과 같은 규칙)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 420.dp)
                .wrapContentHeight()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {

                    // ── Title bar ──────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = Loc.str(R.string.game_tut_17, mode.label),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = Loc.str(R.string.game_tut_18),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp
                    )

                    // ── Tutorial content (keyed for replay restart) ────────
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        key(playKey) {
                            when (mode) {
                                GameMode.SPEED_REACTION ->
                                    SpeedReactionTutorial(onReplay = { playKey++ })
                                GameMode.TEMPO ->
                                    TempoTutorial(onReplay = { playKey++ })
                                GameMode.TEAM_BATTLE ->
                                    TeamBattleTutorial(onReplay = { playKey++ })
                                GameMode.MANUAL_TEAM ->
                                    ManualTeamTutorial(onReplay = { playKey++ })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Step progress bar ────────────────────────────────────────────────────────

@Composable
private fun TutorialStepBar(
    steps: List<String>,
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(IntrinsicSize.Max)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
    ) {
        steps.forEachIndexed { i, step ->
            if (i > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(colors.outline)
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        when {
                            i < currentStep -> Color(0xFF1A3A1A)
                            i == currentStep -> colors.primary.copy(alpha = 0.18f)
                            else -> Color.Transparent
                        }
                    )
                    .padding(vertical = 7.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = step,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        i < currentStep -> WandGreen
                        i == currentStep -> colors.primary
                        else -> colors.outline
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─── Wand shape ───────────────────────────────────────────────────────────────

@Composable
private fun TutorialWand(
    headColor: Color,
    label: String = "",
    score: Int = -1,
    rotation: Float = 0f
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8888AA)
            )
        }
        Canvas(
            modifier = Modifier
                .size(width = 24.dp, height = 76.dp)
                .graphicsLayer { rotationZ = rotation }
        ) {
            val w = size.width
            val headR = w * 0.44f
            val cx = w / 2f
            val headCy = headR + 1f

            if (headColor != WandOff) {
                drawCircle(
                    color = headColor.copy(alpha = 0.22f),
                    radius = headR + 5f,
                    center = Offset(cx, headCy)
                )
            }
            drawCircle(headColor, radius = headR, center = Offset(cx, headCy))
            drawCircle(
                color = if (headColor == WandWhite) Color.White.copy(0.65f)
                        else Color.White.copy(0.18f),
                radius = headR * 0.28f,
                center = Offset(cx - headR * 0.28f, headCy - headR * 0.28f)
            )
            val neckTop = headCy + headR
            drawRect(
                WandNeck,
                topLeft = Offset(cx - w * 0.09f, neckTop),
                size = Size(w * 0.18f, w * 0.18f)
            )
            val gripTop = neckTop + w * 0.18f
            drawRoundRect(
                WandGrip,
                topLeft = Offset(cx - w * 0.14f, gripTop),
                size = Size(w * 0.28f, size.height - gripTop),
                cornerRadius = CornerRadius(w * 0.14f)
            )
            val stripeY = gripTop + (size.height - gripTop) * 0.38f
            drawRoundRect(
                WandStripe,
                topLeft = Offset(cx - w * 0.14f, stripeY),
                size = Size(w * 0.28f, (size.height - gripTop) * 0.10f),
                cornerRadius = CornerRadius(w * 0.10f)
            )
        }
        if (score >= 0) {
            val winner = score >= 5
            Box(
                modifier = Modifier
                    .background(
                        if (winner) Color(0xFF1A3A1A) else Color(0xFF2A2A3E),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = Loc.str(R.string.game_tut_19, score),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (winner) WandGreen else Color(0xFF8888AA)
                )
            }
        }
    }
}

// ─── Legend row item ──────────────────────────────────────────────────────────

@Composable
private fun LegendItem(color: Color, label: String, bordered: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
                .then(
                    if (bordered)
                        Modifier.border(1.dp, Color(0xFF666666), CircleShape)
                    else Modifier
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

// ─── Replay button ────────────────────────────────────────────────────────────

@Composable
private fun ReplayButton(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.primaryContainer)
            .clickable { onClick() }
            .padding(horizontal = 28.dp, vertical = 11.dp)
    ) {
        Text(
            text = Loc.str(R.string.game_tut_20),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onPrimary
        )
    }
}

// ─── Mode 1: 개인전(Speed) ──────────────────────────────────────────────────

@Composable
private fun SpeedReactionTutorial(onReplay: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val labels = listOf("A", "B", "C")

    val headColors = remember { mutableStateListOf(WandOff, WandOff, WandOff) }
    val scores     = remember { mutableStateListOf(0, 0, 0) }
    val rotations  = remember { mutableStateListOf(0f, 0f, 0f) }
    var step       by remember { mutableIntStateOf(0) }
    var message    by remember { mutableStateOf(Loc.str(R.string.game_tut_21)) }
    var subMsg     by remember { mutableStateOf("") }
    var winnerText by remember { mutableStateOf("") }
    var finished   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        val sc = mutableListOf(0, 0, 0)
        while (true) {
            step = 0
            headColors[0] = WandOff; headColors[1] = WandOff; headColors[2] = WandOff
            message = Loc.str(R.string.game_tut_22); subMsg = ""
            delay(430 + Random.nextLong(715))

            step = 1
            headColors[0] = WandWhite; headColors[1] = WandWhite; headColors[2] = WandWhite
            message = Loc.str(R.string.game_tut_23); subMsg = ""
            val wi = Random.nextInt(3)
            delay(430 + Random.nextLong(715))

            step = 2
            launch {
                listOf(-8f, 8f, -6f, 6f, -3f, 3f, 0f).forEach { r ->
                    rotations[wi] = r; delay(80)
                }
            }
            delay(545)

            step = 3
            sc[wi]++; scores[wi] = sc[wi]; headColors[wi] = WandGreen
            message = Loc.str(R.string.game_tut_24, labels[wi], sc[wi]); subMsg = ""

            if (sc[wi] >= 5) {
                step = 4
                winnerText = Loc.str(R.string.game_tut_25, labels[wi])
                message = winnerText; subMsg = Loc.str(R.string.game_tut_26)
                finished = true; break
            }
            delay(1575)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = Loc.str(R.string.game_tut_27),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        TutorialStepBar(mode1Steps(), step, Modifier.fillMaxWidth())

        // Stage + overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StageBackground)
                .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    labels.indices.forEach { i ->
                        TutorialWand(headColors[i], labels[i], scores[i], rotations[i])
                    }
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = StageText,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = StageSubText,
                    textAlign = TextAlign.Center
                )
            }
            Column(modifier = Modifier.matchParentSize()) {
                AnimatedVisibility(
                    visible = finished,
                    enter = fadeIn(tween(500)),
                    exit = fadeOut(tween(285)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(OverlayBackground, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = winnerText,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            ReplayButton(onReplay)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            LegendItem(WandOff, Loc.str(R.string.game_tut_28))
            LegendItem(WandWhite, "LED ON", bordered = true)
            LegendItem(WandGreen, Loc.str(R.string.game_tut_29))
        }
    }
}

// ─── Mode 2: 개인전(Tempo) ────────────────────────────────────────────────────

@Composable
private fun TempoTutorial(onReplay: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    var headColor  by remember { mutableStateOf(WandOff) }
    var rotation   by remember { mutableFloatStateOf(0f) }
    var streak     by remember { mutableIntStateOf(0) }
    var step       by remember { mutableIntStateOf(0) }
    var message    by remember { mutableStateOf(Loc.str(R.string.game_tut_30)) }
    var finished   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(715)
        var stk = 0
        while (true) {
            step = 0; headColor = WandWhite; message = Loc.str(R.string.game_tut_23)
            delay(970)

            val hit = Random.nextFloat() > 0.22f
            step = 1
            launch {
                listOf(-8f, 8f, -6f, 6f, -3f, 3f, 0f).forEach { r ->
                    rotation = r; delay(80)
                }
            }
            delay(455)

            if (hit) {
                stk++; streak = stk; headColor = WandGreen
                if (stk >= 5) {
                    step = 4; message = Loc.str(R.string.game_tut_31)
                    finished = true; break
                }
                step = 3; message = Loc.str(R.string.game_tut_32, stk)
                delay(715)
                step = 2; headColor = WandOff; message = "LED OFF"
                delay(1000)
            } else {
                stk = 0; streak = 0; headColor = WandRed; message = Loc.str(R.string.game_tut_33)
                delay(970)
                step = 2; headColor = WandOff
                delay(1000)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = Loc.str(R.string.game_tut_34),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        TutorialStepBar(mode2Steps(), step, Modifier.fillMaxWidth())

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StageBackground)
                .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TutorialWand(headColor, "W", rotation = rotation)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = StageText,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = Loc.str(R.string.game_tut_35, streak),
                    style = MaterialTheme.typography.bodySmall,
                    color = StageSubText,
                    textAlign = TextAlign.Center
                )
            }
            Column(modifier = Modifier.matchParentSize()) {
                AnimatedVisibility(
                    visible = finished,
                    enter = fadeIn(tween(500)),
                    exit = fadeOut(tween(285)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(OverlayBackground, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = Loc.str(R.string.game_tut_36),
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            ReplayButton(onReplay)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            LegendItem(WandOff, "OFF")
            LegendItem(WandWhite, Loc.str(R.string.game_tut_37), bordered = true)
            LegendItem(WandRed, "MISS")
            LegendItem(WandGreen, Loc.str(R.string.game_tut_29))
        }
    }
}

// ─── Mode 3: 팀전(Random) ──────────────────────────────────────────────────────

@Composable
private fun TeamBattleTutorial(onReplay: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    val redColors    = remember { mutableStateListOf(WandOff, WandOff) }
    val blueColors   = remember { mutableStateListOf(WandOff, WandOff) }
    val redRots      = remember { mutableStateListOf(0f, 0f) }
    val blueRots     = remember { mutableStateListOf(0f, 0f) }
    var redScore     by remember { mutableIntStateOf(0) }
    var blueScore    by remember { mutableIntStateOf(0) }
    var step         by remember { mutableIntStateOf(0) }
    var message      by remember { mutableStateOf(Loc.str(R.string.game_tut_21)) }
    var subMsg       by remember { mutableStateOf(Loc.str(R.string.game_tut_38)) }
    var winnerText   by remember { mutableStateOf("") }
    var finished     by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(715)
        step = 0
        redColors[0] = WandRed;  redColors[1] = WandRed
        blueColors[0] = WandBlue; blueColors[1] = WandBlue
        message = Loc.str(R.string.game_tut_39)
        delay(1575)

        var rs = 0; var bs = 0
        for (rnd in 1..5) {
            step = 1; subMsg = Loc.str(R.string.game_tut_40, rnd)
            redColors[0] = WandOff; redColors[1] = WandOff
            blueColors[0] = WandOff; blueColors[1] = WandOff
            delay(1145)

            val sigRed = Random.nextBoolean()
            if (sigRed) {
                redColors[0] = WandRed; redColors[1] = WandRed
                message = Loc.str(R.string.game_tut_41)
            } else {
                blueColors[0] = WandBlue; blueColors[1] = WandBlue
                message = Loc.str(R.string.game_tut_42)
            }
            delay(1070)

            step = 2
            val hit = Random.nextFloat() > 0.15f
            if (sigRed) {
                launch {
                    listOf(-8f, 8f, -6f, 6f, -3f, 3f, 0f).forEach { r ->
                        redRots[0] = r; redRots[1] = r; delay(80)
                    }
                }
                delay(645)
                if (hit) {
                    rs++; redScore = rs
                    redColors[0] = WandGreen; redColors[1] = WandGreen
                    message = Loc.str(R.string.game_tut_43)
                } else {
                    redColors[0] = WandOff; redColors[1] = WandOff
                    message = Loc.str(R.string.game_tut_44)
                }
                blueColors[0] = WandOff; blueColors[1] = WandOff
            } else {
                launch {
                    listOf(-8f, 8f, -6f, 6f, -3f, 3f, 0f).forEach { r ->
                        blueRots[0] = r; blueRots[1] = r; delay(80)
                    }
                }
                delay(645)
                if (hit) {
                    bs++; blueScore = bs
                    blueColors[0] = WandGreen; blueColors[1] = WandGreen
                    message = Loc.str(R.string.game_tut_45)
                } else {
                    blueColors[0] = WandOff; blueColors[1] = WandOff
                    message = Loc.str(R.string.game_tut_46)
                }
                redColors[0] = WandOff; redColors[1] = WandOff
            }
            step = 3
            delay(1285)
        }

        step = 4
        winnerText = when {
            rs > bs -> Loc.str(R.string.game_tut_47)
            bs > rs -> Loc.str(R.string.game_tut_48)
            else    -> Loc.str(R.string.game_tut_49)
        }
        message = winnerText
        subMsg = Loc.str(R.string.game_tut_50, rs, bs)
        finished = true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = Loc.str(R.string.game_tut_51),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        TutorialStepBar(mode3Steps(), step, Modifier.fillMaxWidth())

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StageBackground)
                .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            Loc.str(R.string.game_tut_52),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = WandRed
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TutorialWand(redColors[0], "R1", rotation = redRots[0])
                            TutorialWand(redColors[1], "R2", rotation = redRots[1])
                        }
                        Text(
                            Loc.str(R.string.game_tut_19, redScore),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = WandRed
                        )
                    }
                    Text(
                        "vs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF444466),
                        modifier = Modifier.padding(top = 32.dp)
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            Loc.str(R.string.game_tut_53),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = WandBlue
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TutorialWand(blueColors[0], "B1", rotation = blueRots[0])
                            TutorialWand(blueColors[1], "B2", rotation = blueRots[1])
                        }
                        Text(
                            Loc.str(R.string.game_tut_19, blueScore),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = WandBlue
                        )
                    }
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = StageText,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = StageSubText,
                    textAlign = TextAlign.Center
                )
            }
            Column(modifier = Modifier.matchParentSize()) {
                AnimatedVisibility(
                    visible = finished,
                    enter = fadeIn(tween(500)),
                    exit = fadeOut(tween(285)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(OverlayBackground, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = winnerText,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            ReplayButton(onReplay)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            LegendItem(WandOff, Loc.str(R.string.game_tut_28))
            LegendItem(WandRed, Loc.str(R.string.game_tut_54))
            LegendItem(WandBlue, Loc.str(R.string.game_tut_55))
            LegendItem(WandGreen, Loc.str(R.string.game_tut_29))
        }
    }
}

// ─── Mode 4: 팀전(Speed) ────────────────────────────────────────────────────

@Composable
private fun ManualTeamTutorial(onReplay: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val totalRounds = 3

    val redColors   = remember { mutableStateListOf(WandOff, WandOff) }
    val blueColors  = remember { mutableStateListOf(WandOff, WandOff) }
    val redChecked  = remember { mutableStateListOf(false, false) }
    val blueChecked = remember { mutableStateListOf(false, false) }
    val redRots     = remember { mutableStateListOf(0f, 0f) }
    val blueRots    = remember { mutableStateListOf(0f, 0f) }
    var redScore    by remember { mutableIntStateOf(0) }
    var blueScore   by remember { mutableIntStateOf(0) }
    var step        by remember { mutableIntStateOf(0) }
    var message     by remember { mutableStateOf(Loc.str(R.string.game_tut_21)) }
    var subMsg      by remember { mutableStateOf("") }
    var winnerText  by remember { mutableStateOf("") }
    var finished    by remember { mutableStateOf(false) }

    suspend fun blink(target: MutableList<Color>, onColor: Color, repeats: Int = 3, intervalMs: Long = 180) {
        repeat(repeats) {
            target[0] = onColor; target[1] = onColor
            delay(intervalMs)
            target[0] = WandOff; target[1] = WandOff
            delay(intervalMs)
        }
    }

    suspend fun shakeBoth() {
        // 흔드는 속도(딜레이)는 그대로 두고, 좌우 왕복 횟수만 늘려 더 오래/더 많이 흔들게 함
        val wiggle = buildList {
            repeat(9) { add(-8f); add(8f) }
            add(-4f); add(4f); add(0f)
        }
        coroutineScope {
            launch {
                wiggle.forEach { r -> redRots[0] = r; redRots[1] = r; delay(70) }
            }
            launch {
                wiggle.forEach { r -> blueRots[0] = r; blueRots[1] = r; delay(70) }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(430)

        // ① 홍팀 배정: 블링크 → 버튼1 확정 (충분히 읽을 수 있도록 느리게 진행)
        step = 0
        message = Loc.str(R.string.game_tut_56); subMsg = Loc.str(R.string.game_tut_57)
        delay(650)
        blink(redColors, WandRed, repeats = 5, intervalMs = 230)
        redColors[0] = WandRed; redColors[1] = WandRed
        redChecked[0] = true; redChecked[1] = true
        message = Loc.str(R.string.game_tut_58); subMsg = Loc.str(R.string.game_tut_59)
        delay(1700)

        // ② 청팀 배정: 블링크 → 버튼1 확정 (충분히 읽을 수 있도록 느리게 진행)
        step = 1
        message = Loc.str(R.string.game_tut_60); subMsg = Loc.str(R.string.game_tut_61)
        delay(650)
        blink(blueColors, WandBlue, repeats = 5, intervalMs = 230)
        blueColors[0] = WandBlue; blueColors[1] = WandBlue
        blueChecked[0] = true; blueChecked[1] = true
        message = Loc.str(R.string.game_tut_62); subMsg = Loc.str(R.string.game_tut_59)
        delay(1700)

        // ③ READY: 팀 색 2회 점멸 후 자동 시작
        step = 2
        message = Loc.str(R.string.game_tut_63); subMsg = Loc.str(R.string.game_tut_64)
        repeat(2) {
            redColors[0] = WandOff; redColors[1] = WandOff
            blueColors[0] = WandOff; blueColors[1] = WandOff
            delay(200)
            redColors[0] = WandRed; redColors[1] = WandRed
            blueColors[0] = WandBlue; blueColors[1] = WandBlue
            delay(200)
        }
        delay(715)

        // ④ 라운드 자동 반복: 라운드 1 → 라운드 2 → 라운드 3(마지막 라운드/결승전)
        var rs = 0; var bs = 0
        for (rnd in 1..totalRounds) {
            val isFinal = rnd == totalRounds
            step = 3

            message = when (rnd) {
                1    -> Loc.str(R.string.game_tut_65)
                else -> if (isFinal) Loc.str(R.string.game_tut_66) else Loc.str(R.string.game_tut_67)
            }
            subMsg = when {
                rnd == 1 -> Loc.str(R.string.game_tut_68, totalRounds)
                isFinal  -> Loc.str(R.string.game_tut_69, rnd, totalRounds)
                else     -> Loc.str(R.string.game_tut_70, rnd, totalRounds)
            }
            shakeBoth()
            delay(600)

            val redGain = Random.nextInt(3, 8)
            val blueGain = Random.nextInt(3, 8)
            rs += redGain; bs += blueGain
            redScore = rs; blueScore = bs
            message = Loc.str(R.string.game_tut_71, redGain, blueGain)
            delay(900)

            redColors[0] = WandOff; redColors[1] = WandOff
            blueColors[0] = WandOff; blueColors[1] = WandOff

            if (isFinal) {
                // 마지막 라운드는 휴식 없이 곧바로 최종 결과로 이어짐
                message = Loc.str(R.string.game_tut_72, rnd)
                subMsg = ""
                delay(600)
            } else {
                message = Loc.str(R.string.game_tut_73, rnd)
                subMsg = when {
                    rs > bs -> Loc.str(R.string.game_tut_74)
                    bs > rs -> Loc.str(R.string.game_tut_75)
                    else    -> Loc.str(R.string.game_tut_76)
                }
                delay(700)
                redColors[0] = WandRed; redColors[1] = WandRed
                blueColors[0] = WandBlue; blueColors[1] = WandBlue
            }
        }

        // ⑤ 결과 + 우승팀 점멸
        step = 4
        winnerText = when {
            rs > bs -> Loc.str(R.string.game_tut_47)
            bs > rs -> Loc.str(R.string.game_tut_48)
            else    -> Loc.str(R.string.game_tut_49)
        }
        message = winnerText
        subMsg = Loc.str(R.string.game_tut_50, rs, bs)

        if (rs != bs) {
            val winnerIsRed = rs > bs
            repeat(3) {
                if (winnerIsRed) { redColors[0] = WandOff; redColors[1] = WandOff }
                else { blueColors[0] = WandOff; blueColors[1] = WandOff }
                delay(160)
                if (winnerIsRed) { redColors[0] = WandRed; redColors[1] = WandRed }
                else { blueColors[0] = WandBlue; blueColors[1] = WandBlue }
                delay(160)
            }
        }
        finished = true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = Loc.str(R.string.game_tut_77),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
        TutorialStepBar(mode4Steps(), step, Modifier.fillMaxWidth())

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StageBackground)
                .border(1.dp, colors.outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            Loc.str(R.string.game_tut_52),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = WandRed
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TutorialWand(redColors[0], if (redChecked[0]) "R1 ✓" else "R1", rotation = redRots[0])
                            TutorialWand(redColors[1], if (redChecked[1]) "R2 ✓" else "R2", rotation = redRots[1])
                        }
                        Text(
                            Loc.str(R.string.game_tut_19, redScore),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = WandRed
                        )
                    }
                    Text(
                        "vs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF444466),
                        modifier = Modifier.padding(top = 32.dp)
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            Loc.str(R.string.game_tut_53),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = WandBlue
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TutorialWand(blueColors[0], if (blueChecked[0]) "B1 ✓" else "B1", rotation = blueRots[0])
                            TutorialWand(blueColors[1], if (blueChecked[1]) "B2 ✓" else "B2", rotation = blueRots[1])
                        }
                        Text(
                            Loc.str(R.string.game_tut_19, blueScore),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = WandBlue
                        )
                    }
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = StageText,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = StageSubText,
                    textAlign = TextAlign.Center
                )
            }
            Column(modifier = Modifier.matchParentSize()) {
                AnimatedVisibility(
                    visible = finished,
                    enter = fadeIn(tween(500)),
                    exit = fadeOut(tween(285)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(OverlayBackground, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = winnerText,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            ReplayButton(onReplay)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            LegendItem(WandOff, Loc.str(R.string.game_tut_78))
            LegendItem(WandRed, Loc.str(R.string.game_tut_79))
            LegendItem(WandBlue, Loc.str(R.string.game_tut_80))
        }
    }
}
