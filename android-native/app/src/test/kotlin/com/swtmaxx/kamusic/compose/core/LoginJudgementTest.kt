package com.swtmaxx.kamusic.compose.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.swtmaxx.kamusic.compose.data.api.LoginJudgement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 登录判据（双轨）测试。
 *
 * 这是本项目最容易踩坑的地方：两种后端的信封不同 ——
 * hoilai 把凭据平铺顶层并带 status；KuGouMusicApi 把结果包在 data 里，
 * 解包后顶层没有 status。因此必须 `status==1` **或** token+userid 非空。
 */
class LoginJudgementTest {

    private fun obj(vararg pairs: Pair<String, String?>): JsonObject =
        JsonObject(pairs.mapNotNull { (k, v) -> v?.let { k to JsonPrimitive(it) } }.toMap())

    @Test
    fun `status 为 1 判成功`() {
        assertTrue(LoginJudgement.isSuccess(obj("status" to "1")))
    }

    @Test
    fun `status 为 0 但 token 与 userid 非空仍判成功`() {
        // KuGouMusicApi 解包后的形状：没有 status，但有凭据
        val json = obj("token" to "tk", "userid" to "42")
        assertTrue(LoginJudgement.isSuccess(json))
    }

    @Test
    fun `status 为 0 且缺凭据判失败`() {
        val json = obj("status" to "0", "error_code" to "20020", "data" to "验证码过期")
        assertFalse(LoginJudgement.isSuccess(json))
    }

    @Test
    fun `只有 token 没有 userid 判失败`() {
        assertFalse(LoginJudgement.isSuccess(obj("token" to "tk")))
    }

    @Test
    fun `只有 userid 没有 token 判失败`() {
        assertFalse(LoginJudgement.isSuccess(obj("userid" to "42")))
    }

    @Test
    fun `空字符串凭据不算数`() {
        assertFalse(LoginJudgement.isSuccess(obj("token" to "", "userid" to "")))
    }

    @Test
    fun `错误码兼容下划线与驼峰两种键名`() {
        assertEquals(20006, LoginJudgement.errorCode(obj("error_code" to "20006")))
        assertEquals(20006, LoginJudgement.errorCode(obj("errorCode" to "20006")))
        assertEquals(20006, LoginJudgement.errorCode(obj("errcode" to "20006")))
    }

    @Test
    fun `失败文案包含错误码与提示`() {
        val suffix = LoginJudgement.failureSuffix(
            obj("status" to "0", "error_code" to "20020", "data" to "验证码过期"),
        )
        assertTrue(suffix, suffix.contains("验证码过期"))
        assertTrue(suffix, suffix.contains("20020"))
    }

    @Test
    fun `需要选择账号时识别 accounts 字段`() {
        val json = JsonObject(
            mapOf(
                "accounts" to kotlinx.serialization.json.JsonArray(
                    listOf(JsonObject(mapOf("userid" to JsonPrimitive("1")))),
                ),
            ),
        )
        assertTrue(LoginJudgement.requiresUserSelection(json))
    }

    @Test
    fun `没有 accounts 字段时不要求选择账号`() {
        assertFalse(LoginJudgement.requiresUserSelection(obj("token" to "tk")))
    }
}
