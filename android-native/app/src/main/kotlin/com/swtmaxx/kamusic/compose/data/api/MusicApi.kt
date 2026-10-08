package com.swtmaxx.kamusic.compose.data.api

import com.swtmaxx.kamusic.compose.core.ApiClient
import com.swtmaxx.kamusic.compose.core.EMPTY_JSON_OBJECT
import com.swtmaxx.kamusic.compose.core.asArrOrNull
import com.swtmaxx.kamusic.compose.core.arr
import com.swtmaxx.kamusic.compose.core.asObjOrNull
import com.swtmaxx.kamusic.compose.core.asStringOrNull
import com.swtmaxx.kamusic.compose.core.int
import com.swtmaxx.kamusic.compose.core.intAny
import com.swtmaxx.kamusic.compose.core.obj
import com.swtmaxx.kamusic.compose.core.objList
import com.swtmaxx.kamusic.compose.core.str
import com.swtmaxx.kamusic.compose.core.strAny
import com.swtmaxx.kamusic.compose.data.model.FmClassGroup
import com.swtmaxx.kamusic.compose.data.model.FmImage
import com.swtmaxx.kamusic.compose.data.model.FmStation
import com.swtmaxx.kamusic.compose.data.model.LoginSession
import com.swtmaxx.kamusic.compose.data.model.PlayUrl
import com.swtmaxx.kamusic.compose.data.model.PlaylistSummary
import com.swtmaxx.kamusic.compose.data.model.QrCheckResult
import com.swtmaxx.kamusic.compose.data.model.QrCodeInfo
import com.swtmaxx.kamusic.compose.data.model.SearchHotCategory
import com.swtmaxx.kamusic.compose.data.model.Song
import com.swtmaxx.kamusic.compose.data.model.SongPage
import com.swtmaxx.kamusic.compose.data.model.UserProfile
import com.swtmaxx.kamusic.compose.data.model.parseSong
import com.swtmaxx.kamusic.compose.data.model.parseSongList
import com.swtmaxx.kamusic.compose.lyric.LyricParser
import com.swtmaxx.kamusic.compose.data.model.LyricLine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ============================================================================
// 登录结果
// ============================================================================

/** 手机号绑定了多个账号，需要用户选择。 */
data class MobileLoginAccount(val userId: String, val nickname: String, val avatarUrl: String?)

sealed interface PhoneLoginResult {
    data class Success(val session: LoginSession) : PhoneLoginResult
    data class AccountSelection(
        val accounts: List<MobileLoginAccount>,
        val message: String,
        val errorCode: Int?,
    ) : PhoneLoginResult
    data class Failure(val message: String, val errorCode: Int?) : PhoneLoginResult
}

/**
 * 登录响应判据（纯函数，可单测）。
 *
 * 采用**双轨判定**，因为两种后端的信封不同：
 * - hoilai 把 userid/token/t1 **平铺在顶层**并带 `status`
 * - KuGouMusicApi 把整个结果包在 `data` 里，解包后顶层**没有** `status`
 *
 * 因此：`status == 1` **或**（token 与 userid 同时非空）都算成功。
 */
object LoginJudgement {

    private val TOKEN_KEYS = arrayOf("token", "access_token")
    private val USER_ID_KEYS = arrayOf("userid", "user_id", "userId")

    fun isSuccess(json: JsonObject): Boolean {
        if (json.int("status") == 1) return true
        val token = json.strAny(*TOKEN_KEYS)
        val userId = json.strAny(*USER_ID_KEYS)
        return !token.isNullOrEmpty() && !userId.isNullOrEmpty()
    }

    fun requiresUserSelection(json: JsonObject): Boolean {
        val accounts = json.arr("accounts") ?: return false
        return accounts.isNotEmpty()
    }

    fun errorCode(json: JsonObject): Int? = json.intAny("error_code", "errorCode", "errcode")

    fun errorMessage(json: JsonObject): String? =
        json.strAny("error_msg", "errorMsg", "msg", "message", "data")

    /** 失败时的排查提示（与 Flutter 版一致）。 */
    const val REGISTER_HINT = "若没有账号请先在酷狗音乐概念版 App 注册"

    fun failureSuffix(json: JsonObject): String {
        val code = errorCode(json)
        val message = errorMessage(json)
        return buildString {
            if (!message.isNullOrEmpty()) append("（$message")
            if (code != null) {
                if (isNotEmpty()) append("，") else append("（")
                append("错误码 $code")
            }
            if (isNotEmpty()) append("）")
        }
    }
}

// ============================================================================
// MusicApi
// ============================================================================

