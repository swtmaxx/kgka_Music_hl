package com.swtmaxx.kamusic.compose.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swtmaxx.kamusic.compose.data.api.MobileLoginAccount
import com.swtmaxx.kamusic.compose.data.api.PhoneLoginResult
import com.swtmaxx.kamusic.compose.data.repo.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    /** 0 = 手机验证码, 1 = 扫码 */
    val tab: Int = 0,
    val mobile: String = "",
    val code: String = "",
    val countdown: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val qrImageBase64: String? = null,
    /** 0 待扫描 / 1 已扫描 / 2 已确认 / 4 已过期 */
    val qrStatus: Int = 0,
    val qrLoading: Boolean = false,
    val accounts: List<MobileLoginAccount> = emptyList(),
) {
    val canSendCode: Boolean get() = mobile.length >= 11 && countdown == 0 && !busy
    val canLogin: Boolean get() = mobile.length >= 11 && code.length >= 4 && !busy
}

class AuthViewModel(private val repo: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null
    private var qrJob: Job? = null

    fun onMobileChange(value: String) {
        _state.update { it.copy(mobile = value.filter(Char::isDigit).take(11), message = null) }
    }

    fun onCodeChange(value: String) {
        _state.update { it.copy(code = value.filter(Char::isDigit).take(6), message = null) }
    }

    fun onTabChange(tab: Int) {
        _state.update { it.copy(tab = tab, message = null) }
        if (tab == 1) startQr() else stopQr()
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ===== 手机验证码 =====

    fun sendCode() {
        val current = _state.value
        if (!current.canSendCode) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = runCatching { repo.sendLoginCode(current.mobile) }
            result.fold(
                onSuccess = {
                    _state.update { it.copy(busy = false, message = "验证码已发送", messageIsError = false) }
                    startCountdown()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            busy = false,
                            message = error.message ?: "发送验证码失败",
                            messageIsError = true,
                        )
                    }
                },
            )
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (remaining in 60 downTo 1) {
                _state.update { it.copy(countdown = remaining) }
                delay(1000L)
            }
            _state.update { it.copy(countdown = 0) }
        }
    }

    fun login(userId: String? = null) {
        val current = _state.value
        if (userId == null && !current.canLogin) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val result = repo.loginWithPhone(current.mobile, current.code, userId)) {
                is PhoneLoginResult.Success ->
                    // 会话已写入 SessionStore，root 的登录门会自动切换页面。
                    _state.update { LoginUiState() }

                is PhoneLoginResult.AccountSelection ->
                    _state.update {
                        it.copy(
                            busy = false,
                            accounts = result.accounts,
                            message = result.message,
                            messageIsError = false,
                        )
                    }

                is PhoneLoginResult.Failure ->
                    _state.update {
                        it.copy(
                            busy = false,
                            accounts = emptyList(),
                            message = result.message,
                            messageIsError = true,
                        )
                    }
            }
        }
    }

    // ===== 扫码 =====

    fun startQr() {
        qrJob?.cancel()
        _state.update { it.copy(qrLoading = true, qrImageBase64 = null, qrStatus = 0, message = null) }
        qrJob = viewModelScope.launch {
            val info = runCatching { repo.getQrCode() }.getOrNull()
            if (info == null || info.imageBase64.isEmpty()) {
                _state.update { it.copy(qrLoading = false, message = "获取二维码失败", messageIsError = true) }
                return@launch
            }
            _state.update { it.copy(qrLoading = false, qrImageBase64 = info.imageBase64, qrStatus = 0) }

            // 每 2 秒轮询一次，直到确认或过期。
            while (true) {
                delay(2000L)
                val check = runCatching { repo.checkQrStatus(info.key) }.getOrNull() ?: continue
                _state.update { it.copy(qrStatus = check.status) }
                if (check.isConfirmed) {
                    repo.saveQrSession(check)
                    _state.update { LoginUiState() }
                    return@launch
                }
                if (check.isExpired) {
                    _state.update { it.copy(message = "二维码已过期，请刷新", messageIsError = true) }
                    return@launch
                }
            }
        }
    }

    fun refreshQr() = startQr()

    private fun stopQr() {
        qrJob?.cancel()
        qrJob = null
    }

    override fun onCleared() {
        countdownJob?.cancel()
        stopQr()
        super.onCleared()
    }
}
