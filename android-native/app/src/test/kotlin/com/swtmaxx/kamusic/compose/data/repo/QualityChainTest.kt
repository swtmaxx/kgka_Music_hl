package com.swtmaxx.kamusic.compose.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音质降级链测试。
 *
 * 服务端对没有对应音质的歌会返回空 url（例如只有 128 的歌请求 flac）。
 * 不降级的话用户设了「无损」就会遇到一大片「无法获取播放地址」。
 */
class QualityChainTest {

    @Test
    fun `无损档依次降级到 320 与 128`() {
        assertEquals(listOf("flac", "320", "128"), qualityChain("flac"))
    }

    @Test
    fun `高品档降级到 128`() {
        assertEquals(listOf("320", "128"), qualityChain("320"))
    }

    @Test
    fun `标准档只试一次`() {
        assertEquals(listOf("128"), qualityChain("128"))
    }

    @Test
    fun `未知档位按标准档处理`() {
        assertEquals(listOf("128"), qualityChain("unknown"))
        assertEquals(listOf("128"), qualityChain(""))
    }

    @Test
    fun `降级链的第一项永远是用户选择的档位`() {
        listOf("flac", "320", "128").forEach { preferred ->
            assertEquals(preferred, qualityChain(preferred).first())
        }
    }

    @Test
    fun `降级链最后一项永远是 128（兼容性最好的档位）`() {
        listOf("flac", "320", "128").forEach { preferred ->
            assertEquals("128", qualityChain(preferred).last())
        }
    }

    @Test
    fun `降级链不含重复项且长度递减`() {
        assertEquals(3, qualityChain("flac").distinct().size)
        assertEquals(2, qualityChain("320").distinct().size)
        assertEquals(1, qualityChain("128").distinct().size)
        assertTrue(qualityChain("flac").size > qualityChain("320").size)
        assertTrue(qualityChain("320").size > qualityChain("128").size)
    }
}
