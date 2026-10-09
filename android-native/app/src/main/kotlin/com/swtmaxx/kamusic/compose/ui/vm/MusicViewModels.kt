package com.swtmaxx.kamusic.compose.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swtmaxx.kamusic.compose.core.SessionStore
import com.swtmaxx.kamusic.compose.data.model.AlbumDetail
import com.swtmaxx.kamusic.compose.data.model.ArtistDetail
import com.swtmaxx.kamusic.compose.data.model.Comment
import com.swtmaxx.kamusic.compose.data.model.LyricLine
import com.swtmaxx.kamusic.compose.data.model.PlaylistSummary
import com.swtmaxx.kamusic.compose.data.model.RankDetail
import com.swtmaxx.kamusic.compose.data.model.RankSummary
import com.swtmaxx.kamusic.compose.data.model.Song
import com.swtmaxx.kamusic.compose.data.model.UserProfile
import com.swtmaxx.kamusic.compose.data.repo.AuthRepository
import com.swtmaxx.kamusic.compose.data.repo.MusicRepository
import com.swtmaxx.kamusic.compose.playback.PlaybackController
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 通用加载态。 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
    data class Ready<T>(val data: T) : UiState<T>
}

// ============================================================================
// 首页
// ============================================================================

data class HomeData(
    val playlists: List<PlaylistSummary> = emptyList(),
    val dailySongs: List<Song> = emptyList(),
    val ranks: List<RankSummary> = emptyList(),
    val fmSongs: List<Song> = emptyList(),
    /** 每个 tab 独立的错误，互不影响。 */
    val errors: Map<Int, String> = emptyMap(),
) {
    fun errorFor(tab: Int): String? = errors[tab]
}

class HomeViewModel(private val repo: MusicRepository) : ViewModel() {

    /** 0 推荐歌单 / 1 每日推荐 / 2 榜单 / 3 私人 FM */
    private val _tab = MutableStateFlow(0)
    val tab: StateFlow<Int> = _tab.asStateFlow()

    private val _state = MutableStateFlow(UiState.Ready(HomeData()))
    val state: StateFlow<UiState<HomeData>> = _state.asStateFlow()

    private var loaded = false

    fun selectTab(index: Int) {
        _tab.value = index
        if (!loaded) load()
    }

    fun load(forceRefresh: Boolean = false) {
        loaded = true
        _state.update { current ->
            if (current is UiState.Ready) current else UiState.Ready(HomeData())
        }
        viewModelScope.launch {
            val data = (_state.value as? UiState.Ready)?.data ?: HomeData()
            val errors = data.errors.toMutableMap()

            val deferredPlaylists = async { runCatching { repo.recommendedPlaylists(forceRefresh) } }
            val deferredDaily = async { runCatching { repo.dailyRecommend() } }
            val deferredRanks = async { runCatching { repo.rankList() } }
            val deferredFm = async { runCatching { repo.personalFm() } }

            val resultPlaylists = deferredPlaylists.await()
            val resultDaily = deferredDaily.await()
            val resultRanks = deferredRanks.await()
            val resultFm = deferredFm.await()

            val playlists = resultPlaylists.getOrNull()
            val daily = resultDaily.getOrNull()
            val ranks = resultRanks.getOrNull()
            val fm = resultFm.getOrNull()

            if (playlists == null) errors[0] = "推荐歌单加载失败" else errors.remove(0)
            if (daily == null) errors[1] = "每日推荐加载失败" else errors.remove(1)
            if (ranks == null) errors[2] = "榜单加载失败" else errors.remove(2)
            if (fm == null) errors[3] = "私人 FM 加载失败" else errors.remove(3)

            _state.value = UiState.Ready(
                HomeData(
                    playlists = playlists ?: data.playlists,
                    dailySongs = daily ?: data.dailySongs,
                    ranks = ranks ?: data.ranks,
                    fmSongs = fm ?: data.fmSongs,
                    errors = errors,
                ),
            )
        }
    }

