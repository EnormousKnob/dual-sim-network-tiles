package com.example.dualsimtiles;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeakSignalPolicyTest {
    @Test public void transientDipDoesNotDowngrade() {
        WeakSignalPolicy p=new WeakSignalPolicy();
        assertFalse(p.observe("A",-120,true,-110,3));
        assertFalse(p.observe("A",-100,true,-110,3));
        assertFalse(p.observe("A",-120,true,-110,3));
    }
    @Test public void consecutiveWeakSamplesTriggerOnce() {
        WeakSignalPolicy p=new WeakSignalPolicy();
        assertFalse(p.observe("A",-120,true,-110,3)); assertFalse(p.observe("A",-120,true,-110,3));
        assertTrue(p.observe("A",-120,true,-110,3)); assertFalse(p.observe("A",-120,true,-110,3));
    }
    @Test public void unknownSignalAndCallsBreakTheRun() {
        WeakSignalPolicy p=new WeakSignalPolicy();
        p.observe("A",-120,true,-110,3); p.observe("A",-120,true,-110,3);
        assertFalse(p.observe("A",Integer.MAX_VALUE,true,-110,3));
        assertFalse(p.observe("A",-120,true,-110,3));
        assertFalse(p.observe("A",-120,false,-110,3));
        assertFalse(p.observe("A",-120,true,-110,3));
    }
    @Test public void samplesFromDifferentCardsDoNotAccumulate() {
        WeakSignalPolicy p=new WeakSignalPolicy(); p.observe("A",-120,true,-110,3); p.observe("A",-120,true,-110,3);
        assertFalse(p.observe("B",-120,true,-110,3));
    }
    @Test public void cooldownCannotRecoverEarlyAndRebootCanRecover() {
        assertFalse(WeakSignalPolicy.recoveryDue(599999,0,600000));
        assertTrue(WeakSignalPolicy.recoveryDue(600000,0,600000));
        assertTrue(WeakSignalPolicy.recoveryDue(100,500000,600000));
    }
}
