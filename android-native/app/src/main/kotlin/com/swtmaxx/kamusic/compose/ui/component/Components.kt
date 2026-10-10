package com.swtmaxx.kamusic.compose.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swtmaxx.kamusic.compose.R
import com.swtmaxx.kamusic.compose.data.model.ClimaxRange
import com.swtmaxx.kamusic.compose.data.model.Song
import com.swtmaxx.kamusic.compose.ui.theme.AccentBlue
import com.swtmaxx.kamusic.compose.ui.theme.OutlineDim
import com.swtmaxx.kamusic.compose.ui.theme.SurfaceRaised
import com.swtmaxx.kamusic.compose.ui.theme.TextDisabled
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics

// ============================================================================
// 按钮
// ============================================================================

/**
 * 圆形图标按钮。最小 48dp 触控目标（手表上约屏宽的 1/5，5 键并排仍放得下）。
 */
@Composable
fun CircleIconButton(
    icon: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = WatchMetrics.minTouch,
    iconSize: Dp = WatchMetrics.icon,
    filled: Boolean = false,
    tint: Color = TextPrimary,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) AccentBlue else Color.Transparent)
            .then(
                if (filled) Modifier else Modifier.border(1.dp, OutlineDim, CircleShape),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = if (enabled) (if (filled) Color.Black else tint) else TextDisabled,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** 播放/暂停主按钮：实心强调色，比次要按钮更大。 */
@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    CircleIconButton(
        icon = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
        contentDescription = if (isPlaying) "暂停" else "播放",
        onClick = onClick,
        modifier = modifier,
        size = size,
        iconSize = size * 0.5f,
        filled = true,
    )
}

// ============================================================================
// 进度条
// ============================================================================

/**
 * 细进度条（3dp）。可点击 seek。
 *
 * 刻意不用 Slider：手表屏太窄，Slider 的 thumb 与 padding 会吃掉大量空间，
 * 且 Material Slider 的默认动画在低端设备上有额外开销。
 * 交互只做「点击定位」（在 240px 宽屏上拖动比点击更难精确）。
 *
 * [touchHeight] 是**触控区**高度（视觉轨道始终只有 [WatchMetrics.progressTrack]）。
 * 默认 48dp（无障碍建议值）；播放页为了给歌词让出屏高会传 24dp ——
 * 这是「可点区域」与「可见内容」分离的做法：视觉不变，但不再白占 17% 屏高。
 *
 * [climax] + [durationMs]：在轨道上叠出一段高潮区间高亮。用 `Row` + `weight`
 * 定位而不是像素偏移，避免依赖已测宽度。
 */
