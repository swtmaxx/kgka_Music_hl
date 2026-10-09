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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swtmaxx.kamusic.compose.data.model.PlaylistSummary
import com.swtmaxx.kamusic.compose.data.model.Song
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.ArtworkFill
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

private val TABS = listOf("推荐", "每日", "排行", "电台")

@Composable
fun HomeScreen(
    onOpenPlaylist: (id: String, title: String) -> Unit,
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
                onSelectFm = viewModel::selectFm,
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
    onSelectFm: (String) -> Unit,
) {
    val error = data.errorFor(tab)
    if (error != null) {
        ErrorBox(error, onRetry = onRetry)
        return
    }

    when (tab) {
        0 -> PlaylistGrid(data.playlists, onOpenPlaylist)
        1 -> SongList(data.dailySongs, onPlaySong)
        2 -> SongList(data.topSongs, onPlaySong)
        3 -> FmSection(data, onSelectFm, onPlaySong)
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

@Composable
private fun FmSection(
    data: HomeData,
    onSelectFm: (String) -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
) {
    val stations = data.fmGroups.flatMap { it.stations }
    Column(modifier = Modifier.fillMaxSize()) {
        if (stations.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = WatchMetrics.gutterSmall),
                horizontalArrangement = Arrangement.spacedBy(WatchMetrics.gutterSmall),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(stations, key = { it.id }) { station ->
                    PillTab(
                        text = station.name,
                        selected = station.id == data.selectedFmId,
                        onClick = { onSelectFm(station.id) },
                    )
                }
            }
            Spacer(Modifier.height(WatchMetrics.gutterSmall))
        }
        SongList(data.fmSongs, onPlaySong)
    }
}

private fun formatPlayCount(count: Int): String = when {
    count >= 100_000_000 -> "%.1f亿".format(count / 100_000_000.0)
    count >= 10_000 -> "%.1f万".format(count / 10_000.0)
    else -> "$count"
}
