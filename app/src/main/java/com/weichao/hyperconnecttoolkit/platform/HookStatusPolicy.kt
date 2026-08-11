package com.weichao.hyperconnecttoolkit.platform

object HookStatusPolicy {
    const val ACTION_HOOKS_ACTIVE =
        "com.weichao.hyperconnecttoolkit.action.HOOKS_ACTIVE"
    const val EXTRA_SOURCE_PACKAGE = "source_package"
    const val EXPECTED_MODULE_VERSION = 3

    private val trustedTargetPackages = setOf(
        "com.xiaomi.mirror",
        "com.milink.service",
    )

    fun isTrustedSender(packagesForUid: Collection<String>): Boolean =
        packagesForUid.any(trustedTargetPackages::contains)
}
