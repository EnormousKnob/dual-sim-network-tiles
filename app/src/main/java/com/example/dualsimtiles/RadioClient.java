package com.example.dualsimtiles;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import rikka.shizuku.Shizuku;

/** All callers share one serial queue; connection callbacks never block the UI thread. */
final class RadioClient {
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile IRadioService remote;
    private static CompletableFuture<IRadioService> connecting;
    private static final Shizuku.UserServiceArgs ARGS = new Shizuku.UserServiceArgs(
            new ComponentName("com.example.dualsimtiles", RadioService.class.getName()))
            .daemon(false).processNameSuffix("radio").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE);
    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder binder) {
            remote = IRadioService.Stub.asInterface(binder);
            if (connecting != null) connecting.complete(remote);
        }
        public void onServiceDisconnected(ComponentName n) {
            remote = null;
            if (connecting != null) connecting.completeExceptionally(new IllegalStateException("Shizuku 服务已断开"));
        }
    };
    static String availability() {
        try {
            if (!Shizuku.pingBinder()) return "Shizuku 未运行";
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return "需 Shizuku 授权";
            return null;
        } catch (RuntimeException e) { return "Shizuku 不可用"; }
    }
    private static IRadioService connect() throws Exception {
        CompletableFuture<IRadioService> future = new CompletableFuture<>();
        MAIN.post(() -> {
            try {
                if (remote != null && remote.asBinder().isBinderAlive()) { future.complete(remote); return; }
                connecting = future;
                Shizuku.bindUserService(ARGS, CONNECTION);
            } catch (Throwable e) { future.completeExceptionally(e); }
        });
        return future.get(8, TimeUnit.SECONDS);
    }
    static void execute(Context context, int action, Consumer<Bundle> callback) {
        Context app = context.getApplicationContext();
        if (action != 0) TileRefresh.beginMutation();
        WORK.execute(() -> {
            Bundle result;
            try {
                String unavailable = availability();
                if (unavailable != null) throw new IllegalStateException(unavailable);
                result = connect().execute(action);
            } catch (Throwable e) {
                remote = null;
                result = new Bundle();
                result.putString("error", e instanceof TimeoutException ? "连接 Shizuku 超时，请重试" : String.valueOf(e.getMessage()));
            }
            Bundle value = result;
            MAIN.post(() -> {
                if (action != 0) TileRefresh.afterMutation(app, value);
                callback.accept(value);
            });
        });
    }
}
