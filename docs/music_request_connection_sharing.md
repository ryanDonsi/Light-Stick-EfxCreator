# [요청] Light-Stick-Music: 다른 앱이 연결한 응원봉 이어받기 (연결 상태 공유)

작성: 2026-11-20 · 대상: Light-Stick-Music 앱 개발자 · 관련: Light-Stick-EfxCreator `0844c11`, `d4cab29`

## 1. 요약

같은 태블릿에서 EfxCreator와 Music이 **같은 응원봉의 연결을 이어받도록** 하려고 합니다.
EfxCreator는 구현과 실기기 확인을 마쳤고, Music은 **앱이 새로 시작될 때만** 이어받고 **이미 실행 중일 때는** 이어받지 못합니다.
Music에 한 곳(복원 재실행)만 추가하면 됩니다.

## 2. 현재 상태 (실기기 로그로 확인한 내용)

| 상황 | 결과 |
|---|---|
| EfxCreator에서 연결 → **Music을 새로 실행** | 정상. 시작하자마자 `onClientConnectionState connected=true`, 서비스 검색, DIS 읽기(`GlowSync4329 RL`), `DeviceVM 연결된 기기: 1개`까지 이어짐 |
| EfxCreator에서 연결 → **이미 실행 중인 Music으로 이동** | **실패.** `연결된 기기: 0개`, 스캔 3초 → `연결 가능한 기기가 없습니다` |
| Music에서 연결 → **이미 실행 중인 EfxCreator로 돌아옴** | 정상(EfxCreator 수정 후). 복귀 후 약 1초 만에 이어받음 |

핵심은 두 가지입니다.
- 안드로이드는 **다른 앱이 맺은 GATT 연결도** `getConnectedDevices(GATT)`로 알려 줍니다(SDK의 시스템 연결 복원이 이를 사용).
- 연결된 응원봉은 광고를 멈추므로 **스캔으로는 절대 찾을 수 없습니다.** 이어받는 방법밖에 없습니다.

## 3. 원인 (Music 코드)

SDK의 시스템 연결 복원(`Facade.restoreSystemConnectedDevices`)은 `LSBluetooth.initialize()` 호출 때 수행됩니다. Music은 `LightStickMusicApp.initializeSDK()`에서 **프로세스 시작 시 한 번만** 호출합니다. 그 뒤에 다른 앱이 연결한 응원봉은 복원 대상이 되지 않습니다.

| 위치 | 문제 |
|---|---|
| `LightStickMusicApp.initializeSDK` | 복원이 앱 시작 시 1회뿐 |
| `EffectViewModel.startAutoScan` (225행~) | SDK 연결 목록을 **한 번만** 확인하고 비어 있으면 바로 스캔 → 광고가 없어서 `ScanFailed` |
| `EventNotificationListenerService.tryAutoReconnect` | `connectedCount() > 0`이면 스킵, 아니면 스캔 (같은 문제) |

참고로 Music의 `DeviceViewModel.observeDeviceStateEvents`, `EffectViewModel.observeDeviceStateEvents`는 SDK의 연결 이벤트를 이미 구독하고 있어서, **복원만 다시 돌려 주면 연결 이벤트로 화면은 자동 갱신**됩니다. 별도 UI 수정은 필요 없습니다.

## 4. 요청 내용

### 4-1. 복원을 다시 수행하는 함수 추가 (필수)

`LSBluetooth.initialize()`는 두 번째부터 초기화는 건너뛰고 **복원만 다시 수행**합니다(`Facade.initialize`의 `if (::appContext.isInitialized) return` 뒤에 `restoreSystemConnectedDevices()`가 매번 실행됨). 이를 이용합니다.

```kotlin
// LightStickMusicApp
companion object {
    fun deviceFilter(): DeviceFilter = DeviceFilter.Builder()
        .addName("LS", DeviceFilter.MatchMode.ENDS_WITH)
        .addName("GL", DeviceFilter.MatchMode.ENDS_WITH)
        .addName("RL", DeviceFilter.MatchMode.ENDS_WITH)
        .build()
}

private fun initializeSDK() {
    LSBluetooth.setDebugLoggingEnabled(true)
    restoreSystemConnections()
}

/** 다른 앱이 이미 연결해 둔 응원봉을 이어받는다 (연결은 비동기라 결과는 연결 이벤트로 온다) */
fun restoreSystemConnections() {
    try {
        LSBluetooth.initialize(context = applicationContext, deviceFilter = deviceFilter())
    } catch (e: SecurityException) {
        Log.w(TAG, "restore without permission: ${e.message}")
    }
}
```

