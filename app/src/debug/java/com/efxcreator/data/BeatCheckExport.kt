package com.efxcreator.data

import android.content.Context
import com.lightstick.efx.EfxEntry
import com.efxcreator.music.MusicAnalysis
import com.efxcreator.music.section.SectionDetector
import com.efxcreator.music.style.MusicStyleClassifier
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Light-Stick-Music 의 Beat Accuracy Checker(tools/beat_accuracy_checker.py)로 이 앱이 만든 비트를
 * GT 엔진(Beat Transformer/madmom/librosa)과 비교할 수 있도록, 그 도구가 읽는 바이너리 두 개를 만든다.
 *
 *  - timeline_<musicId>_v1.bin : Int version, Int count, [Long timeMs, Int payloadLen, payload]...
 *  - sections_<musicId>_v1.bin : SectionMetaStorage 와 같은 형식 (섹션별 비트 시각 목록 포함)
 *
 * 체커는 파일 이름의 musicId 로 원곡 오디오와 짝을 맞춘다(음악 파일의 SHA-256 기반 ID — 이 앱과 동일).
 * 체커에서 원곡 오디오와 이 파일들이 든 폴더를 각각 지정하면 된다.
 */
object BeatCheckExport {
    private const val VERSION = 1
    private const val DIR = "BeatCheck"

    /** 만든 파일 목록(타임라인, 섹션). 분석 결과(엔진 입력)가 없으면 null */
    fun export(context: Context, musicId: Int, entries: List<EfxEntry>, analysis: MusicAnalysis): List<File>? {
        val input = analysis.engineInput ?: return null
        val dir = File(context.getExternalFilesDir(null), DIR).apply { mkdirs() }

        val timeline = File(dir, "timeline_${musicId}_v$VERSION.bin")
        DataOutputStream(FileOutputStream(timeline)).use { out ->
            val sorted = entries.sortedBy { it.timestampMs }
            out.writeInt(VERSION)
            out.writeInt(sorted.size)
            sorted.forEach {
                val bytes = it.payload.toByteArray()
                out.writeLong(it.timestampMs)
                out.writeInt(bytes.size)
                out.write(bytes)
            }
        }

        // 섹션: 같은 종류가 이어지는 비트들을 한 구간으로 묶는다 (엔진이 쓰는 묶음과 동일)
        val groups = ArrayList<List<com.efxcreator.music.EngineBeat>>()
        var start = 0
        val beats = input.beats
        for (i in 1..beats.size) {
            if (i == beats.size || beats[i].sectionType != beats[start].sectionType) {
                groups += beats.subList(start, i)
                start = i
            }
        }
        val styleOrdinal = runCatching { MusicStyleClassifier.MusicStyle.valueOf(input.musicStyle).ordinal }.getOrDefault(-1)
        val sections = File(dir, "sections_${musicId}_v$VERSION.bin")
        DataOutputStream(FileOutputStream(sections)).use { out ->
            out.writeInt(VERSION)
            out.writeInt(styleOrdinal)
            out.writeInt(groups.size)
            groups.forEachIndexed { gi, g ->
                val first = g.first()
                val endMs = if (gi + 1 < groups.size) groups[gi + 1].first().timeMs else input.durationMs
                out.writeLong(first.timeMs)
                out.writeLong(endMs)
                out.writeInt(runCatching { SectionDetector.SectionType.valueOf(first.sectionType).ordinal }.getOrDefault(1))
                out.writeInt(SectionDetector.ChangeStrength.NONE.ordinal)
                out.writeLong(input.globalBeatMs)
                out.writeFloat(g.map { it.confidence }.average().toFloat())
                out.writeFloat(first.sectionEnergy)
                out.writeFloat(first.sectionPeakEnergy)
                out.writeFloat(first.sectionLowRatio)
                out.writeFloat(first.sectionMidRatio)
                out.writeFloat(first.sectionHighRatio)
                out.writeFloat(first.sectionOnsetDensity)
                out.writeFloat(first.sectionPeriodicity)
                out.writeInt(g.size)
                g.forEach { out.writeLong(it.timeMs) }
            }
        }
        return listOf(timeline, sections)
    }
}
