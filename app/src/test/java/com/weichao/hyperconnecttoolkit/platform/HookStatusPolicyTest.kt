package com.weichao.hyperconnecttoolkit.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HookStatusPolicyTest {
    @Test
    fun `accepts a feature owned by the authenticated source package`() {
        val report = HookStatusPolicy.validate(
            senderPackages = listOf("android", "com.milink.service"),
            sourcePackage = "com.milink.service",
            sourceProcess = "com.milink.service:core",
            moduleVersion = HookStatusPolicy.EXPECTED_MODULE_VERSION,
            reportedFeatureMask = HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL,
        )

        assertEquals(
            HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL,
            report?.featureMask,
        )
    }

    @Test
    fun `rejects a claimed package not owned by sender uid`() {
        assertNull(
            HookStatusPolicy.validate(
                senderPackages = listOf("com.example.spoof"),
                sourcePackage = "com.milink.service",
                sourceProcess = "com.milink.service:core",
                moduleVersion = HookStatusPolicy.EXPECTED_MODULE_VERSION,
                reportedFeatureMask = HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL,
            ),
        )
    }

    @Test
    fun `rejects stale versions unknown bits and cross-package features`() {
        val validSender = listOf("com.milink.service")
        assertNull(
            HookStatusPolicy.validate(
                validSender,
                "com.milink.service",
                "com.milink.service:core",
                HookStatusPolicy.EXPECTED_MODULE_VERSION - 1,
                HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL,
            ),
        )
        assertNull(
            HookStatusPolicy.validate(
                validSender,
                "com.milink.service",
                "com.milink.service:core",
                HookStatusPolicy.EXPECTED_MODULE_VERSION,
                1 shl 10,
            ),
        )
        assertNull(
            HookStatusPolicy.validate(
                validSender,
                "com.milink.service",
                "com.milink.service:core",
                HookStatusPolicy.EXPECTED_MODULE_VERSION,
                HookStatusContract.FEATURE_PORTRAIT_STREAMING,
            ),
        )
    }

    @Test
    fun `rejects a feature reported by the wrong process in the same package`() {
        assertNull(
            HookStatusPolicy.validate(
                senderPackages = listOf("com.milink.service"),
                sourcePackage = "com.milink.service",
                sourceProcess = "com.milink.crossdeviceservice",
                moduleVersion = HookStatusPolicy.EXPECTED_MODULE_VERSION,
                reportedFeatureMask = HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL,
            ),
        )
    }

    @Test
    fun `accepts only the notification component owned by each process`() {
        val sender = listOf("com.milink.service")
        assertEquals(
            HookStatusContract.COMPONENT_NOTIFICATION_DISCOVERY,
            HookStatusPolicy.validate(
                sender,
                "com.milink.service",
                "com.milink.service:ui",
                HookStatusPolicy.EXPECTED_MODULE_VERSION,
                HookStatusContract.COMPONENT_NOTIFICATION_DISCOVERY,
            )?.featureMask,
        )
        assertEquals(
            HookStatusContract.COMPONENT_NOTIFICATION_FLOW,
            HookStatusPolicy.validate(
                sender,
                "com.milink.service",
                "com.milink.crossdeviceservice",
                HookStatusPolicy.EXPECTED_MODULE_VERSION,
                HookStatusContract.COMPONENT_NOTIFICATION_FLOW,
            )?.featureMask,
        )
        assertNull(
            HookStatusPolicy.validate(
                sender,
                "com.milink.service",
                "com.milink.service:ui",
                HookStatusPolicy.EXPECTED_MODULE_VERSION,
                HookStatusContract.COMPONENT_NOTIFICATION_FLOW,
            ),
        )
    }
}
