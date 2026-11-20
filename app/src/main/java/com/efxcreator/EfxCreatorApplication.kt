package com.efxcreator

import android.app.Application
import android.content.Context
import com.efxcreator.core.Loc
import com.efxcreator.core.LocaleHelper
import android.util.Log
import com.lightstick.LSBluetooth
import com.lightstick.config.DeviceFilter

class EfxCreatorApplication : Application() {

    companion object {
        /** Light-Stick-Music 과 동일한 장치 이름 필터 */
        fun deviceFilter(): DeviceFilter = DeviceFilter.Builder()
            .addName("LS", DeviceFilter.MatchMode.ENDS_WITH)
            .addName("GL", DeviceFilter.MatchMode.ENDS_WITH)
            .addName("RL", DeviceFilter.MatchMode.ENDS_WITH)
            .build()

        /**
         * 이미 다른 앱(Light-Stick-Music 등)이 연결해 둔 응원봉을 이어받는다.
         * SDK 는 initialize 때 시스템에 연결된 기기를 복원하는데, 초기화 자체는 한 번만 하고 복원만 다시 수행하므로
         * 앱이 켜진 뒤에 다른 앱이 연결한 기기를 알아채려면 이 함수로 다시 불러야 한다. 연결은 비동기라 곧바로 결과가 나오지 않는다.
         */
        fun restoreSystemConnections(context: Context) {
            try {
                LSBluetooth.initialize(context = context.applicationContext, deviceFilter = deviceFilter())
            } catch (e: SecurityException) {
                Log.w("EfxCreatorApplication", "Restore without permission: ${e.message}")
            }
        }
    }

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
        Log.d("EfxCreatorApplication", "SDK debug logging enabled=${BuildConfig.DEBUG} (SDK 로그 태그: Lightstick, 스캔/연결 때 출력)")

        // Android 12+ 에서 BLUETOOTH_CONNECT 권한 허용 전에는 연결 복원이 거부될 수 있다.
        // 스캔/연결은 에디터에서 권한을 요청한 뒤 수행한다.
        // 저장된 BLE 명령 간격을 연결 전에 적용한다 (SDK 는 값을 저장하지 않는다)
        com.efxcreator.data.BleCommandInterval.applyStored(this)
        restoreSystemConnections(this)
    }
}
