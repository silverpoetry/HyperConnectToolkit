package com.weichao.hyperconnecttoolkit.xposed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public final class NotificationClickSessionTest {
    private NotificationClickSession session;

    @Before
    public void setUp() {
        session = new NotificationClickSession();
    }

    @Test
    public void matchingFollowUpIsConsumedExactlyOnce() {
        session.begin("phone-1", "com.example.chat", 1_000L);

        assertTrue(session.consumeMatching("phone-1", "com.example.chat", 1_001L));
        assertFalse(session.consumeMatching("phone-1", "com.example.chat", 1_002L));
    }

    @Test
    public void mismatchedFollowUpConsumesStaleTransaction() {
        session.begin("phone-1", "com.example.chat", 1_000L);

        assertFalse(session.consumeMatching("phone-2", "com.example.chat", 1_001L));
        assertFalse(session.consumeMatching("phone-1", "com.example.chat", 1_002L));
    }

    @Test
    public void expiredTransactionDoesNotSuppressLaterPublish() {
        session.begin("phone-1", "com.example.chat", 1_000L);

        assertFalse(session.consumeMatching(
                "phone-1",
                "com.example.chat",
                1_000L + NotificationClickSession.MAX_AGE_MILLIS + 1L));
    }

    @Test
    public void invalidClickDoesNotArmTransaction() {
        session.begin("", "com.example.chat", 1_000L);

        assertFalse(session.consumeMatching("", "com.example.chat", 1_001L));
    }

    @Test
    public void monotonicClockRegressionDoesNotMatch() {
        session.begin("phone-1", "com.example.chat", 1_000L);

        assertFalse(session.consumeMatching("phone-1", "com.example.chat", 999L));
    }
}
