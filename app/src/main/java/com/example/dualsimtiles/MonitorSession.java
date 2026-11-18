package com.example.dualsimtiles;

import android.content.Context;
import android.os.Bundle;
import org.json.JSONObject;

final class MonitorSession {
    static Bundle load(Context c) throws Exception {
        String raw=AppSettings.prefs(c).getString("monitor_session","");
        if(raw.isEmpty()) return null;
        JSONObject json=new JSONObject(raw); Bundle b=new Bundle();
        b.putInt("slot",json.getInt("slot")); b.putString("identity",json.getString("identity"));
        b.putLong("mask",json.getLong("original")); b.putLong("applied",json.getLong("applied")); b.putLong("since",json.getLong("since"));
        return b;
    }
    static void save(Context c,Bundle card,long applied,long now) throws Exception {
        JSONObject json=new JSONObject(); json.put("slot",card.getInt("slot")); json.put("identity",card.getString("identity"));
        json.put("original",card.getLong("mask")); json.put("applied",applied); json.put("since",now);
        if(!AppSettings.prefs(c).edit().putString("monitor_session",json.toString()).commit())
            throw new IllegalStateException("无法保存自动降级恢复记录");
    }
    static void clear(Context c) { AppSettings.prefs(c).edit().remove("monitor_session").commit(); }
}
