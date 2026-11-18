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
    private static final ExecutorService CALLS = Executors.newCachedThreadPool();
    private static volatile boolean uncertain;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile IRadioService remote;
    private static CompletableFuture<IRadioService> connecting;
    private static final Shizuku.UserServiceArgs ARGS = new Shizuku.UserServiceArgs(
            new ComponentName("com.example.dualsimtiles", RadioService.class.getName()))
            .daemon(false).processNameSuffix("radio").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE);
    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        public void onServiceConnected(ComponentName n, IBinder binder) {
            uncertain=false;
            remote = IRadioService.Stub.asInterface(binder);
            if (connecting != null) connecting.complete(remote);
        }
        public void onServiceDisconnected(ComponentName n) {
            remote = null;
            uncertain=false;
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
        Bundle req=AppSettings.command(action,action==Actions.TOGGLE_NR ? AppSettings.target(context) : 0);
        request(context,req,callback);
    }
    static void request(Context context, Bundle input, Consumer<Bundle> callback) {
        Context app = context.getApplicationContext();
        Bundle req=new Bundle(input); int action=req.getInt("action");
        boolean mutation=Actions.mutates(action);
        if (mutation) TileRefresh.beginMutation();
        if(mutation && !"monitor".equals(req.getString("origin"))) {
            AppSettings.prefs(app).edit().putBoolean("monitor_enabled",false).apply();
            if(!(context instanceof WeakSignalService)) app.stopService(new Intent(app,WeakSignalService.class));
        }
        WORK.execute(() -> {
            long started=SystemClock.elapsedRealtime();
            Bundle result;
            try {
                String unavailable = availability();
                if (unavailable != null) throw new IllegalStateException(unavailable);
                IRadioService service=connect();
                if(req.containsKey("deadline") && SystemClock.elapsedRealtime()>req.getLong("deadline"))
                    throw new IllegalStateException("自动化请求已过期，未执行");
                if("monitor".equals(req.getString("origin")) && !req.getBoolean("restoring")
                        && !AppSettings.prefs(app).getBoolean("monitor_enabled",false))
                    throw new IllegalStateException("自动监控已停止，取消排队中的降级");
                if(mutation) {
                    if(!"monitor".equals(req.getString("origin"))) restoreMonitor(app,service);
                    if(action!=Actions.RESTORE && action!=Actions.RESTORE_MONITOR) {
                    Bundle before=checked(rpc(service,AppSettings.command(Actions.READ,0)));
                    java.util.ArrayList<Bundle> cards=before.getParcelableArrayList("cards");
                    BackupStore.rememberDefault(app,before);
                    if(cards!=null) for(Bundle card:cards) if(card.getBoolean("modeKnown")) BackupStore.remember(app,card);
                    }
                }
                if(action==Actions.RESTORE) result=restoreOriginal(app,service);
                else if(action==Actions.RESTORE_MONITOR) result=rpc(service,AppSettings.command(Actions.READ,0));
                else result=rpc(service,req);
                if(!result.containsKey("error") && (action==Actions.SWITCH_SIM || action==Actions.SET_SIM))
                    result=applyProfiles(app,service,result);
                annotate(app,result);
            } catch (Throwable e) {
                if(e instanceof android.os.DeadObjectException) remote = null;
                result = new Bundle();
                result.putString("error", e instanceof TimeoutException ? "连接 Shizuku 超时，请重试" : RadioService.error(e));
            }
            if(mutation || result.containsKey("error")) Diagnostics.record(app,"action="+action+" slot="+req.getInt("slot")
                    +" origin="+req.getString("origin","user")+" "+(SystemClock.elapsedRealtime()-started)+"ms "
                    +(result.containsKey("error") ? result.getString("error") : "成功")+" "+result.getString("trace",""));
            Bundle value = result;
            MAIN.post(() -> {
                if (mutation) TileRefresh.afterMutation(app, value);
                callback.accept(value);
            });
        });
    }
    static Bundle checked(Bundle result) { if(result.containsKey("error")) throw new IllegalStateException(result.getString("error")); return result; }
    private static Bundle rpc(IRadioService service,Bundle req) throws Exception {
        if(uncertain) throw new IllegalStateException("上次电话调用超时、结果未确认。请检查系统设置并重新启动 Shizuku 后重试");
        Future<Bundle> call=CALLS.submit(()->service.request(req));
        try { return call.get(12,TimeUnit.SECONDS); }
        catch(TimeoutException timeout) {
            uncertain=true; call.cancel(true);
            throw new IllegalStateException("电话服务响应超时；不能确认是否已修改。请检查系统设置并重新启动 Shizuku");
        }
    }
    static Bundle findCard(Bundle snapshot,int slot) {
        java.util.ArrayList<Bundle> cards=snapshot.getParcelableArrayList("cards");
        if(cards!=null) for(Bundle card:cards) if(slot==0 ? card.getInt("id")==snapshot.getInt("subId",-1) : card.getInt("slot")==slot) return card;
        return null;
    }
    private static void annotate(Context c,Bundle snapshot) {
        Bundle card=findCard(snapshot,AppSettings.target(c));
        snapshot.putBoolean("networkModeKnown",card!=null && card.getBoolean("modeKnown"));
        if(card!=null) { snapshot.putBoolean("networkNr",card.getBoolean("nr")); snapshot.putString("networkCard",card.getString("card")); snapshot.putInt("networkSlot",card.getInt("slot")); }
    }
    private static Bundle applyProfiles(Context c,IRadioService service,Bundle snapshot) throws Exception {
        int mainSlot=snapshot.getInt("slot");
        for(int slot=1;slot<=2;slot++) {
            Bundle card=findCard(snapshot,slot); if(card==null) continue;
            int mode=slot==mainSlot ? (AppSettings.profiles(c) ? AppSettings.mode(c,slot) : 0) : (AppSettings.secondary(c) ? 1 : 0);
            if(mode!=0) {
                try { BackupStore.remember(c,card); snapshot=checked(rpc(service,AppSettings.modeCommand(slot,mode==2))); }
                catch(Exception e) { snapshot.putString("error","数据卡已切换，但 SIM "+slot+" 网络预设未完成："+e.getMessage()); break; }
            }
        }
        return snapshot;
    }
    private static Bundle restoreOriginal(Context c,IRadioService service) throws Exception {
        Bundle snapshot=checked(rpc(service,AppSettings.command(Actions.READ,0)));
        java.util.ArrayList<Bundle> cards=snapshot.getParcelableArrayList("cards"); boolean found=false;
        if(cards!=null) for(Bundle card:cards) {
            Bundle restore=BackupStore.original(c,card);
            if(restore!=null) { found=true; checked(rpc(service,restore)); }
        }
        String original=BackupStore.defaultIdentity(c);
        if(cards!=null) for(Bundle card:cards) if(original.equals(card.getString("identity"))) {
            found=true; checked(rpc(service,AppSettings.command(Actions.SET_SIM,card.getInt("slot"))));
        }
        if(!found) throw new IllegalStateException("没有与当前 SIM 匹配的原始备份");
        return checked(rpc(service,AppSettings.command(Actions.READ,0)));
    }
    private static void restoreMonitor(Context c,IRadioService service) throws Exception {
        Bundle saved=MonitorSession.load(c); if(saved==null) return;
        Bundle state=checked(rpc(service,AppSettings.command(Actions.READ,0)));
        Bundle card=findCard(state,saved.getInt("slot"));
        RestoreGuard.Decision decision=RestoreGuard.decide(card!=null && saved.getString("identity").equals(card.getString("identity")),
                card!=null && card.getBoolean("modeKnown"),card==null ? -1 : card.getLong("mask"),saved.getLong("mask"),saved.getLong("applied"));
        if(decision==RestoreGuard.Decision.UNAVAILABLE) throw new IllegalStateException("自动降级卡不可用或模式未知，保留恢复记录");
        if(decision==RestoreGuard.Decision.RESTORE) {
            saved.putInt("action",Actions.SET_MASK); saved.putLong("expectedMask",saved.getLong("applied"));
            checked(rpc(service,saved));
        }
        MonitorSession.clear(c); // External changes win; never overwrite an externally selected mode.
    }
}
