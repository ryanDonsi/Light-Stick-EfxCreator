package com.efxcreator

import android.app.Application
import android.content.Context
import com.efxcreator.core.Loc
import com.efxcreator.core.LocaleHelper
import com.efxcreator.data.PreferencesManager
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
        val prefs = PreferencesManager(this)
        LSBluetooth.setDebugLoggingEnabled(BuildConfig.DEBUG && prefs.bleDebugLogEnabled)

        // 디버그 빌드: 설정 > BLE 전송 설정에서 저장한 전송 간격/큐 크기를 복원한다
        // (SDK 는 값을 저장하지 않으며, 릴리스 빌드는 SDK 기본값 50ms / 8 을 그대로 쓴다).
        if (BuildConfig.DEBUG) {
            runCatching { LSBluetooth.setCommandIntervalMs(prefs.bleCommandIntervalMs) }
                .onFailure { Log.w("EfxCreatorApplication", "invalid saved command interval: ${it.message}") }
            runCatching { LSBluetooth.setCommandQueueSize(prefs.bleCommandQueueSize) }
                .onFailure { Log.w("EfxCreatorApplication", "invalid saved queue size: ${it.message}") }
        }

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
