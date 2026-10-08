package com.swtmaxx.kamusic.native.data.model

import com.swtmaxx.kamusic.native.core.arr
import com.swtmaxx.kamusic.native.core.asIntOrNull
import com.swtmaxx.kamusic.native.core.asStringOrNull
import com.swtmaxx.kamusic.native.core.int
import com.swtmaxx.kamusic.native.core.intAny
import com.swtmaxx.kamusic.native.core.obj
import com.swtmaxx.kamusic.native.core.objList
import com.swtmaxx.kamusic.native.core.str
import com.swtmaxx.kamusic.native.core.strAny
import kotlinx.serialization.json.JsonObject

// ============================================================================
// 通用工具
// ============================================================================

/** 手表屏仅 240 逻辑像素宽，请求 240px 源图即可 1:1 显示。 */
fun normalizeImageUrl(url: String?, size: Int = 240): String? {
    val value = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return value
        .replace("{size}", size.toString())
        .replace("{SIZE}", size.toString())
}

/** 服务端时长字段类型不稳定：有的给秒，有的给毫秒。 */
private fun durationMillisFromSeconds(value: kotlinx.serialization.json.JsonElement?): Long? {
    val seconds = value.asIntOrNull() ?: return null
    return seconds * 1000L
}

private fun durationMillisFromMillis(value: kotlinx.serialization.json.JsonElement?): Long? =
    value.asIntOrNull()?.toLong()

// ============================================================================
// 歌曲
// ============================================================================

