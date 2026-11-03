package com.efxcreator.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.efxcreator.R
import com.efxcreator.core.Loc
import com.efxcreator.game.GameBleManager
import com.efxcreator.model.GameDifficulty
import com.efxcreator.model.GameMode
import com.efxcreator.model.GameResultSummary
import com.efxcreator.model.GameState
import com.efxcreator.model.Team
import com.efxcreator.model.TeamWinner
import com.efxcreator.model.WandResult
import com.efxcreator.viewmodel.GameViewModel

private val BLUE_TEAM = Color(0xFF448AFF)

/** 설정 > 게임: Light-Stick-Music 의 게임 모드 (개인전 2종, 팀전 2종). 연결된 응원봉이 있어야 시작할 수 있다. */
@Composable
fun GameSettingsContent(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val selectedMode by viewModel.selectedMode.collectAsState()
    val selectedDifficulty by viewModel.selectedDifficulty.collectAsState()
    val selectedRounds by viewModel.selectedRounds.collectAsState()
    val gameState by viewModel.gameState.collectAsState()
    val bleState by viewModel.bleConnectionState.collectAsState()
    val isGameModeSupported by viewModel.isGameModeSupported.collectAsState()
    val countdownSeconds by viewModel.countdownSeconds.collectAsState()
    val partialResults by viewModel.partialResults.collectAsState()
    val playingElapsedSeconds by viewModel.playingElapsedSeconds.collectAsState()
    val playingMaxSeconds by viewModel.playingMaxSeconds.collectAsState()

    var showStopDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.connectIfNeeded() }
    // 게임 도중 다른 메뉴로 가거나 화면을 떠나면 기기와 BGM 이 계속 돌지 않도록 중지한다
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { viewModel.stopIfRunning() }
    }
    LaunchedEffect(gameState) {
        (gameState as? GameState.Error)?.let { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
    }

    Column(modifier = modifier) {
        GameStatusBanner(
            bleState = bleState,
            gameState = gameState,
            elapsedSeconds = playingElapsedSeconds,
            maxSeconds = playingMaxSeconds,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        AnimatedContent(
            targetState = gameState,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically { it / 6 })
                    .togetherWith(fadeOut(tween(200)) + slideOutVertically { -it / 6 })
            },
            label = "game_state"
        ) { state ->
            when (state) {
                is GameState.Idle, is GameState.Error -> ModeSelectionContent(
                    selectedMode = selectedMode,
                    selectedDifficulty = selectedDifficulty,
                    selectedRounds = selectedRounds,
                    onModeSelect = viewModel::selectMode,
                    onDifficultySelect = viewModel::selectDifficulty,
                    onRoundsSelect = viewModel::selectRounds,
                    onStart = viewModel::startGame,
                    bleConnected = bleState is GameBleManager.ConnectionState.Connected,
                    isGameModeSupported = isGameModeSupported
                )
                is GameState.Ready -> CountdownContent(countdownSeconds)
                is GameState.TeamAssigning -> TeamAssigningContent(
                    state = state,
                    onConfirmClick = viewModel::confirmTeamAssignment,
                    onStopClick = { showStopDialog = true }
                )
                is GameState.Playing -> PlayingContent(
                    mode = selectedMode,
                    partialResults = partialResults,
                    onStopClick = { showStopDialog = true }
                )
                is GameState.Finished -> ResultContent(
                    summary = state.summary,
                    onPlayAgain = viewModel::resetToIdle
                )
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text(Loc.str(R.string.game_stop_title)) },
            text = { Text(Loc.str(R.string.game_stop_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showStopDialog = false
                    viewModel.stopGame()
                }) { Text(Loc.str(R.string.game_stop), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showStopDialog = false }) { Text(Loc.str(R.string.game_cancel)) } }
        )
    }
}