    /**
     * 私人 FM 换一批。
     *
     * 上游每次调用都返回新的推荐（不需要游标），所以「换一批」就是再调一次。
     */
    fun refreshFm() {
        viewModelScope.launch {
            val songs = runCatching { repo.personalFm() }.getOrNull() ?: return@launch
            _state.update { state ->
                if (state is UiState.Ready) {
                    UiState.Ready(state.data.copy(fmSongs = songs))
                } else {
                    state
                }
            }
        }
    }
}

// ============================================================================
// 搜索
// ============================================================================

data class SearchUiState(
    val query: String = "",
    val hot: List<com.swtmaxx.kamusic.compose.data.model.SearchHotCategory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val results: List<Song> = emptyList(),
    val searching: Boolean = false,
    val error: String? = null,
) {
    val idle: Boolean get() = query.isBlank()
}

class SearchViewModel(private val repo: MusicRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var hotLoaded = false

    fun ensureHotLoaded() {
        if (hotLoaded) return
        hotLoaded = true
        viewModelScope.launch {
            val hot = runCatching { repo.searchHot() }.getOrNull() ?: emptyList()
            _state.update { it.copy(hot = hot) }
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value, error = null) }
        if (value.isBlank()) {
            _state.update { it.copy(suggestions = emptyList(), results = emptyList()) }
            return
        }
        viewModelScope.launch {
            val suggestions = runCatching { repo.searchSuggest(value) }.getOrDefault(emptyList())
            // 只在该关键词仍是最新输入时应用，避免旧响应覆盖新输入。
            if (_state.value.query == value) {
                _state.update { it.copy(suggestions = suggestions.take(10)) }
            }
        }
    }

    fun submit(keyword: String = _state.value.query) {
        if (keyword.isBlank()) return
        _state.update { it.copy(query = keyword, searching = true, error = null, suggestions = emptyList()) }
        viewModelScope.launch {
            val result = runCatching { repo.search(keyword) }
            result.fold(
                onSuccess = { songs ->
                    _state.update { it.copy(searching = false, results = songs) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(searching = false, error = error.message ?: "搜索失败")
                    }
                },
            )
        }
    }

    fun clearQuery() = _state.update { SearchUiState(hot = it.hot) }
}

// ============================================================================
// 歌单详情
// ============================================================================

data class PlaylistUiData(
    val info: PlaylistSummary? = null,
    val songs: List<Song> = emptyList(),
    val loadingMore: Boolean = false,
)

class PlaylistViewModel(
    private val repo: MusicRepository,
    private val playlistId: String,
    private val fallbackTitle: String,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<PlaylistUiData>>(UiState.Loading)
    val state: StateFlow<UiState<PlaylistUiData>> = _state.asStateFlow()

    private var page = 1
    private var endReached = false

    val title: String
        get() = (_state.value as? UiState.Ready)?.data?.info?.title ?: fallbackTitle

    fun load() {
        _state.value = UiState.Loading
        page = 1
        endReached = false
        viewModelScope.launch {
            val infoResult = runCatching { repo.playlistInfo(playlistId) }
            val songsResult = runCatching { repo.playlistSongs(playlistId, page = 1) }

            val songs = songsResult.getOrNull()?.songs.orEmpty()
            if (songs.isEmpty() && songsResult.isFailure) {
                _state.value = UiState.Error(songsResult.exceptionOrNull()?.message ?: "歌单加载失败")
                return@launch
            }
            endReached = songs.isEmpty()
            _state.value = UiState.Ready(
                PlaylistUiData(
                    info = infoResult.getOrNull(),
                    songs = songs,
                ),
            )
        }
    }

    fun loadMore() {
        val current = (_state.value as? UiState.Ready)?.data ?: return
        if (endReached || current.loadingMore) return
        _state.value = UiState.Ready(current.copy(loadingMore = true))
        viewModelScope.launch {
            val nextPage = page + 1
            val result = runCatching { repo.playlistSongs(playlistId, page = nextPage) }
            val more = result.getOrNull()?.songs.orEmpty()
            if (more.isEmpty()) endReached = true else page = nextPage
            _state.update { state ->
                if (state is UiState.Ready) {
                    UiState.Ready(
                        state.data.copy(
                            songs = state.data.songs + more,
                            loadingMore = false,
                        ),
                    )
                } else {
                    state
                }
            }
        }
    }
}

