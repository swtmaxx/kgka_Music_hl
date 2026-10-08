package com.swtmaxx.kamusic.compose.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.swtmaxx.kamusic.compose.core.AppContainer

/** 全局依赖容器。由 MainActivity 注入。 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer 未提供，请检查 MainActivity 的 CompositionLocalProvider")
}
