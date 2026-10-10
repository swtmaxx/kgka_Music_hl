package com.swtmaxx.kamusic.compose.core

import com.swtmaxx.kamusic.compose.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下载相关的纯函数测试。
 *
 * 全部是静态工具方法，不需要 Context / 文件系统 —— 这样能在无设备环境下跑。
 */
class DownloaderUtilsTest {

    // ===== 文件名 =====

    @Test
    fun `非法字符被替换为下划线`() {
        assertEquals("a_b_c_d_e_f_g_h_i", Downloader.safeName("a/b\\c:d*e?f\"g<h>i"))
    }

    @Test
    fun `控制字符被替换`() {
        assertEquals("a_b", Downloader.safeName("a\u0000b"))
    }

    @Test
    fun `空白被折叠且首尾去空格`() {
        assertEquals("周深 - 大鱼", Downloader.safeName("  周深    -   大鱼  "))
    }

    @Test
    fun `超长名字被截断到 80 字`() {
        val long = "歌".repeat(200)
        assertEquals(80, Downloader.safeName(long).length)
    }

    @Test
    fun `空名字回落为未命名`() {
        assertEquals("未命名", Downloader.safeName("   "))
        assertEquals("未命名", Downloader.safeName("///"))
    }

    @Test
    fun `结尾的点被去掉（Windows 与部分工具会误判扩展名）`() {
        assertEquals("abc", Downloader.safeName("abc..."))
    }

    @Test
    fun `中文与常见符号保持原样`() {
        assertEquals("G.E.M.邓紫棋 - 光年之外", Downloader.safeName("G.E.M.邓紫棋 - 光年之外"))
    }

    // ===== 预估大小 =====

    @Test
    fun `128kbps 四分钟约 3_8MB`() {
        // 240s * 128kbps / 8 = 3840 KB
        assertEquals(3_840_000L, Downloader.estimateBytes(240_000L, "128"))
    }

    @Test
    fun `320kbps 四分钟约 9_6MB`() {
        assertEquals(9_600_000L, Downloader.estimateBytes(240_000L, "320"))
    }

    @Test
    fun `无损档明显大于 320`() {
        val flac = Downloader.estimateBytes(240_000L, "flac")
        val high = Downloader.estimateBytes(240_000L, "320")
        assertTrue("flac($flac) 应大于 320($high)", flac > high)
    }

    @Test
    fun `时长缺失时按四分钟估`() {
        assertEquals(
            Downloader.estimateBytes(240_000L, "128"),
            Downloader.estimateBytes(null, "128"),
        )
    }

    @Test
    fun `未知音质按标准档估`() {
        assertEquals(
            Downloader.estimateBytes(240_000L, "128"),
            Downloader.estimateBytes(240_000L, "unknown"),
        )
    }

    // ===== 续传决策 =====

    @Test
    fun `已有字节且回 206 时追加`() {
        assertEquals(ResumeAction.Append, decideResume(existingBytes = 1024L, responseCode = 206))
    }

    @Test
    fun `回 200 表示服务器不支持 Range，从头重下`() {
        assertEquals(ResumeAction.Restart, decideResume(existingBytes = 1024L, responseCode = 200))
    }

    @Test
    fun `回 416 表示区间无效，从头重下`() {
        assertEquals(ResumeAction.Restart, decideResume(existingBytes = 1024L, responseCode = 416))
    }

    @Test
    fun `没有已下载字节时一律从头写`() {
        assertEquals(ResumeAction.Restart, decideResume(existingBytes = 0L, responseCode = 206))
        assertEquals(ResumeAction.Restart, decideResume(existingBytes = 0L, responseCode = 200))
    }

    // ===== 字节格式化 =====

    @Test
    fun `字节格式化覆盖三档单位`() {
        assertEquals("512 B", Downloader.formatBytes(512))
        assertEquals("1 KB", Downloader.formatBytes(1024))
        assertEquals("1 MB", Downloader.formatBytes(1024L * 1024))
        assertEquals("1.0 GB", Downloader.formatBytes(1024L * 1024 * 1024))
    }

    // ===== DownloadedSong ↔ Song =====

    @Test
    fun `下载记录还原成可离线播放的 Song`() {
        val record = DownloadedSong(
            id = "963769012",
            hash = "98CA3FB8B73C0E4C35312A88D3F348E5",
            title = "自由的你",
            artist = "G.E.M.邓紫棋",
            albumId = "207873836",
            albumAudioId = "963769012",
            albumName = "自由的你",
            durationMs = 296_000L,
            coverUrl = "http://img/240.jpg",
            filePath = "/storage/emulated/0/Download/KA Music/G.E.M.邓紫棋 - 自由的你.mp3",
            coverPath = "/storage/emulated/0/Download/KA Music/.cover/963769012.jpg",
            sizeBytes = 4_750_000L,
            quality = "128",
            downloadedAt = 1_700_000_000_000L,
        )

        val song = record.toSong()
        assertEquals("963769012", song.id)
        assertEquals("98CA3FB8B73C0E4C35312A88D3F348E5", song.hash)
        assertEquals("自由的你", song.title)
        assertEquals("G.E.M.邓紫棋", song.artist)
        assertEquals("207873836", song.albumId)
        assertEquals("963769012", song.albumAudioId)
        assertEquals(296_000L, song.durationMs)
        assertEquals(record.filePath, song.localPath)
        assertEquals(record.coverPath, song.localCoverPath)

        // 关键：还原后必须判为可播放、且判为本地
        assertTrue(song.playable)
        assertTrue(song.isLocal)
    }

    @Test
    fun `封面下载失败时 coverPath 为空但歌曲仍可播放`() {
        val record = DownloadedSong(
            id = "1",
            hash = "H",
            title = "t",
            artist = "a",
            filePath = "/tmp/t.mp3",
            coverPath = null,
        )
        val song = record.toSong()
        assertNull(song.localCoverPath)
        assertTrue(song.playable)
    }

    // ===== Song 的可播放判定 =====

    @Test
    fun `只有 hash 的在线歌可播放且不是本地`() {
        val song = Song(id = "1", title = "t", artist = "a", hash = "H")
        assertTrue(song.playable)
        assertTrue(!song.isLocal)
    }

    @Test
    fun `只有 localPath 的本地歌可播放`() {
        val song = Song(id = "l", title = "t", artist = "a", hash = "", localPath = "/tmp/a.mp3")
        assertTrue(song.playable)
        assertTrue(song.isLocal)
    }

    @Test
    fun `既无 hash 也无本地路径则不可播放`() {
        val song = Song(id = "x", title = "t", artist = "a", hash = "")
        assertTrue(!song.playable)
    }

    @Test
    fun `空字符串 localPath 不算本地`() {
        val song = Song(id = "x", title = "t", artist = "a", hash = "H", localPath = "")
        assertTrue(!song.isLocal)
    }
}
