package com.efxcreator.ui.editor

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import com.efxcreator.R
import com.efxcreator.core.Loc

/**
 * 에디터용 간단한 음악 재생 컨트롤러 (MediaPlayer 기반).
 *
 * [positionMs] 는 Compose 상태라 읽는 쪽만 다시 그려진다. 재생 중에는 주기적으로 [tick] 을 호출해
 * 실제 재생 위치를 반영해야 한다. 음악이 없으면 재생은 비활성이고 [seekTo] 만 위치를 옮긴다.
 */
class MusicPlayerController {

    private var player: MediaPlayer? = null

    var isPlaying by mutableStateOf(false)
        private set
    var isReady by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    companion object {
        private const val TAG = "MusicPlayerController"
    }

    /** 음악 파일을 불러온다. null 이면 플레이어를 해제한다. */
    fun load(file: File?) {
        release()
        positionMs = 0L
        durationMs = 0L
        error = null
        if (file == null || !file.exists()) return

        try {
            val mp = MediaPlayer()
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener {
                durationMs = it.duration.toLong()
                isReady = true
            }
            mp.setOnCompletionListener {
                isPlaying = false
                positionMs = durationMs
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                error = Loc.str(R.string.music_player_controller_1)
                isPlaying = false
                isReady = false
                true
            }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            Log.e(TAG, "load failed", e)
            error = Loc.str(R.string.music_player_controller_2, e.message)
            release()
        }
    }

    fun play() {
        val mp = player ?: return
        if (!isReady) return
        if (positionMs >= durationMs && durationMs > 0) {
            mp.seekTo(0)
            positionMs = 0L
        }
        mp.start()
        isPlaying = true
    }

    fun pause() {
        val mp = player ?: return
        if (isReady && mp.isPlaying) mp.pause()
        isPlaying = false
        positionMs = if (isReady) mp.currentPosition.toLong() else positionMs
    }

    fun toggle() {
        if (isPlaying) pause() else play()
    }

    /** 정지 = 일시정지 + 처음으로 */
    fun stop() {
        pause()
        seekTo(0L)
    }

    fun seekTo(ms: Long) {
        val target = if (durationMs > 0) ms.coerceIn(0L, durationMs) else ms.coerceAtLeast(0L)
        positionMs = target
        val mp = player ?: return
        if (isReady) mp.seekTo(target.toInt())
    }

    /** 재생 중 실제 위치를 [positionMs] 에 반영한다 (약 16ms 주기로 호출) */
    fun tick() {
        val mp = player ?: return
        if (isReady && isPlaying) positionMs = mp.currentPosition.toLong()
    }

    fun release() {
        player?.let {
            runCatching { it.release() }
        }
        player = null
        isPlaying = false
        isReady = false
    }
}
