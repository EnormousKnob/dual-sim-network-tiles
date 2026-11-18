package com.example.dualsimtiles;
import org.junit.Test;
import static org.junit.Assert.*;

public class RestoreGuardTest {
    @Test public void replacedOrUnreadableSimKeepsRecoveryPending() {
        assertEquals(RestoreGuard.Decision.UNAVAILABLE,RestoreGuard.decide(false,true,13,15,13));
        assertEquals(RestoreGuard.Decision.UNAVAILABLE,RestoreGuard.decide(true,false,13,15,13));
    }
    @Test public void externallyChangedModeIsNeverOverwritten() {
        assertEquals(RestoreGuard.Decision.EXTERNAL_CHANGE,RestoreGuard.decide(true,true,7,15,13));
    }
    @Test public void onlyOurOwnAppliedModeMayBeRestored() {
        assertEquals(RestoreGuard.Decision.RESTORE,RestoreGuard.decide(true,true,13,15,13));
        assertEquals(RestoreGuard.Decision.ALREADY_RESTORED,RestoreGuard.decide(true,true,15,15,13));
    }
}
