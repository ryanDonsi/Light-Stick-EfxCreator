package com.efxcreator.game

import android.annotation.SuppressLint
import android.util.Log
import com.efxcreator.R
import com.efxcreator.core.Loc
import com.efxcreator.model.GameDifficulty
import com.efxcreator.model.GameMode
import com.efxcreator.model.Team
import com.lightstick.LSBluetooth
import com.lightstick.device.Device
import com.lightstick.game.GameCmd
import com.lightstick.game.GameResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 게임 모드 BLE 통신 관리자 (Light-Stick-Music 에서 이식).
 *
 * SDK [Device] 고수준 API 를 사용한다:
 * - [Device.sendGameCmd] → FF03 명령 전송 + (onResult 전달 시) FF04 Notify 구독 일괄 처리
 */
class GameBleManager {

    private val tag = "GameBleManager"

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting   : ConnectionState()
        object Connected    : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isGameModeSupported = MutableStateFlow(false)
    val isGameModeSupported: StateFlow<Boolean> = _isGameModeSupported.asStateFlow()

    private val _gameResultFlow = MutableSharedFlow<GameResult>(extraBufferCapacity = 32)
    val gameResultFlow: SharedFlow<GameResult> = _gameResultFlow.asSharedFlow()

    @Volatile private var activeDevice: Device? = null

    /** SDK 가 이미 연결한 기기 중 첫 번째를 게임 기기로 선택한다. */
    @SuppressLint("MissingPermission")
    fun connect() {
        val current = _connectionState.value
        if (current is ConnectionState.Connecting) return

        val sdkDevices = try {
            LSBluetooth.connectedDevices()
        } catch (e: Exception) {
            Log.e(tag, "LSBluetooth.connectedDevices() 예외: ${e.message}")
            _connectionState.value = ConnectionState.Error(Loc.str(R.string.game_err_device_query, e.message ?: ""))
            return
        }

        val sdkDevice = sdkDevices.firstOrNull() ?: run {
            activeDevice = null
            _isGameModeSupported.value = false
            _connectionState.value = ConnectionState.Error(Loc.str(R.string.game_err_no_device))
            return
        }

        // 같은 기기에 이미 연결돼 있으면 재조회 불필요. 기기가 바뀐 경우에는 mac 이 다르므로 다시 판정한다.
        if (current is ConnectionState.Connected && activeDevice?.mac == sdkDevice.mac) return

        _connectionState.value = ConnectionState.Connecting

        val device = Device(mac = sdkDevice.mac, name = sdkDevice.name)
        if (!device.isConnected()) {
            activeDevice = null
            _isGameModeSupported.value = false
            _connectionState.value = ConnectionState.Error(Loc.str(R.string.game_err_not_connected))
            return
        }

        activeDevice = device
        _isGameModeSupported.value = device.supportsGameMode()
        _connectionState.value = ConnectionState.Connected
    }

    private inline fun withDevice(name: String, block: (Device) -> Boolean): Boolean {
        val device = activeDevice ?: run {
            Log.e(tag, "$name() — activeDevice null")
            return false
        }
        return block(device)
    }

    /** 게임 시작: READY(cmd=1) 전송 + FF04 Notify 구독을 SDK 가 일괄 처리 */
    @SuppressLint("MissingPermission")
    fun startGame(mode: GameMode, difficulty: GameDifficulty): Boolean = withDevice("startGame") { device ->
        val option = if (mode == GameMode.TEAM_BATTLE) Device.GAME_OPTION_RANDOM_TEAM else 0
        device.sendGameCmd(
            cmd    = GameCmd.START,
            mode   = mode.toSdkMode(),
            level  = difficulty.toSdkLevel().value,
            option = option,
            wandId = 0
        ) { result -> _gameResultFlow.tryEmit(result) }
    }

    /** Mode 4 팀 배정 시작: TEAM_ASSIGN(cmd=7) — 미배정 응원봉이 해당 팀 색으로 블링크 */
    @SuppressLint("MissingPermission")
    fun startTeamAssign(team: Team): Boolean = withDevice("startTeamAssign") { device ->
        device.sendGameCmd(
            cmd   = GameCmd.TEAM_ASSIGN,
            mode  = GameMode.MANUAL_TEAM.toSdkMode(),
            level = team.teamId
        ) { result -> _gameResultFlow.tryEmit(result) }
    }

    /** Mode 4 팀 배정 종료: TEAM_ASSIGN_END(cmd=9) — 중계기가 집계 인원수를 FF04 로 Notify */
    @SuppressLint("MissingPermission")
    fun endTeamAssign(team: Team): Boolean = withDevice("endTeamAssign") { device ->
        device.sendGameCmd(
            cmd   = GameCmd.TEAM_ASSIGN_END,
            mode  = GameMode.MANUAL_TEAM.toSdkMode(),
            level = team.teamId
        )
    }

    /** Mode 4 게임 시작: READY(cmd=1) — level=라운드 수, option=라운드당 측정시간(ms) */
    @SuppressLint("MissingPermission")
    fun startTeamGame(rounds: Int, difficulty: GameDifficulty): Boolean = withDevice("startTeamGame") { device ->
        device.sendGameCmd(
            cmd    = GameCmd.START,
            mode   = GameMode.MANUAL_TEAM.toSdkMode(),
            level  = rounds,
            option = difficulty.teamMeasureMs
        ) { result -> _gameResultFlow.tryEmit(result) }
    }

    /** WINNER(cmd=6) 전송 (Mode 1·2 전용) */
    @SuppressLint("MissingPermission")
    fun sendWinner(mode: GameMode, winnerWandId: Int): Boolean = withDevice("sendWinner") { device ->
        device.sendGameCmd(cmd = GameCmd.WINNER, mode = mode.toSdkMode(), wandId = winnerWandId)
    }

    /** 게임 중지(cmd=3) */
    @SuppressLint("MissingPermission")
    fun stopGame(): Boolean = withDevice("stopGame") { it.sendGameCmd(cmd = GameCmd.STOP) }

    /** 게임 초기화(cmd=4) */
    @SuppressLint("MissingPermission")
    fun clearGame(): Boolean = withDevice("clearGame") { it.sendGameCmd(cmd = GameCmd.CLEAR) }

    /** FF04 Notify 구독 해제 */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        activeDevice?.clearNotifyGameResults()
        activeDevice = null
        _isGameModeSupported.value = false
        _connectionState.value = ConnectionState.Disconnected
    }
}
