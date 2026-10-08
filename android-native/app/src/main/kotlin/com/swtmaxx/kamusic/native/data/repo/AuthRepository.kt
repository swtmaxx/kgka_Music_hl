package com.swtmaxx.kamusic.native.data.repo

import com.swtmaxx.kamusic.native.core.Session
import com.swtmaxx.kamusic.native.core.SessionStore
import com.swtmaxx.kamusic.native.data.api.LoginJudgement
import com.swtmaxx.kamusic.native.data.api.MusicApi
import com.swtmaxx.kamusic.native.data.api.PhoneLoginResult
import com.swtmaxx.kamusic.native.data.model.QrCheckResult
import com.swtmaxx.kamusic.native.data.model.QrCodeInfo

/** 登录 / 登出 / 会话持久化。 */
class AuthRepository(
    private val api: MusicApi,
    private val sessionStore: SessionStore,
) {

    val session: Session get() = sessionStore.session

    val isLoggedIn: Boolean get() = sessionStore.session.isLoggedIn

    suspend fun sendLoginCode(mobile: String) = api.sendLoginCode(mobile)

    suspend fun loginWithPhone(
        mobile: String,
        code: String,
        userId: String? = null,
    ): PhoneLoginResult {
        val result = api.loginWithPhone(mobile, code, userId)
        if (result is PhoneLoginResult.Success) {
            sessionStore.saveSession(
                Session(
                    userId = result.session.userId,
                    token = result.session.token,
                    t1 = result.session.t1,
                    sessionId = result.session.sessionId,
                ),
            )
        }
        return result
    }

    suspend fun getQrCode(): QrCodeInfo = api.getQrCode()

    suspend fun checkQrStatus(key: String): QrCheckResult = api.checkQrStatus(key)

    /** 扫码确认后回填会话。 */
    suspend fun saveQrSession(result: QrCheckResult) {
        sessionStore.saveSession(
            Session(
                userId = result.userId,
                token = result.token,
                t1 = result.t1,
                sessionId = sessionStore.session.sessionId,
            ),
        )
    }

    /** 用持久化的 token 换新会话；失败返回 false。 */
    suspend fun refreshToken(): Boolean = runCatching {
        val refreshed = api.refreshToken()
        if (refreshed.token.isNullOrEmpty() && refreshed.userId.isNullOrEmpty()) {
            false
        } else {
            sessionStore.saveSession(
                Session(
                    userId = refreshed.userId,
                    token = refreshed.token,
                    t1 = refreshed.t1,
                    sessionId = refreshed.sessionId ?: sessionStore.session.sessionId,
                ),
            )
            true
        }
    }.getOrDefault(false)

    /**
     * 登出：仅清本地会话。
     *
     * 部署端 KuGouMusicApi 没有 `login_logout` 模块，`/login/logout` 不存在，
     * 因此不发网络请求（与 Flutter 版行为不同，此处更安全）。
     */
    suspend fun logout() = sessionStore.clearSession()

    /** 供 UI 显示的错误文案。 */
    fun describe(result: PhoneLoginResult): String = when (result) {
        is PhoneLoginResult.Success -> ""
        is PhoneLoginResult.AccountSelection -> result.message
        is PhoneLoginResult.Failure -> result.message
    }

    companion object {
        const val REGISTER_HINT = LoginJudgement.REGISTER_HINT
    }
}