@Composable
private fun ModeSelectionContent(
    selectedMode: GameMode?,
    selectedDifficulty: GameDifficulty,
    selectedRounds: Int,
    bleConnected: Boolean,
    isGameModeSupported: Boolean,
    onModeSelect: (GameMode) -> Unit,
    onDifficultySelect: (GameDifficulty) -> Unit,
    onRoundsSelect: (Int) -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            Loc.str(R.string.game_select_mode),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GameMode.entries.forEach { mode ->
            Column {
                GameModeCard(mode = mode, selected = selectedMode == mode, onClick = { onModeSelect(mode) })
                AnimatedVisibility(
                    visible = selectedMode == mode,
                    enter = fadeIn(tween(200)) + expandVertically(animationSpec = tween(200)),
                    exit = fadeOut(tween(150)) + shrinkVertically(animationSpec = tween(150))
                ) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            Loc.str(if (mode == GameMode.MANUAL_TEAM) R.string.game_measure_time else R.string.game_difficulty),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ChoiceRow(
                            options = GameDifficulty.entries,
                            selected = selectedDifficulty,
                            labelOf = { it.label },
                            onSelect = onDifficultySelect
                        )
                        if (mode == GameMode.MANUAL_TEAM) {
                            Text(
                                Loc.str(R.string.game_rounds_label),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ChoiceRow(
                                options = listOf(1, 2, 3),
                                selected = selectedRounds,
                                labelOf = { Loc.str(R.string.game_rounds_n, it) },
                                onSelect = onRoundsSelect
                            )
                        }
                    }
                }
            }
        }

        if (bleConnected && !isGameModeSupported) {
            Text(
                Loc.str(R.string.game_unsupported),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = onStart,
            enabled = selectedMode != null && bleConnected && isGameModeSupported,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(Loc.str(R.string.game_start)) }
    }
}

@Composable
private fun GameStatusBanner(
    bleState: GameBleManager.ConnectionState,
    gameState: GameState,
    elapsedSeconds: Int,
    maxSeconds: Int,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isPlaying = gameState is GameState.Playing

    val bgColor = when (bleState) {
        is GameBleManager.ConnectionState.Connected -> scheme.tertiaryContainer
        is GameBleManager.ConnectionState.Connecting -> scheme.secondaryContainer
        is GameBleManager.ConnectionState.Error -> scheme.errorContainer
        else -> scheme.surfaceContainerHigh
    }
    val dotColor = when (bleState) {
        is GameBleManager.ConnectionState.Connected -> Color(0xFF4CAF50)
        is GameBleManager.ConnectionState.Connecting -> scheme.secondary
        is GameBleManager.ConnectionState.Error -> scheme.error
        else -> scheme.outline
    }
    val labelText = when (bleState) {
        is GameBleManager.ConnectionState.Connected -> Loc.str(R.string.game_status_connected)
        is GameBleManager.ConnectionState.Connecting -> Loc.str(R.string.game_status_connecting)
        is GameBleManager.ConnectionState.Error -> bleState.message
        else -> Loc.str(R.string.game_status_disconnected)
    }

    val remaining = if (maxSeconds > 0) (maxSeconds - elapsedSeconds).coerceAtLeast(0) else 0
    val progress = if (maxSeconds > 0) remaining.toFloat() / maxSeconds else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(500), label = "timer_progress")

    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(bgColor)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(8.dp).background(dotColor, CircleShape))
            Text(labelText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (bleState is GameBleManager.ConnectionState.Connecting) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            if (isPlaying) {
                val m = remaining / 60
                val s = remaining % 60
                Text(
                    if (m > 0) Loc.str(R.string.game_time_ms, m, s) else Loc.str(R.string.game_time_s, s),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (remaining <= 10) scheme.error else scheme.primary
                )
            }
        }
        if (isPlaying && maxSeconds > 0) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                trackColor = scheme.onSurface.copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun GameModeCard(mode: GameMode, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val icon = when (mode) {
        GameMode.SPEED_REACTION -> Icons.Filled.Bolt
        GameMode.TEMPO -> Icons.Filled.MusicNote
        GameMode.TEAM_BATTLE -> Icons.Filled.Groups
        GameMode.MANUAL_TEAM -> Icons.Filled.WavingHand
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                icon, contentDescription = null,
                tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(mode.label, style = MaterialTheme.typography.titleSmall)
                Text(mode.desc, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text(
                    mode.winCondition,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) scheme.primary else scheme.outline
                )
            }
        }
    }
}

