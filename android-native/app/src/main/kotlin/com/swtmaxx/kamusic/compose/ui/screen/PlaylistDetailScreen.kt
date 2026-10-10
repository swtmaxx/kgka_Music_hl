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
import com.swtmaxx.kamusic.compose.ui.vm.PlaylistViewModel
import com.swtmaxx.kamusic.compose.ui.vm.UiState
import kotlinx.coroutines.launch

/**
 * 歌单详情页。
 *
 * 由手机版 Compose 切换到 Compose for Wear OS 后：
 * - 外层改用 [WatchScreenScaffold]（顶部系统时间 + 方屏贴边 contentPadding）；
 * - 列表改用 `ScalingLazyColumn` 并接入**表冠**滚动；
 * - 原 `WatchTopBar`（含返回键）已删除：标题 + 「全部播放」按钮改为列表首项，
 *   返回改由**右滑手势**（[watchSwipeBack]）或系统返回键承担。
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    fallbackTitle: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val withPermission = rememberStoragePermission()
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

    val listState = rememberScalingLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 右滑返回：只识别水平手势，与列表纵向滚动 / 表冠互不干扰。
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
                        // 标题 + 全部播放（替代原 WatchTopBar，随列表滚走）
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
}
