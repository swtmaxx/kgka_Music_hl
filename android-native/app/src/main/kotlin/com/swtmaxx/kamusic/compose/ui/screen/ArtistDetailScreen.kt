package com.swtmaxx.kamusic.compose.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.swtmaxx.kamusic.compose.R
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.Artwork
import com.swtmaxx.kamusic.compose.ui.component.CircleIconButton
import com.swtmaxx.kamusic.compose.ui.component.ErrorBox
import com.swtmaxx.kamusic.compose.ui.component.LoadingBox
import com.swtmaxx.kamusic.compose.ui.component.TrackRow
import com.swtmaxx.kamusic.compose.ui.component.WatchAutoCentering
import com.swtmaxx.kamusic.compose.ui.component.WatchScreenScaffold
import com.swtmaxx.kamusic.compose.ui.component.watchRotary
import com.swtmaxx.kamusic.compose.ui.component.watchScalingParams
import com.swtmaxx.kamusic.compose.ui.component.watchSwipeBack
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import com.swtmaxx.kamusic.compose.ui.vm.ArtistDetailViewModel
import com.swtmaxx.kamusic.compose.ui.vm.UiState

/** 歌手详情：头像 + 名字 + 统计 + 热门歌曲。 */
@Composable
fun ArtistDetailScreen(
    artistId: String,
    fallbackName: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: ArtistDetailViewModel = viewModel(
        key = "artist_$artistId",
        factory = viewModelFactory {
            initializer {
                ArtistDetailViewModel(
                    repo = container.musicRepository,
                    artistId = artistId,
                    fallbackName = fallbackName,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(artistId) { viewModel.load() }

    val listState = rememberScalingLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .watchSwipeBack(onBack),
    ) {
        when (val current = state) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(current.message) { viewModel.load() }
            is UiState.Ready -> {
                val data = current.data
                WatchScreenScaffold(scrollState = listState) { contentPadding ->
                    ScalingLazyColumn(
                        scalingParams = watchScalingParams(),
                        state = listState,
                        rotaryScrollableBehavior = watchRotary(listState),
                        contentPadding = contentPadding,
                        autoCentering = WatchAutoCentering,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(WatchMetrics.gutterSmall),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = WatchMetrics.gutter),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Artwork(
                                    url = data.detail?.avatarUrl,
                                    size = 72.dp,
                                    corner = 36.dp,
                                )
                                Spacer(Modifier.height(WatchMetrics.gutterSmall))
                                Text(
                                    text = viewModel.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                data.detail?.subtitle?.takeIf { it.isNotEmpty() }?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (data.songs.isNotEmpty()) {
                                    Spacer(Modifier.height(WatchMetrics.gutterSmall))
                                    CircleIconButton(
                                        icon = painterResource(R.drawable.ic_playlist_play),
                                        contentDescription = "全部播放",
                                        onClick = {
                                            container.playbackController.playQueue(data.songs, 0)
                                            onOpenPlayer()
                                        },
                                        size = 36.dp,
                                        iconSize = WatchMetrics.icon,
                                    )
                                }
                            }
                        }

                        itemsIndexed(
                            data.songs,
                            key = { index, song -> "${song.hash}_$index" },
                        ) { index, song ->
                            TrackRow(
                                song = song,
                                isCurrent = false,
                                onClick = {
                                    container.playbackController.playFrom(data.songs, index)
                                    onOpenPlayer()
                                },
                            )
                        }

                        if (data.songs.isEmpty()) {
                            item {
                                Text(
                                    text = "暂无歌曲",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(WatchMetrics.gutter * 2),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 专辑详情：封面 + 名字 + 歌手/发行日期 + 曲目。 */
@Composable
fun AlbumDetailScreen(
    albumId: String,
    fallbackTitle: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: AlbumDetailViewModel = viewModel(
        key = "album_$albumId",
        factory = viewModelFactory {
            initializer {
                AlbumDetailViewModel(
                    repo = container.musicRepository,
                    albumId = albumId,
                    fallbackTitle = fallbackTitle,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(albumId) { viewModel.load() }

    val listState = rememberScalingLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .watchSwipeBack(onBack),
    ) {
        when (val current = state) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(current.message) { viewModel.load() }
            is UiState.Ready -> {
                val data = current.data
                WatchScreenScaffold(scrollState = listState) { contentPadding ->
                    ScalingLazyColumn(
                        scalingParams = watchScalingParams(),
                        state = listState,
                        rotaryScrollableBehavior = watchRotary(listState),
                        contentPadding = contentPadding,
                        autoCentering = WatchAutoCentering,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(WatchMetrics.gutterSmall),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = WatchMetrics.gutter),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Artwork(
                                    url = data.detail?.coverUrl,
                                    size = 72.dp,
                                    corner = 8.dp,
                                )
                                Spacer(Modifier.height(WatchMetrics.gutterSmall))
                                Text(
                                    text = viewModel.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                data.detail?.subtitle?.takeIf { it.isNotEmpty() }?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (data.songs.isNotEmpty()) {
                                    Spacer(Modifier.height(WatchMetrics.gutterSmall))
                                    CircleIconButton(
                                        icon = painterResource(R.drawable.ic_playlist_play),
                                        contentDescription = "全部播放",
                                        onClick = {
                                            container.playbackController.playQueue(data.songs, 0)
                                            onOpenPlayer()
                                        },
                                        size = 36.dp,
                                        iconSize = WatchMetrics.icon,
                                    )
                                }
                            }
                        }

                        itemsIndexed(
                            data.songs,
                            key = { index, song -> "${song.hash}_$index" },
                        ) { index, song ->
                            TrackRow(
                                song = song,
                                isCurrent = false,
                                onClick = {
                                    container.playbackController.playFrom(data.songs, index)
                                    onOpenPlayer()
                                },
                            )
                        }

                        if (data.songs.isEmpty()) {
                            item {
                                Text(
                                    text = "暂无曲目",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(WatchMetrics.gutter * 2),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
