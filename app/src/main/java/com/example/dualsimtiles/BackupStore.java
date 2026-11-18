package com.example.dualsimtiles;

import android.content.*;
import android.os.Bundle;
import org.json.*;
import java.util.*;

/** Backups are committed before writes and retained on restoration failure. */
final class BackupStore {
    private static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("radio_backups",0); }
    static synchronized void remember(Context c,Bundle card) throws Exception {
        String identity=card.getString("identity","");
        if(identity.isEmpty() || !card.getBoolean("modeKnown")) throw new IllegalStateException("不能备份当前卡设置，未修改");
        String key="original_"+identity;
        if(prefs(c).contains(key)) return;
        JSONObject data=new JSONObject(); data.put("identity",identity); data.put("id",card.getInt("id"));
        data.put("slot",card.getInt("slot")); data.put("mask",card.getLong("mask"));
        if(!prefs(c).edit().putString(key,data.toString()).commit()) throw new IllegalStateException("原始设置备份失败，未修改");
    }
    static synchronized Bundle original(Context c,Bundle card) throws Exception {
        String identity=card.getString("identity",""); String raw=prefs(c).getString("original_"+identity,"");
        if(raw.isEmpty()) return null;
        JSONObject saved=new JSONObject(raw);
        if(saved.getInt("slot")!=card.getInt("slot") || saved.getInt("id")!=card.getInt("id"))
            throw new IllegalStateException("备份与当前 SIM 不匹配，未恢复");
        Bundle request=AppSettings.command(Actions.SET_MASK,card.getInt("slot"));
        request.putLong("mask",saved.getLong("mask")); request.putString("identity",identity);
        return request;
    }
    static boolean hasAny(Context c) { return !prefs(c).getAll().isEmpty(); }
    static void rememberDefault(Context c,Bundle snapshot) throws Exception {
        if(prefs(c).contains("default_identity")) return;
        ArrayList<Bundle> cards=snapshot.getParcelableArrayList("cards");
        if(cards!=null) for(Bundle card:cards) if(card.getInt("id")==snapshot.getInt("subId",-1)) {
            if(!prefs(c).edit().putString("default_identity",card.getString("identity")).commit())
                throw new IllegalStateException("默认数据卡备份失败");
        }
    }
    static String defaultIdentity(Context c) { return prefs(c).getString("default_identity",""); }
}
