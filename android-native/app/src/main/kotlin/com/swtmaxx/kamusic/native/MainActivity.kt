package com.swtmaxx.kamusic.native

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.swtmaxx.kamusic.native.core.AppContainer
import com.swtmaxx.kamusic.native.ui.LocalAppContainer
import com.swtmaxx.kamusic.native.ui.KaMusicRoot
import com.swtmaxx.kamusic.native.ui.theme.KaMusicTheme

/**
 * 单 Activity 应用。
 *
 * 手表是竖屏设备，方向由系统决定；这里不做方向锁定，
 * 布局全部基于可用尺寸自适应（见 WatchMetrics）。
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as KaMusicApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                KaMusicTheme {
                    KaMusicRoot()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.playbackController.connect()
    }
}