// ============================================================================
// 榜单详情
// ============================================================================

data class RankUiData(
    val info: RankDetail? = null,
    val songs: List<Song> = emptyList(),
    val loadingMore: Boolean = false,
)

class RankDetailViewModel(
    private val repo: MusicRepository,
    private val rankId: String,
    private val rankCid: String,
    private val fallbackTitle: String,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<RankUiData>>(UiState.Loading)
    val state: StateFlow<UiState<RankUiData>> = _state.asStateFlow()

    private var page = 1
    private var endReached = false

    val title: String
        get() = (_state.value as? UiState.Ready)?.data?.info?.name ?: fallbackTitle

    fun load() {
        _state.value = UiState.Loading
        page = 1
        endReached = false
        viewModelScope.launch {
            val infoResult = runCatching { repo.rankDetail(rankId, rankCid) }
            val songsResult = runCatching { repo.rankSongs(rankId, rankCid, page = 1) }
            val songs = songsResult.getOrNull().orEmpty()
            if (songs.isEmpty() && songsResult.isFailure) {
                _state.value = UiState.Error(songsResult.exceptionOrNull()?.message ?: "榜单加载失败")
                return@launch
            }
            endReached = songs.isEmpty()
            _state.value = UiState.Ready(
                RankUiData(info = infoResult.getOrNull(), songs = songs),
            )
        }
    }

    fun loadMore() {
        val current = (_state.value as? UiState.Ready)?.data ?: return
        if (endReached || current.loadingMore) return
        _state.value = UiState.Ready(current.copy(loadingMore = true))
        viewModelScope.launch {
            val next = page + 1
            val more = runCatching { repo.rankSongs(rankId, rankCid, page = next) }
                .getOrNull().orEmpty()
            if (more.isEmpty()) endReached = true else page = next
            _state.update { s ->
                if (s is UiState.Ready) {
                    UiState.Ready(s.data.copy(songs = s.data.songs + more, loadingMore = false))
                } else {
                    s
                }
            }
        }
    }
}

// ============================================================================
// 歌手详情
// ============================================================================

data class ArtistUiData(
    val detail: ArtistDetail? = null,
    val songs: List<Song> = emptyList(),
)

class ArtistDetailViewModel(
    private val repo: MusicRepository,
    private val artistId: String,
    private val fallbackName: String,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ArtistUiData>>(UiState.Loading)
    val state: StateFlow<UiState<ArtistUiData>> = _state.asStateFlow()

    val title: String
        get() = (_state.value as? UiState.Ready)?.data?.detail?.name ?: fallbackName

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val detail = runCatching { repo.artistDetail(artistId) }
            val songs = runCatching { repo.artistSongs(artistId) }
            val list = songs.getOrNull().orEmpty()
            if (detail.getOrNull() == null && list.isEmpty()) {
                _state.value = UiState.Error(
                    detail.exceptionOrNull()?.message ?: "歌手加载失败",
                )
                return@launch
            }
            _state.value = UiState.Ready(ArtistUiData(detail.getOrNull(), list))
        }
    }
}

// ============================================================================
// 专辑详情
// ============================================================================

data class AlbumUiData(
    val detail: AlbumDetail? = null,
    val songs: List<Song> = emptyList(),
)

class AlbumDetailViewModel(
    private val repo: MusicRepository,
    private val albumId: String,
    private val fallbackTitle: String,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<AlbumUiData>>(UiState.Loading)
    val state: StateFlow<UiState<AlbumUiData>> = _state.asStateFlow()

    val title: String
        get() = (_state.value as? UiState.Ready)?.data?.detail?.name ?: fallbackTitle

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val detail = runCatching { repo.albumDetail(albumId) }
            val songs = runCatching { repo.albumSongs(albumId) }
            val list = songs.getOrNull().orEmpty()
            if (detail.getOrNull() == null && list.isEmpty()) {
                _state.value = UiState.Error(
                    detail.exceptionOrNull()?.message ?: "专辑加载失败",
                )
                return@launch
            }
            _state.value = UiState.Ready(AlbumUiData(detail.getOrNull(), list))
        }
    }
}

// ============================================================================
// 评论（只读）
// ============================================================================

