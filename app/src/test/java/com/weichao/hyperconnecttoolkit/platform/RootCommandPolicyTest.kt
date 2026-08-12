package com.weichao.hyperconnecttoolkit.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootCommandPolicyTest {
    @Test
    fun `root boundary exposes only fixed commands`() {
        assertEquals(
            setOf(
                RootCommand.PROBE,
                RootCommand.OPEN_CROSS_DEVICE_NOTIFICATION,
                RootCommand.STOP_MIRROR,
                RootCommand.STOP_MILINK,
            ),
            RootCommand.entries.toSet(),
        )
        RootCommand.entries.forEach { command ->
            assertFalse(command.shellText.contains('\n'))
            assertFalse(command.shellText.contains('\r'))
            assertFalse(command.shellText.contains("$"))
        }
    }

    @Test
    fun `hidden settings command targets the audited HyperOS activity`() {
        val command = RootCommand.OPEN_CROSS_DEVICE_NOTIFICATION.shellText

        assertTrue(command.startsWith("am start -W -n "))
        assertTrue(command.contains("com.milink.service/"))
        assertTrue(command.endsWith("Notification.FeatureNotificationActivity"))
    }

    @Test
    fun `restart commands cannot target arbitrary packages`() {
        assertEquals("am force-stop com.xiaomi.mirror", RootCommand.STOP_MIRROR.shellText)
        assertEquals("am force-stop com.milink.service", RootCommand.STOP_MILINK.shellText)
    }
}
