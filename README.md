# EFX Creator - Light Stick Effect Creator

Light-Stick SDK를 사용한 EFX(Effect) 파일 생성 Android 애플리케이션

## 주요 기능

### 1. Bluetooth Permission 없이 동작 ✅
- EFX 파일 생성 및 편집만 수행하므로 BLE 권한이 필요 없습니다
- 파일 저장/공유를 위한 스토리지 권한만 사용합니다

### 2. EFX 목록 화면
- 생성된 모든 EFX 프로젝트를 목록으로 표시
- 각 프로젝트의 이름, Music ID, 타임라인 엔트리 개수, 수정 시간 표시
- 동그란 + 버튼으로 새 EFX 생성

### 3. EFX 생성 및 편집
- 프로젝트 이름 설정
- 음악 파일 연결 (선택 사항)
  - 음악 파일 선택 시 자동으로 Music ID 계산 (SHA-256 기반)
  - 음악 파일 없을 시 Music ID = 0

### 4. 타임라인 편집
- 시간순으로 정렬된 타임라인 엔트리 목록 표시
- 동그란 + 버튼으로 새 타임라인 엔트리 추가
- 각 엔트리 클릭으로 편집 모드 진입
- LSEffectPayload의 모든 속성 설정 가능:
  - Timestamp (ms)
  - Effect Type (OFF, ON, BLINK, STROBE, BREATH, RAINBOW)
  - Foreground/Background Color (RGB)
  - Effect Index
  - LED Mask
  - Period, SPF, Fade
  - Random Color, Random Delay
  - Broadcasting, Sync Index

### 5. .efx 파일 저장 및 공유
- 프로젝트를 .efx 바이너리 파일로 익스포트
- SDK v1.4 스펙 준수 (20바이트 헤더 + 엔트리 + CRC32)
- 공유 기능을 통해 다른 앱으로 전송 가능

## 기술 스택

- **Kotlin**: 100% Kotlin으로 작성
- **Jetpack Compose**: 선언형 UI
- **Material3**: 최신 Material Design
- **Navigation Compose**: 화면 전환
- **ViewModel & StateFlow**: 상태 관리
- **Gson**: JSON 직렬화
- **Coroutines**: 비동기 처리

## 프로젝트 구조

```
app/
├── src/main/java/com/efxcreator/
│   ├── model/
│   │   ├── LSEffectPayload.kt      # 20바이트 페이로드 구조
│   │   ├── TimelineEntry.kt        # 타임라인 엔트리
│   │   └── EfxProject.kt           # EFX 프로젝트 모델
│   ├── data/
│   │   ├── EfxBinaryCodec.kt       # .efx 파일 인코더/디코더
│   │   └── EfxRepository.kt        # 프로젝트 저장소
│   ├── ui/
│   │   ├── EfxListScreen.kt        # 목록 화면
│   │   ├── EfxEditScreen.kt        # 편집 화면
│   │   ├── TimelineEntryDialog.kt  # 타임라인 엔트리 다이얼로그
│   │   ├── EfxListViewModel.kt     # 목록 ViewModel
│   │   ├── EfxEditViewModel.kt     # 편집 ViewModel
│   │   └── theme/
│   │       └── Theme.kt
│   ├── MainActivity.kt
│   └── EfxCreatorApp.kt            # Navigation
```

## EFX 파일 포맷

SDK v1.4 스펙에 따른 바이너리 포맷:

### Header (20 bytes)
```
Offset | Size | Field        | Description
-------|------|--------------|---------------------------
0-3    | 4    | magic        | "EFX1" (ASCII)
4-5    | 2    | version      | 0x0103 (v1.3)
6-8    | 3    | reserved     | 0x00 0x00 0x00
9-12   | 4    | musicId      | Music ID (UInt32)
13-16  | 4    | entryCount   | 엔트리 개수
17-19  | 3    | reserved     | 0x00 0x00 0x00
```

### Body (각 엔트리 24 bytes)
```
Offset | Size | Field        | Description
-------|------|--------------|---------------------------
0-3    | 4    | timestampMs  | 타임스탬프 (UInt32)
4-23   | 20   | payload      | LSEffectPayload
```

### Tail (4 bytes)
```
Offset | Size | Field        | Description
-------|------|--------------|---------------------------
0-3    | 4    | crc32        | Header+Body의 CRC32
```

## LSEffectPayload 구조 (20 bytes)

```kotlin
data class LSEffectPayload(
    val effectIndex: Int,      // 0-1: UShort
    val ledMask: Int,          // 2-3: UShort
    val fgColorRed: Int,       // 4: Byte (0-255)
    val fgColorGreen: Int,     // 5: Byte (0-255)
    val fgColorBlue: Int,      // 6: Byte (0-255)
    val bgColorRed: Int,       // 7: Byte (0-255)
    val bgColorGreen: Int,     // 8: Byte (0-255)
    val bgColorBlue: Int,      // 9: Byte (0-255)
    val effectType: Int,       // 10: Byte
    val period: Int,           // 11: Byte (x10ms)
    val spf: Int,              // 12: Byte
    val randomColor: Int,      // 13: Byte
    val randomDelay: Int,      // 14: Byte (x10ms)
    val fade: Int,             // 15: Byte
    val broadcasting: Int,     // 16: Byte
    val syncIndex: Int,        // 17: Byte
    val reserved1: Int,        // 18: Byte
    val reserved2: Int         // 19: Byte
)
```

## 빌드 및 실행

### 요구사항
- Android Studio Hedgehog | 2023.1.1 이상
- JDK 17
- Android SDK 26 이상

### 빌드
```bash
./gradlew assembleDebug
```

### 실행
```bash
./gradlew installDebug
```

## 라이선스

MIT License

## 개발자

개발 환경: Android/iOS 앱 개발, Bluetooth Device 펌웨어 개발
