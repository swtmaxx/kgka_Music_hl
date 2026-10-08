// 顶层构建脚本：只声明插件版本，不 apply。
//
// 版本组合刻意与已验证可用的 Flutter 工程（../android）保持一致：
//   Gradle 8.14 / AGP 8.11.1 / Kotlin 2.2.20 / Java 17
// Kotlin 2.x 起 Compose 编译器随 Kotlin 版本走（org.jetbrains.kotlin.plugin.compose），
// 不再需要手动对齐 compiler 与 Kotlin 的版本。
plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" apply false
}
