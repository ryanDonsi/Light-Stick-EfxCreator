package com.efxcreator.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.efxcreator.data.EfxRepository
import com.efxcreator.ui.editor.MusicPlayerController
import com.efxcreator.viewmodel.BleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * 곡 목록의 카드에서 바로 음악 + 이펙트를 미리 재생하는 컨트롤러.
 * 한 번에 한 곡만 재생하며, 기기가 연결돼 있으면 편집 화면과 같은 방식으로 이펙트를 기기에도 보낸다.
 */
@Stable
class SongPreviewState(private val repository: EfxRepository) {
    var player by mutableStateOf(MusicPlayerController())
        private set

    /** 현재 불러왔거나 재생 중인 곡 (없으면 null) */
    var activeId by mutableStateOf<String?>(null)
        private set

    /** 기기에 올릴 프레임 */
    var frames by mutableStateOf<List<Pair<Long, ByteArray>>>(emptyList())
        private set

    /** 재생 중인 곡의 이펙트 목록 (앨범 아트 위 실시간 배지용) */
    var entries by mutableStateOf<List<com.lightstick.efx.EfxEntry>>(emptyList())
        private set

    private fun reloadFrames(projectId: String) {
        val efx = repository.loadEfx(projectId)
        entries = efx?.body?.entries.orEmpty()
        frames = entries.map { it.timestampMs to it.payload.toByteArray() }
    }

    /** 이 곡을 재생/일시정지한다. 다른 곡이 활성이면 그 곡으로 바꾼다. */
    suspend fun toggle(projectId: String) {
        if (activeId == projectId) {
            player.toggle()
            return
        }
        player.release()
        val file = withContext(Dispatchers.IO) {
            reloadFrames(projectId)
            repository.getStoredMusicFile(projectId)
        }
        activeId = projectId
        player.load(file)
        // 준비(prepare)가 끝날 때까지 잠깐 기다렸다가 재생한다
        var waited = 0
        while (!player.isReady && player.error == null && waited < 3_000) {
            delay(50)
            waited += 50
        }
        if (player.isReady) player.play() else activeId = null
    }

    /** 편집 화면으로 넘어가기 직전에 호출: 이 곡이 활성이면 현재 위치/재생 여부를 넘긴다. */
    fun handOffTo(projectId: String) {
        if (activeId != projectId) return
        // 플레이어 객체 자체를 넘겨 음악이 끊기지 않게 한다. 이 상태는 새 플레이어로 바꿔 둔다.
        PlaybackHandoff.put(projectId, player.positionMs, player.isPlaying, player)
        handedOff = true
        player = MusicPlayerController()
        activeId = null
        frames = emptyList()
        entries = emptyList()
    }

    /** 플레이어를 편집 화면에 넘긴 상태인가 (넘겼다면 화면을 떠날 때 기기 타임라인을 멈추지 않는다) */
    var handedOff = false
        private set

    fun stop() {
        player.release()
        activeId = null
        frames = emptyList()
        entries = emptyList()
    }
}

/**
 * 곡 목록에서 재생 중이던 곡을 편집 화면으로 들어갈 때 이어서 재생하기 위한 한 번짜리 인계 값.
 * 편집 화면이 음악을 불러온 뒤 [take] 로 가져가 같은 위치에서 이어 재생한다.
 */
object PlaybackHandoff {
    private var projectId: String? = null
    private var positionMs: Long = 0L
    private var playing: Boolean = false
    private var controller: MusicPlayerController? = null

    fun put(projectId: String, positionMs: Long, playing: Boolean, controller: MusicPlayerController) {
        this.projectId = projectId
        this.positionMs = positionMs
        this.playing = playing
        this.controller = controller
    }

    /** 넘겨받을 플레이어가 있으면 돌려준다 (이미 같은 곡이 로드돼 재생 중일 수 있다). 값은 [take] 로 비운다. */
    fun peekController(projectId: String): MusicPlayerController? =
        if (this.projectId == projectId) controller else null

    /** 이 곡의 인계 값이 있으면 (위치, 재생 중이었는지) 를 돌려주고 비운다. */
    fun take(projectId: String): Pair<Long, Boolean>? {
        if (this.projectId != projectId) return null
        val result = positionMs to playing
        this.projectId = null
        this.controller = null
        return result
    }
}

@Composable
fun rememberSongPreview(bleVm: BleViewModel): SongPreviewState {
    val context = LocalContext.current
    val state = remember { SongPreviewState(EfxRepository(context.applicationContext)) }
    val connected by bleVm.connected.collectAsState()

    // 재생 위치 갱신
    LaunchedEffect(Unit) {
        while (isActive) {
            state.player.tick()
            delay(100)
        }
    }

    // 기기 동기화: 곡이 바뀌면 새로 올리고, 재생/일시정지를 따라간다
    val activeId = state.activeId
    val isPlaying = state.player.isPlaying
    var loadedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(connected) { loadedFor = null }
    LaunchedEffect(activeId, isPlaying, connected, state.frames) {
        if (connected == null || activeId == null) return@LaunchedEffect
        if (isPlaying) {
            if (loadedFor != activeId) {
                bleVm.loadTimeline(state.frames)
                loadedFor = activeId
            } else {
                bleVm.resumeTimeline()
            }
            while (isActive) {
                bleVm.updatePosition(state.player.positionMs)
                delay(100)
            }
        } else if (loadedFor == activeId) {
            bleVm.pauseTimeline()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { state.player.pause() }
    DisposableEffect(Unit) {
        onDispose {
            // 편집 화면으로 플레이어를 넘겼다면 음악도 기기 타임라인도 그대로 둔다
            if (!state.handedOff) {
                state.stop()
                bleVm.stopTimeline()
            }
        }
    }
    return state
}