/** 난이도 / 라운드 수처럼 한 줄에서 하나를 고르는 선택 줄 */
@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, labelOf: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Card(
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) scheme.secondaryContainer else scheme.surfaceContainerHigh
                ),
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) scheme.primary else scheme.outlineVariant)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        labelOf(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownContent(countdownSeconds: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(Loc.str(R.string.game_preparing), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedContent(
                targetState = countdownSeconds,
                transitionSpec = { fadeIn(tween(200)).togetherWith(fadeOut(tween(150))) },
                label = "countdown"
            ) { sec ->
                Text(
                    if (sec > 0) "$sec" else Loc.str(R.string.game_go),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(Loc.str(R.string.game_wand_starting), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun TeamAssigningContent(state: GameState.TeamAssigning, onConfirmClick: () -> Unit, onStopClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val teamColor = if (state.team == Team.RED) scheme.error else BLUE_TEAM
    // 0=버튼 입력 대기  1=END 전송함, 집계 Notify 응답 대기  2=확정 인원수 수신함
    val phase = when {
        state.confirmedCount != null -> 2
        state.endSent -> 1
        else -> 0
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(teamColor.copy(alpha = 0.18f), CircleShape)
                    .border(2.dp, teamColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = teamColor, modifier = Modifier.size(44.dp))
            }
            Text(
                Loc.str(R.string.game_team_assigning, state.team.label),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn(tween(200)).togetherWith(fadeOut(tween(150))) },
                label = "team_assign_status"
            ) { p ->
                when (p) {
                    2 -> Text(
                        Loc.str(R.string.game_team_confirmed, state.team.label, state.confirmedCount ?: 0),
                        style = MaterialTheme.typography.titleMedium, color = teamColor, textAlign = TextAlign.Center
                    )
                    1 -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = teamColor)
                        Text(
                            Loc.str(R.string.game_waiting_count),
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center
                        )
                    }
                    else -> Text(
                        Loc.str(R.string.game_assign_hint, state.team.label),
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center
                    )
                }
            }
            Button(
                onClick = onConfirmClick,
                enabled = phase == 0,
                modifier = Modifier.width(220.dp).height(52.dp)
            ) { Text(if (phase >= 1) Loc.str(R.string.game_checking) else Loc.str(R.string.game_assign_end, state.team.label)) }
            TextButton(onClick = onStopClick) {
                Text(Loc.str(R.string.game_stop_title), style = MaterialTheme.typography.labelLarge, color = scheme.error)
            }
        }
    }
}

@Composable
private fun PlayingContent(mode: GameMode?, partialResults: List<WandResult>, onStopClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val showRanking = (mode == GameMode.SPEED_REACTION || mode == GameMode.TEMPO) && partialResults.isNotEmpty()

    if (showRanking && mode != null) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Text(Loc.str(R.string.game_playing), style = MaterialTheme.typography.titleLarge, color = scheme.primary)
                Text(mode.label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
            Text(Loc.str(R.string.game_live_rank), style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                partialResults.forEachIndexed { index, result -> RankRow(rank = index + 1, result = result) }
            }
            Button(
                onClick = onStopClick,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text(Loc.str(R.string.game_stop_title)) }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(56.dp), strokeWidth = 4.dp)
                Text(Loc.str(R.string.game_playing), style = MaterialTheme.typography.headlineLarge, color = scheme.primary)
                if (mode != null) {
                    Text(mode.label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onStopClick,
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                    modifier = Modifier.width(200.dp).height(48.dp)
                ) { Text(Loc.str(R.string.game_stop_title)) }
            }
        }
    }
}

@Composable
private fun ResultContent(summary: GameResultSummary, onPlayAgain: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (summary.mode) {
            GameMode.SPEED_REACTION, GameMode.TEMPO -> SoloResultContent(summary)
            GameMode.TEAM_BATTLE, GameMode.MANUAL_TEAM -> TeamResultContent(summary)
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(Loc.str(R.string.game_play_again))
        }
    }
}

