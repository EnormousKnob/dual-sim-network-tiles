package com.example.dualsimtiles;
import org.junit.Test;
import static org.junit.Assert.*;
public class NetworkPolicyTest {
    @Test public void explicitActionsAreIdempotentAndPreserveFallback() {
        long original=NetworkPolicy.NR | NetworkPolicy.LTE | 1L;
        assertEquals(original,NetworkPolicy.setNr(original,original,true));
        long four=NetworkPolicy.setNr(original,original,false);
        assertEquals(NetworkPolicy.LTE | 1L,four);
        assertEquals(four,NetworkPolicy.setNr(four,original,false));
    }
    @Test public void preservesLegacyAndLteWhenDisablingNr() {
        long baseline = 1L | (1L<<2) | NetworkPolicy.LTE;
        assertEquals(baseline, NetworkPolicy.toggle(baseline | NetworkPolicy.NR, baseline | NetworkPolicy.NR));
        assertEquals(baseline | NetworkPolicy.NR, NetworkPolicy.toggle(baseline, baseline | NetworkPolicy.NR));
    }
    @Test(expected=IllegalStateException.class) public void rejectsUnsupportedNr() { NetworkPolicy.toggle(NetworkPolicy.LTE, NetworkPolicy.LTE); }
    @Test(expected=IllegalStateException.class) public void refusesToRemoveLastDataTechnology() { NetworkPolicy.toggle(NetworkPolicy.NR, NetworkPolicy.NR | NetworkPolicy.LTE); }
    @Test(expected=IllegalStateException.class) public void rejectsUnknownMask() { NetworkPolicy.toggle(0, NetworkPolicy.NR | NetworkPolicy.LTE); }
}
