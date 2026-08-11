package com.weichao.hyperconnecttoolkit.platform

import com.weichao.hyperconnecttoolkit.BuildConfig

object HookStatusPolicy {
    const val ACTION_HOOKS_ACTIVE =
        "com.weichao.hyperconnecttoolkit.action.HOOKS_ACTIVE"
    const val EXTRA_SOURCE_PACKAGE = "source_package"
    const val EXPECTED_MODULE_VERSION = BuildConfig.VERSION_CODE

    private val trustedTargetPackages = setOf(
        "com.xiaomi.mirror",
        "com.milink.service",
    )

    fun isTrustedSender(packagesForUid: Collection<String>): Boolean =
        packagesForUid.any(trustedTargetPackages::contains)
}
