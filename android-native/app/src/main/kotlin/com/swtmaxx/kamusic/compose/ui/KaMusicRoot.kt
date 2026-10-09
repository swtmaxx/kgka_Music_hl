package com.swtmaxx.kamusic.compose.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swtmaxx.kamusic.compose.ui.screen.LoginScreen
import com.swtmaxx.kamusic.compose.ui.screen.PlayerScreen
import com.swtmaxx.kamusic.compose.ui.screen.PlaylistDetailScreen
import com.swtmaxx.kamusic.compose.ui.screen.QueueScreen
import com.swtmaxx.kamusic.compose.ui.screen.RankDetailScreen
import com.swtmaxx.kamusic.compose.ui.shell.WatchShell

/**
 * 应用根：登录门 + 导航。
 *
 * 登录态变化时自动在登录页与主界面之间切换（会话由 SessionStore 持久化）。
 */
@Composable
fun KaMusicRoot() {
    val container = LocalAppContainer.current
    val ready by container.sessionStore.ready.collectAsStateWithLifecycle()
    val session by container.sessionStore.sessionFlow.collectAsStateWithLifecycle()

    if (!ready) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    if (!session.isLoggedIn) {
        LoginScreen()
        return
    }

    MainNavHost()
}

@Composable
private fun MainNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            WatchShell(
                onOpenPlayer = { navController.navigate("player") },
                onOpenPlaylist = { id, title ->
                    navController.navigate("playlist/$id?title=${Uri.encode(title)}")
                },
                onOpenRank = { id, cid, title ->
                    navController.navigate("rank/$id?cid=$cid&title=${Uri.encode(title)}")
                },
            )
        }

        composable("player") {
            PlayerScreen(
                onBack = { navController.popBackStack() },
                onOpenQueue = { navController.navigate("queue") },
            )
        }

        composable("queue") {
            QueueScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "playlist/{id}?title={title}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val title = entry.arguments?.getString("title").orEmpty()
            PlaylistDetailScreen(
                playlistId = id,
                fallbackTitle = title.ifEmpty { "歌单" },
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate("player") },
            )
        }
        composable(
            route = "rank/{id}?cid={cid}&title={title}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("cid") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val cid = entry.arguments?.getString("cid").orEmpty()
            val title = entry.arguments?.getString("title").orEmpty()
            RankDetailScreen(
                rankId = id,
                rankCid = cid,
                fallbackTitle = title.ifEmpty { "榜单" },
                onBack = { navController.popBackStack() },
                onOpenPlayer = { navController.navigate("player") },
            )
        }
    }
}
