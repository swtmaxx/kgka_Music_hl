package com.swtmaxx.kamusic.compose.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.swtmaxx.kamusic.compose.ui.component.CircleIconButton
import com.swtmaxx.kamusic.compose.ui.component.ErrorBox
import com.swtmaxx.kamusic.compose.ui.component.LoadingBox
import com.swtmaxx.kamusic.compose.ui.component.PillTab
import com.swtmaxx.kamusic.compose.ui.component.TrackRow
import com.swtmaxx.kamusic.compose.ui.component.WatchTextField
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
fun SearchScreen(onOpenPlayer: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SearchViewModel(container.musicRepository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

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

        when {
            state.searching -> LoadingBox()

            state.error != null -> ErrorBox(state.error!!) { viewModel.submit() }

            state.results.isNotEmpty() -> LazyColumn(modifier = Modifier.fillMaxSize()) {
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

            state.query.isNotBlank() && state.suggestions.isNotEmpty() -> LazyColumn(
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