/**
 * 歌曲。
 *
 * 服务端不同接口返回的字段名差异极大（搜索用 `FileHash`/`FileName`，
 * 排行榜用 `hash`/`songname`，歌单用 `audio_id`/`hash`），
 * 因此统一用宽容的键名回退链解析。
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val hash: String,
    val albumId: String? = null,
    val albumAudioId: String? = null,
    val albumName: String? = null,
    val coverUrl: String? = null,
    val durationMs: Long? = null,
    /** 服务端 privilege 字段，用于判断是否可播放（0 通常表示无版权）。 */
    val privilege: Int? = null,
) {
    val playable: Boolean get() = hash.isNotEmpty()

    val durationText: String
        get() {
            val ms = durationMs ?: return "--:--"
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

/** 从任意歌曲形状的 JSON 解析。所有接口共用。 */
fun parseSong(json: JsonObject): Song {
    val hash = json.strAny("FileHash", "hash", "hash_320", "hash_flac", "filehash") ?: ""
    val audioId = json.strAny("MixSongID", "mixsongid", "audio_id", "album_audio_id", "songid", "fileid")
    val artist = json.strAny(
        "SingerName", "author_name", "singername", "singer_name", "singer",
    ) ?: parseSingerArray(json) ?: "未知艺人"

    return Song(
        id = audioId ?: hash,
        title = json.strAny("FileName", "songname", "name", "audio_name", "filename") ?: "未知歌曲",
        artist = artist,
        hash = hash,
        albumId = json.strAny("AlbumID", "album_id"),
        albumAudioId = json.strAny("album_audio_id", "MixSongID", "mixsongid", "audio_id"),
        albumName = json.strAny("AlbumName", "album_name"),
        coverUrl = normalizeImageUrl(
            json.strAny(
                "Image", "sizable_cover", "album_sizable_cover", "img",
                "cover", "flexible_cover", "trans_param_img",
            ),
        ),
        durationMs = durationMillisFromSeconds(json["Duration"])
            ?: durationMillisFromMillis(json["timelen"])
            ?: durationMillisFromSeconds(json["time_length"])
            ?: durationMillisFromMillis(json["timelength"])
            ?: durationMillisFromSeconds(json["duration"]),
        privilege = json.intAny("privilege", "privilege_type"),
    )
}

/** `singer` / `authors` / `authors_new` 是数组形状时的回退解析。 */
private fun parseSingerArray(json: JsonObject): String? {
    val array = json.arr("singer")
        ?: json.arr("authors")
        ?: json.arr("authors_new")
        ?: json.arr("singers")
        ?: return null
    val names = array.mapNotNull { element ->
        element.asStringOrNull() ?: element.obj()?.strAny("name", "author_name", "singer_name")
    }
    return names.filter { it.isNotEmpty() }.joinToString(" / ").takeIf { it.isNotEmpty() }
}

/** 从任意列表形状的 JSON 解析歌曲列表（兼容 `data` / `list` / `songs` 包裹）。 */
fun parseSongList(element: kotlinx.serialization.json.JsonElement?): List<Song> {
    val direct = element.objList()
    if (direct.isNotEmpty()) return direct.map(::parseSong)
    val obj = element.obj() ?: return emptyList()
    for (key in listOf("list", "songs", "data", "info", "song_list", "audios")) {
        val nested = obj.obj(key)?.let { listOf(it) } ?: obj.arr(key).objList()
        if (nested.isNotEmpty()) return nested.map(::parseSong)
    }
    return emptyList()
}

// ============================================================================
// 播放地址
// ============================================================================

data class PlayUrl(val url: String, val hash: String) {
    val isPlayable: Boolean get() = url.isNotEmpty()

    companion object {
        fun parse(json: JsonObject): PlayUrl {
            val raw = json["url"]
            val urls = when {
                raw.asStringOrNull() != null -> listOfNotNull(raw.asStringOrNull())
                else -> raw.objList().mapNotNull { it.str("url") }
                    .ifEmpty { json.arr("url")?.mapNotNull { it.asStringOrNull() } ?: emptyList() }
            }
            return PlayUrl(
                url = urls.firstOrNull().orEmpty(),
                hash = json.str("hash").orEmpty(),
            )
        }
    }
}

// ============================================================================
// 歌单
// ============================================================================

data class PlaylistSummary(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val coverUrl: String? = null,
    val songCount: Int? = null,
    val playCount: Int? = null,
    val isDefault: Int? = null,
    val creatorName: String? = null,
    val creatorUserId: String? = null,
    val type: Int? = null,
    val sourceGlobalId: String? = null,
    val listId: String? = null,
    val musiclibId: String? = null,
) {
    val isLikedPlaylist: Boolean
        get() = isDefault == 2 || title.trim() == "我喜欢"

    val isCreatedPlaylist: Boolean
        get() = when {
            type == 0 -> true
            type == 1 -> false
            else -> !isLikedPlaylist
        }

    val songCountText: String
        get() = songCount?.let { "$it 首" } ?: ""

    companion object {
        /** `/top/playlist`（推荐歌单）。 */
        fun fromRecommend(json: JsonObject): PlaylistSummary {
            val globalCollectionId = json.str("global_collection_id")
            val id = globalCollectionId ?: json.str("specialid").orEmpty()
            return PlaylistSummary(
                id = id,
                title = json.strAny("specialname", "name") ?: "未命名歌单",
                subtitle = json.strAny("nickname", "intro"),
                coverUrl = normalizeImageUrl(json.strAny("flexible_cover", "imgurl", "img")),
                playCount = json.intAny("play_count", "playcount"),
                sourceGlobalId = globalCollectionId ?: id,
            )
        }

        /** `/user/playlist`（我的歌单）。 */
        fun fromUserPlaylist(json: JsonObject): PlaylistSummary {
            val globalCollectionId = json.str("global_collection_id")
            val listId = json.strAny("listid", "list_id", "specialid")
            return PlaylistSummary(
                id = globalCollectionId ?: listId.orEmpty(),
                title = json.strAny("specialname", "name") ?: "未命名歌单",
                coverUrl = normalizeImageUrl(json.strAny("pic", "flexible_cover", "img")),
                songCount = json.intAny("songcount", "song_count", "count"),
                playCount = json.intAny("playcount", "play_count"),
                isDefault = json.intAny("is_default", "isdefault"),
                creatorName = json.strAny("nickname", "creator_name"),
                creatorUserId = json.strAny("userid", "creator_userid"),
                type = json.int("type"),
                sourceGlobalId = globalCollectionId,
                listId = listId,
                musiclibId = json.str("musiclib_id"),
            )
        }
    }
}

data class PlaylistDetail(val info: PlaylistSummary, val songs: List<Song>)

data class SongPage(val songs: List<Song>, val rawItemCount: Int)

// ============================================================================
// 用户 / 登录
// ============================================================================

data class UserProfile(val nickname: String, val avatarUrl: String? = null) {
    companion object {
        fun parse(json: JsonObject): UserProfile = UserProfile(
            nickname = json.strAny("nickname", "username", "name") ?: "KA Music 用户",
            avatarUrl = normalizeImageUrl(json.strAny("pic", "avatar", "img")),
        )
    }
}

data class LoginSession(
    val userId: String? = null,
    val token: String? = null,
    val t1: String? = null,
    val sessionId: String? = null,
) {
    companion object {
        fun parse(json: JsonObject, sessionId: String? = null): LoginSession = LoginSession(
            userId = json.strAny("userid", "user_id", "userId"),
            token = json.strAny("token", "access_token"),
            t1 = json.strAny("t1"),
            sessionId = sessionId,
        )
    }
}

data class QrCodeInfo(val key: String, val imageBase64: String) {
    companion object {
        fun parse(json: JsonObject): QrCodeInfo = QrCodeInfo(
            key = json.strAny("qrcode", "key", "qrcode_key").orEmpty(),
            imageBase64 = json.strAny("qrcode_img", "qrcodeimg", "img").orEmpty(),
        )
    }
}

/** 扫码状态。status: 0 待扫描 / 1 已扫描待确认 / 2 已确认 / 4 已过期 */
data class QrCheckResult(
    val status: Int,
    val token: String? = null,
    val userId: String? = null,
    val t1: String? = null,
) {
    val isConfirmed: Boolean get() = status == 2
    val isExpired: Boolean get() = status == 4
    val isScanned: Boolean get() = status == 1

    companion object {
        fun parse(json: JsonObject): QrCheckResult = QrCheckResult(
            status = json.intAny("status", "code") ?: 0,
            token = json.strAny("token", "access_token"),
            userId = json.strAny("userid", "user_id", "userId"),
            t1 = json.strAny("t1"),
        )
    }
}

// ============================================================================
// 搜索
// ============================================================================

data class SearchHotCategory(val name: String, val keywords: List<String>)

data class FmStation(val id: String, val name: String, val type: Int = 0)

data class FmClassGroup(val id: String, val name: String, val stations: List<FmStation>)

data class FmImage(val fmid: String, val imageUrl: String?)
