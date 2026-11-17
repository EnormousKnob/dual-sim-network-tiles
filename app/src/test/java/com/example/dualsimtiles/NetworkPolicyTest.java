package com.example.dualsimtiles;
import org.junit.Test;
import static org.junit.Assert.*;
public class NetworkPolicyTest {
    @Test public void preservesLegacyAndLteWhenDisablingNr() {
        long baseline = 1L | (1L<<2) | NetworkPolicy.LTE;
        assertEquals(baseline, NetworkPolicy.toggle(baseline | NetworkPolicy.NR, baseline | NetworkPolicy.NR));
        assertEquals(baseline | NetworkPolicy.NR, NetworkPolicy.toggle(baseline, baseline | NetworkPolicy.NR));
    }
    @Test(expected=IllegalStateException.class) public void rejectsUnsupportedNr() { NetworkPolicy.toggle(NetworkPolicy.LTE, NetworkPolicy.LTE); }
    @Test(expected=IllegalStateException.class) public void refusesToRemoveLastDataTechnology() { NetworkPolicy.toggle(NetworkPolicy.NR, NetworkPolicy.NR | NetworkPolicy.LTE); }
    @Test(expected=IllegalStateException.class) public void rejectsUnknownMask() { NetworkPolicy.toggle(0, NetworkPolicy.NR | NetworkPolicy.LTE); }
}
