package com.weichao.hyperconnecttoolkit.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent

enum class LaunchMethod {
    DIRECT,
    ROOT,
}

sealed interface LaunchResult {
    data class Success(val method: LaunchMethod) : LaunchResult
    data class Failure(val reason: String) : LaunchResult
}

class HiddenSettingsLauncher(
    context: Context,
    private val rootShell: RootShell,
) {
    private val appContext = context.applicationContext

    suspend fun openCrossDeviceNotification(): LaunchResult {
        val directResult = runCatching {
            appContext.startActivity(
                Intent().apply {
                    component = CROSS_DEVICE_NOTIFICATION_COMPONENT
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        }

        if (directResult.isSuccess) {
            return LaunchResult.Success(LaunchMethod.DIRECT)
        }

        val rootResult = rootShell.execute(RootCommand.OPEN_CROSS_DEVICE_NOTIFICATION)
        if (rootResult.isSuccess && !rootResult.output.contains("Error", ignoreCase = true)) {
            return LaunchResult.Success(LaunchMethod.ROOT)
        } else {
            val detail = rootResult.output.ifBlank {
                directResult.exceptionOrNull()?.message ?: "系统拒绝启动隐藏页面"
            }
            val userFacingReason = if (
                detail.contains("Permission denied", ignoreCase = true) ||
                detail.contains("error=13", ignoreCase = true)
            ) {
                "请先在 KernelSU 的“超级用户”中授予 HyperConnect Toolkit Root 权限"
            } else {
                detail
            }
            return LaunchResult.Failure(userFacingReason)
        }
    }

    companion object {
        val CROSS_DEVICE_NOTIFICATION_COMPONENT = ComponentName(
            "com.milink.service",
            "com.miui.circulate.world.ui.connectivitysettings.Notification.FeatureNotificationActivity",
        )
    }
}
