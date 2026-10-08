package com.swtmaxx.kamusic.native.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * 歌词解析测试。
 *
 * 覆盖 KRC 逐字时间轴、offset 偏移、同时间戳翻译合并、LRC 回退、
 * `[language:...]` 翻译/音译合并，以及异常输入不抛异常。
 */
class LyricParserTest {

    @Test
    fun `KRC 解析出逐字时间轴`() {
        val content = "[1000,2000]<0,500,0>你<500,500,0>好<1000,1000,0>世界"
        val lines = LyricParser.parse(content)
        assertEquals(1, lines.size)

        val line = lines[0]
        assertEquals(1000L, line.timeMs)
        assertEquals(2000L, line.durationMs)
        assertEquals("你好世界", line.text)
        assertTrue(line.hasWordTiming)
        assertEquals(3, line.words.size)

        // 字时间 = 行起始 + 字偏移
        assertEquals(1000L, line.words[0].timeMs)
        assertEquals(1500L, line.words[1].timeMs)
        assertEquals(2000L, line.words[2].timeMs)
        assertEquals(500L, line.words[0].durationMs)
    }

    @Test
    fun `KRC 应用 offset 偏移`() {
        val content = "[offset:-500]\n[1000,2000]<0,1000,0>甲"
        val lines = LyricParser.parse(content)
        assertEquals(1, lines.size)
        assertEquals(500L, lines[0].timeMs)
        assertEquals(500L, lines[0].words[0].timeMs)
    }

    @Test
    fun `KRC 多行按时间排序`() {
        val content = "[3000,1000]<0,1000,0>后\n[1000,1000]<0,1000,0>前"
        val lines = LyricParser.parse(content)
        assertEquals(2, lines.size)
        assertEquals("前", lines[0].text)
        assertEquals("后", lines[1].text)
    }

    @Test
    fun `activeWordIndex 返回已唱到的字`() {
        val lines = LyricParser.parse("[1000,3000]<0,1000,0>甲<1000,1000,0>乙<2000,1000,0>丙")
        val line = lines[0]
        assertEquals(-1, line.activeWordIndex(999L))
        assertEquals(0, line.activeWordIndex(1000L))
        assertEquals(1, line.activeWordIndex(2000L))
        assertEquals(2, line.activeWordIndex(9999L))
    }

    @Test
    fun `LRC 回退解析`() {
        val content = "[00:01.00]第一行\n[00:03.50]第二行"
        val lines = LyricParser.parse(content)
        assertEquals(2, lines.size)
        assertEquals(1000L, lines[0].timeMs)
        assertEquals("第一行", lines[0].text)
        assertEquals(3500L, lines[1].timeMs)
    }

    @Test
    fun `LRC 同时间戳两行合并为原文加翻译`() {
        val content = "[00:01.00]原文\n[00:01.00]Translation"
        val lines = LyricParser.parse(content)
        assertEquals(1, lines.size)
        assertEquals("原文", lines[0].text)
        assertEquals("Translation", lines[0].translation)
    }

    @Test
    fun `LRC 相同文本的同时间戳不合并`() {
        val content = "[00:01.00]重复\n[00:01.00]重复"
        val lines = LyricParser.parse(content)
        assertEquals(2, lines.size)
    }

    @Test
    fun `language 标签的翻译按时间合并`() {
        val json = """[{"type":1,"lyricContent":[[1000,"译文一"],[3000,"译文二"]]}]"""
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val content = "[language:$encoded]\n[1000,1000]<0,1000,0>甲\n[3000,1000]<0,1000,0>乙"
        val lines = LyricParser.parse(content)
        assertEquals(2, lines.size)
        assertEquals("译文一", lines[0].translation)
        assertEquals("译文二", lines[1].translation)
    }

    @Test
    fun `language 标签的 type 0 视为音译`() {
        val json = """[{"type":0,"lyricContent":[[1000,"roman"]]}]"""
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val content = "[language:$encoded]\n[1000,1000]<0,1000,0>甲"
        val lines = LyricParser.parse(content)
        assertEquals(1, lines.size)
        assertEquals("roman", lines[0].romanization)
        assertEquals(null, lines[0].translation)
    }

    @Test
    fun `language 标签损坏时不抛异常且不影响主歌词`() {
        val content = "[language:!!!not-base64!!!]\n[1000,1000]<0,1000,0>甲"
        val lines = LyricParser.parse(content)
        assertEquals(1, lines.size)
        assertEquals("甲", lines[0].text)
    }

    @Test
    fun `空输入与纯文本返回空列表`() {
        assertTrue(LyricParser.parse(null).isEmpty())
        assertTrue(LyricParser.parse("").isEmpty())
        assertTrue(LyricParser.parse("   ").isEmpty())
        assertTrue(LyricParser.parse("这不是歌词").isEmpty())
    }

    @Test
    fun `归一化字面量换行与 BOM`() {
        val content = "\uFEFF[1000,1000]<0,1000,0>甲\\n[2000,1000]<0,1000,0>乙"
        val lines = LyricParser.parse(content)
        assertEquals(2, lines.size)
        assertEquals("甲", lines[0].text)
        assertEquals("乙", lines[1].text)
    }

    @Test
    fun `progressFraction 在无逐字时间轴时按行插值`() {
        val line = com.swtmaxx.kamusic.native.data.model.LyricLine(
            timeMs = 1000L,
            text = "x",
            durationMs = 1000L,
        )
        assertEquals(0f, line.progressFraction(1000L), 0.001f)
        assertEquals(0.5f, line.progressFraction(1500L), 0.001f)
        assertEquals(1f, line.progressFraction(5000L), 0.001f)
    }

    @Test
    fun `KRC 行内无字标签时退化为整行文本`() {
        val lines = LyricParser.parse("[1000,2000]纯文本行")
        assertEquals(1, lines.size)
        assertEquals("纯文本行", lines[0].text)
        assertNotNull(lines[0])
        assertTrue(lines[0].words.isEmpty())
    }
}
