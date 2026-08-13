package com.weichao.hyperconnecttoolkit.xposed;

/** Exact process boundaries for every supported Hook group. */
enum TargetProcess {
    MILINK_NOTIFICATION_DISCOVERY("com.milink.service", "com.milink.service:ui"),
    MILINK_NOTIFICATION_FLOW("com.milink.service", "com.milink.crossdeviceservice"),
    MILINK_CORE("com.milink.service", "com.milink.service:core"),
    XIAOMI_MIRROR("com.xiaomi.mirror", "com.xiaomi.mirror");

    final String packageName;
    final String processName;

    TargetProcess(String packageName, String processName) {
        this.packageName = packageName;
        this.processName = processName;
    }

    boolean matches(String candidatePackage, String candidateProcess) {
        return packageName.equals(candidatePackage) && processName.equals(candidateProcess);
    }

    static TargetProcess resolve(String packageName, String processName) {
        for (TargetProcess target : values()) {
            if (target.matches(packageName, processName)) {
                return target;
            }
        }
        return null;
    }
}
