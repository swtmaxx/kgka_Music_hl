package com.swtmaxx.kamusic.compose.core

/**
 * 可变的假会话源，替代依赖 Android Context 的 SessionStore。
 *
 * 供 [ApiClientTest] / [RawEnvelopeTest] 共用：同一包内两个测试文件各自
 * 声明 `private class FakeSessions` 会在 JVM 层产生同名类导致重声明编译错误，
 * 因此提取到这里。
 */
internal class FakeSessions(
    override var effectiveApiBaseUrl: String,
    initial: Session = Session.EMPTY,
) : ApiSessionSource {
    override var session: Session = initial
        private set

    var updateCount = 0
        private set

    override suspend fun updateSessionId(sessionId: String) {
        updateCount++
        session = session.copy(sessionId = sessionId)
    }
}
