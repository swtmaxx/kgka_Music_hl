package com.swtmaxx.kamusic.compose.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import com.swtmaxx.kamusic.compose.ui.component.CircleIconButton
import com.swtmaxx.kamusic.compose.ui.component.ErrorBox
import com.swtmaxx.kamusic.compose.ui.component.LoadingBox
import com.swtmaxx.kamusic.compose.ui.component.TrackRow
import com.swtmaxx.kamusic.compose.ui.component.WatchAutoCentering
import com.swtmaxx.kamusic.compose.ui.component.WatchScreenScaffold
import com.swtmaxx.kamusic.compose.ui.component.watchRotary
import com.swtmaxx.kamusic.compose.ui.component.watchScalingParams
import com.swtmaxx.kamusic.compose.ui.component.watchSwipeBack
import com.swtmaxx.kamusic.compose.ui.component.rememberStoragePermission
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import com.swtmaxx.kamusic.compose.ui.vm.RankDetailViewModel
import com.swtmaxx.kamusic.compose.ui.vm.UiState
import kotlinx.coroutines.launch

/**
 * 榜单详情页。
 *
 * 与 [PlaylistDetailScreen] 结构一致（标题 + 全部播放 + 歌曲列表 + 触底加载），
 * 只是数据源换成 `/rank/audio`（需要 `rankid` + `rank_cid` 两个参数）。
 */
@Composable
fun RankDetailScreen(
    rankId: String,
    rankCid: String,
    fallbackTitle: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val withPermission = rememberStoragePermission()
    val viewModel: RankDetailViewModel = viewModel(
        key = "rank_${rankId}_$rankCid",
        factory = viewModelFactory {
            initializer {
                RankDetailViewModel(
                    repo = container.musicRepository,
                    rankId = rankId,
                    rankCid = rankCid,
                    fallbackTitle = fallbackTitle,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(rankId, rankCid) { viewModel.load() }

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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = WatchMetrics.gutter,
                                        vertical = WatchMetrics.gutter,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = viewModel.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (data.songs.isNotEmpty()) {
                                    Spacer(Modifier.width(WatchMetrics.gutterSmall))
                                    CircleIconButton(
                                        icon = painterResource(R.drawable.ic_download),
                                        contentDescription = "下载全部",
                                        onClick = {
                                            withPermission {
                                                scope.launch {
                                                    container.downloader.enqueue(
                                                        data.songs,
                                                        container.sessionStore.downloadQuality,
                                                    )
                                                }
                                            }
                                        },
                                        size = 36.dp,
                                        iconSize = WatchMetrics.icon,
                                    )
                                    Spacer(Modifier.width(WatchMetrics.gutterSmall))
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
                            item {
                                LaunchedEffect(data.songs.size) { viewModel.loadMore() }
                            }
                        }
                    }
                }
            }
        }
    }
}
