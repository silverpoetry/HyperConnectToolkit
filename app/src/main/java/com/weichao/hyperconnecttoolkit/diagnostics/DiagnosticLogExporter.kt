package com.weichao.hyperconnecttoolkit.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.weichao.hyperconnecttoolkit.BuildConfig
import com.weichao.hyperconnecttoolkit.config.FeatureSettings
import com.weichao.hyperconnecttoolkit.platform.RootCommand
import com.weichao.hyperconnecttoolkit.platform.RootShell
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DiagnosticExportResult(
    val success: Boolean,
    val detail: String,
)

data class ComponentVersion(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long?,
)

data class DiagnosticSnapshot(
    val moduleVersion: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val product: String,
    val android: String,
    val buildDisplay: String,
    val buildIncremental: String,
    val fingerprint: String,
    val framework: XposedFrameworkSnapshot?,
    val components: List<ComponentVersion>,
) {
    fun render(): String = buildString {
        appendLine("Module: $moduleVersion")
        appendLine("Device: $manufacturer $model ($device / $product)")
        appendLine("Android: $android")
        appendLine("Build display: $buildDisplay")
        appendLine("Build incremental: $buildIncremental")
        appendLine("Build fingerprint: $fingerprint")
        if (framework == null) {
            appendLine("Xposed framework: unavailable")
        } else {
            appendLine(
                "Xposed framework: ${framework.name} ${framework.version} " +
                    "(${framework.versionCode}), API ${framework.apiVersion}",
            )
            appendLine("Xposed properties: ${framework.properties.toULong()}")
            appendLine(
                "Xposed scope: ${framework.scope.joinToString().ifBlank { "empty" }}",
            )
        }
        components.forEach { component ->
            append("Component: ${component.packageName} = ")
            if (component.versionCode == null) {
                appendLine("not visible or not installed")
            } else {
                appendLine("${component.versionName ?: "unknown"} (${component.versionCode})")
            }
        }
    }
}

data class DiagnosticReport(
    val generatedAt: String,
    val settings: FeatureSettings,
    val snapshot: DiagnosticSnapshot,
    val lsposedLog: String,
    val lsposedError: String?,
    val appLog: String,
) {
    fun render(): String = DiagnosticRedactor.redact(
        buildString {
            appendLine("HyperConnect Toolkit device-discovery diagnostics")
            appendLine("Generated: $generatedAt")
            appendLine("Diagnostic session: ${settings.diagnosticSessionId.ifBlank { "missing" }}")
            appendLine("Detailed logging: ${enabled(settings.detailedLogging)}")
            appendLine("Cross-device notification: ${enabled(settings.crossDeviceNotification)}")
            appendLine("Portrait streaming: ${enabled(settings.portraitStreaming)}")
            appendLine("MiLink Multi-Channel: ${enabled(settings.miLinkMultiChannel)}")
            appendLine()
            append(snapshot.render())
            appendLine("===== LSPosed module log =====")
            if (lsposedLog.isBlank()) {
                appendLine("No HyperConnect Toolkit entries were found.")
                appendLine(
                    "Confirm the module scope, enable daemon log output in LSPosed, " +
                        "restart MiLink, reproduce once, and export again.",
                )
            } else {
                appendLine(lsposedLog.trim())
            }
            if (!lsposedError.isNullOrBlank()) {
                appendLine("LSPosed export error: ${lsposedError.trim()}")
            }
            appendLine()
            appendLine("===== Companion app log =====")
            appendLine(appLog.ifBlank { "No companion-app entries were recorded." }.trim())
        },
    )

    private fun enabled(value: Boolean): String = if (value) "enabled" else "disabled"
}

/** Creates one self-contained, sanitized report for an external tester. */
class DiagnosticLogExporter(
    context: Context,
    private val rootShell: RootShell,
) {
    private val appContext = context.applicationContext

    suspend fun export(
        destination: Uri,
        settings: FeatureSettings,
    ): DiagnosticExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val lsposedResult = rootShell.execute(RootCommand.EXPORT_LSPOSED_MODULE_LOG)
            val report = DiagnosticReport(
                generatedAt = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                settings = settings,
                snapshot = collectSnapshot(appContext),
                lsposedLog = if (lsposedResult.isSuccess) lsposedResult.output else "",
                lsposedError = if (lsposedResult.isSuccess) {
                    null
                } else {
                    "exit=${lsposedResult.exitCode} · ${lsposedResult.output}"
                },
                appLog = AppDiagnosticLog.read(appContext),
            )
            val output = requireNotNull(
                appContext.contentResolver.openOutputStream(destination, "w"),
            ) { "无法打开导出位置" }
            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(report.render())
            }
            DiagnosticExportResult(true, "诊断日志已导出")
        }.getOrElse { error ->
            DiagnosticExportResult(false, error.message ?: error.javaClass.simpleName)
        }
    }

    companion object {
        fun defaultFileName(): String =
            "HyperConnectToolkit-diagnostics-${
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            }.txt"

        internal fun collectSnapshot(context: Context): DiagnosticSnapshot {
            val packageManager = context.packageManager
            return DiagnosticSnapshot(
                moduleVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                device = Build.DEVICE,
                product = Build.PRODUCT,
                android = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                buildDisplay = Build.DISPLAY,
                buildIncremental = Build.VERSION.INCREMENTAL,
                fingerprint = Build.FINGERPRINT,
                framework = XposedFrameworkStore.current(),
                components = COMPONENT_PACKAGES.map { packageName ->
                    packageVersion(packageManager, packageName)
                },
            )
        }

        private fun packageVersion(
            packageManager: PackageManager,
            packageName: String,
        ): ComponentVersion = runCatching {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        }.fold(
            onSuccess = { info ->
                ComponentVersion(packageName, info.versionName, info.longVersionCode)
            },
            onFailure = {
                ComponentVersion(packageName, null, null)
            },
        )

        private val COMPONENT_PACKAGES = listOf(
            "com.milink.service",
            "com.xiaomi.mirror",
            "com.xiaomi.continuity.sdkapp",
            "com.android.settings",
        )
    }
}