- `@MainThread` 함수이므로 메인 스레드에서 호출합니다.
- `BLUETOOTH_CONNECT` 권한이 없으면 `SecurityException`이 날 수 있어 try/catch로 감쌉니다.

### 4-2. 호출 시점 (필수)

| 시점 | 방법 |
|---|---|
| 앱이 화면으로 돌아올 때 | `MainActivity.onStart`(또는 `onResume`)에서 연결된 기기가 없을 때만 `restoreSystemConnections()` 호출 |
| 이펙트 화면 진입 | `EffectViewModel.startAutoScan` 맨 앞에서 호출한 뒤, 아래 4-3 대기 |
| 자동 재연결 루프 | `tryAutoReconnect`에서 스캔하기 전에 한 번 호출하고 몇 초 확인 |

이미 연결된 기기가 있으면 호출하지 않아도 됩니다(`LSBluetooth.connectedCount() > 0`).

### 4-3. 스캔으로 넘어가기 전에 잠시 기다리기 (권장)

복원은 비동기입니다(GATT 연결 + 서비스 검색 + DIS 읽기 ≈ 1초). EfxCreator는 0.5초 간격으로 최대 12번(6초) 연결 목록을 확인합니다.

```kotlin
// EffectViewModel.startAutoScan, 연결 목록 확인 부분 대체
restoreSystemConnections()
repeat(12) {
    delay(500L)
    val sdkConnected = runCatching { LSBluetooth.connectedDevices() }.getOrDefault(emptyList())
    if (sdkConnected.isNotEmpty()) {
        _deviceConnectionState.value = DeviceConnectionState.Connected(sdkConnected.first())
        return@launch
    }
}
// 6초 안에 연결이 없으면 지금처럼 bonded 기기 스캔으로 이어감
```

- 대기 중에는 상태를 `Scanning`으로 두어 사용자가 "확인 중"으로 보게 합니다.
- 이미 연결 이벤트로 `Connected`가 되면(`observeDeviceStateEvents`) 대기를 끝냅니다.

### 4-4. 안내 토스트는 넣지 않습니다
EfxCreator에서도 "연결되었습니다" 토스트를 뺐습니다. 이어받기는 조용히 상태만 바꿉니다.

## 5. 주의 사항

- **동시 제어**: 두 앱이 같은 응원봉을 동시에 쥐고 이펙트를 보내면 명령이 섞입니다. 이번 작업 범위는 "연결 상태 공유"까지이고, "한쪽만 제어" 같은 정책은 별도로 정합니다.
- **연결 해제**: 한 앱이 연결을 끊어도 다른 앱이 연결을 쥐고 있으면 안드로이드가 물리적 연결을 유지할 수 있습니다. 해제 동작을 테스트할 때 응원봉이 실제로 끊기는지 확인하세요.
- **중계기 모드 기기**(`readDeviceMode → RELAY`)에서 확인했습니다. FF09(Broadcasting)는 BLE 모드 기기 전용입니다.
- 이어받기 때 `BluetoothGatt.refresh()` 리플렉션이 한 번 실행됩니다(SDK 내부 동작, 로그에 `hiddenapi ... allowed`로 보임). 문제는 없습니다.

## 6. 확인 방법

1. **EfxCreator 연결 → 이미 실행 중인 Music으로 이동**: 3초 안에 Music에 연결됨이 표시되고, Logcat에 `[GattClient] onConnectionStateChange ... CONNECTED`, `DeviceVM ... 연결된 기기: 1개` 또는 연결 이벤트가 나와야 합니다. `연결 가능한 기기가 없습니다`는 나오지 않아야 합니다.
2. **Music 연결 → 이미 실행 중인 EfxCreator로 이동**: 이미 동작합니다(회귀 확인).
3. **Music을 완전히 종료한 뒤 EfxCreator 연결 상태로 새로 실행**: 이미 동작합니다(회귀 확인).
4. **아무도 연결하지 않은 상태**: 지금처럼 bonded 기기 스캔 → 연결이 정상이어야 합니다(최대 6초 대기가 추가됨).
5. 응원봉이 꺼져 있거나 범위 밖일 때도 앱이 6초 안에 스캔 결과로 넘어가고 멈추지 않아야 합니다.

## 7. 참고: EfxCreator 구현 위치

| 파일 | 내용 |
|---|---|
| `EfxCreatorApplication.restoreSystemConnections` | `LSBluetooth.initialize`를 다시 불러 복원 |
| `BleViewModel.refreshSystemConnection` | 연결된 기기가 없고 권한이 있으면 복원 후 0.5초 간격으로 최대 6초 확인, 이어받으면 모니터링 시작 |
| `MainActivity.onStart` | 앱이 화면으로 돌아올 때 `refreshSystemConnection()` 호출 |
