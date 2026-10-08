package com.swtmaxx.kamusic.native.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** 共享的空 JSON 对象，避免各处重复构造。 */
val EMPTY_JSON_OBJECT: JsonObject = JsonObject(emptyMap())

/** 全局 JSON 配置。宽容解析：服务端字段经常增删，不能让 App 崩。 */
val KaJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/**
 * 解包统一响应信封。
 *
 * **仅当顶层 `data` 是结构化载荷（对象/数组）时才解包**。
 *
 * 这是与 Flutter 版逐字对齐的关键规则：登录失败时服务端返回
 * ```
 * {"userid":null,"token":null,"t1":null,"data":"验证码过期","status":0,"error_code":20020}
 * ```
 * 此时 `data` 是提示文案。若把它当结果返回，就会丢掉 `status` / `error_code`，
 * 上层只能把「验证码过期」误报成「登录失败」且拿不到错误码。
 */
fun unwrapData(element: JsonElement?): JsonElement? {
    val obj = element as? JsonObject ?: return element
    val data = obj["data"] ?: return element
    return if (data is JsonObject || data is JsonArray) data else element
}

// ===== JsonElement 便捷访问器（对齐 Dart 版 asMap/asString/asInt/asList 的语义） =====

fun JsonElement?.asObjOrNull(): JsonObject? = this as? JsonObject

fun JsonElement?.asArrOrNull(): JsonArray? = this as? JsonArray

fun JsonElement?.asStringOrNull(): String? = when (this) {
    null, is JsonNull -> null
    is JsonPrimitive -> content.takeIf { it.isNotEmpty() && it != "null" }
    else -> null
}

fun JsonElement?.asIntOrNull(): Int? = when (this) {
    null, is JsonNull -> null
    is JsonPrimitive -> intOrNull ?: doubleOrNull?.toInt() ?: content.toIntOrNull()
    else -> null
}

fun JsonElement?.asLongOrNull(): Long? = when (this) {
    null, is JsonNull -> null
    is JsonPrimitive -> longOrNull ?: doubleOrNull?.toLong() ?: content.toLongOrNull()
    else -> null
}

fun JsonElement?.asDoubleOrNull(): Double? = when (this) {
    null, is JsonNull -> null
    is JsonPrimitive -> doubleOrNull ?: content.toDoubleOrNull()
    else -> null
}

fun JsonElement?.asBoolOrNull(): Boolean? = when (this) {
    null, is JsonNull -> null
    is JsonPrimitive -> booleanOrNull
        ?: when (content.lowercase()) {
            "1", "true", "yes" -> true
            "0", "false", "no" -> false
            else -> null
        }
    else -> null
}

/** 字符串字段读取：数字/布尔也会被转成字符串（服务端 id 类型不稳定）。 */
fun JsonObject?.str(key: String): String? = this?.get(key).asStringOrNull()

fun JsonObject?.int(key: String): Int? = this?.get(key).asIntOrNull()

fun JsonObject?.long(key: String): Long? = this?.get(key).asLongOrNull()

fun JsonObject?.double(key: String): Double? = this?.get(key).asDoubleOrNull()

fun JsonObject?.bool(key: String): Boolean? = this?.get(key).asBoolOrNull()

fun JsonObject?.obj(key: String): JsonObject? = this?.get(key).asObjOrNull()

fun JsonObject?.arr(key: String): JsonArray? = this?.get(key).asArrOrNull()

/** 依次尝试多个键，返回第一个非空字符串。 */
fun JsonObject?.strAny(vararg keys: String): String? {
    for (k in keys) {
        val v = str(k)
        if (!v.isNullOrEmpty()) return v
    }
    return null
}

/** 依次尝试多个键，返回第一个非空整数。 */
fun JsonObject?.intAny(vararg keys: String): Int? {
    for (k in keys) {
        val v = int(k)
        if (v != null) return v
    }
    return null
}

/** 安全地把任意 JsonElement 转成对象列表（跳过非对象元素）。 */
fun JsonElement?.objList(): List<JsonObject> =
    this.asArrOrNull()?.mapNotNull { it.asObjOrNull() } ?: emptyList()
