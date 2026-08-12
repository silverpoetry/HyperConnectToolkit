package com.weichao.hyperconnecttoolkit.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Fixed, auditable root operations used by the app. No user-controlled shell text is accepted. */
enum class RootCommand(val shellText: String) {
    PROBE("id -u"),
    OPEN_CROSS_DEVICE_NOTIFICATION(
        "am start -W -n " +
            "com.milink.service/com.miui.circulate.world.ui.connectivitysettings.Notification.FeatureNotificationActivity",
    ),
    STOP_MIRROR("am force-stop com.xiaomi.mirror"),
    STOP_MILINK("am force-stop com.milink.service"),
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

        val completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return@withContext RootResult(
                exitCode = -1,
                output = "Root command timed out",
                timedOut = true,
            )
        }

        RootResult(
            exitCode = process.exitValue(),
            output = process.inputStream.bufferedReader().use { it.readText() }.trim(),
        )
    }

    suspend fun isAvailable(): Boolean {
        val result = execute(RootCommand.PROBE)
        return result.isSuccess && result.output.lineSequence().lastOrNull()?.trim() == "0"
    }

    private companion object {
        const val COMMAND_TIMEOUT_SECONDS = 8L
    }
}
