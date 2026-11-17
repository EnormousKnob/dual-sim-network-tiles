package com.example.dualsimtiles;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class NetworkSetterTest {
    @Test public void missingSamsungMethodUsesCommandOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        NetworkSetter.set(() -> { throw new NoSuchMethodException("setAllowedNetworkTypesForReason/3"); },
                () -> calls.incrementAndGet());
        assertEquals(1, calls.get());
    }
    @Test public void successfulDirectCallDoesNotRunCommand() throws Exception {
        NetworkSetter.set(() -> true, () -> fail("unexpected fallback"));
    }
    @Test public void permissionDenialDoesNotRetryViaCommand() throws Exception {
        try {
            NetworkSetter.set(() -> { throw new SecurityException("denied"); }, () -> fail("unexpected fallback"));
            fail("expected denial");
        } catch (SecurityException expected) { assertEquals("denied", expected.getMessage()); }
    }
    @Test public void rejectedDirectCallDoesNotRunCommand() throws Exception {
        try {
            NetworkSetter.set(() -> false, () -> fail("unexpected fallback"));
            fail("expected rejection");
        } catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("拒绝")); }
    }
    @Test public void commandUsesZeroBasedSlotAndBinaryMask() {
        long mask = NetworkPolicy.NR | NetworkPolicy.LTE | 1;
        String[] command = NetworkSetter.command(1, mask);
        assertEquals("1", command[4]);
        assertEquals(mask, Long.parseLong(command[5], 2));
        assertEquals("/system/bin/cmd", command[0]);
        assertEquals("set-allowed-network-types-for-users", command[2]);
    }
    @Test(expected=IllegalArgumentException.class) public void missingSlotCannotTouchDefaultVoiceSim() {
        NetworkSetter.command(-1, NetworkPolicy.LTE);
    }
}
