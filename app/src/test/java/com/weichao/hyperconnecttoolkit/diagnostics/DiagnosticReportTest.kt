package com.weichao.hyperconnecttoolkit.diagnostics

import com.weichao.hyperconnecttoolkit.config.FeatureSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportTest {
    @Test
    fun `report includes session versions framework scope and both log sources`() {
        val report = DiagnosticReport(
            generatedAt = "2026-08-13T12:00:00+08:00",
            settings = FeatureSettings(
                detailedLogging = true,
                diagnosticSessionId = "abc123",
            ),
            snapshot = snapshot(),
            lsposedLog = "HCT_DIAG|event=start_discovery|startedAfter=true",
            lsposedError = null,
            appLog = "detailedLogging=true",
        ).render()

        assertTrue(report.contains("Diagnostic session: abc123"))
        assertTrue(report.contains("LSPosed 1.10.1"))
        assertTrue(report.contains("com.milink.service = 17.2.4 (170020401)"))
        assertTrue(report.contains("===== LSPosed module log ====="))
        assertTrue(report.contains("event=start_discovery"))
        assertTrue(report.contains("===== Companion app log ====="))
        assertFalse(report.contains("No HyperConnect Toolkit entries were found."))
    }

    @Test
    fun `report explains missing daemon entries`() {
        val report = DiagnosticReport(
            generatedAt = "2026-08-13T12:00:00+08:00",
            settings = FeatureSettings(),
            snapshot = snapshot(),
            lsposedLog = "",
            lsposedError = null,
            appLog = "",
        ).render()

        assertTrue(report.contains("No HyperConnect Toolkit entries were found."))
        assertTrue(report.contains("enable daemon log output"))
    }

    private fun snapshot() = DiagnosticSnapshot(
        moduleVersion = "1.6.0-diagnostic.1 (12)",
        manufacturer = "Xiaomi",
        model = "Test Pad",
        device = "test",
        product = "test_product",
        android = "16 (SDK 36)",
        buildDisplay = "OS3.0",
        buildIncremental = "OS3.0.1",
        fingerprint = "Xiaomi/test",
        framework = XposedFrameworkSnapshot(
            name = "LSPosed",
            version = "1.10.1",
            versionCode = 7100,
            apiVersion = 101,
            properties = 0,
            scope = listOf("com.milink.service"),
        ),
        components = listOf(
            ComponentVersion("com.milink.service", "17.2.4", 170020401),
        ),
    )
}
