package com.efxcreator.data

import android.content.Context
import com.efxcreator.music.MusicAnalysis
import com.lightstick.efx.EfxEntry
import java.io.File

/** 릴리스(releaseTest 포함)에서는 정확도 검사용 내보내기를 제공하지 않는다 (debug 소스셋에만 실제 구현이 있다) */
@Suppress("UNUSED_PARAMETER")
object BeatCheckExport {
    fun export(context: Context, musicId: Int, entries: List<EfxEntry>, analysis: MusicAnalysis): List<File>? = null
}
