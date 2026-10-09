package com.swtmaxx.kamusic.compose.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.swtmaxx.kamusic.compose.R
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.Artwork
import com.swtmaxx.kamusic.compose.ui.component.CircleIconButton
import com.swtmaxx.kamusic.compose.ui.component.WatchAutoCentering
import com.swtmaxx.kamusic.compose.ui.component.WatchScreenScaffold
import com.swtmaxx.kamusic.compose.ui.component.watchRotary
import com.swtmaxx.kamusic.compose.ui.component.watchScalingParams
import com.swtmaxx.kamusic.compose.ui.component.watchSwipeBack
import com.swtmaxx.kamusic.compose.ui.theme.AccentBlue
import com.swtmaxx.kamusic.compose.ui.theme.TextDisabled
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics

/**
 * 播放队列页。
 *
 * 由手机版 Compose 切换到 Compose for Wear OS 后：
 * - 外层改用 [WatchScreenScaffold]（顶部系统时间 + 方屏贴边 contentPadding）；
 * - 列表改用 `ScalingLazyColumn` 并接入**表冠**滚动；
 * - 原 `WatchTopBar`（含返回键）已删除，页面标题改为列表首项、随列表滚走，
 *   返回改由**右滑手势**（[watchSwipeBack]）或系统返回键承担。
 */
@Composable
fun QueueScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val queue by container.playbackController.queue.collectAsStateWithLifecycle()
    val state by container.playbackController.state.collectAsStateWithLifecycle()

    val listState = rememberScalingLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 右滑返回：只识别水平手势，与列表纵向滚动 / 表冠互不干扰。
            .watchSwipeBack(onBack),
    ) {
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
                // 页面标题作为列表首项（随列表滚走）——顶栏位置已交给 ScreenScaffold 的系统时间。
                item {
                    Text(
                        text = "播放队列（${queue.size}）",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = WatchMetrics.gutter,
                                vertical = WatchMetrics.gutter,
                            ),
                    )
                }

                if (queue.isEmpty()) {
                    item {
                        Text(
                            text = "队列是空的",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextDisabled,
                            modifier = Modifier.padding(top = WatchMetrics.gutter * 3),
                        )
                    }
                } else {
                    itemsIndexed(
                        queue,
                        key = { index, song -> "${song.hash}_$index" },
                    ) { index, song ->
                        val isCurrent = index == state.queueIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(WatchMetrics.listRow)
                                .clickable { container.playbackController.playAt(index) }
                                .padding(horizontal = WatchMetrics.gutter),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(url = song.coverUrl, size = WatchMetrics.coverSmall)
                            Spacer(Modifier.width(WatchMetrics.gutter))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCurrent) AccentBlue else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            CircleIconButton(
                                icon = painterResource(R.drawable.ic_delete),
                                contentDescription = "从队列移除",
                                onClick = { container.playbackController.removeAt(index) },
                                size = 36.dp,
                                iconSize = WatchMetrics.icon,
                            )
                        }
                    }
                }
            }
        }
    }
}
