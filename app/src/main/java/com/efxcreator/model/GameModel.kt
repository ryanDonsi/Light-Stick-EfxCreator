package com.efxcreator.model

import androidx.annotation.StringRes
import com.efxcreator.R
import com.efxcreator.core.Loc
import com.lightstick.game.GameLevel as SdkGameLevel
import com.lightstick.game.GameMode as SdkGameMode

enum class GameMode(
    val subIndex: Int,
    @StringRes private val labelRes: Int,
    @StringRes private val descRes: Int,
    @StringRes private val winRes: Int,
    val defaultDifficulty: GameDifficulty = GameDifficulty.NORMAL
) {
    SPEED_REACTION(1, R.string.game_mode_speed, R.string.game_mode_speed_desc, R.string.game_mode_speed_win),
    TEMPO(2, R.string.game_mode_tempo, R.string.game_mode_tempo_desc, R.string.game_mode_tempo_win),
    TEAM_BATTLE(3, R.string.game_mode_team_random, R.string.game_mode_team_random_desc, R.string.game_mode_team_random_win, GameDifficulty.EASY),
    MANUAL_TEAM(4, R.string.game_mode_team_speed, R.string.game_mode_team_speed_desc, R.string.game_mode_team_speed_win);

    val label: String get() = Loc.str(labelRes)
    val desc: String get() = Loc.str(descRes)
    val winCondition: String get() = Loc.str(winRes)

    fun toSdkMode(): SdkGameMode = when (this) {
        SPEED_REACTION -> SdkGameMode.SPEED_REACTION
        TEMPO          -> SdkGameMode.TEMPO
        TEAM_BATTLE    -> SdkGameMode.TEAM_BATTLE
        MANUAL_TEAM    -> SdkGameMode.TEAM_SIMULTANEOUS
    }

    companion object {
        fun fromSdkMode(sdkMode: SdkGameMode): GameMode? =
            entries.find { it.toSdkMode() == sdkMode }
    }
}

enum class GameDifficulty(val level: Int, @StringRes private val labelRes: Int) {
    EASY(1, R.string.game_difficulty_easy),
    NORMAL(2, R.string.game_difficulty_normal),
    HARD(3, R.string.game_difficulty_hard);

    val label: String get() = Loc.str(labelRes)

    fun toSdkLevel(): SdkGameLevel = when (this) {
        EASY   -> SdkGameLevel.EASY
        NORMAL -> SdkGameLevel.NORMAL
        HARD   -> SdkGameLevel.HARD
    }

    /** Mode 4 라운드당 측정 시간(ms) — easy=8000 / normal=5000 / hard=3000 */
    val teamMeasureMs: Int
        get() = when (this) {
            EASY   -> 8000
            NORMAL -> 5000
            HARD   -> 3000
        }
}

/** Mode 4 수동 팀 배정 — teamId 는 프로토콜 값(0=RED/1=BLUE)과 동일 */
enum class Team(val teamId: Int, @StringRes private val labelRes: Int) {
    RED(0, R.string.game_team_red),
    BLUE(1, R.string.game_team_blue);

    val label: String get() = Loc.str(labelRes)
}

sealed class GameState {
    /** 기기 미연결 또는 게임 대기 */
    object Idle : GameState()
    /** READY 전송 완료, 응원봉 2초 카운트다운 중 */
    object Ready : GameState()
    /** 게임 진행 중 */
    object Playing : GameState()
    /**
     * 결과 수신 완료.
     * [isProvisional] 이 true 면 실제 결과 Notify 없이 클라이언트 타이머 만료로 잠정 마감한 상태
     * (팀 합산 모드에서 늦게 도착하는 진짜 결과로 교체될 수 있음).
     */
    data class Finished(val summary: GameResultSummary, val isProvisional: Boolean = false) : GameState()
    /** 오류 발생 */
    data class Error(val message: String) : GameState()
    /**
     * Mode 4 전용 — [team] 수동 배정 대기 중.
     * [endSent] 가 false 면 "배정 종료" 버튼 입력 대기, true 면 TEAM_ASSIGN_END 를 보내고 집계 Notify(cmd=8) 응답을 기다리는 중.
     * [confirmedCount] 는 집계 Notify 수신 후 확정 인원수.
     */
    data class TeamAssigning(
        val team: Team,
        val endSent: Boolean = false,
        val confirmedCount: Int? = null
    ) : GameState()
}

data class WandResult(
    /** 응원봉 고유 ID (MAC 하위 2바이트) */
    val wandId: Int,
    /** Red팀 점수 또는 개인 점수 */
    val redScore: Int,
    /** Blue팀 점수 (Mode1·2 에서는 항상 0) */
    val blueScore: Int,
    /** Notify 수신 시각 */
    val receivedAt: Long = System.currentTimeMillis()
)

data class GameResultSummary(
    val mode: GameMode,
    val wandResults: List<WandResult>,
    val totalWandCount: Int,
    /** Red팀 누적 점수 합계 */
    val totalRedScore: Int,
    /** Blue팀 누적 점수 합계 */
    val totalBlueScore: Int
) {
    /** Mode 1·2: 개인 우승자 (score==5, 빠른 수신 순) */
    val soloWinner: WandResult?
        get() = wandResults
            .filter { it.redScore == 5 && it.wandId != 0 && it.wandId != 0xFFFF }
            .minByOrNull { it.receivedAt }

    /** Mode 3·4: 팀 승자 */
    val teamWinner: TeamWinner
        get() = when {
            totalRedScore > totalBlueScore -> TeamWinner.RED
            totalBlueScore > totalRedScore -> TeamWinner.BLUE
            else -> TeamWinner.DRAW
        }

    /** Mode 1·2: 순위 (score 높은 순, 동점이면 수신 시각 빠른 순) */
    val rankedResults: List<WandResult>
        get() = wandResults
            .filter { it.wandId != 0 && it.wandId != 0xFFFF }
            .sortedWith(compareByDescending<WandResult> { it.redScore }
                .thenBy { it.receivedAt })
}

enum class TeamWinner { RED, BLUE, DRAW }
