package com.swtmaxx.kamusic.compose.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import com.swtmaxx.kamusic.compose.ui.component.CircleIconButton
import com.swtmaxx.kamusic.compose.ui.component.EmptyBox
import com.swtmaxx.kamusic.compose.ui.component.ErrorBox
import com.swtmaxx.kamusic.compose.ui.component.LoadingBox
import com.swtmaxx.kamusic.compose.ui.component.TrackRow
import com.swtmaxx.kamusic.compose.ui.component.WatchAutoCentering
import com.swtmaxx.kamusic.compose.ui.component.WatchScreenScaffold
import com.swtmaxx.kamusic.compose.ui.component.watchRotary
import com.swtmaxx.kamusic.compose.ui.component.watchScalingParams
import com.swtmaxx.kamusic.compose.ui.component.watchSwipeBack
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import com.swtmaxx.kamusic.compose.ui.vm.DiscoverViewModel
import com.swtmaxx.kamusic.compose.ui.vm.UiState

/**
 * 刷歌（`/home/discover`）。
 *
 * 上游是「沉浸式推荐流」，每次返回一小批（官方客户端默认 4 首），
 * 不需要游标 —— 「换一批」就是再调一次，并用 `today_play_num` 递增来让上游去重。
 */
@Composable
fun DiscoverScreen(onBack: () -> Unit, onOpenPlayer: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: DiscoverViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DiscoverViewModel(container.musicRepository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    val listState = rememberScalingLazyListState()

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).watchSwipeBack(onBack),
    ) {
        when (val current = state) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(current.message) { viewModel.load() }
            is UiState.Ready -> {
                val songs = current.data
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = WatchMetrics.gutterSmall,
                                vertical = WatchMetrics.gutterSmall,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "刷歌",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        CircleIconButton(
                            icon = painterResource(R.drawable.ic_refresh),
                            contentDescription = "换一批",
                            onClick = viewModel::refresh,
                            size = 32.dp,
                            iconSize = WatchMetrics.icon,
                        )
                    }

                    if (songs.isEmpty()) {
                        EmptyBox("暂无推荐")
                    } else {
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
                                itemsIndexed(
                                    songs,
                                    key = { index, song -> "${song.hash}_$index" },
                                ) { index, song ->
                                    TrackRow(
                                        song = song,
                                        isCurrent = false,
                                        onClick = {
                                            container.playbackController.playFrom(songs, index)
                                            onOpenPlayer()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
