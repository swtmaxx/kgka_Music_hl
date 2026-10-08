package com.swtmaxx.kamusic.compose.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swtmaxx.kamusic.compose.R
import com.swtmaxx.kamusic.compose.playback.PlayerUiState
import com.swtmaxx.kamusic.compose.ui.theme.AccentBlue
import com.swtmaxx.kamusic.compose.ui.theme.OutlineDim
import com.swtmaxx.kamusic.compose.ui.theme.SurfaceRaised
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics

/**
 * 悬浮迷你播放器。
 *
 * 只在有当前曲目时显示；点击进入全屏播放器。
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onTap: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.song ?: return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(WatchMetrics.listRow)
            .padding(horizontal = WatchMetrics.gutterSmall)
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceRaised)
            .clickable(onClick = onTap)
            .padding(horizontal = WatchMetrics.gutterSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = song.coverUrl, size = 30.dp, corner = 5.dp)
        Spacer(Modifier.width(WatchMetrics.gutter))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
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
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                ),
                contentDescription = if (state.isPlaying) "暂停" else "播放",
                tint = AccentBlue,
                modifier = Modifier.size(WatchMetrics.iconLarge),
            )
        }
    }
}

/** 底部导航栏（3 个主页面）。 */
@Composable
fun WatchBottomBar(
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val icons = listOf(
        R.drawable.ic_home to "首页",
        R.drawable.ic_search to "搜索",
        R.drawable.ic_person to "我的",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(WatchMetrics.bottomBar)
            .background(androidx.compose.ui.graphics.Color.Black),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icons.forEachIndexed { index, (iconRes, label) ->
            val selected = index == currentIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = label,
                    tint = if (selected) AccentBlue else TextSecondary,
                    modifier = Modifier.size(WatchMetrics.iconLarge),
                )
            }
        }
    }
}
