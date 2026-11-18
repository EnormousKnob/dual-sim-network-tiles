package com.example.dualsimtiles;

import android.app.*;
import android.os.*;
import android.widget.*;

public class ShortcutActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(state!=null || !AppSettings.authorized(this,"shortcut_token",getIntent().getStringExtra("token"))) { finish(); return; }
        TextView label=new TextView(this); label.setPadding(40,40,40,40); label.setText("正在执行快捷操作…"); setContentView(label);
        KeyguardManager keyguard=getSystemService(KeyguardManager.class);
        if(keyguard.isKeyguardLocked()) keyguard.requestDismissKeyguard(this,new KeyguardManager.KeyguardDismissCallback() {
            @Override public void onDismissSucceeded() { run(); }
            @Override public void onDismissCancelled() { finish(); }
            @Override public void onDismissError() { finish(); }
        }); else run();
    }
    private void run() {
        try {
            Bundle req=ExternalActions.request(getIntent().getStringExtra("operation"),getIntent().getIntExtra("slot",0));
            RadioClient.request(this,req,result->{
                Toast.makeText(getApplicationContext(),result.getString("error","操作已读回确认"),Toast.LENGTH_LONG).show(); finish();
            });
        } catch(Exception e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); finish(); }
    }
}
