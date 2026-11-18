package com.example.dualsimtiles;

final class RestoreGuard {
    enum Decision { UNAVAILABLE, ALREADY_RESTORED, RESTORE, EXTERNAL_CHANGE }
    static Decision decide(boolean sameSim,boolean known,long current,long original,long applied) {
        if(!sameSim || !known || current<=0 || original<=0 || applied<=0) return Decision.UNAVAILABLE;
        if(current==original) return Decision.ALREADY_RESTORED;
        if(current==applied) return Decision.RESTORE;
        return Decision.EXTERNAL_CHANGE;
    }
}
