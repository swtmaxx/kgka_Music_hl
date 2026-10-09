package com.swtmaxx.kamusic.compose.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** 接口调用失败。`statusCode` 仅在 HTTP 层失败时有值。 */
class ApiException(message: String, val statusCode: Int? = null) : Exception(message)

/**
 * [ApiClient] 对会话与地址的最小依赖面。
 *
 * 抽成接口而不是直接依赖 SessionStore，是为了让 HTTP 层能在 JVM 单测里
 * 用假实现驱动（SessionStore 依赖 Android Context，无法在本地单测实例化）。
 */
interface ApiSessionSource {
    val session: Session

    /** 生效的 API 根地址（用户自定义优先）。 */
    val effectiveApiBaseUrl: String

    suspend fun updateSessionId(sessionId: String)
}

/**
 * 酷狗外置 API 客户端。
 *
 * 传输契约与 Flutter 版 `lib/core/api_client.dart` **逐字对齐**：
 * - 请求头 `X-Kg-Session-Id` / `t1` 携带登录态
 * - 响应头 `x-kg-session-id` 回写会话
 * - `data` 仅在是对象/数组时解包（见 [unwrapData]）
 * - 仅对 5xx 与连接异常重试，最多 2 次，退避 500ms / 1000ms
 * - 单请求超时 20s
 */
class ApiClient(
    private val http: OkHttpClient,
    private val sessionStore: ApiSessionSource,
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** 当前会话的 sessionId（服务端下发），供登录结果回填。 */
    fun sessionId(): String? = sessionStore.session.sessionId

    /** 当前会话快照。 */
    fun session(): Session = sessionStore.session

    suspend fun get(path: String, query: Map<String, Any?> = emptyMap()): JsonElement? =
        execute(buildRequest("GET", path, query, null), unwrap = true)

    suspend fun post(
        path: String,
        query: Map<String, Any?> = emptyMap(),
        body: Map<String, Any?>? = null,
    ): JsonElement? = execute(buildRequest("POST", path, query, body), unwrap = true)

    /**
     * 与 [get] 相同，但**不解包 `data`**，保留顶层 `status` / `error_code`。
     *
     * 用于需要读信封本身的接口。典型例子：`/captcha/sent` 成功时返回
     * `{"data":{"count":8},"status":1,"error_code":0}`，
     * `data` 是对象 → 走 [get] 会被解包成 `{"count":8}`，顶层 `status` 就没了。
     */
    suspend fun getRaw(path: String, query: Map<String, Any?> = emptyMap()): JsonElement? =
        execute(buildRequest("GET", path, query, null), unwrap = false)

    /** 与 [post] 相同，但**不解包 `data`**。 */
    suspend fun postRaw(
        path: String,
        query: Map<String, Any?> = emptyMap(),
        body: Map<String, Any?>? = null,
    ): JsonElement? = execute(buildRequest("POST", path, query, body), unwrap = false)

    // ===== 内部实现 =====

    private fun buildRequest(
        method: String,
        path: String,
        query: Map<String, Any?>,
        body: Map<String, Any?>?,
    ): Request {
        val url = buildUrl(path, query)
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")

        sessionStore.session.sessionHeader?.let { builder.header("X-Kg-Session-Id", it) }
        sessionStore.session.t1?.takeIf { it.isNotEmpty() }?.let { builder.header("t1", it) }

        when (method) {
            "POST" -> {
                val payload = body?.let { KaJson.encodeToString(JsonObject.serializer(), toJsonObject(it)) }
                    ?: ""
                builder.post(payload.toRequestBody(jsonMediaType))
            }
            else -> builder.get()
        }
        return builder.build()
    }

    private fun buildUrl(path: String, query: Map<String, Any?>): HttpUrl {
        val base = sessionStore.effectiveApiBaseUrl.trim().trimEnd('/')
        val full = "$base/${path.trimStart('/')}"
        val parsed = full.toHttpUrlOrNull() ?: throw ApiException("无效的 API 地址：$full")
        val builder = parsed.newBuilder()
        for ((key, value) in query) {
            if (value == null) continue
            when (value) {
                // 列表参数展开成重复键（与 Dart Uri.replace(queryParameters:) 行为一致）
                is Iterable<*> -> value.forEach { item ->
                    val s = item?.toString().orEmpty()
                    if (s.isNotEmpty()) builder.addQueryParameter(key, s)
                }
                is Array<*> -> value.forEach { item ->
                    val s = item?.toString().orEmpty()
                    if (s.isNotEmpty()) builder.addQueryParameter(key, s)
                }
                else -> {
                    val s = value.toString()
                    if (s.isNotEmpty()) builder.addQueryParameter(key, s)
                }
            }
        }
        return builder.build()
    }

    private suspend fun execute(request: Request, unwrap: Boolean): JsonElement? = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                val response = http.newCall(request).execute()
                val code = response.code
                val headerSessionId = response.header("x-kg-session-id")
                val text = response.body?.string().orEmpty()
                response.close()

                if (!headerSessionId.isNullOrEmpty()) {
                    sessionStore.updateSessionId(headerSessionId)
                }

                // 5xx 可重试
                if (code >= 500 && attempt < MAX_RETRIES) {
                    delay(500L shl attempt)
                    continue
                }
                if (code !in 200..299) {
                    throw ApiException(text.ifBlank { "HTTP $code" }, code)
                }
                if (text.isBlank()) return@withContext null

                val parsed = runCatching { KaJson.parseToJsonElement(text) }.getOrNull()
                    ?: return@withContext JsonPrimitive(text)
                return@withContext if (unwrap) unwrapData(parsed) else parsed
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                if (attempt < MAX_RETRIES) {
                    delay(500L shl attempt)
                    continue
                }
                throw ApiException("网络连接失败：${e.message ?: "未知错误"}")
            }
        }
        throw ApiException("请求失败：${lastError?.message ?: "已重试 $MAX_RETRIES 次"}")
    }

    private companion object {
        const val MAX_RETRIES = 2
    }
}

/** 把任意 Kotlin 值转成 JsonElement（供 POST body 使用）。 */
fun toJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to toJsonElement(it.value) })
    is Iterable<*> -> JsonArray(value.map { toJsonElement(it) })
    is Array<*> -> JsonArray(value.map { toJsonElement(it) })
    else -> JsonPrimitive(value.toString())
}

fun toJsonObject(map: Map<String, Any?>): JsonObject =
    JsonObject(map.entries.associate { it.key to toJsonElement(it.value) })
