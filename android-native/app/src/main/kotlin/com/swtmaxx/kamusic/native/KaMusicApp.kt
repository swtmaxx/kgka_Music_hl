package com.swtmaxx.kamusic.native

import android.app.Application
import com.swtmaxx.kamusic.native.core.AppContainer
import kotlinx.coroutines.launch

class KaMusicApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 恢复登录态；UI 通过 SessionStore.ready 等待完成。
        container.appScope.launch { container.hydrate() }
    }
}
