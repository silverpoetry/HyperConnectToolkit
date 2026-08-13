package com.weichao.hyperconnecttoolkit.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Fixed, auditable root operations used by the app. No user-controlled shell text is accepted. */
enum class RootCommand(
    val shellText: String,
    val timeoutSeconds: Long = 8L,
) {
    PROBE("id -u"),
    OPEN_CROSS_DEVICE_NOTIFICATION(
        "am start -W -n " +
            "com.milink.service/com.miui.circulate.world.ui.connectivitysettings.Notification.FeatureNotificationActivity",
    ),
    STOP_MIRROR("am force-stop com.xiaomi.mirror"),
    STOP_MILINK("am force-stop com.milink.service"),
    EXPORT_LSPOSED_MODULE_LOG(
        "grep -a -F '[com.weichao.hyperconnecttoolkit,' " +
            "/data/adb/lspd/log.old/modules_*.log /data/adb/lspd/log.old/modules.log " +
            "/data/adb/lspd/log/modules_*.log /data/adb/lspd/log/modules.log " +
            "2>/dev/null | tail -c 2097152",
        timeoutSeconds = 20L,
    ),
}

data class RootResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false,
) {
    val isSuccess: Boolean get() = !timedOut && exitCode == 0
}

class RootShell {
    suspend fun execute(command: RootCommand): RootResult = withContext(Dispatchers.IO) {
        val process = runCatching {
            ProcessBuilder("su", "-c", command.shellText)
                .redirectErrorStream(true)
                .start()
        }.getOrElse { error ->
            return@withContext RootResult(
                exitCode = -1,
                output = error.message.orEmpty(),
            )
        }

        val outputReader = async(Dispatchers.IO) {
            process.inputStream.bufferedReader().use { it.readText().trim() }
        }

        val completed = process.waitFor(command.timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            outputReader.cancel()
            return@withContext RootResult(
                exitCode = -1,
                output = "Root command timed out",
                timedOut = true,
            )
        }

        RootResult(
            exitCode = process.exitValue(),
            output = outputReader.await(),
        )
    }

    suspend fun isAvailable(): Boolean {
        val result = execute(RootCommand.PROBE)
        return result.isSuccess && result.output.lineSequence().lastOrNull()?.trim() == "0"
    }
}
