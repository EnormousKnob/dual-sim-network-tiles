package com.example.dualsimtiles;

import android.content.*;
import android.os.Bundle;

public class AutomationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null || !ExternalActions.BROADCAST.equals(intent.getAction())) return;
        if(!AppSettings.prefs(context).getBoolean("automation",false)
                || !AppSettings.authorized(context,"automation_token",intent.getStringExtra("token"))) { setResultCode(1); return; }
        try {
            Bundle req=ExternalActions.request(intent.getStringExtra("operation"),intent.getIntExtra("slot",0));
            req.putLong("deadline",android.os.SystemClock.elapsedRealtime()+7000);
            PendingResult pending=goAsync();
            java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean();
            android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
            handler.postDelayed(()-> {
                if(finished.compareAndSet(false,true)) { pending.setResultCode(2); pending.setResultData("等待确认超时，请检查系统设置或诊断日志，勿盲目重试"); pending.finish(); }
            },8000);
            RadioClient.request(context,req,b->{
                if(!finished.compareAndSet(false,true)) return;
                pending.setResultCode(b.containsKey("error") ? 1 : 0);
                pending.setResultData(b.getString("error","已读回确认")); pending.finish();
            });
        } catch(Exception e) { setResultCode(1); setResultData("无效操作"); }
    }
}
