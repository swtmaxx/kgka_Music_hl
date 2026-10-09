package com.swtmaxx.kamusic.compose.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.min
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
import com.swtmaxx.kamusic.compose.playback.PlayerUiState
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.Artwork
import com.swtmaxx.kamusic.compose.ui.component.CircleIconButton
import com.swtmaxx.kamusic.compose.ui.component.PlayPauseButton
import com.swtmaxx.kamusic.compose.ui.component.WatchProgressBar
import com.swtmaxx.kamusic.compose.ui.theme.AccentBlue
import com.swtmaxx.kamusic.compose.ui.theme.OutlineDim
import com.swtmaxx.kamusic.compose.ui.theme.TextDisabled
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import com.swtmaxx.kamusic.compose.ui.vm.LyricsUiData
import com.swtmaxx.kamusic.compose.ui.vm.PlayerViewModel

/**
 * 播放页尺寸令牌。
 *
 * 布局参考 **官方 Wear OS 播放器**（`google/horologist`）的两点做法：
 * 1. **水平翻页**：官方 `PlayerLibraryPagerScreen` 用 `HorizontalPager(pageCount = 2)`
 *    在「播放器」与「音乐库」之间左右翻页。本页借用同一模式，改为
 *    「**控制页 ⇄ 歌词页**」——这样歌词能拿满整屏，不再和封面/控制键抢那 60dp。
 * 2. **尺寸由屏高推导**：官方 `middleSectionHeight = max(MIDDLE_BUTTON_SIZE, screenHeightDp / 3f)`，
 *    不写死像素。本页的封面尺寸与进度触控高度同理。
 *
 * ⚠️ 因为 `HorizontalPager` 会消费水平手势，本页**不再挂 `watchSwipeBack`**（两者会抢手势）。
 * 返回依靠头部返回键与系统返回键。
 */
private object PlayerLayout {
    /** 顶部「返回键 + 歌名/歌手 + 页码点」行高。 */
    val headerHeight = 34.dp

    /** 控制页播放键直径。官方小屏 64dp / 大屏 80dp，此处按 240px 屏宽收窄。 */
    val middleButtonSize = 56.dp

    /**
     * 次要按钮直径。
     *
     * 40dp → 36dp：下段现在要放两行（模式/静音/队列 + 歌手/专辑/评论），
     * 收窄后才能给封面留出可用高度（240×284 上封面仍有约 81dp）。
     */
    val secondaryButtonSize = 36.dp

    /**
     * 进度条触控区高度。
     *
     * 改造前沿用 `WatchMetrics.minTouch`（48dp），但进度条视觉只有 3dp —— 相当于用 17% 的屏高
     * 去承载一根看不见的线。压缩到 24dp 后仍高于无障碍建议下限。
     */
    val progressTouchHeight = 24.dp

    /** 页码指示点直径。 */
    val dotSize = 5.dp
}

