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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.swtmaxx.kamusic.compose.core.DownloadState
import com.swtmaxx.kamusic.compose.core.Downloader
import com.swtmaxx.kamusic.compose.core.DownloadedSong
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.Artwork
import com.swtmaxx.kamusic.compose.ui.component.CircleIconButton
import com.swtmaxx.kamusic.compose.ui.component.EmptyBox
import com.swtmaxx.kamusic.compose.ui.component.WatchAutoCentering
import com.swtmaxx.kamusic.compose.ui.component.WatchProgressBar
import com.swtmaxx.kamusic.compose.ui.component.WatchScreenScaffold
import com.swtmaxx.kamusic.compose.ui.component.watchRotary
import com.swtmaxx.kamusic.compose.ui.component.watchScalingParams
import com.swtmaxx.kamusic.compose.ui.component.watchSwipeBack
import com.swtmaxx.kamusic.compose.ui.theme.AccentBlue
import com.swtmaxx.kamusic.compose.ui.theme.TextDisabled
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import java.io.File
import kotlinx.coroutines.launch

/**
 * 下载管理。
 *
 * 顶部显示「已用 / 可用」（`LocalStore.totalBytes()` + `StatFs`），
 * 中间是进行中/失败的任务（带进度条与「续传中」标记），
 * 下面是已下载列表（点击播放 / 单独删除 / 右上「全部删除」）。
 */
@Composable
fun DownloadScreen(onBack: () -> Unit, onOpenPlayer: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val downloads by container.localStore.downloads.collectAsStateWithLifecycle()
    val states by container.downloader.states.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    // 未完成的任务（Queued / Running / Failed）
    val pending = states.filterValues { it !is DownloadState.Done }.entries.toList()
    val usedBytes = downloads.sumOf { it.sizeBytes }
    val freeBytes = container.downloader.freeBytes()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = WatchMetrics.gutter, vertical = WatchMetrics.gutter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "下载管理",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                maxLines = 1,
                            )
                            Text(
                                text = "${Downloader.formatBytes(usedBytes)} / 可用 " +
                                    Downloader.formatBytes(freeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                            )
                        }
                        if (downloads.isNotEmpty()) {
                            CircleIconButton(
                                icon = painterResource(R.drawable.ic_delete),
                                contentDescription = "全部删除",
                                onClick = {
                                    scope.launch {
                                        downloads.forEach { deleteFiles(it) }
                                        container.localStore.clearAll()
                                        container.downloader.clearFinished()
                                    }
                                },
                                size = 32.dp,
                                iconSize = WatchMetrics.icon,
                            )
                        }
                    }
                }

                if (pending.isNotEmpty()) {
                    item { SectionLabel("进行中（${pending.size}）") }
                    itemsIndexed(pending, key = { _, e -> "p_${e.key}" }) { _, entry ->
                        PendingRow(
                            title = entry.key,
                            state = entry.value,
                            onCancel = { container.downloader.cancel(entry.key) },
                        )
                    }
                }

                if (downloads.isNotEmpty()) {
                    item { SectionLabel("已下载（${downloads.size}）") }
                    itemsIndexed(downloads, key = { _, d -> "d_${d.id}" }) { index, record ->
                        DownloadedRow(
                            record = record,
                            onClick = {
                                container.playbackController.playFrom(
                                    downloads.map { it.toSong() },
                                    index,
                                )
                                onOpenPlayer()
                            },
                            onDelete = {
                                scope.launch {
                                    deleteFiles(record)
                                    container.localStore.remove(record.id)
                                    container.downloader.clearFinished()
                                }
                            },
                        )
                    }
                }

                if (downloads.isEmpty() && pending.isEmpty()) {
                    item {
                        EmptyBox("还没有下载\n在播放页点下载按钮")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = WatchMetrics.gutter,
                end = WatchMetrics.gutter,
                top = WatchMetrics.gutterSmall,
            ),
    )
}

@Composable
private fun PendingRow(title: String, state: DownloadState, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WatchMetrics.gutter),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CircleIconButton(
                icon = painterResource(R.drawable.ic_close),
                contentDescription = "取消",
                onClick = onCancel,
                size = 28.dp,
                iconSize = WatchMetrics.icon,
            )
        }

        when (state) {
            is DownloadState.Running -> {
                val fraction = if (state.total > 0L) {
                    (state.bytes.toFloat() / state.total.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                WatchProgressBar(
                    progress = fraction,
                    touchHeight = 8.dp,
                )
                Text(
                    text = buildString {
                        append(Downloader.formatBytes(state.bytes))
                        if (state.total > 0L) append(" / ${Downloader.formatBytes(state.total)}")
                        if (state.resuming) append(" · 续传中")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }

            is DownloadState.Failed -> Text(
                text = state.message,
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                maxLines = 2,
            )

            DownloadState.Queued -> Text(
                text = "排队中",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )

            DownloadState.Done -> Unit
        }
    }
}

@Composable
private fun DownloadedRow(
    record: DownloadedSong,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(WatchMetrics.listRow)
            .clickable(onClick = onClick)
            .padding(horizontal = WatchMetrics.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = record.coverPath?.let { "file://$it" } ?: record.coverUrl, size = WatchMetrics.coverSmall)
        Spacer(Modifier.width(WatchMetrics.gutter))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${record.artist} · ${Downloader.formatBytes(record.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CircleIconButton(
            icon = painterResource(R.drawable.ic_delete),
            contentDescription = "删除",
            onClick = onDelete,
            size = 32.dp,
            iconSize = WatchMetrics.icon,
            tint = TextSecondary,
        )
    }
}

/** 删掉音频与封面文件（记录由调用方负责移除）。 */
private fun deleteFiles(record: DownloadedSong) {
    runCatching { File(record.filePath).delete() }
    record.coverPath?.let { runCatching { File(it).delete() } }
}
