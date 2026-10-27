package com.efxcreator.core

import android.util.Log
import java.util.zip.ZipInputStream

/**
 * 펌웨어 바이너리에서 버전 정보 추출 유틸
 *
 * Light-Stick-Music 에서 가져왔다. 테스트용 버전 시뮬레이션은 가져오지 않았고,
 * 파일에서 버전을 읽을 수 없으면 null 을 돌려준다 (호출부가 "버전 확인 불가" 로 처리).
 */
object FirmwareVersionParser {

    private const val TAG = "FirmwareVersionParser"

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /**
     * 펌웨어 바이너리에서 버전 문자열 추출 시도
     *
     * 지원 포맷:
     *  1. Nordic DFU ZIP 패키지 → manifest.json의 "version" 필드
     *  2. Raw binary → X.Y.Z 형식 ASCII 문자열 탐색
     *
     * @return 파싱된 버전 문자열, 실패 시 null
     */
    fun parseFromBytes(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        return try {
            if (isDfuZip(bytes)) parseFromDfuZip(bytes) else findVersionInBinary(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "버전 파싱 실패: ${e.message}")
            null
        }
    }

    /**
     * fileVersion이 deviceVersion보다 최신인지 여부
     */
    fun isNewerVersion(fileVersion: String, deviceVersion: String): Boolean =
        compareVersions(fileVersion, deviceVersion) > 0

    /**
     * 시맨틱 버전 비교 (X.Y.Z)
     * @return 양수: v1 > v2 / 0: 동일 / 음수: v1 < v2
     */
    fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.trim().split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.trim().split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(parts1.size, parts2.size)
        for (i in 0 until len) {
            val diff = parts1.getOrElse(i) { 0 } - parts2.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }

    private fun isDfuZip(bytes: ByteArray): Boolean =
        bytes[0] == ZIP_MAGIC[0] && bytes[1] == ZIP_MAGIC[1] &&
        bytes[2] == ZIP_MAGIC[2] && bytes[3] == ZIP_MAGIC[3]

    /** Nordic DFU ZIP 패키지의 manifest.json에서 "version" 필드 추출 */
    private fun parseFromDfuZip(bytes: ByteArray): String? {
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "manifest.json") {
                    val content = zip.readBytes().toString(Charsets.UTF_8)
                    val match = Regex(""""version"\s*:\s*"([^"]+)"""").find(content)
                    return match?.groupValues?.get(1)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return null
    }

    /** Raw binary에서 X.Y.Z 형식 버전 문자열 탐색 */
    private fun findVersionInBinary(bytes: ByteArray): String? {
        val text = String(bytes, Charsets.ISO_8859_1)
        return Regex("""\b(\d{1,3})\.(\d{1,3})\.(\d{1,3})\b""").find(text)?.value
    }
}
