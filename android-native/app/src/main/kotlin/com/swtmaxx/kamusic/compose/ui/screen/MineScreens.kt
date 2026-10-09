package com.swtmaxx.kamusic.compose.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.ErrorBox
import com.swtmaxx.kamusic.compose.ui.component.LoadingBox
import com.swtmaxx.kamusic.compose.ui.component.TrackRow
import com.swtmaxx.kamusic.compose.ui.component.WatchAutoCentering
import com.swtmaxx.kamusic.compose.ui.component.WatchScreenScaffold
import com.swtmaxx.kamusic.compose.ui.component.watchRotary
import com.swtmaxx.kamusic.compose.ui.component.watchScalingParams
import com.swtmaxx.kamusic.compose.ui.component.watchSwipeBack
import com.swtmaxx.kamusic.compose.ui.theme.AccentBlue
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import com.swtmaxx.kamusic.compose.ui.vm.CloudViewModel
import com.swtmaxx.kamusic.compose.ui.vm.HistoryViewModel
import com.swtmaxx.kamusic.compose.ui.vm.UiState
import com.swtmaxx.kamusic.compose.ui.vm.VipViewModel

/**
 * 「我的」下的三个子页：云盘 / VIP / 播放历史。
 *
 * 三者都是「列表或信息卡 + 右滑返回」，结构同构，放在同一文件里。
 * 它们都依赖登录态，未登录时上游会返回 5xx，页面会显示 [ErrorBox] 并带重试。
 */

@Composable
fun CloudScreen(onBack: () -> Unit, onOpenPlayer: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: CloudViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CloudViewModel(container.musicRepository) }
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
                            Text(
                                text = "云盘 · ${songs.size} 首",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = WatchMetrics.gutter,
                                        vertical = WatchMetrics.gutter,
                                    ),
                            )
                        }
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
                        if (songs.isEmpty()) {
                            item {
                                Text(
                                    text = "云盘是空的",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(WatchMetrics.gutter * 2),
                                )
                            }
                        } else {
                            item {
                                LaunchedEffect(songs.size) { viewModel.loadMore() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VipScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: VipViewModel = viewModel(
        factory = viewModelFactory {
            initializer { VipViewModel(container.musicRepository) }
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
                val vip = current.data
                WatchScreenScaffold(scrollState = listState) { contentPadding ->
                    ScalingLazyColumn(
                        scalingParams = watchScalingParams(),
                        state = listState,
                        rotaryScrollableBehavior = watchRotary(listState),
                        contentPadding = contentPadding,
                        autoCentering = WatchAutoCentering,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = WatchMetrics.gutter * 2),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = vip.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (vip.isVip) AccentBlue else TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                vip.expireText?.takeIf { it.isNotEmpty() }?.let {
                                    Spacer(Modifier.height(WatchMetrics.gutterSmall))
                                    Text(
                                        text = "到期：$it",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
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

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenPlayer: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HistoryViewModel(container.musicRepository) }
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
                            Text(
                                text = "播放历史 · ${songs.size} 首",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = WatchMetrics.gutter,
                                        vertical = WatchMetrics.gutter,
                                    ),
                            )
                        }
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
                        if (songs.isEmpty()) {
                            item {
                                Text(
                                    text = "暂无播放历史",
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