/**
 * 酷狗外置 API 封装。
 *
 * 路由与 Flutter 版 `lib/services/music_api.dart` 一一对应，
 * 已核对部署端 module 目录下的模块全部存在（唯一例外是 `/login/logout`）。
 */
class MusicApi(private val client: ApiClient) {

    // ===== 登录 =====

    suspend fun sendLoginCode(mobile: String) {
        val json = client.post("/captcha/sent", query = mapOf("mobile" to mobile)).asObjOrNull()
        val status = json.int("status")
        val errCode = json.intAny("error_code", "errcode")
        if (status != 1 && errCode != 0) {
            throw IllegalStateException("发送验证码失败，${LoginJudgement.REGISTER_HINT}${LoginJudgement.failureSuffix(json ?: EMPTY_JSON_OBJECT)}")
        }
    }

    suspend fun loginWithPhone(mobile: String, code: String, userId: String? = null): PhoneLoginResult {
        val body = buildMap<String, Any?> {
            put("mobile", mobile)
            put("code", code)
            if (!userId.isNullOrEmpty()) put("userId", userId)
        }
        val json = client.post("/login/cellphone", body = body).asObjOrNull()
            ?: return PhoneLoginResult.Failure("登录失败：服务端返回空响应", null)

        if (LoginJudgement.requiresUserSelection(json)) {
            val accounts = json.arr("accounts").objList().mapNotNull { item ->
                val id = item.strAny("userid", "user_id", "userId") ?: return@mapNotNull null
                MobileLoginAccount(
                    userId = id,
                    nickname = item.strAny("nickname", "username") ?: "未知账号",
                    avatarUrl = item.strAny("pic", "avatar"),
                )
            }
            return PhoneLoginResult.AccountSelection(
                accounts = accounts,
                message = json.strAny("message", "msg") ?: "请选择需要登录的账号",
                errorCode = LoginJudgement.errorCode(json),
            )
        }

        if (!LoginJudgement.isSuccess(json)) {
            return PhoneLoginResult.Failure(
                message = "登录失败，${LoginJudgement.REGISTER_HINT}${LoginJudgement.failureSuffix(json)}",
                errorCode = LoginJudgement.errorCode(json),
            )
        }

        return PhoneLoginResult.Success(LoginSession.parse(json, client.sessionId()))
    }

    suspend fun refreshToken(): LoginSession {
        val json = client.post("/login/token").asObjOrNull() ?: EMPTY_JSON_OBJECT
        return LoginSession.parse(json, client.sessionId())
    }

    // ===== 扫码登录 =====

    suspend fun getQrCode(): QrCodeInfo {
        val json = client.get("/login/qr/key").asObjOrNull() ?: EMPTY_JSON_OBJECT
        return QrCodeInfo.parse(json)
    }

    suspend fun checkQrStatus(key: String): QrCheckResult {
        val json = client.get("/login/qr/check", mapOf("key" to key)).asObjOrNull()
            ?: EMPTY_JSON_OBJECT
        return QrCheckResult.parse(json)
    }

    // ===== 用户 =====

    suspend fun userDetail(): UserProfile {
        val json = client.get("/user/detail").asObjOrNull() ?: EMPTY_JSON_OBJECT
        return UserProfile.parse(json)
    }

    suspend fun userPlaylists(page: Int = 1, pageSize: Int = 50): List<PlaylistSummary> {
        val raw = client.get(
            "/user/playlist",
            mapOf("page" to page, "pagesize" to pageSize),
        )
        val list = raw.asObjOrNull()?.let { it.arr("info") ?: it.arr("list") } ?: raw.asArrOrNull()
        return list.objList().map(PlaylistSummary::fromUserPlaylist)
    }

    // ===== 首页 =====

    suspend fun recommendedPlaylists(page: Int = 1, pageSize: Int = 30): List<PlaylistSummary> {
        val raw = client.get(
            "/top/playlist",
            mapOf("page" to page, "pagesize" to pageSize),
        )
        val list = raw.asObjOrNull()?.let { it.arr("special_list") ?: it.arr("list") } ?: raw.asArrOrNull()
        return list.objList().map(PlaylistSummary::fromRecommend)
    }

    suspend fun dailyRecommend(): List<Song> {
        val raw = client.get("/recommend/songs")
        return songsFrom(raw, listOf("data", "songs", "song_list", "list"))
    }

    suspend fun topSongs(type: Int = 21608, page: Int = 1): List<Song> {
        val raw = client.get("/top/song", mapOf("type" to type, "page" to page))
        return songsFrom(raw, listOf("data", "songs", "song_list", "list"))
    }

