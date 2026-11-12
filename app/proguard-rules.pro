# 릴리스 빌드에서 난독화/축소(isMinifyEnabled)를 켤 때 쓰는 규칙이다.
# 현재는 minify 가 꺼져 있어 적용되지 않는다. 켜기 전에 릴리스 빌드로 전체 기능을 점검해야 한다.

# Gson 이 필드 이름으로 읽고 쓰는 저장 데이터 모델 (공연, 이펙트 패턴, 세트, 분석 결과, 프로젝트 목록 등)
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.efxcreator.model.** { *; }
-keep class com.efxcreator.music.** { *; }
-keep class com.efxcreator.data.** { *; }

# LightStick SDK (자체 consumer rules 가 비어 있어 앱에서 유지한다)
-keep class com.lightstick.** { *; }

# SDK 가 컴파일 때만 쓰는 @Parcelize 어노테이션 클래스는 AAR 에 들어 있지 않다 (실행 중에는 쓰이지 않으므로 경고만 무시한다)
-dontwarn kotlinx.parcelize.**
