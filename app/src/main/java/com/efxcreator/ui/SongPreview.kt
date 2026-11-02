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
    val player = MusicPlayerController()

    /** 현재 불러왔거나 재생 중인 곡 (없으면 null) */
    var activeId by mutableStateOf<String?>(null)
        private set

    /** 기기에 올릴 프레임 (Broadcasting 설정이 반영된 것) */
    var frames by mutableStateOf<List<Pair<Long, ByteArray>>>(emptyList())
        private set

    private var broadcasting = true

    fun setBroadcasting(enabled: Boolean) {
        if (broadcasting == enabled) return
        broadcasting = enabled
        activeId?.let { reloadFrames(it) }
    }

    private fun reloadFrames(projectId: String) {
        val efx = repository.loadEfx(projectId)
        frames = efx?.body?.entries.orEmpty().map {
            it.timestampMs to it.payload.copy(broadcasting = if (broadcasting) 1 else 0).toByteArray()
        }
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

    fun stop() {
        player.release()
        activeId = null
        frames = emptyList()
    }
}

@Composable
fun rememberSongPreview(bleVm: BleViewModel): SongPreviewState {
    val context = LocalContext.current
    val state = remember { SongPreviewState(EfxRepository(context.applicationContext)) }
    val connected by bleVm.connected.collectAsState()
    val broadcasting by bleVm.broadcasting.collectAsState()
    LaunchedEffect(broadcasting) { state.setBroadcasting(broadcasting) }

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
            state.stop()
            bleVm.stopTimeline()
        }
    }
    return state
}
