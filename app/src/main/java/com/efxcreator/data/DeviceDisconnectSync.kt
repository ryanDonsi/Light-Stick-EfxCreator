package com.efxcreator.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.lightstick.LSBluetooth

/**
 * 같은 태블릿의 다른 앱(Light-Stick-Music)과 응원봉 "연결 해제"를 맞춘다.
 *
 * 안드로이드는 한 앱이 연결을 끊어도 다른 앱이 연결을 쥐고 있으면 물리 연결을 유지하므로, 사용자가 직접 해제했을 때
 * 상대 앱에 알려서(명시적 브로드캐스트) 상대도 같은 기기의 연결을 끊게 한다. 받은 쪽은 다시 알리지 않는다(반복 방지).
 *
 * - 동작 이름과 값은 Music 과 같아야 한다: action [ACTION_DISCONNECT_DEVICE], extra [EXTRA_MAC] (String)
 * - 받는 쪽은 앱이 실행 중일 때만 등록되어 있다 (실행 중이 아니면 끊을 연결도 없다)
 * - 누구나 보낼 수 있는 동작이지만 하는 일이 "연결 해제"뿐이라 위험은 낮다
 */
object DeviceDisconnectSync {

    private const val TAG = "DeviceDisconnectSync"
    const val ACTION_DISCONNECT_DEVICE = "com.lightstick.action.DISCONNECT_DEVICE"
    const val EXTRA_MAC = "mac"

    /** 연동 대상 앱 */
    private const val PEER_PACKAGE = "com.lightstick.music"

    /** 사용자가 직접 연결을 해제했을 때 상대 앱에 알린다 */
    fun notifyDisconnected(context: Context, mac: String) {
        runCatching {
            context.sendBroadcast(
                Intent(ACTION_DISCONNECT_DEVICE).setPackage(PEER_PACKAGE).putExtra(EXTRA_MAC, mac)
            )
        }.onSuccess { Log.d(TAG, "sent disconnect notice to $PEER_PACKAGE: mac=$mac (상대 앱이 받는지는 상대 쪽 로그로 확인)") }
            .onFailure { Log.w(TAG, "notify failed: ${it.message}") }
    }

    /** 상대 앱이 보낸 해제 알림을 받는다 (앱 시작 때 한 번 등록) */
    fun register(context: Context) {
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACTION_DISCONNECT_DEVICE) return
                val mac = intent.getStringExtra(EXTRA_MAC)
                Log.d(TAG, "received disconnect notice: mac=$mac")
                if (mac == null) return
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "ignored: BLUETOOTH_CONNECT not granted")
                    return
                }
                // 사용자가 직접 해제한 경우와 달리 상대 앱에 다시 알리지 않는다 (BleViewModel.disconnect 를 거치지 않는다)
                val device = runCatching { LSBluetooth.connectedDevices().firstOrNull { it.mac == mac } }.getOrNull()
                if (device == null) {
                    Log.d(TAG, "ignored: no connected device with mac=$mac in this app")
                    return
                }
                Log.d(TAG, "disconnecting $mac by peer request")
                runCatching { device.stopTimeline(); device.disconnect() }
                // 화면 상태는 BleViewModel 의 연결 감시(5초 주기)가 끊긴 것을 알아채고 정리한다
            }
        }
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(ACTION_DISCONNECT_DEVICE), ContextCompat.RECEIVER_EXPORTED
        )
        Log.d(TAG, "receiver registered (action=$ACTION_DISCONNECT_DEVICE)")
    }
}
