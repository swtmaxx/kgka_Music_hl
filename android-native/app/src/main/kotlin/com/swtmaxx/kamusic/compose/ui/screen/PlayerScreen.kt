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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swtmaxx.kamusic.compose.R
import com.swtmaxx.kamusic.compose.data.model.LyricLine
import com.swtmaxx.kamusic.compose.playback.PlaybackMapping
import com.swtmaxx.kamusic.compose.playback.PlayMode
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.CircleIconButton
import com.swtmaxx.kamusic.compose.ui.component.PlayPauseButton
import com.swtmaxx.kamusic.compose.ui.component.watchSwipeBack
import com.swtmaxx.kamusic.compose.ui.component.WatchProgressBar
import com.swtmaxx.kamusic.compose.ui.theme.AccentBlue
import com.swtmaxx.kamusic.compose.ui.theme.TextDisabled
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import com.swtmaxx.kamusic.compose.ui.vm.PlayerViewModel

/**
 * 播放器页。
 *
 * 布局对齐参考图：歌名/歌手（左上）→ 控制键 → 进度条 → 次要按钮一行；
 * 中间区域给逐字高亮歌词。
 *
 * 性能要点：
 * - 高频播放位置单独订阅 [PlayerViewModel.positionMs]，只让进度条重组；
 * - 歌词用 `derivedStateOf` 算出当前行，避免每 500ms 重建整个列表；
 * - 自动滚动用 `scrollToItem`（瞬时）而非动画，避免低端设备掉帧。
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: PlayerViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PlayerViewModel(container.musicRepository, container.playbackController) }
        },
    )

    val state by viewModel.playerState.collectAsStateWithLifecycle()
    val positionState = viewModel.positionMs.collectAsStateWithLifecycle()
    val positionMs = positionState.value
    val lyricsState by viewModel.lyrics.collectAsStateWithLifecycle()

    val song = state.song
    val durationMs = state.durationMs

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 右滑返回。手势层放在 padding 之前，保证覆盖整个屏幕（含 padding 让出的左右边距），
            // 且只识别水平手势，与歌词列表的纵向滚动互不干扰。
            .watchSwipeBack(onBack)
            .background(Color.Black)
            .padding(horizontal = WatchMetrics.gutter),
    ) {
        // ===== 头部：返回 + 歌名/歌手 =====
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "返回",
                onClick = onBack,
                size = 32.dp,
                iconSize = WatchMetrics.icon,
            )
            Spacer(Modifier.width(WatchMetrics.gutterSmall))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song?.title ?: "未在播放",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song?.artist.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ===== 歌词 =====
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LyricsView(
                lines = lyricsState.lines,
                loading = lyricsState.loading,
                position = positionState,
            )
        }

        // ===== 控制键 =====
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = painterResource(R.drawable.ic_skip_previous),
                contentDescription = "上一首",
                onClick = { container.playbackController.previous() },
                size = WatchMetrics.minTouch,
                iconSize = WatchMetrics.iconLarge,
                enabled = state.song != null,
            )
            Spacer(Modifier.width(WatchMetrics.gutter))
            PlayPauseButton(
                isPlaying = state.isPlaying,
                onClick = { container.playbackController.togglePlayPause() },
                size = 52.dp,
            )
            Spacer(Modifier.width(WatchMetrics.gutter))
            CircleIconButton(
                icon = painterResource(R.drawable.ic_skip_next),
                contentDescription = "下一首",
                onClick = { container.playbackController.next() },
                size = WatchMetrics.minTouch,
                iconSize = WatchMetrics.iconLarge,
                enabled = state.song != null,
            )
        }

        // ===== 进度 =====
        WatchProgressBar(
            progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
            onSeek = { fraction ->
                if (durationMs > 0) {
                    container.playbackController.seekTo((durationMs * fraction).toLong())
                }
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Text(
                text = if (durationMs > 0) formatTime(durationMs) else "--:--",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }

        // ===== 次要按钮一行 =====
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = painterResource(
                    when (state.mode) {
                        PlayMode.SEQUENCE -> R.drawable.ic_repeat
                        PlayMode.REPEAT_ONE -> R.drawable.ic_repeat_one
                        PlayMode.SHUFFLE -> R.drawable.ic_shuffle
                    },
                ),
                contentDescription = PlaybackMapping.modeLabel(state.mode),
                onClick = { container.playbackController.cycleMode() },
                size = 40.dp,
                iconSize = WatchMetrics.icon,
                tint = if (state.mode == PlayMode.SEQUENCE) TextSecondary else AccentBlue,
            )
            CircleIconButton(
                icon = painterResource(R.drawable.ic_volume_up),
                contentDescription = if (state.isMuted) "取消静音" else "静音",
                onClick = { container.playbackController.toggleMute() },
                size = 40.dp,
                iconSize = WatchMetrics.icon,
                tint = if (state.isMuted) TextDisabled else TextSecondary,
            )
            CircleIconButton(
                icon = painterResource(R.drawable.ic_queue_music),
                contentDescription = "播放队列",
                onClick = onOpenQueue,
                size = 40.dp,
                iconSize = WatchMetrics.icon,
                tint = TextSecondary,
            )
        }
    }
}

@Composable
private fun LyricsView(
    lines: List<LyricLine>,
    loading: Boolean,
    position: androidx.compose.runtime.State<Long>,
) {
    if (lines.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (loading) "歌词加载中…" else "暂无歌词",
                style = MaterialTheme.typography.labelMedium,
                color = TextDisabled,
            )
        }
        return
    }

    // 一次 derivedStateOf 同时算出「当前行」与「当前字」，
    // 避免每个播放位置 tick 都重建列表；只有可见的几行会重组。
    val highlight by remember(lines) {
        derivedStateOf {
            val pos = position.value
            var index = -1
            for (i in lines.indices) {
                if (pos >= lines[i].timeMs) index = i else break
            }
            val word = if (index >= 0) lines[index].activeWordIndex(pos) else -1
            index to word
        }
    }
    val activeIndex = highlight.first
    val activeWord = highlight.second

    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.scrollToItem(activeIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndex
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = buildLyricText(line, if (isActive) activeWord else -1),
                    style = if (isActive) {
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    color = if (isActive) TextPrimary else TextDisabled,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isActive) {
                    line.translation?.takeIf { it.isNotEmpty() }?.let { translation ->
                        Text(
                            text = translation,
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

/**
 * 构造逐字高亮的歌词文本。
 *
 * - 已唱过的字：强调色
 * - 正在唱的字：白色
 * - 未唱的字：暗灰
 *
 * [activeWord] 为 -1 表示非当前行，整行使用默认颜色。
 */
private fun buildLyricText(line: LyricLine, activeWord: Int): AnnotatedString {
    if (line.words.isEmpty()) return AnnotatedString(line.text)

    return buildAnnotatedString {
        line.words.forEachIndexed { index, word ->
            val color = when {
                activeWord < 0 -> TextDisabled
                index < activeWord -> AccentBlue
                index == activeWord -> TextPrimary
                else -> TextDisabled
            }
            withStyle(SpanStyle(color = color)) { append(word.text) }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
