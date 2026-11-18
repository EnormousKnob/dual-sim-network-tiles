package com.example.dualsimtiles;

import android.content.*;
import android.os.Bundle;
import java.security.*;

final class AppSettings {
    static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("settings_v2",Context.MODE_PRIVATE); }
    static int target(Context c) { return prefs(c).getInt("target",0); }
    static boolean profiles(Context c) { return prefs(c).getBoolean("profiles",false); }
    static boolean secondary(Context c) { return prefs(c).getBoolean("secondary4g",false); }
    static int mode(Context c,int slot) { return prefs(c).getInt("mode_"+slot,0); } // 0 unchanged, 1 4G, 2 5G
    static String token(Context c,String key) {
        String value=prefs(c).getString(key,"");
        if(value.isEmpty()) {
            byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
            StringBuilder text=new StringBuilder(); for(byte b:bytes) text.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            value=text.toString(); prefs(c).edit().putString(key,value).commit();
        }
        return value;
    }
    static boolean authorized(Context c,String key,String supplied) {
        return TokenAuth.matches(token(c,key),supplied);
    }
    static Bundle command(int action,int slot) {
        Bundle b=new Bundle(); b.putInt("action",action); b.putInt("slot",slot); return b;
    }
    static Bundle modeCommand(int slot,boolean nr) {
        Bundle b=command(Actions.SET_NR,slot); b.putBoolean("nr",nr); return b;
    }
}
