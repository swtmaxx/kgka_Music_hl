package com.swtmaxx.kamusic.compose.playback

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
    fun `歌曲登记表双索引：按 id 主查、按 hash 辅助`() {
        SongRegistry.clear()
        val online = com.swtmaxx.kamusic.compose.data.model.Song(
            id = "1",
            title = "t",
            artist = "a",
            hash = "HASH",
        )
        // 本地歌没有 hash —— 改造前会被 remember() 直接丢掉
        val local = com.swtmaxx.kamusic.compose.data.model.Song(
            id = "local_x",
            title = "lt",
            artist = "la",
            hash = "",
            localPath = "/tmp/x.mp3",
        )
        SongRegistry.remember(listOf(online, local))

        // 主索引（PlaybackController 用）
        assertEquals(online, SongRegistry.get("1"))
        assertEquals(local, SongRegistry.get("local_x"))
        // hash 索引（播放地址解析用）
        assertEquals(online, SongRegistry.getByHash("HASH"))
        assertEquals(null, SongRegistry.getByHash(""))
        assertEquals(null, SongRegistry.get("NOPE"))

        SongRegistry.clear()
        assertEquals(null, SongRegistry.get("1"))
        assertEquals(null, SongRegistry.getByHash("HASH"))
    }

    @Test
    fun `toMediaItem 的 mediaId 始终是 song_id`() {
        val online = com.swtmaxx.kamusic.compose.data.model.Song(
            id = "1",
            title = "t",
            artist = "a",
            hash = "HASH",
        )
        val local = com.swtmaxx.kamusic.compose.data.model.Song(
            id = "local_x",
            title = "lt",
            artist = "la",
            hash = "",
            localPath = "/tmp/x.mp3",
        )
        assertEquals("1", PlaybackMapping.toMediaItem(online).mediaId)
        // 关键回归：改造前 mediaId 用的是 hash，本地歌会得到空 mediaId，
        // 导致 SongRegistry 取不回 Song、UI 与通知栏都拿不到曲目。
        assertEquals("local_x", PlaybackMapping.toMediaItem(local).mediaId)
    }
}
