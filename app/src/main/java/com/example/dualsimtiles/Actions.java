package com.example.dualsimtiles;

final class Actions {
    static final int READ=0, SWITCH_SIM=1, TOGGLE_NR=2, SET_SIM=3, SET_NR=4, SET_MASK=5, SAMPLE=6, RESTORE=7, RESTORE_MONITOR=8;
    static boolean mutates(int action) { return action != READ && action != SAMPLE; }
}
