package com.example.dualsimtiles;

/** Signal-only policy: unknown measurements and interruptions break the consecutive run. */
final class WeakSignalPolicy {
    private int weakCount;
    private String identity="";
    void reset() { weakCount=0; }
    boolean observe(String sim,int dbm,boolean eligible,int threshold,int required) {
        if(!sim.equals(identity)) { identity=sim; weakCount=0; }
        if(!eligible || dbm < -140 || dbm > -44 || dbm > threshold) { weakCount=0; return false; }
        weakCount++;
        if(weakCount>=required) { weakCount=0; return true; }
        return false;
    }
    static boolean recoveryDue(long now,long since,long cooldown) {
        return now<since || now-since>=cooldown;
    }
}
