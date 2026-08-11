package com.weichao.hyperconnecttoolkit.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookStatusPolicyTest {
    @Test
    fun `accepts only target package identities reported for sender uid`() {
        assertTrue(HookStatusPolicy.isTrustedSender(listOf("com.xiaomi.mirror")))
        assertTrue(
            HookStatusPolicy.isTrustedSender(
                listOf("android", "com.xiaomi.mirror", "com.android.settings"),
            ),
        )
        assertFalse(HookStatusPolicy.isTrustedSender(listOf("com.example.spoof")))
        assertFalse(HookStatusPolicy.isTrustedSender(emptyList()))
    }
}
