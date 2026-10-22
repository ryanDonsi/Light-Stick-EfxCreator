package com.efxcreator.automusic

interface AutoTimelineGenerator {
    fun generate(musicPath: String, musicId: Int, paletteSize: Int): List<Pair<Long, ByteArray>>
}
