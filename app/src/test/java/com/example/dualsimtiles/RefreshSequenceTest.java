package com.example.dualsimtiles;
import org.junit.Test;
import static org.junit.Assert.*;

public class RefreshSequenceTest {
    @Test public void previousSwitchCannotOverwriteNewerSwitch() {
        RefreshSequence sequence = new RefreshSequence();
        long first = sequence.next();
        long second = sequence.next();
        assertFalse(sequence.isCurrent(first));
        assertTrue(sequence.isCurrent(second));
    }
    @Test public void followUpReadsHaveFiniteIncreasingDelays() {
        long previous = 0;
        for (long delay : RefreshSequence.DELAYS_MS) {
            assertTrue(delay > previous);
            assertTrue(delay <= 10000);
            previous = delay;
        }
    }
}
