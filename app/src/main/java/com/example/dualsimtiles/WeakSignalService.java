package com.example.dualsimtiles;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;

/** Opt-in signal-only monitoring; no Internet probes or boot autostart. */
public class WeakSignalService extends Service {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final WeakSignalPolicy policy=new WeakSignalPolicy();
    static volatile boolean running;
    private final Runnable tick=this::sample;
    private static final String CHANNEL="weak_signal";
    private void notifyStatus(String status) {
        AppSettings.prefs(this).edit().putString("monitor_status",status).apply();
        PendingIntent open=PendingIntent.getActivity(this,41,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,42,new Intent(this,WeakSignalService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_network)
                .setContentTitle("弱 5G 信号自动降级").setContentText(status).setContentIntent(open)
                .setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"停止并恢复",stop).build()).build();
        startForeground(80,n);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"弱网监控",NotificationManager.IMPORTANCE_LOW));
        if(intent!=null && "STOP".equals(intent.getAction())) {
            running=false; handler.removeCallbacks(tick); notifyStatus("正在停止并尝试恢复…");
            RadioClient.request(this,AppSettings.command(Actions.RESTORE_MONITOR,0),b->{
                AppSettings.prefs(this).edit().putString("monitor_status",b.getString("error","已停止，已处理恢复记录")).apply();
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
            });
            return START_NOT_STICKY;
        }
        if(!running) {
            running=true; AppSettings.prefs(this).edit().putBoolean("monitor_enabled",true).apply();
            notifyStatus("等待信号采样；手动操作会暂停自动监控"); handler.post(tick);
        }
        return START_NOT_STICKY;
    }
    private boolean wifiActive() {
        ConnectivityManager cm=getSystemService(ConnectivityManager.class);
        NetworkCapabilities cap=cm.getNetworkCapabilities(cm.getActiveNetwork());
        return cap!=null && cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }
    private void later(String status) {
        if(!running) return;
        notifyStatus(status); handler.removeCallbacks(tick);
        boolean screen=getSystemService(PowerManager.class).isInteractive();
        handler.postDelayed(tick,screen ? 30000 : 90000);
    }
    private void sample() {
        if(!running || !AppSettings.prefs(this).getBoolean("monitor_enabled",false)) return;
        RadioClient.request(this,AppSettings.command(Actions.SAMPLE,0),snapshot->{
            if(!running || !AppSettings.prefs(this).getBoolean("monitor_enabled",false)) return;
            try {
                if(snapshot.containsKey("error")) { policy.reset(); later(snapshot.getString("error")); return; }
                Bundle session=MonitorSession.load(this);
                Bundle card=RadioClient.findCard(snapshot,0);
                boolean safe=snapshot.getInt("callState",-1)==0;
                if(session!=null) {
                    Bundle old=RadioClient.findCard(snapshot,session.getInt("slot"));
                    RestoreGuard.Decision decision=RestoreGuard.decide(old!=null && session.getString("identity").equals(old.getString("identity")),
                            old!=null && old.getBoolean("modeKnown"),old==null ? -1 : old.getLong("mask"),session.getLong("mask"),session.getLong("applied"));
                    if(decision==RestoreGuard.Decision.UNAVAILABLE) { later("恢复卡暂不可用或模式未知，保留记录"); return; }
                    if(decision==RestoreGuard.Decision.ALREADY_RESTORED) { MonitorSession.clear(this); later("原设置已恢复，重新观察"); return; }
                    if(decision==RestoreGuard.Decision.EXTERNAL_CHANGE) {
                        MonitorSession.clear(this); AppSettings.prefs(this).edit().putString("monitor_status","检测到外部修改，已暂停监控").apply(); stopSelf(); return;
                    }
                    long cooldown=AppSettings.prefs(this).getInt("cooldown_minutes",10)*60000L;
                    if(safe && (snapshot.getInt("slot")!=session.getInt("slot") || WeakSignalPolicy.recoveryDue(SystemClock.elapsedRealtime(),session.getLong("since"),cooldown))) {
                        session.putInt("action",Actions.SET_MASK); session.putLong("expectedMask",session.getLong("applied"));
                        session.putString("origin","monitor"); session.putBoolean("restoring",true);
                        session.putBoolean("requireIdle",true);
                        RadioClient.request(this,session,b->{
                            if(!b.containsKey("error")) MonitorSession.clear(this);
                            later(b.getString("error","已试探恢复原设置，继续观察"));
                        });
                    } else later("已禁用 5G，冷却后试探恢复；通话或状态未知时延后");
                    return;
                }
                int threshold=AppSettings.prefs(this).getInt("weak_threshold",-110);
                int required=AppSettings.prefs(this).getInt("weak_samples",3);
                boolean eligible=card!=null && card.getBoolean("modeKnown") && card.getBoolean("nr")
                        && safe && snapshot.getBoolean("mobileData") && !wifiActive();
                int dbm=snapshot.getInt("nrDbm",Integer.MAX_VALUE);
                if(card!=null && policy.observe(card.getString("identity",""),dbm,eligible,threshold,required)) {
                    long next=NetworkPolicy.setNr(card.getLong("mask"),card.getLong("supported"),false);
                    MonitorSession.save(this,card,next,SystemClock.elapsedRealtime());
                    Bundle req=AppSettings.command(Actions.SET_MASK,card.getInt("slot")); req.putLong("mask",next);
                    req.putLong("expectedMask",card.getLong("mask")); req.putString("identity",card.getString("identity")); req.putString("origin","monitor");
                    req.putBoolean("requireIdle",true); req.putInt("expectedData",card.getInt("id"));
                    RadioClient.request(this,req,b->later(b.getString("error","连续弱信号，已降为 4G 优先")));
                } else { if(card==null) policy.reset(); later(!eligible ? "未满足条件（需空闲通话、移动数据、非 Wi-Fi 及已知模式）" : snapshot.getString("signalInfo","NR 信号未知，不自动修改")); }
            } catch(Exception e) { later("已跳过："+e.getMessage()); }
        });
    }
    @Override public void onDestroy() {
        running=false; handler.removeCallbacksAndMessages(null);
        AppSettings.prefs(this).edit().putBoolean("monitor_enabled",false).apply(); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
