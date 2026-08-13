package com.weichao.hyperconnecttoolkit.platform;

/** Stable activation-report protocol shared by injected processes and the companion app. */
public final class HookStatusContract {
    public static final String ACTION_HOOKS_ACTIVE =
            "com.weichao.hyperconnecttoolkit.action.HOOKS_ACTIVE";
    public static final String EXTRA_SOURCE_PACKAGE = "source_package";
    public static final String EXTRA_SOURCE_PROCESS = "source_process";
    public static final String EXTRA_MODULE_VERSION = "module_version";
    public static final String EXTRA_ACTIVE_FEATURES = "active_features";

    public static final int FEATURE_CROSS_DEVICE_NOTIFICATION = 1;
    public static final int FEATURE_PORTRAIT_STREAMING = 1 << 1;
    public static final int FEATURE_MILINK_MULTI_CHANNEL = 1 << 2;
    public static final int ALL_FEATURES = FEATURE_CROSS_DEVICE_NOTIFICATION
            | FEATURE_PORTRAIT_STREAMING
            | FEATURE_MILINK_MULTI_CHANNEL;

    /** Internal evidence bits used when one user feature spans independently injected processes. */
    public static final int COMPONENT_NOTIFICATION_DISCOVERY = 1 << 8;
    public static final int COMPONENT_NOTIFICATION_FLOW = 1 << 9;
    public static final int ALL_REPORT_BITS = ALL_FEATURES
            | COMPONENT_NOTIFICATION_DISCOVERY
            | COMPONENT_NOTIFICATION_FLOW;

    private HookStatusContract() {
    }
}
