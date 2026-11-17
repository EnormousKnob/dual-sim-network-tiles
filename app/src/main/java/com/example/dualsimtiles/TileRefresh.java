package com.example.dualsimtiles;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.quicksettings.TileService;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Main-thread coordination independent of a TileService's short listening window. */
final class TileRefresh {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Set<Consumer<Bundle>> LISTENERS = new HashSet<>();
    private static final RefreshSequence SEQUENCE = new RefreshSequence();
    private static Bundle confirmed;
    private static long confirmedAt;
    static void add(Consumer<Bundle> listener) { LISTENERS.add(listener); }
    static void remove(Consumer<Bundle> listener) { LISTENERS.remove(listener); }
    static void beginMutation() { SEQUENCE.next(); confirmed = null; }
    static Bundle recent() {
        return confirmed != null && SystemClock.elapsedRealtime() - confirmedAt < 10000
                && RadioClient.availability() == null ? new Bundle(confirmed) : null;
    }
    private static void publish(Context context, Bundle value) {
        if (value.containsKey("error")) { confirmed = null; }
        else { confirmed = new Bundle(value); confirmedAt = SystemClock.elapsedRealtime(); }
        for (Consumer<Bundle> listener : new HashSet<>(LISTENERS)) listener.accept(new Bundle(value));
        TileService.requestListeningState(context, new ComponentName(context, DataTile.class));
        TileService.requestListeningState(context, new ComponentName(context, NetworkTile.class));
    }
    static void afterMutation(Context context, Bundle result) {
        Context app = context.getApplicationContext();
        long token = SEQUENCE.next();
        publish(app, result);
        // A SIM switch may outlive onStopListening. Keep these bounded retries alive.
        for (long delay : RefreshSequence.DELAYS_MS) MAIN.postDelayed(() -> {
            if (!SEQUENCE.isCurrent(token)) return;
            RadioClient.execute(app, 0, value -> {
                if (SEQUENCE.isCurrent(token)) publish(app, value);
            });
        }, delay);
    }
}
