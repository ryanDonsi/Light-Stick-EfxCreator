package com.efxcreator

import android.app.Application
import android.content.Context
import com.efxcreator.core.Loc
import com.efxcreator.core.LocaleHelper
import android.util.Log
import com.lightstick.LSBluetooth
import com.lightstick.config.DeviceFilter

class EfxCreatorApplication : Application() {

    // 저장된 표시 언어(자동 = 시스템 언어)를 앱 전체에 적용한다
    override fun attachBaseContext(base: Context) {
        val wrapped = LocaleHelper.wrap(base)
        super.attachBaseContext(wrapped)
        Loc.init(this)
    }

    override fun onCreate() {
        super.onCreate()

        // 스캔/연결 문제 진단용 SDK 패킷·연결 로그(태그 'Lightstick')는 디버그 빌드에서만 켠다.
        // 릴리스(release/releaseTest)에서는 기기 MAC 주소 등이 로그에 남지 않도록 끈다.
        LSBluetooth.setDebugLoggingEnabled(BuildConfig.DEBUG)

        // Light-Stick-Music 과 동일한 장치 이름 필터
        val filter = DeviceFilter.Builder()
            .addName("LS", DeviceFilter.MatchMode.ENDS_WITH)
            .addName("GL", DeviceFilter.MatchMode.ENDS_WITH)
            .addName("RL", DeviceFilter.MatchMode.ENDS_WITH)
            .build()

        try {
            LSBluetooth.initialize(
                context = applicationContext,
                deviceFilter = filter
            )
        } catch (e: SecurityException) {
            // Android 12+ 에서 BLUETOOTH_CONNECT 권한 허용 전에는 연결 복원이 거부될 수 있다.
            // 스캔/연결은 에디터에서 권한을 요청한 뒤 수행한다.
            Log.w("EfxCreatorApplication", "Bluetooth init without permission: ${e.message}")
        }
    }
}
