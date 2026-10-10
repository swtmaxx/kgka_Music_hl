package com.swtmaxx.kamusic.compose.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放错误文案映射测试。
 *
 * 直接显示 `error.message` 会得到
 * `Source error: InvalidResponseCodeException: Response code: 403`
 * 这种串，在 240px 的屏上又长又无意义。
 */
class PlaybackErrorTest {

    private fun error(code: Int, message: String = "raw"): PlaybackException =
        PlaybackException(message, null, code)

    @Test
    fun `网络失败翻译为中文`() {
        assertEquals(
            "网络连接失败",
            friendlyMessage(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)),
        )
        assertEquals(
            "网络连接失败",
            friendlyMessage(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)),
        )
    }

    @Test
    fun `HTTP 异常与文件问题分别翻译`() {
        assertEquals(
            "服务器返回异常",
            friendlyMessage(error(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)),
        )
        assertEquals(
            "文件不存在",
            friendlyMessage(error(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)),
        )
        assertEquals(
            "没有读取权限",
            friendlyMessage(error(PlaybackException.ERROR_CODE_IO_NO_PERMISSION)),
        )
    }

    @Test
    fun `解析失败翻译为文件损坏`() {
        assertEquals(
            "音频文件损坏",
            friendlyMessage(error(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)),
        )
        assertEquals(
            "音频文件损坏",
            friendlyMessage(error(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED)),
        )
    }

    @Test
    fun `解码器问题翻译为格式不支持`() {
        assertEquals(
            "不支持这种音频格式",
            friendlyMessage(error(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)),
        )
        assertEquals(
            "不支持这种音频格式",
            friendlyMessage(error(PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED)),
        )
        assertEquals(
            "不支持这种音频格式",
            friendlyMessage(error(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)),
        )
    }

    @Test
    fun `未知错误码回落到原始 message`() {
        assertEquals("raw", friendlyMessage(error(999_999, "raw")))
    }

    @Test
    fun `未知错误码且 message 为空时给出兜底文案`() {
        assertEquals("播放失败", friendlyMessage(error(999_999, "")))
    }

    @Test
    fun `所有映射结果都非空`() {
        val codes = listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        )
        codes.forEach { code ->
            assertTrue(
                "错误码 $code 的文案不应为空",
                friendlyMessage(error(code, "")).isNotEmpty(),
            )
        }
    }

    @Test
    fun `睡眠定时模式枚举齐全`() {
        assertEquals(3, SleepTimerMode.entries.size)
        assertTrue(SleepTimerMode.entries.contains(SleepTimerMode.OFF))
        assertTrue(SleepTimerMode.entries.contains(SleepTimerMode.COUNTDOWN))
        assertTrue(SleepTimerMode.entries.contains(SleepTimerMode.END_OF_TRACK))
    }
}
