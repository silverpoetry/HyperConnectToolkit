package com.weichao.hyperconnecttoolkit.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.weichao.hyperconnecttoolkit.domain.model.EnvironmentSnapshot
import com.weichao.hyperconnecttoolkit.domain.model.ToggleState
import com.weichao.hyperconnecttoolkit.platform.RootCommand
import com.weichao.hyperconnecttoolkit.platform.RootShell
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class AndroidSystemStatusRepository(
    context: Context,
    private val rootShell: RootShell,
) : SystemStatusRepository {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    override suspend fun readEnvironment(): EnvironmentSnapshot = coroutineScope {
        val root = async { rootShell.isAvailable() }
        val receiveState = async {
            readSecureToggle(
                key = RECEIVE_STATE_KEY,
                rootCommand = RootCommand.READ_NOTIFICATION_RECEIVE,
            )
        }
        val sendState = async {
            readSecureToggle(
                key = SEND_STATE_KEY,
                rootCommand = RootCommand.READ_NOTIFICATION_SEND,
            )
        }

        EnvironmentSnapshot(
            deviceName = Settings.Global.getString(
                appContext.contentResolver,
                Settings.Global.DEVICE_NAME,
            )?.takeIf { it.isNotBlank() } ?: listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .joinToString(" "),
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            isRootAvailable = root.await(),
            isMiLinkInstalled = isPackageInstalled(MILINK_PACKAGE),
            isMirrorInstalled = isPackageInstalled(MIRROR_PACKAGE),
            notificationReceiveState = receiveState.await(),
            notificationSendState = sendState.await(),
        )
    }

    override suspend fun restartTargetProcesses(): RestartResult {
        val mirror = rootShell.execute(RootCommand.STOP_MIRROR)
        if (!mirror.isSuccess) {
            return RestartResult(false, "Xiaomi Mirror 停止失败：${mirror.output}")
        }

        val miLink = rootShell.execute(RootCommand.STOP_MILINK)
        if (!miLink.isSuccess) {
            return RestartResult(false, "MiLink 停止失败：${miLink.output}")
        }

        return RestartResult(
            isSuccess = true,
            message = "目标服务已停止，将在系统需要时自动重新启动",
        )
    }

    private suspend fun readSecureToggle(
        key: String,
        rootCommand: RootCommand,
    ): ToggleState {
        val directValue = runCatching {
            Settings.Secure.getString(appContext.contentResolver, key)
        }.getOrNull()
        parseToggle(directValue)?.let { return it }

        val rootResult = rootShell.execute(rootCommand)
        return parseToggle(rootResult.output) ?: ToggleState.UNKNOWN
    }

    private fun parseToggle(value: String?): ToggleState? = when (value?.trim()) {
        "1" -> ToggleState.ENABLED
        "0" -> ToggleState.DISABLED
        else -> null
    }

    private fun isPackageInstalled(packageName: String): Boolean = runCatching {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    }.isSuccess

    private companion object {
        const val MILINK_PACKAGE = "com.milink.service"
        const val MIRROR_PACKAGE = "com.xiaomi.mirror"
        const val RECEIVE_STATE_KEY = "pref_key_connectivity_share_notification_local_state"
        const val SEND_STATE_KEY = "pref_key_connectivity_share_notification_mi_phone_state"
    }
}
