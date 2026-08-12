package com.weichao.hyperconnecttoolkit.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.weichao.hyperconnecttoolkit.domain.model.EnvironmentSnapshot
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

    private fun isPackageInstalled(packageName: String): Boolean = runCatching {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    }.isSuccess

    private companion object {
        const val MILINK_PACKAGE = "com.milink.service"
        const val MIRROR_PACKAGE = "com.xiaomi.mirror"
    }
}
