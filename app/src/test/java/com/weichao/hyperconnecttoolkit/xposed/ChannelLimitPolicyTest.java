package com.weichao.hyperconnecttoolkit.xposed;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public final class ChannelLimitPolicyTest {
    @Test
    public void updatesOnlySingleChannelAndroidPadEntry() {
        FakePolicy policy = new FakePolicy(1);

        assertEquals(
                ChannelLimitPolicy.Result.UPDATED,
                ChannelLimitPolicy.ensureAndroidPadDualChannel(policy));
        assertEquals(Integer.valueOf(2), policy.limits.get(2));
        assertEquals(Integer.valueOf(1), policy.limits.get(1));
        assertEquals(Integer.valueOf(4), policy.limits.get(4));
    }

    @Test
    public void acceptsAnAlreadyConfiguredPolicyIdempotently() {
        FakePolicy policy = new FakePolicy(2);

        assertEquals(
                ChannelLimitPolicy.Result.ALREADY_SUPPORTED,
                ChannelLimitPolicy.ensureAndroidPadDualChannel(policy));
        assertEquals(
                ChannelLimitPolicy.Result.ALREADY_SUPPORTED,
                ChannelLimitPolicy.ensureAndroidPadDualChannel(policy));
    }

    @Test
    public void rejectsUnexpectedLimitsAndUnrelatedMaps() {
        assertEquals(
                ChannelLimitPolicy.Result.UNSUPPORTED,
                ChannelLimitPolicy.ensureAndroidPadDualChannel(new FakePolicy(3)));
        assertEquals(
                ChannelLimitPolicy.Result.UNSUPPORTED,
                ChannelLimitPolicy.ensureAndroidPadDualChannel(new UnrelatedPolicy()));
    }

    private static final class FakePolicy {
        private final Map<Integer, Integer> limits = new LinkedHashMap<>();

        FakePolicy(int padLimit) {
            limits.put(1, 1);
            limits.put(2, padLimit);
            limits.put(4, 4);
        }
    }

    private static final class UnrelatedPolicy {
        @SuppressWarnings("unused")
        private final Map<String, Integer> values = Map.of("other", 1);
    }
}
