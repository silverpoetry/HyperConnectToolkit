package com.weichao.hyperconnecttoolkit.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactorTest {
    @Test
    fun `redacts common user and network identifiers`() {
        val source = "deviceId=abcdef hostId:opaque user@example.com " +
            "AA:BB:CC:DD:EE:FF 192.168.3.79 " +
            "550e8400-e29b-41d4-a716-446655440000"

        val redacted = DiagnosticRedactor.redact(source)

        assertFalse(redacted.contains("abcdef"))
        assertFalse(redacted.contains("opaque"))
        assertFalse(redacted.contains("user@example.com"))
        assertFalse(redacted.contains("AA:BB:CC:DD:EE:FF"))
        assertFalse(redacted.contains("192.168.3.79"))
        assertFalse(redacted.contains("550e8400"))
        assertTrue(redacted.contains("deviceId=<redacted>"))
    }

    @Test
    fun `preserves structured diagnostic decisions`() {
        val source = "HCT_DIAG|event=candidate_evaluation|miLinkBit4=true|region=cn|accepted=false"

        assertTrue(DiagnosticRedactor.redact(source).contains(source))
    }
}
