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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.swtmaxx.kamusic.compose.R
import com.swtmaxx.kamusic.compose.data.model.LyricLine
import com.swtmaxx.kamusic.compose.playback.PlayMode
import com.swtmaxx.kamusic.compose.playback.PlaybackMapping
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.CircleIconButton
import com.swtmaxx.kamusic.compose.ui.component.PlayPauseButton
import com.swtmaxx.kamusic.compose.ui.component.WatchProgressBar
import com.swtmaxx.kamusic.compose.ui.component.watchSwipeBack
import com.swtmaxx.kamusic.compose.ui.theme.AccentBlue
import com.swtmaxx.kamusic.compose.ui.theme.TextDisabled
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import com.swtmaxx.kamusic.compose.ui.vm.PlayerViewModel

/**
 * 播放页尺寸令牌。
 *
 * 结构对齐 **官方 Wear OS 播放页**（`google/horologist` 的
 * `media/ui-material3/.../screens/player/PlayerScreen.kt`）的**三段式等分布局**：
 *
 * ```
 * Column(verticalArrangement = SpaceBetween) {
 *   ① 上段 requiredHeight(topSectionHeight)    BottomCenter  媒体信息 / 歌词
 *   ② 中段 requiredHeight(middleSectionHeight) Center        播放控制键（含进度）
 *   ③ 下段 weight(1f)                          Center        次要按钮
 * }
 * middleSectionHeight = max(MIDDLE_BUTTON_SIZE, screenHeightDp / 3f)
 * topSectionHeight    = max(TOP_SECTION_MIN_HEIGHT, (screenHeightDp - middleSectionHeight) / 2f)
 * ```
 *
 * 代入 240×284（`screenHeightDp = 284`）：
 * ```
 * middleSectionHeight = max(52, 94.67) = 94.67dp
 * topSectionHeight    = max(68, 94.67) = 94.67dp
 * 下段 weight(1f)                       = 94.67dp
 * ```
 * 即三段严格等分 94.67dp。
 *
 * 与官方的两处刻意差异：
 * 1. **播放键用 52dp**（官方大屏 80dp / 小屏 64dp）。240px 宽的屏上 80dp 按钮会占掉 1/3 屏宽，
 *    两侧的上一首/下一首会被挤没，故收窄。
 * 2. **上段放歌词而非纯媒体信息**。官方上段是「歌名 + 歌手」纯文字（封面走 background slot），
 *    且官方布局里**没有歌词的位置**。本 App 以歌词为核心，因此把「歌名 + 歌手」压成上段顶部的
 *    一行（与返回键同行），把上段主体让给歌词——这样歌词仍能显示 3 行，不因改版而退步。
 */
private object PlayerLayout {
    /** 中段播放键直径。官方小屏 64dp / 大屏 80dp，此处按 240px 屏宽收窄。 */
    val middleButtonSize = 52.dp

    /** 上段最小高度（官方 `SMALL_DEVICE_PLAYER_SCREEN_TOP_SECTION_HEIGHT = 68.dp`）。 */
    val minTopSectionHeight = 68.dp

    /** 顶部「返回键 + 歌名/歌手」行高。 */
    val headerHeight = 34.dp

    /**
     * 进度条触控区高度。
     *
     * 改造前沿用 `WatchMetrics.minTouch`（48dp），但进度条视觉只有 3dp —— 相当于用 17% 的屏高
     * 去承载一根看不见的线。压缩到 24dp 后仍高于 Android 无障碍建议的 24dp 下限，
     * 同时把省下的 24dp 让给歌词。
     */
    val progressTouchHeight = 24.dp
}

/**
 * 播放器页。
 *
 * 三段式布局（上：歌词 / 中：控制键 + 进度 / 下：次要按钮），尺寸由屏高推导，
 * 换屏时无需改常量。右滑返回。
 *
 * 性能要点：
 * - 高频播放位置单独订阅 [PlayerViewModel.positionMs]，只让进度条与歌词重组；
 * - 歌词用 `derivedStateOf` 算出当前行与当前字，避免每个位置 tick 都重建列表；
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

    // ===== 官方三段式的尺寸推导（见 PlayerLayout 文档）=====
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val middleSectionHeight = max(PlayerLayout.middleButtonSize, screenHeight / 3f)
    val topSectionHeight = max(
        PlayerLayout.minTopSectionHeight,
        (screenHeight - middleSectionHeight) / 2f,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 右滑返回。手势层放在 padding 之前，保证覆盖整个屏幕，
            // 且只识别水平手势，与歌词列表的纵向滚动互不干扰。
            .watchSwipeBack(onBack)
            .background(Color.Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = WatchMetrics.gutter),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // ================= ① 上段：头部 + 歌词 =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(topSectionHeight),
            ) {
                // 头部：返回 + 歌名/歌手（与返回键同行，为歌词让出高度）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PlayerLayout.headerHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleIconButton(
                        icon = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "返回",
                        onClick = onBack,
                        size = 28.dp,
                        iconSize = WatchMetrics.icon,
                    )
                    Spacer(Modifier.width(WatchMetrics.gutterSmall))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song?.title ?: "未在播放",
                            style = MaterialTheme.typography.titleSmall,
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

                // 歌词：吃掉上段剩余高度（284 屏上约 60.67dp，可显示 3 行）
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LyricsView(
                        lines = lyricsState.lines,
                        loading = lyricsState.loading,
                        position = positionState,
                    )
                }
            }

            // ================= ② 中段：控制键 + 进度 =================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(middleSectionHeight),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 上一首 / 播放暂停 / 下一首
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircleIconButton(
                            icon = painterResource(R.drawable.ic_skip_previous),
                            contentDescription = "上一首",
                            onClick = { container.playbackController.previous() },
                            size = WatchMetrics.minTouch,
                            iconSize = WatchMetrics.iconLarge,
                            enabled = song != null,
                        )
                        Spacer(Modifier.width(WatchMetrics.gutter))
                        PlayPauseButton(
                            isPlaying = state.isPlaying,
                            onClick = { container.playbackController.togglePlayPause() },
                            size = PlayerLayout.middleButtonSize,
                        )
                        Spacer(Modifier.width(WatchMetrics.gutter))
                        CircleIconButton(
                            icon = painterResource(R.drawable.ic_skip_next),
                            contentDescription = "下一首",
                            onClick = { container.playbackController.next() },
                            size = WatchMetrics.minTouch,
                            iconSize = WatchMetrics.iconLarge,
                            enabled = song != null,
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    // 进度：点击 seek（240px 宽屏上拖动难以精确，故只做点击）
                    WatchProgressBar(
                        progress =
                            if (durationMs > 0) {
                                positionMs.toFloat() / durationMs.toFloat()
                            } else {
                                0f
                            },
                        onSeek = { fraction ->
                            if (durationMs > 0) {
                                container.playbackController.seekTo(
                                    (durationMs * fraction).toLong(),
                                )
                            }
                        },
                        touchHeight = PlayerLayout.progressTouchHeight,
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
                }
            }

            // ================= ③ 下段：次要按钮 =================
            // 官方下段是 `buttons`（默认空实现）。我们放 3 个次要按钮，
            // 剩余约 50dp 空白留给后续功能入口（下载 / 评论 / 专辑 / 歌手）。
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

    // 行距从 6dp 收到 4dp：284 屏的上段给歌词只剩约 60dp，
    // 4dp 行距能让「当前行 + 下两行」正好放得下（17 + 4 + 15 + 4 + 15 = 55dp）。
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
