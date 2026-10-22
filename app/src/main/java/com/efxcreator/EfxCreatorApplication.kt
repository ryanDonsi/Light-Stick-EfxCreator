package com.efxcreator

import android.app.Application
import android.util.Log
import com.lightstick.LSBluetooth
import com.lightstick.config.DeviceFilter

class EfxCreatorApplication : Application() {

    override fun onCreate() {
        super.onCreate()

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
