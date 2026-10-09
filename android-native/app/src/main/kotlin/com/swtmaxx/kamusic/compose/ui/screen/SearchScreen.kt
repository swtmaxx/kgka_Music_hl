package com.swtmaxx.kamusic.compose.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swtmaxx.kamusic.compose.R
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.Artwork
import com.swtmaxx.kamusic.compose.ui.component.CircleIconButton
import com.swtmaxx.kamusic.compose.ui.component.ErrorBox
import com.swtmaxx.kamusic.compose.ui.component.LoadingBox
import com.swtmaxx.kamusic.compose.ui.component.PillTab
import com.swtmaxx.kamusic.compose.ui.component.TrackRow
import com.swtmaxx.kamusic.compose.ui.component.WatchAutoCentering
import com.swtmaxx.kamusic.compose.ui.component.WatchTextField
import com.swtmaxx.kamusic.compose.ui.component.watchRotary
import com.swtmaxx.kamusic.compose.ui.component.watchScalingParams
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import com.swtmaxx.kamusic.compose.ui.vm.SearchViewModel

/**
 * 搜索页。
 *
 * 热搜分类用 `Column` + `Wrap` 渲染（**不用嵌套滚动**）：
 * 手表屏上「纵向列表里再套一个可滚动面板」会让滑动手势互相抢，
 * 这也是 Flutter 版遗留的体验问题。
 */
@Composable
fun SearchScreen(
    onOpenPlayer: () -> Unit,
    onOpenAlbum: (id: String, title: String) -> Unit = { _, _ -> },
) {
    val container = LocalAppContainer.current
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SearchViewModel(container.musicRepository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 结果列表与建议列表共用同一个表冠滚动状态。本页有固定搜索行，因此不套 ScreenScaffold
    // （否则系统时间会与搜索行叠加），只取列表本身的手表能力。
    val listState = rememberScalingLazyListState()

    LaunchedEffect(Unit) { viewModel.ensureHotLoaded() }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WatchMetrics.gutterSmall, vertical = WatchMetrics.gutterSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WatchTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = "搜索歌曲",
                imeAction = ImeAction.Search,
                onImeAction = { viewModel.submit() },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(WatchMetrics.gutterSmall))
            if (state.query.isNotEmpty()) {
                CircleIconButton(
                    icon = painterResource(R.drawable.ic_close),
                    contentDescription = "清空",
                    onClick = viewModel::clearQuery,
                    size = 36.dp,
                    iconSize = WatchMetrics.icon,
                )
            } else {
                CircleIconButton(
                    icon = painterResource(R.drawable.ic_search),
                    contentDescription = "搜索",
                    onClick = { viewModel.submit() },
                    size = 36.dp,
                    iconSize = WatchMetrics.icon,
                )
            }
        }

        // 单曲 / 专辑 切换（有关键词时才显示，避免空态多占一行）
        if (state.query.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = WatchMetrics.gutterSmall,
                        vertical = WatchMetrics.gutterSmall,
                    ),
                horizontalArrangement = Arrangement.spacedBy(WatchMetrics.gutterSmall),
            ) {
                PillTab(
                    text = "单曲",
                    selected = state.type == 0,
                    onClick = { viewModel.selectType(0) },
                    modifier = Modifier.weight(1f),
                )
                PillTab(
                    text = "专辑",
                    selected = state.type == 1,
                    onClick = { viewModel.selectType(1) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when {
            state.searching -> LoadingBox()

            state.error != null -> ErrorBox(state.error!!) { viewModel.submit() }

            state.type == 1 && state.albums.isNotEmpty() -> ScalingLazyColumn(
                scalingParams = watchScalingParams(),
                state = listState,
                rotaryScrollableBehavior = watchRotary(listState),
                contentPadding = PaddingValues(0.dp),
                autoCentering = WatchAutoCentering,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.albums, key = { _, album -> album.id }) { _, album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WatchMetrics.listRow)
                            .clickable { onOpenAlbum(album.id, album.name) }
                            .padding(horizontal = WatchMetrics.gutter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(url = album.coverUrl, size = WatchMetrics.coverSmall)
                        Spacer(Modifier.width(WatchMetrics.gutter))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subtitle = album.subtitle
                            if (subtitle.isNotEmpty()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            state.results.isNotEmpty() -> ScalingLazyColumn(
                scalingParams = watchScalingParams(),
                state = listState,
                rotaryScrollableBehavior = watchRotary(listState),
                contentPadding = PaddingValues(0.dp),
                autoCentering = WatchAutoCentering,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    state.results,
                    key = { index, song -> "${song.hash}_$index" },
                ) { index, song ->
                    TrackRow(
                        song = song,
                        isCurrent = false,
                        onClick = {
                            container.playbackController.playFrom(state.results, index)
                            onOpenPlayer()
                        },
                    )
                }
            }

            state.query.isNotBlank() && state.suggestions.isNotEmpty() -> ScalingLazyColumn(
                scalingParams = watchScalingParams(),
                state = listState,
                rotaryScrollableBehavior = watchRotary(listState),
                contentPadding = PaddingValues(0.dp),
                autoCentering = WatchAutoCentering,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.suggestions) { _, suggestion ->
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.submit(suggestion) }
                            .padding(
                                horizontal = WatchMetrics.gutter,
                                vertical = WatchMetrics.gutter + 2.dp,
                            ),
                    )
                }
            }

            else -> HotPanel(state, viewModel)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HotPanel(
    state: com.swtmaxx.kamusic.compose.ui.vm.SearchUiState,
    viewModel: SearchViewModel,
) {
    if (state.hot.isEmpty()) {
        LoadingBox("热搜加载中…")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = WatchMetrics.gutter),
    ) {
        state.hot.forEach { category ->
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                modifier = Modifier.padding(top = WatchMetrics.gutter, bottom = WatchMetrics.gutterSmall),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(WatchMetrics.gutterSmall),
                verticalArrangement = Arrangement.spacedBy(WatchMetrics.gutterSmall),
                modifier = Modifier.fillMaxWidth(),
            ) {
                category.keywords.forEach { keyword ->
                    PillTab(
                        text = keyword,
                        selected = false,
                        onClick = { viewModel.submit(keyword) },
                    )
                }
            }
        }
        Spacer(Modifier.height(WatchMetrics.gutter * 2))
    }
}
