package com.swtmaxx.kamusic.compose.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * `getRaw` / `postRaw` 契约测试。
 *
 * `/captcha/sent` 成功响应形如
 * `{"data":{"count":8},"status":1,"error_code":0}`：
 * - [ApiClient.post] 会把对象型 `data` 解包，顶层 `status` 丢失；
 * - [ApiClient.postRaw] 保留整个信封，`status` / `error_code` 可读。
 */
class RawEnvelopeTest {

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

    private fun enqueue(body: String) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
    }

    @Test
    fun `postRaw 保留顶层 status 与 error_code`() = runTest {
        enqueue("""{"data":{"count":8},"status":1,"error_code":0}""")
        val obj = api.postRaw("/x")?.jsonObject
        assertNotNull(obj)
        assertEquals(1, obj!!["status"]?.jsonPrimitive?.content?.toInt())
        assertEquals(0, obj["error_code"]?.jsonPrimitive?.content?.toInt())
        assertNotNull("data 应仍然存在", obj["data"])
    }

    @Test
    fun `post 解包 data 后丢失顶层 status`() = runTest {
        enqueue("""{"data":{"count":8},"status":1,"error_code":0}""")
        val obj = api.post("/x")?.jsonObject
        assertNotNull(obj)
        assertEquals(8, obj!!["count"]?.jsonPrimitive?.content?.toInt())
        assertNull("解包后不应残留 status", obj["status"])
    }
}