/**
 * 播放页：**控制页 ⇄ 歌词页** 水平翻页。
 *
 * - 第 0 页（控制页）：封面 + 进度 + 传输键 + 次要按钮
 * - 第 1 页（歌词页）：整屏歌词 + 底部细进度条
 *
 * 两页共用同一个 [PlayerViewModel]，所以切页不会重新拉歌词、也不会打断播放。
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
    val lyricsState by viewModel.lyrics.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { 2 })

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 与主壳一致：关闭越界预加载，低端设备上省合成帧。
            beyondViewportPageCount = 0,
        ) { page ->
            when (page) {
                0 -> ControlPage(
                    state = state,
                    positionState = positionState,
                    currentPage = pagerState.currentPage,
                    onBack = onBack,
                    onOpenQueue = onOpenQueue,
                )

                else -> LyricsPage(
                    lyrics = lyricsState,
                    positionState = positionState,
                    songTitle = state.song?.title,
                    songArtist = state.song?.artist,
                    durationMs = state.durationMs,
                    currentPage = pagerState.currentPage,
                    onBack = onBack,
                    onSeekFraction = { fraction ->
                        val duration = state.durationMs
                        if (duration > 0) {
                            container.playbackController.seekTo((duration * fraction).toLong())
                        }
                    },
                )
            }
        }
    }
}

// ============================================================================
// 第 0 页：控制页
// ============================================================================

@Composable
private fun ControlPage(
    state: PlayerUiState,
    positionState: State<Long>,
    currentPage: Int,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val container = LocalAppContainer.current
    val song = state.song
    val durationMs = state.durationMs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = WatchMetrics.gutter),
    ) {
        PlayerHeader(
            title = song?.title ?: "未在播放",
            artist = song?.artist.orEmpty(),
            currentPage = currentPage,
            onBack = onBack,
        )

        // ===== 封面：吃掉所有剩余高度（约 105dp @ 240×284）=====
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val side = min(maxWidth, maxHeight)
            Artwork(
                url = song?.coverUrl,
                size = side,
                corner = 12.dp,
            )
        }

        // ===== 进度 =====
        PlayerProgress(
            positionState = positionState,
            durationMs = durationMs,
            showTime = true,
            onSeekFraction = { fraction ->
                if (durationMs > 0) {
                    container.playbackController.seekTo((durationMs * fraction).toLong())
                }
            },
        )

        // ===== 传输键 =====
        Row(
            modifier = Modifier.fillMaxWidth().height(PlayerLayout.middleButtonSize),
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

        // ===== 次要按钮（两行）=====
        // 第一行：播放模式 / 静音 / 队列
        // 第二行：歌手 / 专辑 / 评论 —— 从当前曲目跳转，缺对应 id 时置灰
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlayerLayout.secondaryButtonSize),
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
                size = PlayerLayout.secondaryButtonSize,
                iconSize = WatchMetrics.icon,
                tint = if (state.mode == PlayMode.SEQUENCE) TextSecondary else AccentBlue,
            )
            CircleIconButton(
                icon = painterResource(R.drawable.ic_volume_up),
                contentDescription = if (state.isMuted) "取消静音" else "静音",
                onClick = { container.playbackController.toggleMute() },
                size = PlayerLayout.secondaryButtonSize,
                iconSize = WatchMetrics.icon,
                tint = if (state.isMuted) TextDisabled else TextSecondary,
            )
            CircleIconButton(
                icon = painterResource(R.drawable.ic_queue_music),
                contentDescription = "播放队列",
                onClick = onOpenQueue,
                size = PlayerLayout.secondaryButtonSize,
                iconSize = WatchMetrics.icon,
                tint = TextSecondary,
            )
        }

        Spacer(Modifier.height(WatchMetrics.gutterSmall))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlayerLayout.secondaryButtonSize),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = painterResource(R.drawable.ic_person),
                contentDescription = "歌手",
                onClick = { song?.artistId?.let { onOpenArtist(it, song.artist) } },
                size = PlayerLayout.secondaryButtonSize,
                iconSize = WatchMetrics.icon,
                tint = TextSecondary,
                enabled = song?.artistId != null,
            )
            CircleIconButton(
                icon = painterResource(R.drawable.ic_album),
                contentDescription = "专辑",
                onClick = {
                    song?.albumId?.let { onOpenAlbum(it, song.albumName ?: song.title) }
                },
                size = PlayerLayout.secondaryButtonSize,
                iconSize = WatchMetrics.icon,
                tint = TextSecondary,
                enabled = song?.albumId != null,
            )
            CircleIconButton(
                icon = painterResource(R.drawable.ic_comment),
                contentDescription = "评论",
                onClick = { song?.albumAudioId?.let { onOpenComments(it, song.title) } },
                size = PlayerLayout.secondaryButtonSize,
                iconSize = WatchMetrics.icon,
                tint = TextSecondary,
                enabled = song?.albumAudioId != null,
            )
        }
    }
}

// ============================================================================
// 第 1 页：歌词页
// ============================================================================

@Composable
private fun LyricsPage(
    lyrics: LyricsUiData,
    positionState: State<Long>,
    songTitle: String?,
    songArtist: String?,
    durationMs: Long,
    currentPage: Int,
    onBack: () -> Unit,
    onSeekFraction: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = WatchMetrics.gutter),
    ) {
        PlayerHeader(
            title = songTitle ?: "未在播放",
            artist = songArtist.orEmpty(),
            currentPage = currentPage,
            onBack = onBack,
        )

        // 整屏歌词：240×284 上可用约 226dp，能显示 10 行以上（改造前只有 3 行）
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LyricsView(
                lines = lyrics.lines,
                loading = lyrics.loading,
                position = positionState,
            )
        }

        // 底部细进度条：在歌词页也能看到进度并点击 seek
        PlayerProgress(
            positionState = positionState,
            durationMs = durationMs,
            showTime = false,
            onSeekFraction = onSeekFraction,
        )
    }
}

/**
 * 进度条 + （可选）时间行。
 *
 * **单独抽出来的目的是把 `positionState.value` 的读取收在这一层** —— 播放位置每 500ms 更新一次，
 * 若在 `ControlPage` / `LyricsPage` 的顶层读取，整页（含封面、传输键、整份歌词）都会跟着重组。
 */
@Composable
private fun PlayerProgress(
    positionState: State<Long>,
    durationMs: Long,
    showTime: Boolean,
    onSeekFraction: (Float) -> Unit,
) {
    WatchProgressBar(
        progress =
            if (durationMs > 0) {
                positionState.value.toFloat() / durationMs.toFloat()
            } else {
                0f
            },
        onSeek = onSeekFraction,
        touchHeight = PlayerLayout.progressTouchHeight,
    )
    if (showTime) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(positionState.value),
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

// ============================================================================
// 共用：头部（返回 + 歌名/歌手 + 页码点）
// ============================================================================

/**
 * 两页共用的头部。右侧的页码点提示「可以左右翻页」——
 * 官方 `PlayerLibraryPagerScreen` 没有做指示，但在 240px 小屏上滑动手势不易被发现，
 * 加两个点成本很低。
 */
@Composable
private fun PlayerHeader(
    title: String,
    artist: String,
    currentPage: Int,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(PlayerLayout.headerHeight),
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
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(WatchMetrics.gutterSmall))
        PageDots(count = 2, current = currentPage)
    }
}

/** 极简页码点：当前页实心强调色，其余为描边灰。 */
@Composable
private fun PageDots(count: Int, current: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(PlayerLayout.dotSize)
                    .clip(CircleShape)
                    .background(if (index == current) AccentBlue else OutlineDim),
            )
        }
    }
}

// ============================================================================
// 歌词列表
// ============================================================================

@Composable
private fun LyricsView(
    lines: List<LyricLine>,
    loading: Boolean,
    position: State<Long>,
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
        // 整屏后行距可以放回 6dp（改造前为挤出 3 行曾收到 4dp）
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
