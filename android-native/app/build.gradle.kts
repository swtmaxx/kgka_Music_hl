import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// ===== 签名 =====
// 复用 Flutter 工程的固定调试 keystore，保证 CI 每次产物签名一致、可覆盖安装。
// （原生版是独立包名，签名无需与 Flutter 版相同，但保持稳定便于反复安装。）
val keystoreFile = rootProject.file("../android/keystore/fixed-debug.keystore")
val hasFixedKeystore = keystoreFile.exists()

// 若用户提供了 android/key.properties（与 Flutter 工程共用同一份），优先使用正式签名。
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("../android/key.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}
val hasReleaseSigning = keystorePropertiesFile.exists() &&
    !keystoreProperties.getProperty("storeFile").isNullOrBlank() &&
    !keystoreProperties.getProperty("storePassword").isNullOrBlank() &&
    !keystoreProperties.getProperty("keyAlias").isNullOrBlank() &&
    !keystoreProperties.getProperty("keyPassword").isNullOrBlank()

android {
    namespace = "com.swtmaxx.kamusic.native"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.swtmaxx.kamusic.native"
        // 目标设备只有 S100（Android 8.1 = API 27），因此直接以 27 为下限，
        // 让 R8 可以移除全部向下兼容分支。
        minSdk = 27
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters.clear()
            // 手表是 32 位，只保留 armeabi-v7a。
            abiFilters.add("armeabi-v7a")
        }

        // 默认 API 服务器地址；「我的」页可在运行时覆盖。
        buildConfigField(
            "String",
            "DEFAULT_API_BASE_URL",
            "\"https://kgapi-full.vercel.app\"",
        )
    }

    signingConfigs {
        create("fixed") {
            if (hasFixedKeystore) {
                storeFile = keystoreFile
                storePassword = "kkmusic123"
                keyAlias = "kkmusic"
                keyPassword = "kkmusic123"
            }
        }
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file("../android/${keystoreProperties.getProperty("storeFile")}")
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else if (hasFixedKeystore) {
                storeFile = keystoreFile
                storePassword = "kkmusic123"
                keyAlias = "kkmusic"
                keyPassword = "kkmusic123"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            ndk {
                abiFilters.clear()
                abiFilters.add("armeabi-v7a")
            }
            signingConfig = when {
                hasReleaseSigning -> signingConfigs.getByName("release")
                hasFixedKeystore -> signingConfigs.getByName("fixed")
                else -> signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 不把依赖元数据写进 APK（省体积，且对本地安装无用）。
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module",
                "kotlin-tooling-metadata.json",
                "DebugProbesKt.bin",
                "kotlin/**",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }

    lint {
        abortOnError = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
