package com.swtmaxx.kamusic.native.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swtmaxx.kamusic.native.R
import com.swtmaxx.kamusic.native.ui.LocalAppContainer
import com.swtmaxx.kamusic.native.ui.component.Artwork
import com.swtmaxx.kamusic.native.ui.component.CircleIconButton
import com.swtmaxx.kamusic.native.ui.component.EmptyBox
import com.swtmaxx.kamusic.native.ui.component.WatchTopBar
import com.swtmaxx.kamusic.native.ui.theme.AccentBlue
import com.swtmaxx.kamusic.native.ui.theme.TextPrimary
import com.swtmaxx.kamusic.native.ui.theme.TextSecondary
import com.swtmaxx.kamusic.native.ui.theme.WatchMetrics

@Composable
fun QueueScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val queue by container.playbackController.queue.collectAsStateWithLifecycle()
    val state by container.playbackController.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        WatchTopBar(title = "播放队列（${queue.size}）", onBack = onBack)

        if (queue.isEmpty()) {
            EmptyBox("队列是空的")
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(queue, key = { index, song -> "${song.hash}_$index" }) { index, song ->
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