class CommentViewModel(
    private val repo: MusicRepository,
    private val mixSongId: String,
    private val fallbackTitle: String,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<Comment>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Comment>>> = _state.asStateFlow()

    val title: String get() = fallbackTitle

    private var page = 1
    private var endReached = false

    fun load() {
        _state.value = UiState.Loading
        page = 1
        endReached = false
        viewModelScope.launch {
            val result = runCatching { repo.comments(mixSongId, page = 1) }
            val list = result.getOrNull()
            if (list == null) {
                _state.value = UiState.Error(result.exceptionOrNull()?.message ?: "评论加载失败")
                return@launch
            }
            endReached = list.isEmpty()
            _state.value = UiState.Ready(list)
        }
    }

    fun loadMore() {
        val current = (_state.value as? UiState.Ready)?.data ?: return
        if (endReached) return
        viewModelScope.launch {
            val next = page + 1
            val more = runCatching { repo.comments(mixSongId, page = next) }
                .getOrNull().orEmpty()
            if (more.isEmpty()) endReached = true else page = next
            _state.value = UiState.Ready(current + more)
        }
    }
}

// ============================================================================
// 我的
// ============================================================================

data class MineUiData(
    val profile: UserProfile? = null,
    val createdPlaylists: List<PlaylistSummary> = emptyList(),
    val collectedPlaylists: List<PlaylistSummary> = emptyList(),
)

class MineViewModel(
    private val repo: MusicRepository,
    private val authRepo: AuthRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<MineUiData>>(UiState.Loading)
    val state: StateFlow<UiState<MineUiData>> = _state.asStateFlow()

    val apiBaseUrl: StateFlow<String> = sessionStore.apiBaseUrlFlow
        .map { it ?: com.swtmaxx.kamusic.compose.BuildConfig.DEFAULT_API_BASE_URL }
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessionStore.effectiveApiBaseUrl)

    private val _quality = MutableStateFlow(sessionStore.quality)
    val quality: StateFlow<String> = _quality.asStateFlow()

    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val profileResult = runCatching { repo.userDetail() }
            val playlistsResult = runCatching { repo.userPlaylists(forceRefresh) }

            if (profileResult.isFailure && playlistsResult.isFailure) {
                _state.value = UiState.Error(
                    profileResult.exceptionOrNull()?.message ?: "加载失败",
                )
                return@launch
            }

            val playlists = playlistsResult.getOrDefault(emptyList())
            _state.value = UiState.Ready(
                MineUiData(
                    profile = profileResult.getOrNull(),
                    createdPlaylists = playlists.filter { it.isCreatedPlaylist },
                    collectedPlaylists = playlists.filter { !it.isCreatedPlaylist },
                ),
            )
        }
    }

    fun updateApiBaseUrl(url: String?) {
        viewModelScope.launch { sessionStore.setCustomApiBaseUrl(url) }
    }

    fun updateQuality(value: String) {
        viewModelScope.launch {
            sessionStore.setQuality(value)
            _quality.value = sessionStore.quality
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepo.logout()
            onDone()
        }
    }
}

// ============================================================================
// 播放器
// ============================================================================

data class LyricsUiData(
    val lines: List<LyricLine> = emptyList(),
    val loading: Boolean = false,
    val songHash: String? = null,
)

class PlayerViewModel(
    private val repo: MusicRepository,
    val controller: PlaybackController,
) : ViewModel() {

    val playerState = controller.state
    val positionMs = controller.positionMs
    val queue = controller.queue

    private val _lyrics = MutableStateFlow(LyricsUiData())
    val lyrics: StateFlow<LyricsUiData> = _lyrics.asStateFlow()

    init {
        viewModelScope.launch {
            controller.state
                .map { it.song }
                .distinctUntilChanged()
                .collect { song ->
                    if (song == null) {
                        _lyrics.value = LyricsUiData()
                        return@collect
                    }
                    _lyrics.value = LyricsUiData(loading = true, songHash = song.hash)
                    val lines = repo.lyrics(song)
                    _lyrics.value = LyricsUiData(lines = lines, loading = false, songHash = song.hash)
                }
        }
    }
}