    suspend fun personalFm(mode: Int = 0, page: Int = 1): List<Song> {
        val raw = client.get("/personal/fm", mapOf("mode" to mode, "page" to page))
        return songsFrom(raw, listOf("data", "songs", "song_list", "list"))
    }

    suspend fun fmClassGroups(): List<FmClassGroup> {
        val raw = client.get("/fm/class")
        val groups = raw.asObjOrNull()?.let { it.arr("data") ?: it.arr("list") } ?: raw.asArrOrNull()
        return groups.objList().map { group ->
            FmClassGroup(
                id = group.strAny("id", "fm_class_id").orEmpty(),
                name = group.strAny("name", "class_name") ?: "未知分类",
                stations = group.arr("stations").objList().map { station ->
                    FmStation(
                        id = station.strAny("id", "fmid").orEmpty(),
                        name = station.strAny("name", "fm_name") ?: "未知电台",
                        type = station.int("type") ?: 0,
                    )
                },
            )
        }.filter { it.stations.isNotEmpty() }
    }

    suspend fun fmSongs(fmIds: List<String>, page: Int = 1, pageSize: Int = 30): List<Song> {
        val raw = client.get(
            "/fm/songs",
            mapOf("fmids" to fmIds, "page" to page, "pagesize" to pageSize),
        )
        return songsFrom(raw, listOf("data", "songs", "song_list", "list"))
    }

    suspend fun fmImages(fmIds: List<String>): Map<String, FmImage> {
        val raw = client.get("/fm/image", mapOf("fmid" to fmIds))
        val list = raw.asObjOrNull()?.let { it.arr("data") ?: it.arr("list") } ?: raw.asArrOrNull()
        return list.objList().mapNotNull { item ->
            val id = item.strAny("fmid", "id") ?: return@mapNotNull null
            id to FmImage(id, item.strAny("img", "image", "imgurl"))
        }.toMap()
    }

    // ===== 搜索 =====

    suspend fun searchHotKeywords(): List<SearchHotCategory> {
        val json = client.get("/search/hot").asObjOrNull() ?: EMPTY_JSON_OBJECT
        return json.arr("list").objList().map { category ->
            SearchHotCategory(
                name = category.strAny("name", "category") ?: "热搜",
                keywords = category.arr("keywords").objList()
                    .mapNotNull { it.strAny("keyword", "name", "hint") }
                    .filter { it.isNotEmpty() },
            )
        }.filter { it.keywords.isNotEmpty() }
    }

    suspend fun searchSuggest(keywords: String): List<String> {
        val raw = client.get("/search/suggest", mapOf("keywords" to keywords))

        // 老格式：{ music: [ { keyword: "..." } ] }
        val legacy = raw.asObjOrNull()?.arr("music").objList()
            .mapNotNull { it.str("keyword") }
            .filter { it.isNotEmpty() }
        if (!legacy.isNullOrEmpty()) return legacy

        // 真实返回：{ data: [ { RecordDatas: [ { HintInfo: "..." } ] } ] }
        val groups = when (raw) {
            is JsonArray -> raw.objList()
            else -> raw.asObjOrNull()?.arr("data").objList() ?: emptyList()
        }
        return groups.flatMap { group ->
            group.arr("RecordDatas").objList().mapNotNull { it.str("HintInfo") }
        }.filter { it.isNotEmpty() }
    }

    suspend fun searchSongs(keywords: String, page: Int = 1, pageSize: Int = 30): List<Song> {
        val raw = client.get(
            "/search",
            mapOf(
                "keywords" to keywords,
                "page" to page,
                "pagesize" to pageSize,
                "type" to "song",
            ),
        )
        return songsFrom(raw, listOf("songs", "song", "lists", "data"))
    }

    // ===== 歌单 =====

    suspend fun playlistInfo(id: String): PlaylistSummary {
        val json = client.get("/playlist/detail", mapOf("ids" to id)).asObjOrNull()
            ?: EMPTY_JSON_OBJECT
        val detail = json.obj("data") ?: json
        return PlaylistSummary.fromRecommend(detail)
    }

    suspend fun playlistSongs(id: String, page: Int = 1, pageSize: Int = 50): SongPage {
        val raw = client.get(
            "/playlist/track/all",
            mapOf("id" to id, "page" to page, "pagesize" to pageSize),
        )
        val songs = songsFrom(raw, listOf("songs", "song_list", "list", "data", "audios"))
        val rawCount = raw.asObjOrNull()?.let { it.arr("songs") ?: it.arr("list") }?.size
            ?: raw.asArrOrNull()?.size
            ?: songs.size
        return SongPage(songs = songs, rawItemCount = rawCount)
    }

