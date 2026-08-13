package com.weichao.hyperconnecttoolkit.diagnostics

import android.content.Context
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Bounded application-side log for diagnostic configuration and privileged actions. */
object AppDiagnosticLog {
    private val mutex = Mutex()

    suspend fun record(
        context: Context,
        enabled: Boolean,
        component: String,
        message: String,
        error: Throwable? = null,
    ) {
        if (!enabled) return
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = currentFile(appContext)
                rotateIfNeeded(current)
                current.appendText(
                    buildString {
                        append(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        append(" [")
                        append(component.filter { it.isLetterOrDigit() || it in "_.-" })
                        append("] ")
                        append(DiagnosticRedactor.redact(message.trim()))
                        if (error != null) {
                            append(" · error=")
                            append(error.javaClass.name)
                        }
                        append('\n')
                    },
                )
            }
        }
    }

    suspend fun read(context: Context): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val directory = logDirectory(context.applicationContext)
            listOf(File(directory, OLD_FILE_NAME), File(directory, CURRENT_FILE_NAME))
                .filter(File::isFile)
                .joinToString(separator = "\n") { file ->
                    "===== ${file.name} =====\n${file.readText().trim()}"
                }
                .let(DiagnosticRedactor::redact)
        }
    }

    private fun currentFile(context: Context): File = File(logDirectory(context), CURRENT_FILE_NAME)

    private fun logDirectory(context: Context): File =
        File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    private fun rotateIfNeeded(current: File) {
        if (!current.isFile || current.length() < MAX_FILE_BYTES) return
        val old = File(current.parentFile, OLD_FILE_NAME)
        if (old.exists()) old.delete()
        if (!current.renameTo(old)) {
            current.copyTo(old, overwrite = true)
            current.writeText("")
        }
    }

    private const val DIRECTORY_NAME = "diagnostics"
    private const val CURRENT_FILE_NAME = "app.log"
    private const val OLD_FILE_NAME = "app.log.1"
    private const val MAX_FILE_BYTES = 256L * 1024L
}
