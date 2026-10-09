package com.swtmaxx.kamusic.compose.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swtmaxx.kamusic.compose.data.model.PlaylistSummary
import com.swtmaxx.kamusic.compose.data.model.RankSummary
import com.swtmaxx.kamusic.compose.data.model.Song
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.Artwork
import com.swtmaxx.kamusic.compose.ui.component.ArtworkFill
import com.swtmaxx.kamusic.compose.ui.component.CircleIconButton
import com.swtmaxx.kamusic.compose.ui.component.EmptyBox
import com.swtmaxx.kamusic.compose.ui.component.ErrorBox
import com.swtmaxx.kamusic.compose.ui.component.LoadingBox
import com.swtmaxx.kamusic.compose.ui.component.PillTab
import com.swtmaxx.kamusic.compose.ui.component.TrackRow
import com.swtmaxx.kamusic.compose.ui.component.WatchAutoCentering
import com.swtmaxx.kamusic.compose.ui.component.watchRotary
import com.swtmaxx.kamusic.compose.ui.component.watchScalingParams
import com.swtmaxx.kamusic.compose.ui.theme.SurfaceRaised
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import com.swtmaxx.kamusic.compose.ui.vm.HomeData
import com.swtmaxx.kamusic.compose.ui.vm.HomeViewModel
import com.swtmaxx.kamusic.compose.ui.vm.UiState

private val TABS = listOf("推荐", "每日", "榜单", "FM")

