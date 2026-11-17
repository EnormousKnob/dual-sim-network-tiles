package com.example.dualsimtiles;

/** Invalidate delayed observations when a newer user action starts. */
final class RefreshSequence {
    static final long[] DELAYS_MS = {300, 1000, 2500, 5000, 8000};
    private long revision;
    long next() { return ++revision; }
    boolean isCurrent(long token) { return revision == token; }
}
