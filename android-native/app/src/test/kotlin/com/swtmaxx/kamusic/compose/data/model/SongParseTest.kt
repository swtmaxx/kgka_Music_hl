package com.swtmaxx.kamusic.compose.data.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 歌曲解析测试。
 *
 * 服务端不同接口的字段名差异极大，解析器必须能从多种形状里抽出同样的结果，
 * 否则搜索能播、排行榜播不了这类问题会反复出现。
 */
class SongParseTest {

    private fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(
        pairs.mapNotNull { (k, v) ->
            when (v) {
                null -> null
                is Int -> k to JsonPrimitive(v)
                else -> k to JsonPrimitive(v.toString())
            }
        }.toMap(),
    )

    @Test
    fun `解析搜索结果形状`() {
        val song = parseSong(
            obj(
                "MixSongID" to "12345",
                "FileHash" to "HASH1",
                "FileName" to "歌名",
                "SingerName" to "歌手",
                "AlbumID" to "999",
                "AlbumName" to "专辑",
                "Image" to "http://img/{size}.jpg",
                "Duration" to 215,
            ),
        )
        assertEquals("12345", song.id)
        assertEquals("HASH1", song.hash)
        assertEquals("歌名", song.title)
        assertEquals("歌手", song.artist)
        assertEquals("999", song.albumId)
        assertEquals("专辑", song.albumName)
        // {size} 应被替换为手表用尺寸 240
        assertEquals("http://img/240.jpg", song.coverUrl)
        // Duration 是秒
        assertEquals(215_000L, song.durationMs)
        assertEquals("3:35", song.durationText)
    }

    @Test
    fun `解析排行榜形状`() {
        val song = parseSong(
            obj(
                "audio_id" to "777",
                "hash" to "HASH2",
                "songname" to "榜单曲",
                "author_name" to "某歌手",
                "album_id" to "888",
                "album_audio_id" to "777",
                "album_name" to "某专辑",
                "album_sizable_cover" to "http://c/{size}",
                "timelength" to 180_000,
            ),
        )
        assertEquals("777", song.id)
        assertEquals("HASH2", song.hash)
        assertEquals("榜单曲", song.title)
        assertEquals("某歌手", song.artist)
        assertEquals("http://c/240", song.coverUrl)
        // timelength 是毫秒
        assertEquals(180_000L, song.durationMs)
    }

    @Test
    fun `缺少时长时显示占位`() {
        val song = parseSong(obj("hash" to "H", "songname" to "x"))
        assertNull(song.durationMs)
        assertEquals("--:--", song.durationText)
    }

    @Test
    fun `缺少歌名与歌手时使用兜底文案`() {
        val song = parseSong(obj("hash" to "H"))
        assertEquals("未知歌曲", song.title)
        assertEquals("未知艺人", song.artist)
    }

    @Test
    fun `singer 数组形状也能解析出歌手`() {
        val song = parseSong(
            JsonObject(
                mapOf(
                    "hash" to JsonPrimitive("H"),
                    "songname" to JsonPrimitive("x"),
                    "singer" to kotlinx.serialization.json.JsonArray(
                        listOf(
                            JsonObject(mapOf("name" to JsonPrimitive("甲"))),
                            JsonObject(mapOf("name" to JsonPrimitive("乙"))),
                        ),
                    ),
                ),
            ),
        )
        assertEquals("甲 / 乙", song.artist)
    }

    @Test
    fun `hash 为空时不可播放`() {
        val song = parseSong(obj("songname" to "x"))
        assertTrue(song.hash.isEmpty())
        assertTrue(!song.playable)
    }

    @Test
    fun `hash 嵌在 audio_info 里也能解析`() {
        // 实测 /rank/audio 的顶层键完全没有 hash，只有 audio_info.hash_*；
        // 不回退会解析出空串 → playable=false → 列表能显示但点了没声音。
        // /user/cloud、/album/songs、/artist/audios 也是同一形状。
        val song = parseSong(
            JsonObject(
                mapOf(
                    "audio_id" to JsonPrimitive(653605791),
                    "songname" to JsonPrimitive("自由的你"),
                    "author_name" to JsonPrimitive("G.E.M.邓紫棋"),
                    "album_id" to JsonPrimitive(207873836),
                    "album_audio_id" to JsonPrimitive(963769012),
                    "audio_info" to JsonObject(
                        mapOf(
                            "hash_128" to JsonPrimitive("HASH128"),
                            "hash_320" to JsonPrimitive("HASH320"),
                            "hash_flac" to JsonPrimitive("HASHFLAC"),
                            "duration_128" to JsonPrimitive(296000),
                        ),
                    ),
                    "trans_param" to JsonObject(
                        mapOf("union_cover" to JsonPrimitive("http://img/{size}/cover.jpg")),
                    ),
                ),
            ),
        )
        assertEquals("HASH320", song.hash)
        assertTrue(song.playable)
        assertEquals("自由的你", song.title)
        assertEquals("G.E.M.邓紫棋", song.artist)
        // audio_info.duration_* 是毫秒
        assertEquals(296_000L, song.durationMs)
        // 封面回退到 trans_param.union_cover
        assertEquals("http://img/240/cover.jpg", song.coverUrl)
    }

    @Test
    fun `audio_info 只有 hash_128 时回退到 128`() {
        val song = parseSong(
            JsonObject(
                mapOf(
                    "songname" to JsonPrimitive("x"),
                    "audio_info" to JsonObject(mapOf("hash_128" to JsonPrimitive("ONLY128"))),
                ),
            ),
        )
        assertEquals("ONLY128", song.hash)
        assertTrue(song.playable)
    }

    @Test
    fun `deprecated hash 作为最后兜底`() {
        val song = parseSong(
            JsonObject(
                mapOf(
                    "songname" to JsonPrimitive("x"),
                    "deprecated" to JsonObject(mapOf("hash" to JsonPrimitive("DEPHASH"))),
                ),
            ),
        )
        assertEquals("DEPHASH", song.hash)
    }

    @Test
    fun `顶层 hash 优先于 audio_info`() {
        val song = parseSong(
            JsonObject(
                mapOf(
                    "hash" to JsonPrimitive("TOP"),
                    "songname" to JsonPrimitive("x"),
                    "audio_info" to JsonObject(mapOf("hash_320" to JsonPrimitive("NESTED"))),
                ),
            ),
        )
        assertEquals("TOP", song.hash)
    }

    @Test
    fun `normalizeImageUrl 替换两种占位符`() {
        assertEquals("a/240/b", normalizeImageUrl("a/{size}/b"))
        assertEquals("a/240/b", normalizeImageUrl("a/{SIZE}/b"))
        assertEquals("a/240", normalizeImageUrl("a/{size}"))
        assertNull(normalizeImageUrl(null))
        assertNull(normalizeImageUrl("   "))
    }

    @Test
    fun `播放地址兼容字符串与数组两种形状`() {
        val single = PlayUrl.parse(obj("url" to "http://a/b.mp3", "hash" to "H"))
        assertEquals("http://a/b.mp3", single.url)
        assertTrue(single.isPlayable)

        val array = PlayUrl.parse(
            JsonObject(
                mapOf(
                    "url" to kotlinx.serialization.json.JsonArray(
                        listOf(JsonPrimitive("http://x/1.mp3")),
                    ),
                    "hash" to JsonPrimitive("H"),
                ),
            ),
        )
        assertEquals("http://x/1.mp3", array.url)
    }

    @Test
    fun `空播放地址判为不可播放`() {
        val playUrl = PlayUrl.parse(obj("url" to ""))
        assertTrue(!playUrl.isPlayable)
    }
}
