package com.example.dualsimtiles;

import android.content.*;
import android.os.*;
import java.text.SimpleDateFormat;
import java.util.*;

final class Diagnostics {
    static synchronized void record(Context c,String message) {
        String line=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.ROOT).format(new Date())+" "+message;
        String[] existing=c.getSharedPreferences("diagnostics",0).getString("log","").split("\n");
        StringBuilder out=new StringBuilder();
        for(int i=Math.max(0,existing.length-59);i<existing.length;i++) if(!existing[i].isEmpty()) out.append(existing[i]).append('\n');
        out.append(line.length()>1600 ? line.substring(0,1600) : line);
        c.getSharedPreferences("diagnostics",0).edit().putString("log",out.toString()).apply();
    }
    static String report(Context c,Bundle snapshot) {
        StringBuilder b=new StringBuilder("双卡网络磁贴 · 本地诊断\n版本：").append(BuildConfig.VERSION_NAME)
                .append("\n设备：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("\n系统：Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT)
                .append("\n固件：").append(Build.DISPLAY).append("\nShizuku：")
                .append(RadioClient.availability()==null ? "已连接并授权" : RadioClient.availability());
        if(snapshot!=null) {
            b.append("\nOne UI 属性：").append(snapshot.getString("oneui","未知"))
                    .append("\n默认订阅：").append(snapshot.getInt("subId",-1)).append(" / SIM ").append(snapshot.getInt("slot"));
            ArrayList<Bundle> cards=snapshot.getParcelableArrayList("cards");
            if(cards!=null) for(Bundle card:cards) b.append("\nSIM ").append(card.getInt("slot"))
                    .append(" subId=").append(card.getInt("id")).append(" mask=").append(card.getLong("mask",-1))
                    .append(" supported=").append(card.getLong("supported",-1));
            b.append("\n接口：").append(snapshot.getString("interfaces","未知"));
            if(snapshot.containsKey("signalInfo")) b.append("\n信号采样：").append(snapshot.getString("signalInfo"))
                    .append("\n通话状态：").append(snapshot.getInt("callState",-1)).append("（0=空闲；-1=未知）")
                    .append("\n数据制式编号：").append(snapshot.getInt("dataType",0));
        }
        return b.append("\n\n最近操作（最多60条；不包含卡名称、手机号、ICCID或自动化令牌）\n")
                .append(c.getSharedPreferences("diagnostics",0).getString("log","暂无记录")).toString();
    }
}
