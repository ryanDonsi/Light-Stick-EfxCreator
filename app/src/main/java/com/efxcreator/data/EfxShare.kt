package com.efxcreator.data

/**
 * EFX 파일을 다른 앱(Light-Stick-Music 등)이나 다른 기기로 공유할 때의 약속.
 *
 * `.efx` 는 표준 MIME 이 없어서, 받는 앱이 이 파일임을 알아보도록 전용 MIME 을 붙여 보낸다.
 * (Music 의 수신 필터와 같은 값을 써야 한다)
 */
object EfxShare {
    const val MIME_TYPE = "application/x-lightstick-efx"
    const val EXTENSION = "efx"

    private val FORBIDDEN = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    /** 파일 이름으로 쓸 수 없는 문자를 없애고 길이를 제한한다. 비면 "efx" */
    fun safeFileName(name: String): String =
        name.replace(FORBIDDEN, " ").replace(Regex("\\s+"), " ").trim().trim('.').take(80).ifEmpty { "efx" }
}
