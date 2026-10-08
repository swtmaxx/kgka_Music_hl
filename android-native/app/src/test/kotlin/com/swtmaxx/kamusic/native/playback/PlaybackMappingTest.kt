package com.swtmaxx.kamusic.native.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 播放模式与 ExoPlayer 常量的映射测试。 */
class PlaybackMappingTest {

    @Test
    fun `顺序播放映射为不循环不随机`() {
        assertEquals(Player.REPEAT_MODE_OFF, PlaybackMapping.repeatModeFor(PlayMode.SEQUENCE))
        assertFalse(PlaybackMapping.shuffleEnabledFor(PlayMode.SEQUENCE))
    }

    @Test
    fun `单曲循环映射为 REPEAT_MODE_ONE`() {
        assertEquals(Player.REPEAT_MODE_ONE, PlaybackMapping.repeatModeFor(PlayMode.REPEAT_ONE))
        assertFalse(PlaybackMapping.shuffleEnabledFor(PlayMode.REPEAT_ONE))
    }

    @Test
    fun `随机播放映射为整列表循环加随机`() {
        assertEquals(Player.REPEAT_MODE_ALL, PlaybackMapping.repeatModeFor(PlayMode.SHUFFLE))
        assertTrue(PlaybackMapping.shuffleEnabledFor(PlayMode.SHUFFLE))
    }

    @Test
    fun `从播放器状态还原模式`() {
        assertEquals(
            PlayMode.SEQUENCE,
            PlaybackMapping.fromPlayer(Player.REPEAT_MODE_OFF, shuffleEnabled = false),
        )
        assertEquals(
            PlayMode.REPEAT_ONE,
            PlaybackMapping.fromPlayer(Player.REPEAT_MODE_ONE, shuffleEnabled = false),
        )
        assertEquals(
            PlayMode.SHUFFLE,
            PlaybackMapping.fromPlayer(Player.REPEAT_MODE_ALL, shuffleEnabled = true),
        )
        // 随机优先级低于单曲循环（两者同时为真时以单曲循环为准）
        assertEquals(
            PlayMode.REPEAT_ONE,
            PlaybackMapping.fromPlayer(Player.REPEAT_MODE_ONE, shuffleEnabled = true),
        )
    }

    @Test
    fun `模式循环一周回到原点`() {
        var mode = PlayMode.SEQUENCE
        mode = PlaybackMapping.nextMode(mode)
        assertEquals(PlayMode.REPEAT_ONE, mode)
        mode = PlaybackMapping.nextMode(mode)
        assertEquals(PlayMode.SHUFFLE, mode)
        mode = PlaybackMapping.nextMode(mode)
        assertEquals(PlayMode.SEQUENCE, mode)
    }

    @Test
    fun `模式与播放器常量可往返`() {
        PlayMode.entries.forEach { mode ->
            val restored = PlaybackMapping.fromPlayer(
                PlaybackMapping.repeatModeFor(mode),
                PlaybackMapping.shuffleEnabledFor(mode),
            )
            assertEquals(mode, restored)
        }
    }

    @Test
    fun `每种模式都有中文标签`() {
        PlayMode.entries.forEach { mode ->
            assertTrue(PlaybackMapping.modeLabel(mode).isNotEmpty())
        }
    }

    @Test
    fun `自定义 scheme 常量稳定`() {
        // ResolvingDataSource 依赖这个 scheme 识别需要解析的媒体项
        assertEquals("kamusic", PlaybackMapping.SCHEME)
    }

    @Test
    fun `歌曲登记表可写入与读取`() {
        SongRegistry.clear()
        val song = com.swtmaxx.kamusic.native.data.model.Song(
            id = "1",
            title = "t",
            artist = "a",
            hash = "HASH",
        )
        SongRegistry.remember(listOf(song))
        assertEquals(song, SongRegistry.get("HASH"))
        assertEquals(null, SongRegistry.get("NOPE"))
        SongRegistry.clear()
        assertEquals(null, SongRegistry.get("HASH"))
    }
}
