package com.weichao.hyperconnecttoolkit.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Before;
import org.junit.Test;

public final class HostBindingRepairTest {
    private FakeDiscoveryHost host;
    private HostBindingRepair repair;

    @Before
    public void setUp() throws ReflectiveOperationException {
        FakeMultiplatformProcessor.INSTANCE.reset();
        host = new FakeDiscoveryHost();
        repair = HostBindingRepair.resolve(
                FakeDiscoveryHost.class,
                FakeDiscoveryProxy.class,
                FakeMultiplatformProcessor.class,
                FakeCompatibility.class);
        repair.attach(host);
    }

    @Test
    public void hostNotBoundCompletesOfficialBindAndDiscovery()
            throws ReflectiveOperationException {
        FakeRemoteProxy remote = new FakeRemoteProxy(HostBindingRepair.SUCCESS);
        FakeDiscoveryProxy proxy = new FakeDiscoveryProxy(remote);
        host.headsetDiscoveries.put("pad-2", proxy);

        assertTrue(repair.bind("pad-2"));

        assertEquals(1, remote.bindCount);
        assertTrue(proxy.boundResult);
        assertEquals(1, proxy.startDiscoveryCount);
        assertEquals("pad-2", FakeMultiplatformProcessor.INSTANCE.lastHostId);
        assertTrue(FakeMultiplatformProcessor.INSTANCE.lastBound);
    }

    @Test
    public void alreadyBoundProxyIsUntouched() throws ReflectiveOperationException {
        FakeRemoteProxy remote = new FakeRemoteProxy(HostBindingRepair.SUCCESS);
        FakeDiscoveryProxy proxy = new FakeDiscoveryProxy(remote);
        proxy.boundResult = true;
        host.headsetDiscoveries.put("pad-2", proxy);

        assertFalse(repair.bind("pad-2"));

        assertEquals(0, remote.bindCount);
        assertEquals(0, proxy.startDiscoveryCount);
    }

    @Test
    public void failedOfficialBindDoesNotForgeBoundState()
            throws ReflectiveOperationException {
        FakeRemoteProxy remote = new FakeRemoteProxy(214);
        FakeDiscoveryProxy proxy = new FakeDiscoveryProxy(remote);
        host.headsetDiscoveries.put("pad-2", proxy);

        assertFalse(repair.bind("pad-2"));

        assertEquals(1, remote.bindCount);
        assertFalse(proxy.boundResult);
        assertEquals(0, proxy.startDiscoveryCount);
        assertNull(FakeMultiplatformProcessor.INSTANCE.lastHostId);
    }

    @Test
    public void unrelatedHostDoesNotCreateProxy() throws ReflectiveOperationException {
        assertFalse(repair.bind("unknown"));
        assertTrue(host.headsetDiscoveries.isEmpty());
    }

    private static final class FakeDiscoveryHost {
        private final Map<String, Object> headsetDiscoveries = new ConcurrentHashMap<>();
    }

    private static final class FakeDiscoveryProxy {
        private final FakeRemoteProxy proxy;
        private volatile boolean boundResult;
        private int startDiscoveryCount;

        FakeDiscoveryProxy(FakeRemoteProxy proxy) {
            this.proxy = proxy;
        }

        public FakeRemoteProxy getProxy() {
            return proxy;
        }

        public void startDiscovery() {
            startDiscoveryCount++;
        }
    }

    private static final class FakeRemoteProxy {
        private final int result;
        private int bindCount;

        FakeRemoteProxy(int result) {
            this.result = result;
        }

        public FakeBindResult doBind(String hostId) {
            bindCount++;
            return new FakeBindResult(result, new FakeCompatibility(9));
        }
    }

    private static final class FakeBindResult {
        private final int status;
        private final FakeCompatibility compatibility;

        FakeBindResult(int status, FakeCompatibility compatibility) {
            this.status = status;
            this.compatibility = compatibility;
        }

        public Integer component1() {
            return status;
        }

        public FakeCompatibility component2() {
            return compatibility;
        }
    }

    private static final class FakeCompatibility {
        @SuppressWarnings("unused")
        private final int version;

        FakeCompatibility(int version) {
            this.version = version;
        }
    }

    private static final class FakeMultiplatformProcessor {
        public static final FakeMultiplatformProcessor INSTANCE =
                new FakeMultiplatformProcessor();

        private String lastHostId;
        private boolean lastBound;

        public synchronized void override(
                String hostId,
                boolean isHostBound,
                FakeCompatibility compatibility) {
            lastHostId = hostId;
            lastBound = isHostBound;
        }

        void reset() {
            lastHostId = null;
            lastBound = false;
        }
    }
}
