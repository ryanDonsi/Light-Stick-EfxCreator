plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.efxcreator"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.efxcreator"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // 난독화와 코드/리소스 축소. 규칙은 proguard-rules.pro (Gson 저장 모델과 LightStick SDK 유지)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // 릴리스와 같은 설정(난독화 포함)이지만 디버그 키로 서명해서 기기에 바로 설치해 볼 수 있는 테스트용 빌드.
        // 스토어에 올리거나 배포하는 용도로 쓰지 않는다 (배포는 release 를 업로드 키로 서명해서 쓴다).
        create("releaseTest") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Lint 설정 - LightStick SDK의 오래된 lint.jar 경고 무시
    // ──────────────────────────────────────────────────────────────
    lint {
        // 에러로 빌드 중단하지 않음
        abortOnError = false

        // 의존성 Lint 체크 비활성화 (SDK의 lint.jar 무시)
        checkDependencies = false

        // 경고를 에러로 처리하지 않음
        warningsAsErrors = false

        // 조용한 모드
        quiet = true
    }
}

dependencies {
    // ⭐ LightStick SDK (AAR 파일)
    implementation(files("libs/lightstick-sdk-2.1.0.aar"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Gson
    implementation(libs.gson)

    // DocumentFile (SAF)
    implementation(libs.androidx.documentfile)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}