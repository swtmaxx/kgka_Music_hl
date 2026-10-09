package com.swtmaxx.kamusic.compose.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.MiniPlayer
import com.swtmaxx.kamusic.compose.ui.component.WatchBottomBar
import com.swtmaxx.kamusic.compose.ui.screen.HomeScreen
import com.swtmaxx.kamusic.compose.ui.screen.MineScreen
import com.swtmaxx.kamusic.compose.ui.screen.SearchScreen
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import kotlinx.coroutines.launch

/**
 * 主壳：3 个主页面横向翻页 + 底栏 + 悬浮迷你播放器。
 *
 * 用 HorizontalPager 而不是 Navigation：手表上左右滑动切页是主要导航方式，
 * Pager 的跟手体验比导航转场更自然，且只保留 3 个常驻页面。
 */
@Composable
fun WatchShell(
    onOpenPlayer: () -> Unit,
    onOpenPlaylist: (id: String, title: String) -> Unit,
    onOpenRank: (id: String, cid: String, title: String) -> Unit,
) {
    val container = LocalAppContainer.current
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val playerState by container.playbackController.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                // 关闭越界回弹动画：低端设备上可省下不必要的合成帧。
                beyondViewportPageCount = 0,
            ) { page ->
                when (page) {
                    0 -> HomeScreen(
                        onOpenPlaylist = onOpenPlaylist,
                        onOpenRank = { rank -> onOpenRank(rank.id, rank.cid, rank.name) },
                        onOpenPlayer = onOpenPlayer,
                    )

                    1 -> SearchScreen(onOpenPlayer = onOpenPlayer)
                    else -> MineScreen(
                        onOpenPlayer = onOpenPlayer,
                        onOpenPlaylist = onOpenPlaylist,
                    )
                }
            }

            // 迷你播放器浮在底栏之上；没有当前曲目时 MiniPlayer 自身返回空。
            if (playerState.song != null) {
                MiniPlayer(
                    state = playerState,
                    onTap = onOpenPlayer,
                    onTogglePlay = { container.playbackController.togglePlayPause() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = WatchMetrics.gutterSmall, vertical = 2.dp),
                )
            }

            WatchBottomBar(
                currentIndex = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )
        }
    }
}
