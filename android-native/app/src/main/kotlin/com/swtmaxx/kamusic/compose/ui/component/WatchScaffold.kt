package com.swtmaxx.kamusic.compose.ui.component

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.ScalingParams
import androidx.wear.compose.foundation.rotary.RotaryScrollableBehavior
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScreenScaffoldDefaults
import androidx.wear.compose.material3.TimeText

// ============================================================================
// Compose for Wear OS 适配工具（方屏专用）
//
// 目标机 S100 是 240×284 的**方屏**（非 Wear OS 圆表），因此本文件不实现圆屏分支：
// 边缘缩放关闭、首项不自动居中、无横向内缩。若将来换圆表，需要在此处引入
// 屏幕形态判断（参考 md3Music/QQMusicWear 的 LocalIsRoundScreen 做法）。
// ============================================================================

/** 方屏首部距屏幕顶端的留白：库默认竖直留白为屏高的 10%（≈28dp），方屏首部会明显偏下。 */
private val WatchTopContentPadding: Dp = 12.dp

/**
 * 方屏的 `ScalingLazyColumn.autoCentering` 取值：关闭首项自动居中。
 *
 * 圆屏需要首项居中（弧面视口惯例），方屏应从顶部 contentPadding 起自上而下排布，
 * 页面首部才能贴回屏幕顶端。
 */
val WatchAutoCentering: AutoCenteringParams? = null

/**
 * 表冠滚动手感：`ScalingLazyColumn` 内置的 snap 行为。
 *
 * ⚠️ 必须通过 `ScalingLazyColumn` 的 `rotaryScrollableBehavior` 参数传入，**不要**再用
 * `Modifier.rotaryScrollable` 另挂一层——`ScalingLazyColumn` 内部已自带
 * `requestFocusOnHierarchyActive().rotaryScrollable()` 焦点协调机制，外挂第二层 focusTarget
 * 会与之抢焦点，表现为「有震动但页面不滚」或「页面能滚但无震动」。
 */
@Composable
fun watchRotary(state: ScalingLazyListState): RotaryScrollableBehavior =
    RotaryScrollableDefaults.snapBehavior(state)

/**
 * 方屏边缘缩放参数：`edgeScale = 1f` 表示**禁用**边缘缩放。
 *
 * 库默认 0.7f 会让靠近视口边缘的卡片变窄，视觉上不再贴边；方屏的贴边要求由物理黑边承担，
 * 因此全宽显示。
 */
@Composable
fun watchScalingParams(): ScalingParams =
    ScalingLazyColumnDefaults.scalingParams(edgeScale = 1f)

/**
 * 方屏专用的 `ScreenScaffold` 包装。
 *
 * 相对库默认做了三处方屏适配：
 * 1. `contentPadding` 去掉横向分量（库默认带屏宽 5.2% ≈ 13dp，方屏会形成双层空隙），
 *    并把顶部留白收敛到 [WatchTopContentPadding]；
 * 2. `scrollIndicator = null` —— 该 slot 内的 fullscreen Box 会插入 content 与列表之间，
 *    破坏 `ScalingLazyColumn` 内置的表冠焦点链；方屏不需要弧形指示条；
 * 3. `timeText` 固定为系统时间（Wear 惯例）。
 *
 * 仅用于「整页就是一个滚动列表」的页面。带固定头部（搜索框 / tab 行）的页面不要套它，
 * 否则 `TimeText` 会与固定头部叠加，在 284px 高的屏上白吃约 28dp。
 */
@Composable
fun WatchScreenScaffold(
    scrollState: ScalingLazyListState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val base = ScreenScaffoldDefaults.contentPadding
    val padding = PaddingValues(
        top = base.calculateTopPadding().coerceAtMost(WatchTopContentPadding),
        bottom = base.calculateBottomPadding(),
    )
    ScreenScaffold(
        scrollState = scrollState,
        modifier = modifier,
        timeText = { TimeText() },
        contentPadding = padding,
        scrollIndicator = null,
        content = content,
    )
}

/** 右滑返回的累计位移阈值（逻辑像素）。 */
private const val SwipeBackThresholdPx = 90f

/**
 * 右滑返回：二级页内容包裹层。
 *
 * 手指在屏幕任意位置向右拖动累计超过 [SwipeBackThresholdPx] 即触发 [onBack]
 * （与系统边缘返回手势同向，即「上一页在左侧」的标准导航模型）。
 *
 * **只识别水平手势**（`detectHorizontalDragGestures`），因此与页内 `ScalingLazyColumn`
 * 的纵向滚动、以及表冠滚动互不干扰。
 *
 * 触发时用 Compose 内置的 [LocalHapticFeedback] 给一次震动反馈——不引入额外依赖、
 * 不新建触觉模块，API 27 上走 `View.performHapticFeedback`。
 */
@Composable
fun Modifier.watchSwipeBack(
    onBack: () -> Unit,
    threshold: Float = SwipeBackThresholdPx,
): Modifier {
    val latestBack by rememberUpdatedState(onBack)
    val haptics = LocalHapticFeedback.current
    var accumulated by remember { mutableFloatStateOf(0f) }

    return this.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { accumulated = 0f },
            onDragEnd = { accumulated = 0f },
            onDragCancel = { accumulated = 0f },
        ) { _, dragAmount ->
            accumulated += dragAmount
            if (accumulated > threshold) {
                accumulated = 0f
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                latestBack()
            }
        }
    }
}
