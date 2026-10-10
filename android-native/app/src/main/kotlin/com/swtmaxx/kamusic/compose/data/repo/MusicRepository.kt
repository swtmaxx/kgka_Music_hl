package com.swtmaxx.kamusic.compose.data.repo

import com.swtmaxx.kamusic.compose.core.SessionStore
import com.swtmaxx.kamusic.compose.data.api.MusicApi
import com.swtmaxx.kamusic.compose.data.model.AlbumDetail
import com.swtmaxx.kamusic.compose.data.model.ArtistDetail
import com.swtmaxx.kamusic.compose.data.model.ClimaxRange
import com.swtmaxx.kamusic.compose.data.model.Comment
import com.swtmaxx.kamusic.compose.data.model.LyricLine
import com.swtmaxx.kamusic.compose.data.model.PlaylistSummary
import com.swtmaxx.kamusic.compose.data.model.RankDetail
import com.swtmaxx.kamusic.compose.data.model.RankSummary
import com.swtmaxx.kamusic.compose.data.model.Song
import com.swtmaxx.kamusic.compose.data.model.SongPage
import com.swtmaxx.kamusic.compose.data.model.UserProfile
import com.swtmaxx.kamusic.compose.data.model.VipStatus

/** 带 TTL 的极简内存缓存（首页/我的等低频变化数据）。 */
private class TimedCache<T>(private val ttlMs: Long) {
    private var value: T? = null
    private var storedAt: Long = 0L

    fun get(): T? {
        val current = value ?: return null
        if (System.currentTimeMillis() - storedAt > ttlMs) {
            value = null
            return null
        }
        return current
    }

    fun put(newValue: T) {
        value = newValue
        storedAt = System.currentTimeMillis()
    }

    fun invalidate() {
        value = null
    }
}

/**
 * 首页 / 搜索 / 歌单 / 播放地址 / 歌词。
 *
 * 只做「缓存 + 错误归一化」，业务逻辑留在 ViewModel。
 */
class MusicRepository(
    private val api: MusicApi,
    private val sessionStore: SessionStore,
) {

    private val homeCache = TimedCache<List<PlaylistSummary>>(30 * 60 * 1000L)
    private val userPlaylistCache = TimedCache<List<PlaylistSummary>>(24 * 60 * 60 * 1000L)
    private val lyricCache = TimedCache<Pair<String, List<LyricLine>>>(6 * 60 * 60 * 1000L)

    // ===== 首页 =====

    suspend fun recommendedPlaylists(forceRefresh: Boolean = false): List<PlaylistSummary> {
        if (!forceRefresh) homeCache.get()?.let { return it }
        val fresh = api.recommendedPlaylists()
        if (fresh.isNotEmpty()) homeCache.put(fresh)
        return fresh
    }

    suspend fun dailyRecommend(): List<Song> = api.dailyRecommend()

    suspend fun topSongs(): List<Song> = api.topSongs()

    // ===== 私人 FM =====

    suspend fun personalFm(mode: String = "normal"): List<Song> = api.personalFm(mode)

    // ===== 榜单 =====

    suspend fun rankList(): List<RankSummary> = api.rankList()

    suspend fun rankDetail(rankId: String, rankCid: String): RankDetail? =
        api.rankDetail(rankId, rankCid)

    suspend fun rankSongs(rankId: String, rankCid: String, page: Int = 1): List<Song> =
        api.rankSongs(rankId, rankCid, page)

    // ===== 歌手 / 专辑 =====

    suspend fun artistDetail(id: String): ArtistDetail? = api.artistDetail(id)

    suspend fun artistSongs(id: String, page: Int = 1): List<Song> = api.artistSongs(id, page)

    suspend fun albumDetail(id: String): AlbumDetail? = api.albumDetail(id)

    suspend fun albumSongs(id: String, page: Int = 1): List<Song> = api.albumSongs(id, page)

    suspend fun searchAlbums(keywords: String, page: Int = 1): List<AlbumDetail> =
        api.searchAlbums(keywords, page)

    // ===== 评论 =====

    /** 评论接口要的是 `mixsongid`（= `Song.albumAudioId`），不是 hash。 */
    suspend fun comments(song: Song, page: Int = 1): List<Comment> =
        api.comments(song.albumAudioId ?: song.id, page)

    // ===== 云盘 / VIP =====

    suspend fun cloudSongs(page: Int = 1): List<Song> = api.cloudSongs(page)

    suspend fun userVipDetail(): VipStatus? = api.userVipDetail()

    // ===== 高潮区间 =====

    suspend fun songClimax(hash: String): ClimaxRange? = api.songClimax(hash)

    // ===== 刷歌 / 风格推荐 =====

    suspend fun homeDiscover(pageSize: Int = 6): List<Song> = api.homeDiscover(pageSize)

    suspend fun everydayStyleRecommend(): List<Song> = api.everydayStyleRecommend()

    // ===== 播放历史 =====

    suspend fun playHistory(page: Int = 1): List<Song> = api.playHistory(page)

    suspend fun uploadHistory(song: Song, playedAtSeconds: Long) =
        api.uploadHistory(song, playedAtSeconds)

    suspend fun listenReport(song: Song, event: String) = api.listenReport(song, event)

    // ===== 用户 =====

    suspend fun userDetail(): UserProfile = api.userDetail()

    suspend fun userPlaylists(forceRefresh: Boolean = false): List<PlaylistSummary> {
        if (!forceRefresh) userPlaylistCache.get()?.let { return it }
        val fresh = api.userPlaylists()
        if (fresh.isNotEmpty()) userPlaylistCache.put(fresh)
        return fresh
    }

    fun invalidateUserCache() {
        userPlaylistCache.invalidate()
    }

    // ===== 搜索 =====

    suspend fun searchHot() = api.searchHotKeywords()

    suspend fun searchSuggest(keywords: String) = api.searchSuggest(keywords)

    suspend fun search(keywords: String, page: Int = 1) = api.searchSongs(keywords, page)

    // ===== 歌单 =====

    suspend fun playlistInfo(id: String): PlaylistSummary = api.playlistInfo(id)

    suspend fun playlistSongs(id: String, page: Int = 1, pageSize: Int = 50): SongPage =
        api.playlistSongs(id, page, pageSize)

    // ===== 播放 =====

    /** 解析可播放地址；失败返回 null（调用方决定跳过还是提示）。 */
    suspend fun resolvePlayUrl(song: Song): String? = runCatching {
        val playUrl = api.songUrl(song, sessionStore.quality)
        playUrl.url.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * 按 hash 解析播放地址。
     *
     * 供 [com.swtmaxx.kamusic.compose.playback.PlaybackService] 的 ResolvingDataSource
     * 在 ExoPlayer 加载线程上同步调用（那里不是协程上下文，用 runBlocking 包一层）。
     */
    suspend fun resolvePlayUrlByHash(hash: String): String? {
        val song = com.swtmaxx.kamusic.compose.playback.SongRegistry.getByHash(hash) ?: return null
        return resolvePlayUrl(song)
    }

    // ===== 歌词 =====

    suspend fun lyrics(song: Song): List<LyricLine> {
        val cached = lyricCache.get()
        if (cached != null && cached.first == song.hash) return cached.second
        val lines = runCatching { api.lyrics(song) }.getOrDefault(emptyList())
        if (lines.isNotEmpty()) lyricCache.put(song.hash to lines)
        return lines
    }
}
