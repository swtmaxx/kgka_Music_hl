package com.swtmaxx.kamusic.compose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography

// ===== 颜色：纯黑 + 单一强调色（对齐参考图风格） =====

/** 唯一强调色。 */
val AccentBlue = Color(0xFF4A9EFF)

val PureBlack = Color(0xFF000000)
val SurfaceDim = Color(0xFF0E0E0E)
val SurfaceRaised = Color(0xFF161616)
val OutlineDim = Color(0xFF2A2A2A)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9AA0A6)
val TextDisabled = Color(0xFF5F6368)
val ErrorRed = Color(0xFFFF6B6B)

// ===== 字号：手表只有两级主次，最多四级 =====
//
// 槽位名与手机版 Material 3 一致（Wear Compose 的 Typography 沿用 M3 命名），
// 因此调用点 `MaterialTheme.typography.xxx` 无需改动，只换了库。
private val KaTypography = Typography(
    // 歌名 / 页面主标题
    titleMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    // 正文
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 17.sp),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
    // 歌手 / 副标题 / 标签
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 13.sp),
)

/**
 * 手表尺寸令牌。
 *
 * 目标机 S100：240×284 逻辑像素、DPR 1.0。
 * 所有触控目标不小于 [minTouch]（Wear OS 规范建议 48dp）。
 */
object WatchMetrics {
    val minTouch = 48.dp
    val appBar = 40.dp
    val bottomBar = 34.dp
    val gutter = 8.dp
    val gutterSmall = 4.dp

    val icon = 18.dp
    val iconLarge = 22.dp
    val iconTransport = 30.dp

    val listRow = 44.dp
    val coverSmall = 36.dp
    val coverMedium = 56.dp
    val coverLarge = 96.dp

    val progressTrack = 3.dp
    val progressThumb = 12.dp

    val qrCode = 132.dp
    val inputHeight = 40.dp
}

/**
 * 主题。
 *
 * 由手机版 `androidx.compose.material3` 切换到 Compose for Wear OS：
 * - Wear 的 Material 3 只有深色方案（手表设计如此），**没有** `darkColorScheme()` 构造器，
 *   因此改为在 composable 内取 Wear 默认 scheme 再 `copy()` 覆盖品牌色。
 * - 覆盖字段只保留项目真正会用到的：`primary` 决定 `CircularProgressIndicator` 与
 *   对话框按钮的强调色，`onSurface*` 决定 `Text` 的默认前景色，其余为一致性补齐。
 * - 手机版曾用 `darkColorScheme(surfaceVariant = …, outlineVariant = …)`，但全项目
 *   **从未读取过 `colorScheme` 的任何字段**，故这些字段直接不再设置。
 */
@Composable
fun KaMusicTheme(content: @Composable () -> Unit) {
    // 手表固定纯黑，不跟随系统深浅色（避免亮色主题在 OLED 表上刺眼且更耗电）。
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()

    val scheme = MaterialTheme.colorScheme.copy(
        primary = AccentBlue,
        onPrimary = PureBlack,
        primaryContainer = SurfaceRaised,
        onPrimaryContainer = TextPrimary,
        secondary = AccentBlue,
        onSecondary = PureBlack,
        background = PureBlack,
        onBackground = TextPrimary,
        surfaceContainer = SurfaceDim,
        surfaceContainerHigh = SurfaceRaised,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
        outline = OutlineDim,
        error = ErrorRed,
        onError = PureBlack,
    )

    MaterialTheme(
        colorScheme = scheme,
        typography = KaTypography,
        content = content,
    )
}