@Composable
private fun SoloResultContent(summary: GameResultSummary) {
    var showAllResults by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val winner = summary.soloWinner

    if (winner != null) {
        WinnerBanner(
            label = Loc.str(R.string.game_winner),
            wandId = Loc.str(R.string.game_wand, winner.wandId.toString(16).uppercase().padStart(4, '0')),
            score = Loc.str(R.string.game_points, winner.redScore)
        )
    } else {
        NoWinnerBanner()
    }

    Text(
        Loc.str(R.string.game_participants, summary.totalWandCount),
        style = MaterialTheme.typography.bodySmall, color = scheme.outline,
        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End
    )
    Text(Loc.str(R.string.game_ranking), style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)

    if (summary.rankedResults.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
            Text(
                Loc.str(R.string.game_no_scores),
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
        }
    } else {
        val displayed = if (showAllResults) summary.rankedResults else summary.rankedResults.take(3)
        displayed.forEachIndexed { index, result -> RankRow(rank = index + 1, result = result) }
        if (!showAllResults && summary.rankedResults.size > 3) {
            TextButton(onClick = { showAllResults = true }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                Text(Loc.str(R.string.game_show_all), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun TeamResultContent(summary: GameResultSummary) {
    val scheme = MaterialTheme.colorScheme
    val winnerLabel = when (summary.teamWinner) {
        TeamWinner.RED -> Loc.str(R.string.game_red_wins)
        TeamWinner.BLUE -> Loc.str(R.string.game_blue_wins)
        TeamWinner.DRAW -> Loc.str(R.string.game_draw)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.85f), scheme.primaryContainer)))
            .padding(vertical = 28.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("🏆", fontSize = 44.sp)
            Text(winnerLabel, style = MaterialTheme.typography.headlineSmall, color = scheme.onPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TeamScoreColumn(Loc.str(R.string.game_red), summary.totalRedScore)
                Box(Modifier.width(1.dp).height(72.dp).background(scheme.onPrimary.copy(alpha = 0.3f)))
                TeamScoreColumn(Loc.str(R.string.game_blue), summary.totalBlueScore)
            }
        }
    }
    Text(
        Loc.str(R.string.game_participants, summary.totalWandCount),
        style = MaterialTheme.typography.bodySmall, color = scheme.outline,
        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TeamScoreBar(redScore = summary.totalRedScore, blueScore = summary.totalBlueScore)
    }
}

@Composable
private fun TeamScoreColumn(name: String, score: Int) {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = MaterialTheme.typography.labelLarge, color = onPrimary.copy(alpha = 0.7f))
        Text("$score", style = MaterialTheme.typography.displayMedium, color = onPrimary)
        Text(Loc.str(R.string.game_points_unit), style = MaterialTheme.typography.bodySmall, color = onPrimary.copy(alpha = 0.7f))
    }
}

@Composable
private fun NoWinnerBanner() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(vertical = 24.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⏱", fontSize = 36.sp)
            Text(Loc.str(R.string.game_time_over), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
            Text(Loc.str(R.string.game_no_winner), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WinnerBanner(label: String, wandId: String, score: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.85f), scheme.primaryContainer)))
            .padding(vertical = 24.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🏆", fontSize = 40.sp)
            Text(label, style = MaterialTheme.typography.headlineSmall, color = scheme.onPrimary)
            Text(wandId, style = MaterialTheme.typography.titleMedium, color = scheme.onPrimary.copy(alpha = 0.9f))
            Text(score, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = scheme.onPrimary)
        }
    }
}

@Composable
private fun TeamScoreBar(redScore: Int, blueScore: Int) {
    val total = (redScore + blueScore).coerceAtLeast(1)
    val redRatio by animateFloatAsState(targetValue = redScore.toFloat() / total, animationSpec = tween(800), label = "team_score_ratio")
    val red = MaterialTheme.colorScheme.error

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Loc.str(R.string.game_team_score_red, redScore), style = MaterialTheme.typography.labelLarge, color = red)
            Text(Loc.str(R.string.game_team_score_blue, blueScore), style = MaterialTheme.typography.labelLarge, color = BLUE_TEAM)
        }
        // 파란 막대 위에 빨간 막대를 비율만큼 겹쳐 그린다
        Box(modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(BLUE_TEAM)) {
            Box(Modifier.fillMaxWidth(redRatio.coerceIn(0f, 1f)).fillMaxHeight().background(red))
        }
    }
}

@Composable
private fun RankRow(rank: Int, result: WandResult) {
    val scheme = MaterialTheme.colorScheme
    val isWinner = rank == 1
    val medalColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> scheme.onSurfaceVariant
    }
    val wandHex = result.wandId.toString(16).uppercase().padStart(4, '0')
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = if (isWinner) scheme.secondaryContainer else scheme.surfaceContainerHigh),
        border = BorderStroke(if (isWinner) 2.dp else 1.dp, if (isWinner) scheme.primary else scheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                Loc.str(R.string.game_rank_n, rank),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = medalColor,
                modifier = Modifier.width(48.dp)
            )
            Text(Loc.str(R.string.game_wand, wandHex), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                Loc.str(R.string.game_points, result.redScore),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = scheme.primary
            )
        }
    }
}