    // ===== 播放 =====

    suspend fun songUrl(song: Song, quality: String = "128"): PlayUrl {
        val json = client.get(
            "/song/url",
            mapOf(
                "hash" to song.hash,
                "quality" to quality,
                "album_id" to song.albumId,
                "album_audio_id" to song.albumAudioId,
                "free_part" to false,
            ),
        ).asObjOrNull() ?: EMPTY_JSON_OBJECT
        return PlayUrl.parse(json)
    }

    // ===== 歌词 =====

    /**
     * 获取歌词。
     *
     * 链路：`/search/lyric?hash=` 拿候选 → `/lyric?id=&accesskey=&fmt=krc&decode=true`。
     * 服务端 `decode=true` 已解密 KRC，客户端只解析。
     * 先试 krc（逐字），失败再试 lrc。
     */
    suspend fun lyrics(song: Song): List<LyricLine> {
        val candidate = findLyricCandidate(song.hash) ?: return emptyList()
        lyricByFormat(candidate, "krc").takeIf { it.isNotEmpty() }?.let { return it }
        return lyricByFormat(candidate, "lrc")
    }

    private suspend fun findLyricCandidate(hash: String): JsonObject? {
        if (hash.isEmpty()) return null
        val raw = client.get("/search/lyric", mapOf("hash" to hash))
        return findCandidateRecursive(raw)
    }

    private fun findCandidateRecursive(value: JsonElement?): JsonObject? {
        val obj = value.asObjOrNull() ?: return null
        if (hasCandidateKeys(obj)) return obj
        for (key in listOf("candidates", "candidate", "list", "lyrics", "items", "info", "data")) {
            val child = obj[key] ?: continue
            when (child) {
                is JsonArray -> child.forEach { item ->
                    findCandidateRecursive(item)?.let { return it }
                }
                is JsonObject -> findCandidateRecursive(child)?.let { return it }
                else -> Unit
            }
        }
        return null
    }

    private fun hasCandidateKeys(obj: JsonObject): Boolean {
        val id = obj.strAny("id", "lyrics_id", "lyric_id", "lyricid")
        val accessKey = obj.strAny("accesskey", "access_key", "accessKey")
        return !id.isNullOrEmpty() && !accessKey.isNullOrEmpty()
    }

    private suspend fun lyricByFormat(candidate: JsonObject, format: String): List<LyricLine> {
        val id = candidate.strAny("id", "lyrics_id", "lyric_id", "lyricid") ?: return emptyList()
        val accessKey = candidate.strAny("accesskey", "access_key", "accessKey") ?: return emptyList()

        val result = client.get(
            "/lyric",
            mapOf("id" to id, "accesskey" to accessKey, "fmt" to format, "decode" to true),
        ).asObjOrNull() ?: return emptyList()

        val candidates = listOfNotNull(
            result.str("decodedContent"),
            result.str("rawContent"),
            result.str("content"),
        )
        for (content in candidates.sortedByDescending(::contentScore)) {
            val lines = LyricParser.parse(content)
            if (lines.isNotEmpty()) return lines
        }
        return emptyList()
    }

    private fun contentScore(content: String): Int {
        var score = 0
        if (Regex("""^\[\s*-?\d+\s*,\s*-?\d+\s*\].*<""", RegexOption.MULTILINE).containsMatchIn(content)) score += 100
        if (Regex("""^\[\s*-?\d+\s*,\s*-?\d+\s*\]""", RegexOption.MULTILINE).containsMatchIn(content)) score += 60
        if (Regex("""\[\d{1,2}:\d{1,2}""").containsMatchIn(content)) score += 40
        if (content.contains("[language:")) score += 10
        return score
    }

    // ===== 内部工具 =====

    /** 从各种包裹形状里抽出歌曲列表。 */
    private fun songsFrom(raw: JsonElement?, keys: List<String>): List<Song> {
        if (raw is JsonArray) return parseSongList(raw)
        val obj = raw.asObjOrNull() ?: return emptyList()
        for (key in keys) {
            val child = obj[key] ?: continue
            if (child is JsonArray && child.isNotEmpty()) {
                val songs = parseSongList(child)
                if (songs.isNotEmpty()) return songs
            }
            if (child is JsonObject) {
                val songs = parseSongList(child)
                if (songs.isNotEmpty()) return songs
            }
        }
        // 兜底：直接在根对象上找歌曲形状的数组
        for (value in obj.values) {
            if (value is JsonArray && value.isNotEmpty()) {
                val songs = parseSongList(value)
                if (songs.isNotEmpty()) return songs
            }
        }
        return emptyList()
    }
}
