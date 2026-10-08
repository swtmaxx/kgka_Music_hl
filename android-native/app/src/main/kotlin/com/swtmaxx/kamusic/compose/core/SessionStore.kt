package com.swtmaxx.kamusic.compose.core

import com.swtmaxx.kamusic.compose.BuildConfig
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** 登录态。字段命名与服务端一致，便于排查。 */
data class Session(
    val userId: String? = null,
    val token: String? = null,
    val t1: String? = null,
    val sessionId: String? = null,
) {
    val isLoggedIn: Boolean
        get() = !token.isNullOrEmpty() || !sessionId.isNullOrEmpty()

    /**
     * `X-Kg-Session-Id` 头的取值。
     * sessionId 才是服务端下发的真实会话，token 仅作为扫码/登录早期的兼容值。
     */
    val sessionHeader: String?
        get() = sessionId?.takeIf { it.isNotEmpty() } ?: token?.takeIf { it.isNotEmpty() }

    companion object {
        val EMPTY = Session()
    }
}

private val Context.sessionDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "ka_music_session")

/**
 * 登录态与设置的持久化（DataStore）。
 *
 * 同时维护一份**内存副本**（`@Volatile`），因为 HTTP 层需要在构造请求头时
 * 同步读取会话，不能走 suspend。
 */
class SessionStore(private val context: Context) : ApiSessionSource {

    private object Keys {
        val userId = stringPreferencesKey("user_id")
        val token = stringPreferencesKey("token")
        val t1 = stringPreferencesKey("t1")
        val sessionId = stringPreferencesKey("session_id")
        val apiBaseUrl = stringPreferencesKey("api_base_url")
        val quality = stringPreferencesKey("quality")
    }

    @Volatile
    override var session: Session = Session.EMPTY
        private set

    private val _sessionFlow = MutableStateFlow(Session.EMPTY)

    /** 响应式会话流，供 Compose 驱动登录门。 */
    val sessionFlow: StateFlow<Session> = _sessionFlow.asStateFlow()

    @Volatile
    var customApiBaseUrl: String? = null
        private set

    private val _apiBaseUrlFlow = MutableStateFlow<String?>(null)

    /** 响应式：用户自定义的 API 地址。 */
    val apiBaseUrlFlow: StateFlow<String?> = _apiBaseUrlFlow.asStateFlow()

    @Volatile
    var quality: String = DEFAULT_QUALITY
        private set

    private val _ready = MutableStateFlow(false)

    /** DataStore 首次读取完成。UI 应等到 true 再决定进登录页还是主页。 */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** 生效的 API 根地址：用户自定义优先，否则用编译期默认值。 */
    override val effectiveApiBaseUrl: String
        get() = customApiBaseUrl?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_API_BASE_URL

    suspend fun hydrate() {
        val prefs = runCatching { context.sessionDataStore.data.first() }.getOrNull()
        if (prefs != null) {
            session = Session(
                userId = prefs[Keys.userId],
                token = prefs[Keys.token],
                t1 = prefs[Keys.t1],
                sessionId = prefs[Keys.sessionId],
            )
            _sessionFlow.value = session
            customApiBaseUrl = prefs[Keys.apiBaseUrl]?.takeIf { it.isNotBlank() }
            _apiBaseUrlFlow.value = customApiBaseUrl
            quality = prefs[Keys.quality] ?: DEFAULT_QUALITY
        }
        _ready.value = true
    }

    suspend fun saveSession(newSession: Session) {
        session = newSession
        _sessionFlow.value = newSession
        runCatching {
            context.sessionDataStore.edit { prefs ->
                newSession.userId?.let { prefs[Keys.userId] = it } ?: prefs.remove(Keys.userId)
                newSession.token?.let { prefs[Keys.token] = it } ?: prefs.remove(Keys.token)
                newSession.t1?.let { prefs[Keys.t1] = it } ?: prefs.remove(Keys.t1)
                newSession.sessionId?.let { prefs[Keys.sessionId] = it } ?: prefs.remove(Keys.sessionId)
            }
        }
    }

    /** 外置服务器会持续通过响应头下发新的 sessionId，这里增量更新。 */
    override suspend fun updateSessionId(sessionId: String) {
        if (sessionId.isEmpty() || session.sessionId == sessionId) return
        saveSession(session.copy(sessionId = sessionId))
    }

    /**
     * 登出：只清本地会话。
     *
     * 部署端 KuGouMusicApi 没有 `login_logout` 模块（`/login/logout` 不存在），
     * 因此不发网络请求。
     */
    suspend fun clearSession() {
        session = Session.EMPTY
        _sessionFlow.value = Session.EMPTY
        runCatching {
            context.sessionDataStore.edit { prefs ->
                prefs.remove(Keys.userId)
                prefs.remove(Keys.token)
                prefs.remove(Keys.t1)
                prefs.remove(Keys.sessionId)
            }
        }
    }

    suspend fun setCustomApiBaseUrl(url: String?) {
        val trimmed = url?.trim()?.takeIf { it.isNotEmpty() }
        customApiBaseUrl = trimmed
        _apiBaseUrlFlow.value = trimmed
        runCatching {
            context.sessionDataStore.edit { prefs ->
                if (trimmed == null) prefs.remove(Keys.apiBaseUrl) else prefs[Keys.apiBaseUrl] = trimmed
            }
        }
    }

    suspend fun setQuality(value: String) {
        val normalized = QUALITY_OPTIONS.firstOrNull { it.first == value }?.first ?: DEFAULT_QUALITY
        quality = normalized
        runCatching {
            context.sessionDataStore.edit { prefs -> prefs[Keys.quality] = normalized }
        }
    }

    companion object {
        const val DEFAULT_QUALITY = "128"

        /** (API 参数值, 展示名) */
        val QUALITY_OPTIONS = listOf(
            "128" to "标准",
            "320" to "高品",
            "flac" to "无损",
        )
    }
}