@Composable
fun WatchProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null,
    touchHeight: Dp = WatchMetrics.minTouch,
    durationMs: Long = 0L,
    climax: ClimaxRange? = null,
) {
    val fraction = progress.coerceIn(0f, 1f)
    var trackWidthPx by remember { mutableIntStateOf(1) }

    // 把高潮区间换算成 0..1 的比例，无效或超出时置 null
    val climaxFraction: Pair<Float, Float>? = remember(climax, durationMs) {
        if (climax == null || durationMs <= 0L || !climax.isValid) {
            null
        } else {
            val start = (climax.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val end = (climax.endMs.toFloat() / durationMs).coerceIn(0f, 1f)
            if (end > start) start to end else null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(touchHeight)
            .onSizeChanged { trackWidthPx = it.width.coerceAtLeast(1) }
            .then(
                if (onSeek != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            onSeek((offset.x / trackWidthPx).coerceIn(0f, 1f))
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WatchMetrics.progressTrack)
                .clip(CircleShape)
                .background(OutlineDim),
        ) {
            // 已播放段
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(AccentBlue),
            )
            // 高潮区间：叠在已播放段之上，用浅色保证在蓝底上也能看见
            climaxFraction?.let { (start, end) ->
                Row(modifier = Modifier.fillMaxSize()) {
                    if (start > 0f) Spacer(Modifier.weight(start))
                    Box(
                        modifier = Modifier
                            .weight(end - start)
                            .fillMaxHeight()
                            .background(TextPrimary.copy(alpha = 0.45f)),
                    )
                    if (end < 1f) Spacer(Modifier.weight(1f - end))
                }
            }
        }
    }
}

// ============================================================================
// 列表项
// ============================================================================

/**
 * 按目标显示尺寸缩略解码。
 *
 * ⚠️ 酷狗封面常是 480~1000px，而手表屏只有 240px 宽。
 * 不告诉 Coil 目标尺寸，它会**按原图全尺寸解码** —— 在 1GB RAM 上白白浪费 4~16 倍内存。
 * 这里把目标像素宽高交给 Coil（`size()`），让它直接解码到需要的尺寸。
 */
@Composable
private fun rememberSizedImageRequest(url: String?, widthPx: Int, heightPx: Int): ImageRequest? {
    val context = LocalContext.current
    if (url.isNullOrEmpty()) return null
    return remember(url, widthPx, heightPx) {
        ImageRequest.Builder(context)
            .data(url)
            .size(widthPx.coerceAtLeast(MIN_DECODE_PX), heightPx.coerceAtLeast(MIN_DECODE_PX))
            .crossfade(false)
            .build()
    }
}

/** 解码下限，避免 1dp 的占位图也被要求解码成 1px（模糊）。 */
private const val MIN_DECODE_PX = 32

/** 屏幕宽度量级的上限，用于尺寸未知的铺满型封面。 */
private const val MAX_DECODE_PX = 480

@Composable
fun Artwork(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 6.dp,
) {
    val density = LocalDensity.current
    val sidePx = with(density) { size.roundToPx() }
    val request = rememberSizedImageRequest(url, sidePx, sidePx)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(SurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (request == null) {
            Icon(
                painter = painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(size * 0.45f),
            )
        } else {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 铺满可用空间的封面（用于网格单元）。 */
@Composable
fun ArtworkFill(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp,
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(SurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        // 网格单元尺寸由外部（fillMaxWidth + aspectRatio）决定，这里用约束实测；
        // 无界时回落到 240（= 屏宽量级），总比全尺寸解码好。
        val wPx = if (maxWidth.isFinite) with(density) { maxWidth.roundToPx() } else 240
        val hPx = if (maxHeight.isFinite) with(density) { maxHeight.roundToPx() } else 240
        val request = rememberSizedImageRequest(url, wPx, hPx)

        if (request == null) {
            Icon(
                painter = painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(24.dp),
            )
        } else {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun TrackRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(WatchMetrics.listRow)
            .clickable(onClick = onClick)
            .padding(horizontal = WatchMetrics.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = song.coverUrl, size = WatchMetrics.coverSmall)
        Spacer(Modifier.width(WatchMetrics.gutter))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) AccentBlue else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(WatchMetrics.gutterSmall))
        Text(
            text = song.durationText,
            style = MaterialTheme.typography.labelSmall,
            color = TextDisabled,
        )
    }
}

// ============================================================================
// 状态占位
// ============================================================================

@Composable
fun LoadingBox(message: String = "加载中…", modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                // Wear 版没有 `color` 参数（改用 colors），这里靠 colorScheme.primary 着色，
                // 主题里已把 primary 设为 AccentBlue，视觉效果与改造前一致。
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(WatchMetrics.gutter))
            Text(message, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        }
    }
}

@Composable
fun ErrorBox(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = WatchMetrics.gutter * 2),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_wifi_off),
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(WatchMetrics.iconLarge),
            )
            Spacer(Modifier.height(WatchMetrics.gutter))
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(WatchMetrics.gutter))
                CircleIconButton(
                    icon = painterResource(R.drawable.ic_refresh),
                    contentDescription = "重试",
                    onClick = onRetry,
                    size = 40.dp,
                    iconSize = WatchMetrics.icon,
                )
            }
        }
    }
}

@Composable
fun EmptyBox(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            color = TextDisabled,
        )
    }
}

// ============================================================================
// 输入框
// ============================================================================

/**
 * 手表用单行输入框。
 *
 * 不用 Material 的 OutlinedTextField：其默认最小高度（56dp）与内置 padding
 * 在 240×284 屏上太占空间，且会引入额外的状态层与动画。
 */
@Composable
fun WatchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
        cursorBrush = SolidColor(AccentBlue),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = { onImeAction?.invoke() },
            onSearch = { onImeAction?.invoke() },
            onGo = { onImeAction?.invoke() },
        ),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WatchMetrics.inputHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceRaised)
                    .border(1.dp, OutlineDim, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDisabled,
                        maxLines = 1,
                    )
                }
                innerTextField()
            }
        },
    )
}

// ============================================================================
// 标签页
// ============================================================================

@Composable
fun PillTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) AccentBlue else Color.Transparent)
            .border(1.dp, if (selected) AccentBlue else OutlineDim, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.Black else TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = TextPrimary,
        modifier = modifier.padding(
            start = WatchMetrics.gutter,
            end = WatchMetrics.gutter,
            top = WatchMetrics.gutter,
            bottom = WatchMetrics.gutterSmall,
        ),
    )
}

/** 一行圆形入口（对齐参考图的「歌单/歌手/专辑/本地/播放列表」样式）。 */
@Composable
fun CircleEntryRow(
    items: List<Triple<Painter, String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WatchMetrics.gutterSmall),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        items.forEach { (icon, label, onClick) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onClick),
            ) {
                Box(
                    modifier = Modifier
                        .size(WatchMetrics.minTouch)
                        .clip(CircleShape)
                        .background(SurfaceRaised)
                        .border(1.dp, OutlineDim, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = label,
                        tint = AccentBlue,
                        modifier = Modifier.size(WatchMetrics.iconLarge),
                    )
                }
                Spacer(Modifier.height(WatchMetrics.gutterSmall))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
