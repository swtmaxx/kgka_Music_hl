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
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.Artwork
import com.swtmaxx.kamusic.compose.ui.component.ErrorBox
import com.swtmaxx.kamusic.compose.ui.component.LoadingBox
import com.swtmaxx.kamusic.compose.ui.component.WatchAutoCentering
import com.swtmaxx.kamusic.compose.ui.component.WatchScreenScaffold
import com.swtmaxx.kamusic.compose.ui.component.watchRotary
import com.swtmaxx.kamusic.compose.ui.component.watchScalingParams
import com.swtmaxx.kamusic.compose.ui.component.watchSwipeBack
import com.swtmaxx.kamusic.compose.ui.theme.TextDisabled
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import com.swtmaxx.kamusic.compose.ui.vm.CommentViewModel
import com.swtmaxx.kamusic.compose.ui.vm.UiState

/**
 * 歌曲评论页（**只读**）。
 *
 * 240×284 上不适合阅读长文与输入，因此只展示：头像 + 昵称 + 点赞数 + 正文（最多 4 行）。
 * 接口需要的是 `mixsongid`（= `Song.albumAudioId`），不是 hash。
 */
@Composable
fun CommentScreen(
    mixSongId: String,
    fallbackTitle: String,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: CommentViewModel = viewModel(
        key = "comment_$mixSongId",
        factory = viewModelFactory {
            initializer {
                CommentViewModel(
                    repo = container.musicRepository,
                    mixSongId = mixSongId,
                    fallbackTitle = fallbackTitle,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(mixSongId) { viewModel.load() }

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
                val comments = current.data
                WatchScreenScaffold(scrollState = listState) { contentPadding ->
                    ScalingLazyColumn(
                        scalingParams = watchScalingParams(),
                        state = listState,
                        rotaryScrollableBehavior = watchRotary(listState),
                        contentPadding = contentPadding,
                        autoCentering = WatchAutoCentering,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(WatchMetrics.gutter),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item {
                            Text(
                                text = "评论 · ${comments.size}",
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

                        itemsIndexed(comments, key = { _, c -> c.id }) { _, comment ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = WatchMetrics.gutter),
                            ) {
                                Artwork(url = comment.avatarUrl, size = 24.dp, corner = 12.dp)
                                Spacer(Modifier.width(WatchMetrics.gutterSmall))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = comment.userName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        comment.likeCount?.takeIf { it > 0 }?.let {
                                            Text(
                                                text = "♥ $it",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextDisabled,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = comment.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    comment.time?.let {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextDisabled,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }

                        if (comments.isEmpty()) {
                            item {
                                Text(
                                    text = "暂无评论",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(WatchMetrics.gutter * 2),
                                )
                            }
                        } else {
                            item {
                                Text(
                                    text = "已加载 ${comments.size} 条",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextDisabled,
                                    modifier = Modifier.padding(WatchMetrics.gutter),
                                )
                            }
                            item {
                                LaunchedEffect(comments.size) { viewModel.loadMore() }
                            }
                        }
                    }
                }
            }
        }
    }
}
