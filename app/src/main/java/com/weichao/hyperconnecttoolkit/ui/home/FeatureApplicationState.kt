package com.weichao.hyperconnecttoolkit.ui.home

/** Presentation state derived from desired configuration and actual target-process evidence. */
enum class FeatureApplicationState(val label: String) {
    DISABLED("已关闭"),
    APPLIED("已应用"),
    NOT_APPLIED("未应用"),
    STILL_RUNNING("仍在运行"),
    PENDING_ENABLE("重启后启用"),
    PENDING_DISABLE("重启后关闭"),
    ;

    val isAttentionRequired: Boolean
        get() = this == NOT_APPLIED || this == STILL_RUNNING

    val isPending: Boolean
        get() = this == PENDING_ENABLE || this == PENDING_DISABLE

    companion object {
        fun resolve(
            enabled: Boolean,
            applied: Boolean,
            pendingRestart: Boolean,
        ): FeatureApplicationState = when {
            pendingRestart && enabled -> PENDING_ENABLE
            pendingRestart -> PENDING_DISABLE
            enabled && applied -> APPLIED
            enabled -> NOT_APPLIED
            applied -> STILL_RUNNING
            else -> DISABLED
        }
    }
}
