package com.weichao.hyperconnecttoolkit.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class TargetProcessTest {
    @Test
    public void resolvesOnlyExactAuditedProcesses() {
        assertEquals(
                TargetProcess.MILINK_NOTIFICATION,
                TargetProcess.resolve(
                        "com.milink.service",
                        "com.milink.crossdeviceservice"));
        assertEquals(
                TargetProcess.MILINK_CORE,
                TargetProcess.resolve("com.milink.service", "com.milink.service:core"));
        assertEquals(
                TargetProcess.XIAOMI_MIRROR,
                TargetProcess.resolve("com.xiaomi.mirror", "com.xiaomi.mirror"));

        assertNull(TargetProcess.resolve("com.milink.service", "com.milink.service:ui"));
        assertNull(TargetProcess.resolve("com.xiaomi.mirror", "com.xiaomi.mirror:remote"));
        assertNull(TargetProcess.resolve("com.example.spoof", "com.milink.service:core"));
    }
}
