package com.swtmaxx.kamusic.compose.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

private val KaDarkScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = PureBlack,
    secondary = AccentBlue,
    onSecondary = PureBlack,
    background = PureBlack,
    onBackground = TextPrimary,
    surface = PureBlack,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDim,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = SurfaceDim,
    surfaceContainerHigh = SurfaceRaised,
    outline = OutlineDim,
    outlineVariant = OutlineDim,
    error = ErrorRed,
    onError = PureBlack,
)

// ===== 字号：手表只有两级主次，最多四级 =====

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

@Composable
fun KaMusicTheme(content: @Composable () -> Unit) {
    // 手表固定纯黑，不跟随系统深浅色（避免亮色主题在 OLED 表上刺眼且更耗电）。
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = KaDarkScheme,
        typography = KaTypography,
        content = content,
    )
}
