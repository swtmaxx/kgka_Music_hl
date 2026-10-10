package com.swtmaxx.kamusic.compose.ui.component

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 存储权限。
 *
 * 只在 **API 28 及以下**才需要（`WRITE_EXTERNAL_STORAGE` 写公共 Downloads 目录）；
 * API 29+ 的分区存储下，`Downloader` 会自动改用应用外部私有目录，不需要任何权限。
 *
 * 刻意**不在启动时申请** —— 首次点「下载」时才弹，避免一进 App 就打断用户。
 */
private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.READ_EXTERNAL_STORAGE,
)

/** 当前是否已有写公共下载目录的权限。API 29+ 恒为 true（走私有目录）。 */
fun hasStoragePermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    ) == PackageManager.PERMISSION_GRANTED
    return granted
}

/**
 * 返回一个「带权限检查的执行器」：有权限立即执行，没有则先申请、授权后再执行。
 *
 * 用法：
 * ```
 * val withPermission = rememberStoragePermission()
 * ...
 * onClick = { withPermission { scope.launch { downloader.enqueue(...) } } }
 * ```
 * 用户拒绝时**什么都不做**（调用方可在 UI 上保持按钮可用，下次再点会重新申请）。
 */
@Composable
fun rememberStoragePermission(): (onGranted: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val action = pending
        pending = null
        if (hasStoragePermission(context)) action?.invoke()
    }

    return remember(context) {
        { onGranted ->
            if (hasStoragePermission(context)) {
                onGranted()
            } else {
                pending = onGranted
                launcher.launch(REQUIRED_PERMISSIONS)
            }
        }
    }
}
