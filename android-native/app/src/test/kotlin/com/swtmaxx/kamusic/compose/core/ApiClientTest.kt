package com.swtmaxx.kamusic.compose.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 可变的假会话源，替代依赖 Android Context 的 SessionStore。 */
private class FakeSessions(
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

/**
 * HTTP 层契约测试。
 *
 * 覆盖与 Flutter 版逐字对齐的四条规则：
 * 1. `data` 是对象/数组时解包
 * 2. `data` 是字符串时**保持信封**（否则登录失败会丢 status/error_code）
 * 3. 响应头 `x-kg-session-id` 写回会话
 * 4. 非 2xx 抛 ApiException 并带 statusCode
 * 另外验证请求头携带 `X-Kg-Session-Id` / `t1`。
 */
class ApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var sessions: FakeSessions
    private lateinit var api: ApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sessions = FakeSessions(effectiveApiBaseUrl = server.url("/").toString().trimEnd('/'))
        api = ApiClient(createOkHttpClient(), sessions)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(body: String, code: Int = 200, sessionId: String? = null) {
        val response = MockResponse().setResponseCode(code).setBody(body)
        sessionId?.let { response.addHeader("x-kg-session-id", it) }
        server.enqueue(response)
    }

    @Test
    fun `data 是对象时解包`() = runTest {
        enqueue("""{"status":1,"data":{"userid":"42","token":"tk"}}""")
        val result = api.get("/x")
        val obj = result?.jsonObject
        assertNotNull(obj)
        assertEquals("42", obj!!["userid"]?.jsonPrimitive?.content)
        assertNull("解包后不应残留 status", obj["status"])
    }

    @Test
    fun `data 是字符串时保持整个信封`() = runTest {
        // 登录失败的典型响应：data 是提示文案
        enqueue("""{"userid":null,"token":null,"data":"验证码过期","status":0,"error_code":20020}""")
        val obj = api.get("/login/cellphone")?.jsonObject
        assertNotNull(obj)
        assertEquals("验证码过期", obj!!["data"]?.jsonPrimitive?.content)
        assertEquals(0, obj["status"]?.jsonPrimitive?.content?.toInt())
        assertEquals(20020, obj["error_code"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `data 是数组时解包`() = runTest {
        enqueue("""{"status":1,"data":[{"a":1},{"a":2}]}""")
        val result = api.get("/x")
        assertTrue(result is kotlinx.serialization.json.JsonArray)
        assertEquals(2, (result as kotlinx.serialization.json.JsonArray).size)
    }

    @Test
    fun `响应头 sessionId 写回会话`() = runTest {
        enqueue("""{"status":1}""", sessionId = "sid-abc")
        api.get("/x")
        assertEquals("sid-abc", sessions.session.sessionId)
        assertEquals(1, sessions.updateCount)
    }

    @Test
    fun `非 2xx 抛出带状态码的异常`() = runTest {
        enqueue("""{"error":"boom"}""", code = 400)
        val error = runCatching { api.get("/x") }.exceptionOrNull()
        assertTrue(error is ApiException)
        assertEquals(400, (error as ApiException).statusCode)
    }

    @Test
    fun `请求头携带会话与 t1`() = runTest {
        sessions = FakeSessions(
            effectiveApiBaseUrl = server.url("/").toString().trimEnd('/'),
            initial = Session(userId = "1", token = "tok", t1 = "t1value", sessionId = "sid"),
        )
        api = ApiClient(createOkHttpClient(), sessions)
        enqueue("""{"status":1}""")

        api.get("/x")
        val recorded = server.takeRequest()
        // sessionId 优先于 token
        assertEquals("sid", recorded.getHeader("X-Kg-Session-Id"))
        assertEquals("t1value", recorded.getHeader("t1"))
    }

    @Test
    fun `query 中空值与 null 被跳过`() = runTest {
        enqueue("""{"status":1}""")
        api.get("/x", mapOf("a" to "1", "b" to null, "c" to ""))
        val path = server.takeRequest().path.orEmpty()
        assertTrue(path.contains("a=1"))
        assertTrue(!path.contains("b="))
        assertTrue(!path.contains("c="))
    }

    @Test
    fun `列表参数展开成重复键`() = runTest {
        enqueue("""{"status":1}""")
        api.get("/x", mapOf("fmid" to listOf("11", "22")))
        val path = server.takeRequest().path.orEmpty()
        assertTrue(path, path.contains("fmid=11"))
        assertTrue(path, path.contains("fmid=22"))
    }

    @Test
    fun `POST body 以 JSON 发送`() = runTest {
        enqueue("""{"status":1}""")
        api.post("/login/cellphone", body = mapOf("mobile" to "13800000000", "code" to "1234"))
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertTrue(body, body.contains("\"mobile\":\"13800000000\""))
        assertTrue(body, body.contains("\"code\":\"1234\""))
    }

    @Test
    fun `空响应体返回 null`() = runTest {
        enqueue("")
        assertNull(api.get("/x"))
    }

    @Test
    fun `非 JSON 响应体原样返回为字符串`() = runTest {
        enqueue("not json at all")
        val result = api.get("/x")
        assertTrue(result is JsonPrimitive)
        assertEquals("not json at all", (result as JsonPrimitive).content)
    }
}
