package com.swtmaxx.kamusic.compose.core

import android.content.Context
import com.swtmaxx.kamusic.compose.data.api.MusicApi
import com.swtmaxx.kamusic.compose.data.repo.AuthRepository
import com.swtmaxx.kamusic.compose.data.repo.MusicRepository
import com.swtmaxx.kamusic.compose.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient

/**
 * 手动依赖注入容器。
 *
 * 刻意不引入 Hilt/Koin：本项目依赖图很浅（1 个 HTTP 客户端 + 2 个仓储），
 * 手写容器既省体积（无注解处理器、无 KSP）又省编译时间。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val sessionStore: SessionStore = SessionStore(appContext)

    /** 下载记录（DataStore 单键 JSON）。 */
    val localStore: LocalStore = LocalStore(appContext)

    private val http: OkHttpClient = createOkHttpClient()

    val apiClient: ApiClient = ApiClient(http, sessionStore)

    private val musicApi: MusicApi = MusicApi(apiClient)

    val authRepository: AuthRepository = AuthRepository(musicApi, sessionStore)

    val musicRepository: MusicRepository = MusicRepository(musicApi, sessionStore)

    /** 下载器（并发 1 + 断点续传 + 原子落盘）。 */
    val downloader: Downloader = Downloader(appContext, http, musicRepository, localStore)

    val playbackController: PlaybackController = PlaybackController(appContext, appScope)

    /** 从 DataStore 恢复登录态、设置与下载记录。UI 应等到 [SessionStore.ready] 为 true 再渲染。 */
    suspend fun hydrate() {
        sessionStore.hydrate()
        localStore.hydrate()
    }

    fun shutdown() {
        appScope.cancel()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }
}
