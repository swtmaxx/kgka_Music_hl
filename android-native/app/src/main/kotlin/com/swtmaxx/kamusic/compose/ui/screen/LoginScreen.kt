package com.swtmaxx.kamusic.compose.ui.screen

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swtmaxx.kamusic.compose.R
import com.swtmaxx.kamusic.compose.ui.LocalAppContainer
import com.swtmaxx.kamusic.compose.ui.component.CircleIconButton
import com.swtmaxx.kamusic.compose.ui.component.PillTab
import com.swtmaxx.kamusic.compose.ui.component.WatchTextField
import com.swtmaxx.kamusic.compose.ui.theme.AccentBlue
import com.swtmaxx.kamusic.compose.ui.theme.ErrorRed
import com.swtmaxx.kamusic.compose.ui.theme.SurfaceRaised
import com.swtmaxx.kamusic.compose.ui.theme.TextDisabled
import com.swtmaxx.kamusic.compose.ui.theme.TextPrimary
import com.swtmaxx.kamusic.compose.ui.theme.TextSecondary
import com.swtmaxx.kamusic.compose.ui.theme.WatchMetrics
import com.swtmaxx.kamusic.compose.ui.vm.AuthViewModel

/**
 * 登录页：手机验证码 / 扫码两种方式。
 *
 * 登录成功后会话写入 SessionStore，根组件的登录门会自动切到主界面，
 * 本页无需做导航。
 */
@Composable
fun LoginScreen() {
    val container = LocalAppContainer.current
    val viewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AuthViewModel(container.authRepository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "KA Music",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = AccentBlue,
        )
        Spacer(Modifier.height(WatchMetrics.gutter))

        // 方式切换
        Row(horizontalArrangement = Arrangement.spacedBy(WatchMetrics.gutter)) {
            PillTab(
                text = "验证码",
                selected = state.tab == 0,
                onClick = { viewModel.onTabChange(0) },
            )
            PillTab(
                text = "扫码",
                selected = state.tab == 1,
                onClick = { viewModel.onTabChange(1) },
            )
        }
        Spacer(Modifier.height(WatchMetrics.gutter))

        if (state.tab == 0) {
            PhoneLoginSection(state, viewModel)
        } else {
            QrLoginSection(state, viewModel)
        }

        state.message?.let { message ->
            Spacer(Modifier.height(WatchMetrics.gutter))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = if (state.messageIsError) ErrorRed else TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (state.accounts.isNotEmpty()) {
            Spacer(Modifier.height(WatchMetrics.gutter))
            Text(
                text = "请选择账号",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(WatchMetrics.gutterSmall))
            state.accounts.forEach { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceRaised)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = account.nickname,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    CircleIconButton(
                        icon = painterResource(R.drawable.ic_check),
                        contentDescription = "选择该账号",
                        onClick = { viewModel.login(account.userId) },
                        size = 32.dp,
                        iconSize = WatchMetrics.icon,
                        tint = AccentBlue,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneLoginSection(
    state: com.swtmaxx.kamusic.compose.ui.vm.LoginUiState,
    viewModel: AuthViewModel,
) {
    WatchTextField(
        value = state.mobile,
        onValueChange = viewModel::onMobileChange,
        placeholder = "手机号",
        keyboardType = KeyboardType.Phone,
    )
    Spacer(Modifier.height(WatchMetrics.gutter))

    Row(verticalAlignment = Alignment.CenterVertically) {
        WatchTextField(
            value = state.code,
            onValueChange = viewModel::onCodeChange,
            placeholder = "验证码",
            keyboardType = KeyboardType.NumberPassword,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(WatchMetrics.gutter))
        Box(
            modifier = Modifier
                .height(WatchMetrics.inputHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(if (state.canSendCode) AccentBlue else SurfaceRaised)
                .clickable(enabled = state.canSendCode, onClick = viewModel::sendCode)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    state.countdown > 0 -> "${state.countdown}s"
                    state.busy -> "发送中"
                    else -> "获取"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (state.canSendCode) Color.Black else TextDisabled,
            )
        }
    }

    Spacer(Modifier.height(WatchMetrics.gutter))
    PrimaryActionButton(
        text = "登录",
        enabled = state.canLogin,
        onClick = { viewModel.login() },
    )
}

@Composable
private fun QrLoginSection(
    state: com.swtmaxx.kamusic.compose.ui.vm.LoginUiState,
    viewModel: AuthViewModel,
) {
    val bitmap = remember(state.qrImageBase64) {
        val raw = state.qrImageBase64 ?: return@remember null
        val payload = raw.substringAfter("base64,", raw)
        runCatching {
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .size(WatchMetrics.qrCode)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.qrLoading -> Text("获取中…", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = "登录二维码",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
            else -> Text("二维码不可用", style = MaterialTheme.typography.labelMedium, color = TextDisabled)
        }
    }

    Spacer(Modifier.height(WatchMetrics.gutterSmall))
    Text(
        text = when (state.qrStatus) {
            1 -> "已扫描，请在手机上确认"
            2 -> "登录成功"
            4 -> "二维码已过期"
            else -> "请用酷狗 App 扫码"
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (state.qrStatus == 4) ErrorRed else TextSecondary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(WatchMetrics.gutterSmall))
    CircleIconButton(
        icon = painterResource(R.drawable.ic_refresh),
        contentDescription = "刷新二维码",
        onClick = viewModel::refreshQr,
        size = 36.dp,
        iconSize = WatchMetrics.icon,
    )
}

@Composable
private fun PrimaryActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WatchMetrics.minTouch - 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) AccentBlue else SurfaceRaised)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) Color.Black else TextDisabled,
        )
    }
}
