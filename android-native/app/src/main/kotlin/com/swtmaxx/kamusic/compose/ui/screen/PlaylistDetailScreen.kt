package com.swtmaxx.kamusic.compose.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swtmaxx.kamusic.compose.R
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.CircleIconButton
import com.swtmaxx.kamusic.compose.ui.component.ErrorBox
import com.swtmaxx.kamusic.compose.ui.component.LoadingBox
import com.swtmaxx.kamusic.compose.ui.component.TrackRow
import com.swtmaxx.kamusic.compose.ui.component.WatchTopBar
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import com.swtmaxx.kamusic.compose.ui.vm.PlaylistViewModel
import com.swtmaxx.kamusic.compose.ui.vm.UiState

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    fallbackTitle: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: PlaylistViewModel = viewModel(
        key = "playlist_$playlistId",
        factory = viewModelFactory {
            initializer {
                PlaylistViewModel(container.musicRepository, playlistId, fallbackTitle)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(playlistId) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val songs = (state as? UiState.Ready)?.data?.songs.orEmpty()

        WatchTopBar(
            title = viewModel.title,
            onBack = onBack,
            actions = {
                if (songs.isNotEmpty()) {
                    CircleIconButton(
                        icon = painterResource(R.drawable.ic_playlist_play),
                        contentDescription = "全部播放",
                        onClick = {
                            container.playbackController.playQueue(songs, 0)
                            onOpenPlayer()
                        },
                        size = 36.dp,
                        iconSize = WatchMetrics.icon,
                    )
                }
            },
        )

        when (val current = state) {
            is UiState.Loading -> LoadingBox()

            is UiState.Error -> ErrorBox(current.message) { viewModel.load() }

            is UiState.Ready -> {
                val data = current.data
                LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                    if (data.loadingMore) {
                        item { LoadingBox("加载更多…") }
                    } else if (data.songs.isNotEmpty()) {
                        item {
                            Text(
                                text = "已加载 ${data.songs.size} 首",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(WatchMetrics.gutter),
                            )
                        }
                        // 滚动到底部时自动加载下一页
                        item {
                            LaunchedEffect(data.songs.size) { viewModel.loadMore() }
                        }
                    }
                }
            }
        }
    }
}
