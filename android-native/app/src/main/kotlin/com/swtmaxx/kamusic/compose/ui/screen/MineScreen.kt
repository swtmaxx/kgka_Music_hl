package com.swtmaxx.kamusic.compose.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.swtmaxx.kamusic.compose.BuildConfig
import com.swtmaxx.kamusic.compose.R
import com.swtmaxx.kamusic.compose.core.SessionStore
import com.swtmaxx.kamusic.compose.data.model.PlaylistSummary
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.Artwork
import com.swtmaxx.kamusic.compose.ui.component.CircleIconButton
import com.swtmaxx.kamusic.compose.ui.component.ErrorBox
import com.swtmaxx.kamusic.compose.ui.component.LoadingBox
import com.swtmaxx.kamusic.compose.ui.component.PillTab
import com.swtmaxx.kamusic.compose.ui.component.WatchTextField
import com.swtmaxx.kamusic.compose.ui.theme.AccentBlue
import com.swtmaxx.kamusic.compose.ui.theme.SurfaceRaised
import com.swtmaxx.kamusic.compose.ui.theme.TextDisabled
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import com.swtmaxx.kamusic.compose.ui.vm.MineViewModel
import com.swtmaxx.kamusic.compose.ui.vm.UiState

@Composable
fun MineScreen(
    onOpenPlayer: () -> Unit,
    onOpenPlaylist: (id: String, title: String) -> Unit = { _, _ -> },
    onOpenCloud: () -> Unit = {},
    onOpenVip: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenDiscover: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val viewModel: MineViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                MineViewModel(
                    container.musicRepository,
                    container.authRepository,
                    container.sessionStore,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val apiBaseUrl by viewModel.apiBaseUrl.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()

    var editingUrl by remember { mutableStateOf(false) }
    var urlDraft by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState()),
    ) {
        when (val current = state) {
            is UiState.Loading -> Box(Modifier.height(120.dp)) { LoadingBox() }

            is UiState.Error -> Box(Modifier.height(160.dp)) {
                ErrorBox(current.message) { viewModel.load(forceRefresh = true) }
            }

            is UiState.Ready -> {
                val data = current.data

                // ===== 用户信息 =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WatchMetrics.gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SurfaceRaised),
                        contentAlignment = Alignment.Center,
                    ) {
                        val avatar = data.profile?.avatarUrl
                        if (avatar.isNullOrEmpty()) {
                            Icon(
                                painter = painterResource(R.drawable.ic_person),
                                contentDescription = null,
                                tint = TextDisabled,
                                modifier = Modifier.size(WatchMetrics.iconLarge),
                            )
                        } else {
                            AsyncImage(
                                model = avatar,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(Modifier.width(WatchMetrics.gutter))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = data.profile?.nickname ?: "KA Music 用户",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "共 ${data.createdPlaylists.size + data.collectedPlaylists.size} 个歌单",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }

                PlaylistSection(
                    title = "我创建的歌单",
                    playlists = data.createdPlaylists,
                    onOpenPlaylist = onOpenPlaylist,
                )
                PlaylistSection(
                    title = "我收藏的歌单",
                    playlists = data.collectedPlaylists,
                    onOpenPlaylist = onOpenPlaylist,
                )

                // ===== 更多（云盘 / VIP / 播放历史）=====
                Text(
                    text = "更多",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    modifier = Modifier.padding(
                        start = WatchMetrics.gutter,
                        end = WatchMetrics.gutter,
                        top = WatchMetrics.gutter * 2,
                        bottom = WatchMetrics.gutterSmall,
                    ),
                )
                SettingRow(label = "云盘", value = "", onClick = onOpenCloud)
                SettingRow(label = "VIP", value = "", onClick = onOpenVip)
                SettingRow(label = "播放历史", value = "", onClick = onOpenHistory)
                SettingRow(label = "刷歌", value = "", onClick = onOpenDiscover)

                // ===== 设置 =====
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    modifier = Modifier.padding(
                        start = WatchMetrics.gutter,
                        end = WatchMetrics.gutter,
                        top = WatchMetrics.gutter * 2,
                        bottom = WatchMetrics.gutterSmall,
                    ),
                )

                SettingRow(
                    label = "API 服务器",
                    value = apiBaseUrl,
                    onClick = {
                        urlDraft = container.sessionStore.customApiBaseUrl.orEmpty()
                        editingUrl = true
                    },
                )

                Column(modifier = Modifier.padding(horizontal = WatchMetrics.gutter, vertical = WatchMetrics.gutterSmall)) {
                    Text(
                        text = "音质",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(WatchMetrics.gutterSmall))
                    QualityChips(
                        quality = quality,
                        onSelect = { viewModel.updateQuality(it) },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WatchMetrics.gutter)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceRaised)
                        .clickable { viewModel.logout { } }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_logout),
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(WatchMetrics.icon),
                    )
                    Spacer(Modifier.width(WatchMetrics.gutterSmall))
                    Text(
                        text = "退出登录",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }

                Text(
                    text = "KA Music 原生版 ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDisabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = WatchMetrics.gutter * 2),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    if (editingUrl) {
        AlertDialog(
            // Wear 版 AlertDialog 比手机版多一个 visible 参数（它自带显隐动画）。
            visible = true,
            onDismissRequest = { editingUrl = false },
            title = { Text("API 服务器地址", style = MaterialTheme.typography.titleSmall) },
            text = {
                Column {
                    Text(
                        text = "留空则使用默认地址：\n${BuildConfig.DEFAULT_API_BASE_URL}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(WatchMetrics.gutter))
                    WatchTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        placeholder = "https://…",
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateApiBaseUrl(urlDraft.ifBlank { null })
                    editingUrl = false
                }) { Text("保存", color = AccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { editingUrl = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QualityChips(quality: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(WatchMetrics.gutterSmall)) {
        SessionStore.QUALITY_OPTIONS.forEach { (value, label) ->
            PillTab(
                text = label,
                selected = quality == value,
                onClick = { onSelect(value) },
            )
        }
    }
}

@Composable
private fun PlaylistSection(
    title: String,
    playlists: List<PlaylistSummary>,
    onOpenPlaylist: (id: String, title: String) -> Unit,
) {
    if (playlists.isEmpty()) return
    Text(
        text = "$title（${playlists.size}）",
        style = MaterialTheme.typography.titleSmall,
        color = TextPrimary,
        modifier = Modifier.padding(
            start = WatchMetrics.gutter,
            end = WatchMetrics.gutter,
            top = WatchMetrics.gutter,
            bottom = WatchMetrics.gutterSmall,
        ),
    )
    playlists.forEach { playlist ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(WatchMetrics.listRow)
                .clickable { onOpenPlaylist(playlist.id, playlist.title) }
                .padding(horizontal = WatchMetrics.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(url = playlist.coverUrl, size = WatchMetrics.coverSmall)
            Spacer(Modifier.width(WatchMetrics.gutter))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = playlist.songCountText.ifEmpty { playlist.creatorName.orEmpty() }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = WatchMetrics.gutter, vertical = WatchMetrics.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = TextPrimary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(130.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        CircleIconButton(
            icon = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            onClick = onClick,
            size = 32.dp,
            iconSize = WatchMetrics.icon,
        )
    }
}
