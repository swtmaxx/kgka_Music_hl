package com.swtmaxx.kamusic.native.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.swtmaxx.kamusic.native.core.AppContainer

/** 全局依赖容器。由 MainActivity 注入。 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer 未提供，请检查 MainActivity 的 CompositionLocalProvider")
}