@Composable
fun HomeScreen(
    onOpenPlaylist: (id: String, title: String) -> Unit,
    onOpenRank: (RankSummary) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.musicRepository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // tab 选择器
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WatchMetrics.gutterSmall, vertical = WatchMetrics.gutterSmall),
            horizontalArrangement = Arrangement.spacedBy(WatchMetrics.gutterSmall),
        ) {
            TABS.forEachIndexed { index, label ->
                PillTab(
                    text = label,
                    selected = tab == index,
                    onClick = { viewModel.selectTab(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when (val current = state) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(current.message) { viewModel.load() }
            is UiState.Ready -> HomeContent(
                data = current.data,
                tab = tab,
                onRetry = { viewModel.load(forceRefresh = true) },
                onOpenPlaylist = onOpenPlaylist,
                onPlaySong = { songs, index ->
                    container.playbackController.playFrom(songs, index)
                    onOpenPlayer()
                },
                onOpenRank = onOpenRank,
                onRefreshFm = viewModel::refreshFm,
            )
        }
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    tab: Int,
    onRetry: () -> Unit,
    onOpenPlaylist: (id: String, title: String) -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
    onOpenRank: (RankSummary) -> Unit,
    onRefreshFm: () -> Unit,
) {
    val error = data.errorFor(tab)
    if (error != null) {
        ErrorBox(error, onRetry = onRetry)
        return
    }

    when (tab) {
        0 -> PlaylistGrid(data.playlists, onOpenPlaylist)
        1 -> TwoSectionSongList(
            firstTitle = "每日推荐",
            first = data.dailySongs,
            secondTitle = "按风格推荐",
            second = data.styleSongs,
            onPlaySong = onPlaySong,
        )
        2 -> RankList(data.ranks, onOpenRank)
        else -> FmList(data.fmSongs, onPlaySong, onRefreshFm)
    }
}

/** 分节标题。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = Modifier.padding(
            start = WatchMetrics.gutter,
            end = WatchMetrics.gutter,
            top = WatchMetrics.gutter,
            bottom = WatchMetrics.gutterSmall,
        ),
    )
}

/**
 * 两段歌曲列表（共用同一个 `ScalingLazyColumn` 与表冠状态）。
 *
 * 「每日」tab 用：上面是 `/recommend/songs` 的每日推荐，下面是 `/everyday/style/recommend`
 * 的按风格推荐。点击时把两段拼成一个播放队列，所以索引要加 `first.size` 偏移；
 * 两段的 key 也必须加不同前缀，否则跨段可能重名。
 */
@Composable
private fun TwoSectionSongList(
    firstTitle: String,
    first: List<Song>,
    secondTitle: String,
    second: List<Song>,
    onPlaySong: (List<Song>, Int) -> Unit,
) {
    val all = first + second
    if (all.isEmpty()) {
        EmptyBox("暂无歌曲")
        return
    }
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        scalingParams = watchScalingParams(),
        state = listState,
        rotaryScrollableBehavior = watchRotary(listState),
        contentPadding = PaddingValues(0.dp),
        autoCentering = WatchAutoCentering,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (first.isNotEmpty()) {
            item { SectionLabel(firstTitle) }
            itemsIndexed(first, key = { i, s -> "a_${s.hash}_$i" }) { i, song ->
                TrackRow(
                    song = song,
                    isCurrent = false,
                    onClick = { onPlaySong(all, i) },
                )
            }
        }
        if (second.isNotEmpty()) {
            item { SectionLabel(secondTitle) }
            itemsIndexed(second, key = { i, s -> "b_${s.hash}_$i" }) { i, song ->
                TrackRow(
                    song = song,
                    isCurrent = false,
                    onClick = { onPlaySong(all, first.size + i) },
                )
            }
        }
    }
}

/** 榜单列表：点任一项进榜单详情。 */
@Composable
private fun RankList(ranks: List<RankSummary>, onOpenRank: (RankSummary) -> Unit) {
    if (ranks.isEmpty()) {
        EmptyBox("暂无榜单")
        return
    }
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        scalingParams = watchScalingParams(),
        state = listState,
        rotaryScrollableBehavior = watchRotary(listState),
        contentPadding = PaddingValues(0.dp),
        autoCentering = WatchAutoCentering,
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(ranks, key = { _, rank -> rank.id }) { _, rank ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WatchMetrics.listRow)
                    .clickable { onOpenRank(rank) }
                    .padding(horizontal = WatchMetrics.gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(url = rank.coverUrl, size = WatchMetrics.coverSmall)
                Spacer(Modifier.width(WatchMetrics.gutter))
                Text(
                    text = rank.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 私人 FM。
 *
 * 上游每次调用 `/personal/fm` 都返回新的推荐，所以「换一批」就是再调一次，
 * 不需要游标或分页。
 */
@Composable
private fun FmList(
    songs: List<Song>,
    onPlaySong: (List<Song>, Int) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WatchMetrics.gutterSmall, vertical = WatchMetrics.gutterSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (songs.isEmpty()) "私人 FM" else "私人 FM · ${songs.size} 首",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            CircleIconButton(
                icon = painterResource(R.drawable.ic_refresh),
                contentDescription = "换一批",
                onClick = onRefresh,
                size = 32.dp,
                iconSize = WatchMetrics.icon,
            )
        }
        SongList(songs, onPlaySong)
    }
}

@Composable
private fun PlaylistGrid(
    playlists: List<PlaylistSummary>,
    onOpenPlaylist: (id: String, title: String) -> Unit,
) {
    if (playlists.isEmpty()) {
        EmptyBox("暂无推荐歌单")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(WatchMetrics.gutterSmall),
        horizontalArrangement = Arrangement.spacedBy(WatchMetrics.gutterSmall),
        verticalArrangement = Arrangement.spacedBy(WatchMetrics.gutter),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(playlists, key = { it.id }) { playlist ->
            Column(
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .clickable { onOpenPlaylist(playlist.id, playlist.title) },
            ) {
                ArtworkFill(
                    url = playlist.coverUrl,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Spacer(Modifier.height(WatchMetrics.gutterSmall))
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                playlist.playCount?.let { count ->
                    Text(
                        text = formatPlayCount(count),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SongList(songs: List<Song>, onPlaySong: (List<Song>, Int) -> Unit) {
    if (songs.isEmpty()) {
        EmptyBox("暂无歌曲")
        return
    }
    // 换成 Wear 的 ScalingLazyColumn 以接入表冠滚动。本页有固定 tab 行，因此不套
    // ScreenScaffold（否则系统时间会与 tab 行叠加），只取列表本身的手表能力。
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        scalingParams = watchScalingParams(),
        state = listState,
        rotaryScrollableBehavior = watchRotary(listState),
        contentPadding = PaddingValues(0.dp),
        autoCentering = WatchAutoCentering,
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(
            songs,
            key = { index, song -> "${song.hash}_$index" },
        ) { index, song ->
            TrackRow(
                song = song,
                isCurrent = false,
                onClick = { onPlaySong(songs, index) },
            )
        }
    }
}

private fun formatPlayCount(count: Int): String = when {
    count >= 100_000_000 -> "%.1f亿".format(count / 100_000_000.0)
    count >= 10_000 -> "%.1f万".format(count / 10_000.0)
    else -> "$count"
}
