import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    //id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// 发布签名信息从 android/key.properties 读取（该文件已被 .gitignore 忽略，不入库）。
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// 固定调试签名：GitHub Actions 每次构建使用同一个 keystore，保证 APK 可覆盖安装。
val fixedDebugKeystore = rootProject.file("keystore/fixed-debug.keystore")
val useFixedDebugSigning = fixedDebugKeystore.exists()

/** key.properties 是否已填好完整签名信息。 */
fun hasReleaseSigning(): Boolean = keystorePropertiesFile.exists() &&
    !keystoreProperties.getProperty("storeFile").isNullOrBlank() &&
    !keystoreProperties.getProperty("storePassword").isNullOrBlank() &&
    !keystoreProperties.getProperty("keyAlias").isNullOrBlank() &&
    !keystoreProperties.getProperty("keyPassword").isNullOrBlank()

android {
    namespace = "com.swtmaxx.kamusic"
    compileSdk = 37
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else if (useFixedDebugSigning) {
                storeFile = fixedDebugKeystore
                storePassword = "kkmusic123"
                keyAlias = "kkmusic"
                keyPassword = "kkmusic123"
            }
        }
        create("fixedDebug") {
            if (useFixedDebugSigning) {
                storeFile = fixedDebugKeystore
                storePassword = "kkmusic123"
                keyAlias = "kkmusic"
                keyPassword = "kkmusic123"
            }
        }
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.swtmaxx.kamusic"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        // 目标设备只有 32 位手表（S100，armeabi-v7a），不再依赖 JitPack 上的
        // SuperLyricApi（已删），minSdk 保留 26（Android 8.0）。
        minSdk = 26
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        ndk {
            abiFilters.clear()
            // 只留 armeabi-v7a：手表是 32 位，去掉 arm64-v8a 可省约 40% 体积。
            abiFilters.add("armeabi-v7a")
        }
    }

    buildTypes {
        release {
            // key.properties 填好后，命令行与 Android Studio 打包统一使用 release 正式签名；
            // 未配置完整信息时回退 debug 签名，保证开发期构建不被阻塞。
            if (hasReleaseSigning()) {
                signingConfig = signingConfigs.getByName("release")
            } else if (useFixedDebugSigning) {
                println("Using fixed debug signing for CI builds.")
                signingConfig = signingConfigs.getByName("fixedDebug")
            } else {
                println("Warning: android/key.properties 未配置完整签名信息，release 构建将回退使用 debug 签名。")
                signingConfig = signingConfigs.getByName("debug")
            }
            ndk {
                abiFilters.clear()
                // 只留 armeabi-v7a（同上）。
                abiFilters.add("armeabi-v7a")
            }
            isMinifyEnabled = false
            // Flutter Gradle 插件在 apply 阶段（早于本脚本体执行）会默认打开
            // release.shrinkResources = true（见 FlutterPluginUtils.shouldShrinkResources，
            // 无 -Pshrink 属性时恒为 true）。如果我们只关 minify 而不关 shrinkResources，
            // AGP 配置期会直接报错：
            //   "Removing unused resources requires unused code shrinking to be turned on"
            // 因此必须成对显式关闭。
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
}
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}


// === 依赖解析策略：对 JitPack 等按需构建的仓库不缓存失败结果，确保一次冷启动失败后
// 等若干秒再次构建时能重新尝试拉取（而不是被 Gradle 本地缓存的 "404 / module not found"
// 结果一直挡住）。
configurations.all {
    resolutionStrategy {
        // changing 模块（例如 JitPack 的版本号固定但 artifact 是懒构建）不缓存
        cacheChangingModulesFor(0, "seconds")
        // 动态版本（1.+、[1.0, 2.0) 等）不缓存，本项目未用，保留默认即可。
    }
}

dependencies {
}

flutter {
    source = "../.."
}