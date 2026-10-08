package com.swtmaxx.kamusic.native.data.repo

import com.swtmaxx.kamusic.native.core.SessionStore
import com.swtmaxx.kamusic.native.data.api.MusicApi
import com.swtmaxx.kamusic.native.data.model.FmClassGroup
import com.swtmaxx.kamusic.native.data.model.LyricLine
import com.swtmaxx.kamusic.native.data.model.PlaylistSummary
import com.swtmaxx.kamusic.native.data.model.Song
import com.swtmaxx.kamusic.native.data.model.SongPage
import com.swtmaxx.kamusic.native.data.model.UserProfile

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

    suspend fun fmClassGroups(): List<FmClassGroup> = api.fmClassGroups()

    suspend fun fmSongs(fmIds: List<String>): List<Song> = api.fmSongs(fmIds)

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
     * 供 [com.swtmaxx.kamusic.native.playback.PlaybackService] 的 ResolvingDataSource
     * 在 ExoPlayer 加载线程上同步调用（那里不是协程上下文，用 runBlocking 包一层）。
     */
    suspend fun resolvePlayUrlByHash(hash: String): String? {
        val song = com.swtmaxx.kamusic.native.playback.SongRegistry.get(hash) ?: return null
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
