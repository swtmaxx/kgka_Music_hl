package com.swtmaxx.kamusic.compose.data.model

import com.swtmaxx.kamusic.compose.core.arr
import com.swtmaxx.kamusic.compose.core.asIntOrNull
import com.swtmaxx.kamusic.compose.core.asObjOrNull
import com.swtmaxx.kamusic.compose.core.asStringOrNull
import com.swtmaxx.kamusic.compose.core.int
import com.swtmaxx.kamusic.compose.core.intAny
import com.swtmaxx.kamusic.compose.core.obj
import com.swtmaxx.kamusic.compose.core.objList
import com.swtmaxx.kamusic.compose.core.str
import com.swtmaxx.kamusic.compose.core.strAny
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
    val artistId: String? = null,
    val albumAudioId: String? = null,
    val albumName: String? = null,
    val coverUrl: String? = null,
    val durationMs: Long? = null,
    /** 服务端 privilege 字段，用于判断是否可播放（0 通常表示无版权）。 */
    val privilege: Int? = null,
    /** 已下载到本地的音频绝对路径；非空时优先本地播放，不联网。 */
    val localPath: String? = null,
    /** 已下载到本地的封面绝对路径；非空时优先用它当封面。 */
    val localCoverPath: String? = null,
) {
    /** 有 hash 就能联网解析，有 localPath 就能离线播放。 */
    val playable: Boolean get() = hash.isNotEmpty() || !localPath.isNullOrEmpty()

    /** 是否已下载到本地。 */
    val isLocal: Boolean get() = !localPath.isNullOrEmpty()

    val durationText: String
        get() {
            val ms = durationMs ?: return "--:--"
            val totalSeconds = ms / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}

/**
 * hash 回退链。
 *
 * ⚠️ 部分接口（`/rank/audio`、`/user/cloud`、`/album/songs`、`/artist/audios`）**不返回顶层 `hash`**，
 * 而是按音质嵌在 `audio_info.hash_128 / hash_320 / hash_flac / hash_high` 里（或 `deprecated.hash`）。
 * 实测 `/rank/audio` 的顶层键完全没有 `hash`，若不回退会解析出空串 → `Song.playable == false`
 * → 列表能显示、点了没声音。
 */
private fun parseHash(json: JsonObject): String {
    json.strAny("FileHash", "hash", "hash_320", "hash_flac", "filehash")?.let { return it }
    return json.obj("audio_info").strAny("hash_320", "hash_flac", "hash_high", "hash_128", "hash")
        ?: json.obj("deprecated").strAny("hash_320", "hash", "hash_128")
        ?: ""
}

/**
 * 时长回退链。
 *
 * 顶层字段单位不统一：`Duration` 是**秒**、`timelen`/`timelength` 是**毫秒**；
 * 而 `audio_info.duration_*` 一律是**毫秒**（与 `hash_*` 同一层，见 [parseHash]）。
 */
private fun parseDurationMs(json: JsonObject): Long? =
    durationMillisFromSeconds(json["Duration"])
        ?: durationMillisFromMillis(json["timelen"])
        ?: durationMillisFromSeconds(json["time_length"])
        ?: durationMillisFromMillis(json["timelength"])
        ?: durationMillisFromSeconds(json["duration"])
        ?: json.obj("audio_info").let { info ->
            info.long("duration_320") ?: info.long("duration_flac")
                ?: info.long("duration_128") ?: info.long("duration_high")
        }

/** 封面回退链：顶层图 → `trans_param.union_cover`（榜单/云盘常用）→ `album_info`。 */
private fun parseCoverUrl(json: JsonObject): String? =
    normalizeImageUrl(
        json.strAny(
            "Image", "sizable_cover", "album_sizable_cover", "img",
            "cover", "flexible_cover", "trans_param_img",
        ),
    )
        ?: normalizeImageUrl(json.obj("trans_param").strAny("union_cover", "cover"))
        ?: normalizeImageUrl(json.obj("album_info").strAny("sizable_cover", "cover", "img"))
        ?: normalizeImageUrl(json.obj("base").strAny("sizable_cover", "cover", "img"))

/**
 * 歌手 id。
 *
 * 各家接口放的位置不同：
 * - `/rank/audio`、`/album/songs`：`authors: [{author_id, author_name}]`
 * - `/personal/fm`：`singerinfo: [{id, name}]`
 * - 少数接口直接给顶层 `author_id` / `singer_id`
 * 都没有时返回 null（歌手详情入口会置灰，而不是跳到空页）。
 */
private fun parseArtistId(json: JsonObject): String? =
    json.strAny("author_id", "authorId", "singer_id", "singerid")
        ?: json.arr("authors").objList().firstOrNull()?.strAny("author_id", "id")
        ?: json.arr("singerinfo").objList().firstOrNull()?.strAny("id", "author_id")
        ?: json.obj("base").strAny("author_id", "singer_id")

/** 从任意歌曲形状的 JSON 解析。所有接口共用。 */
fun parseSong(json: JsonObject): Song {
    // /album/songs 这类接口把歌名/歌手/专辑 id 全部嵌在 `base` 里，顶层只有
    // audio_info / album_info / authors，因此每项都要有 base 回退。
    val base = json.obj("base")
    val hash = parseHash(json)
    val audioId = json.strAny("MixSongID", "mixsongid", "audio_id", "album_audio_id", "songid", "fileid")
        ?: base.strAny("audio_id", "album_audio_id")
    val artist = json.strAny(
        "SingerName", "author_name", "singername", "singer_name", "singer",
    ) ?: parseSingerArray(json) ?: base.str("author_name") ?: "未知艺人"

    return Song(
        id = audioId ?: hash,
        title = json.strAny("FileName", "songname", "name", "audio_name", "filename")
            ?: base.strAny("audio_name", "songname")
            ?: "未知歌曲",
        artist = artist,
        hash = hash,
        albumId = json.strAny("AlbumID", "album_id") ?: base.str("album_id"),
        artistId = parseArtistId(json),
        albumAudioId = json.strAny("album_audio_id", "MixSongID", "mixsongid", "audio_id")
            ?: base.str("album_audio_id"),
        albumName = json.strAny("AlbumName", "album_name")
            ?: json.obj("album_info").str("album_name"),
        coverUrl = parseCoverUrl(json),
        durationMs = parseDurationMs(json),
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
        element.asStringOrNull() ?: element.asObjOrNull()?.strAny("name", "author_name", "singer_name")
    }
    return names.filter { it.isNotEmpty() }.joinToString(" / ").takeIf { it.isNotEmpty() }
}

/** 从任意列表形状的 JSON 解析歌曲列表（兼容 `data` / `list` / `songs` 包裹）。 */
fun parseSongList(element: kotlinx.serialization.json.JsonElement?): List<Song> {
    val direct = element.objList()
    if (direct.isNotEmpty()) return direct.map(::parseSong)
    val obj = element.asObjOrNull() ?: return emptyList()
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

// ============================================================================
// 榜单（/rank/list、/rank/audio）
// ============================================================================

/**
 * 榜单条目。
 *
 * `/rank/list` 返回 55 个榜，每项带 `rankid` 与 `classify`（即调 `/rank/audio` 时要传的 `rank_cid`）。
 */
data class RankSummary(
    val id: String,
    val cid: String,
    val name: String,
    val coverUrl: String? = null,
    /** 1=每天 / 0=不定期…（官方 `intro` 里会写「更新频率：每天」）。 */
    val updateFrequency: Int? = null,
    val playCount: Int? = null,
) {
    companion object {
        fun parse(json: JsonObject): RankSummary = RankSummary(
            id = json.strAny("rankid", "rankId", "id").orEmpty(),
            cid = json.strAny("classify", "rank_cid", "rankCid").orEmpty(),
            name = json.strAny("rankname", "rankName", "name") ?: "未命名榜单",
            coverUrl = normalizeImageUrl(
                json.strAny("img_9", "base_img", "banner_9", "img", "album_img_9"),
            ),
            updateFrequency = json.int("update_frequency_type"),
            playCount = json.intAny("play_times", "playcount"),
        )
    }
}

/** `/rank/info` 的详情。 */
data class RankDetail(
    val name: String,
    val intro: String?,
) {
    companion object {
        fun parse(json: JsonObject): RankDetail = RankDetail(
            name = json.strAny("rankname", "rankName", "name") ?: "榜单",
            intro = json.strAny("intro", "long_intro"),
        )
    }
}

// ============================================================================
// 歌手 / 专辑
// ============================================================================

data class ArtistDetail(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val songCount: Int? = null,
    val albumCount: Int? = null,
    val fansCount: Int? = null,
    val intro: String? = null,
) {
    val subtitle: String
        get() = listOfNotNull(
            songCount?.let { "$it 首" },
            albumCount?.let { "$it 专辑" },
        ).joinToString(" · ")

    companion object {
        fun parse(json: JsonObject): ArtistDetail = ArtistDetail(
            id = json.strAny("author_id", "authorId", "id").orEmpty(),
            name = json.strAny("author_name", "authorName", "name", "singername") ?: "未知歌手",
            avatarUrl = normalizeImageUrl(
                json.strAny("sizable_avatar", "avatar", "img", "pic"),
            ),
            songCount = json.intAny("song_count", "songcount"),
            albumCount = json.intAny("album_count", "albumcount"),
            fansCount = json.intAny("fansnums", "fans_count"),
            intro = json.strAny("intro", "long_intro"),
        )
    }
}

/**
 * 专辑。
 *
 * 同时服务 `/album/detail`（取 `data[0]`）与 `/search?type=album`（取 `data.lists`）
 * 两种字段命名（`album_id`/`albumid`、`album_name`/`albumname`、`sizable_cover`/`img`）。
 */
data class AlbumDetail(
    val id: String,
    val name: String,
    val artistName: String? = null,
    val coverUrl: String? = null,
    val songCount: Int? = null,
    val publishDate: String? = null,
    val intro: String? = null,
) {
    val subtitle: String
        get() = listOfNotNull(artistName, publishDate).joinToString(" · ")

    companion object {
        fun parse(json: JsonObject): AlbumDetail = AlbumDetail(
            id = json.strAny("album_id", "albumid", "id").orEmpty(),
            name = json.strAny("album_name", "albumname", "name") ?: "未知专辑",
            artistName = json.strAny("author_name", "singername", "singer_name")
                ?: json.arr("authors").objList().firstOrNull()
                    ?.strAny("author_name", "name"),
            coverUrl = normalizeImageUrl(
                json.strAny("sizable_cover", "cover", "img", "album_sizable_cover"),
            ),
            songCount = json.intAny("song_count", "songcount", "song_count_total"),
            publishDate = json.strAny("publish_date", "publish_time"),
            intro = json.strAny("intro", "album_intro"),
        )
    }
}

// ============================================================================
// 评论
// ============================================================================

/**
 * 歌曲评论（只读）。
 *
 * 注意 `/comment/music` 的响应**没有 `data` 包裹**，是扁平的
 * `{status, err_code, count, list:[...]}`，且必须传 `mixsongid`（不是 hash）。
 */
data class Comment(
    val id: String,
    val userName: String,
    val avatarUrl: String? = null,
    val content: String,
    val time: String? = null,
    val likeCount: Int? = null,
    val replyCount: Int? = null,
) {
    companion object {
        fun parse(json: JsonObject): Comment = Comment(
            id = json.strAny("id", "comment_id").orEmpty(),
            userName = json.strAny("user_name", "username", "nickname") ?: "匿名",
            avatarUrl = normalizeImageUrl(
                json.strAny("user_pic", "user_avatar", "avatar"),
            ),
            content = json.strAny("content", "pcontent").orEmpty(),
            time = json.strAny("addtime", "time"),
            likeCount = json.intAny("like", "like_count"),
            replyCount = json.intAny("reply_num", "reply_count"),
        )
    }
}

// ============================================================================
// VIP
// ============================================================================

data class VipStatus(
    val isVip: Boolean,
    val label: String,
    val expireText: String? = null,
) {
    companion object {
        /**
         * `/user/vip/detail` 需要登录，未登录时上游返回 502，调用方应 try/catch。
         * 字段名在不同版本间不统一，故全部走宽容读取。
         */
        fun parse(json: JsonObject): VipStatus {
            val expire = json.strAny(
                "vip_end_time", "end_time", "expire_time", "union_vip_end_time",
            )
            val isVip = json.intAny("is_vip", "vip_type", "is_vip_user", "busi_vip")?.let { it > 0 }
                ?: !expire.isNullOrEmpty()
            return VipStatus(
                isVip = isVip,
                label = if (isVip) "VIP" else "未开通",
                expireText = expire,
            )
        }
    }
}

// ============================================================================
// 高潮区间（/song/climax）
// ============================================================================

/** 歌曲高潮区间（毫秒）。用于进度条高亮。 */
data class ClimaxRange(val startMs: Long, val endMs: Long) {
    val isValid: Boolean get() = endMs > startMs && startMs >= 0

    companion object {
        fun parse(json: JsonObject): ClimaxRange? {
            val start = json.long("start_time") ?: return null
            val end = json.long("end_time") ?: return null
            return ClimaxRange(start, end).takeIf { it.isValid }
        }
    }
}
