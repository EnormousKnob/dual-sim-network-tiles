package com.example.dualsimtiles;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import java.util.*;

final class ExternalActions {
    static final String BROADCAST="com.example.dualsimtiles.SET_NETWORK";
    static Bundle request(String operation,int slot) {
        if(slot<0 || slot>2) throw new IllegalArgumentException("slot 只能为 0、1、2");
        Bundle b;
        switch(operation==null ? "" : operation) {
            case "sim1": b=AppSettings.command(Actions.SET_SIM,1); break;
            case "sim2": b=AppSettings.command(Actions.SET_SIM,2); break;
            case "4g": b=AppSettings.modeCommand(slot,false); break;
            case "5g": b=AppSettings.modeCommand(slot,true); break;
            case "restore": b=AppSettings.command(Actions.RESTORE,0); break;
            default: throw new IllegalArgumentException("不支持的操作");
        }
        b.putString("origin","automation"); return b;
    }
    static String title(String op) {
        switch(op) { case "sim1": return "切到 SIM 1"; case "sim2": return "切到 SIM 2";
            case "4g": return "设为 4G 优先"; case "5g": return "设为 5G 自动"; default: return "恢复原始设置"; }
    }
    private static ShortcutInfo info(Context c,String operation) {
        Intent intent=new Intent(c,ShortcutActivity.class).setAction(Intent.ACTION_VIEW)
                .putExtra("operation",operation).putExtra("token",AppSettings.token(c,"shortcut_token"));
        int icon=operation.equals("sim1") ? R.drawable.ic_sim_1 : operation.equals("sim2") ? R.drawable.ic_sim_2
                : operation.equals("4g") ? R.drawable.ic_network_4g : operation.equals("5g") ? R.drawable.ic_network_5g : R.drawable.ic_sim_switch;
        return new ShortcutInfo.Builder(c,"action_"+operation).setShortLabel(title(operation))
                .setIcon(Icon.createWithResource(c,icon)).setIntent(intent).build();
    }
    static void publish(Context c) {
        ShortcutManager manager=c.getSystemService(ShortcutManager.class);
        List<ShortcutInfo> list=new ArrayList<>();
        for(String op:new String[]{"sim1","sim2","4g","5g"}) list.add(info(c,op));
        try { manager.setDynamicShortcuts(list.subList(0,Math.min(list.size(),manager.getMaxShortcutCountPerActivity()))); }
        catch(RuntimeException e) { Diagnostics.record(c,"快捷方式发布失败："+e.getClass().getSimpleName()); }
    }
    static boolean pin(Context c,String operation) {
        ShortcutManager manager=c.getSystemService(ShortcutManager.class);
        return manager.isRequestPinShortcutSupported() && manager.requestPinShortcut(info(c,operation),null);
    }
}
