package com.example.dualsimtiles;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.*;
import android.service.quicksettings.*;
import android.widget.Toast;

public abstract class BaseTile extends TileService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean listening, busy;
    private int generation;
    private final Runnable poll = this::refresh;
    private final java.util.function.Consumer<Bundle> changed = b -> {
        generation++;
        if (listening) {
            render(b);
            handler.removeCallbacks(poll);
            handler.postDelayed(poll, 3000);
        }
    };
    protected abstract int action();
    @Override public void onCreate() { super.onCreate(); TileRefresh.add(changed); }
    @Override public void onStartListening() {
        listening = true; generation++;
        Bundle recent = TileRefresh.recent();
        if (recent != null) render(recent);
        refresh();
    }
    @Override public void onStopListening() { listening = false; generation++; handler.removeCallbacks(poll); }
    @Override public void onDestroy() { TileRefresh.remove(changed); listening = false; generation++; handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private void refresh() {
        if (!listening || busy) return;
        int token = generation;
        RadioClient.execute(this, 0, b -> {
            if (!listening || token != generation) return;
            render(b);
            handler.removeCallbacks(poll);
            handler.postDelayed(poll, 3000);
        });
    }
    @Override public void onClick() {
        if (busy) return;
        if (RadioClient.availability() != null) { openApp(); return; }
        if (isLocked()) unlockAndRun(this::toggle); else toggle();
    }
    private void toggle() {
        if (busy) return;
        busy = true;
        generation++;
        handler.removeCallbacks(poll);
        Tile tile = getQsTile();
        if (tile != null) { tile.setSubtitle("正在切换…"); tile.updateTile(); }
        RadioClient.execute(this, action(), b -> {
            busy = false;
            render(b);
            if (b.containsKey("error")) Toast.makeText(this, b.getString("error"), Toast.LENGTH_LONG).show();
            if (listening) { handler.removeCallbacks(poll); handler.postDelayed(poll, 1000); }
        });
    }
    // Intent overload is required on API 31–33; API 34+ always uses PendingIntent.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34)
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        else startActivityAndCollapse(intent);
    }
    private void render(Bundle b) {
        Tile tile = getQsTile();
        if (tile == null) return;
        String unavailable = RadioClient.availability();
        String error = unavailable != null ? unavailable : b.getString("error");
        if (error != null) {
            tile.setIcon(Icon.createWithResource(this, action() == 1 ? R.drawable.ic_sim_switch : R.drawable.ic_network));
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel(action() == 1 ? "数据卡" : "4G / 5G");
            tile.setSubtitle(unavailable != null ? unavailable : "读取失败 · 长按查看");
        } else if (action() == 1) {
            int slot = b.getInt("slot");
            tile.setIcon(Icon.createWithResource(this, slot == 1 ? R.drawable.ic_sim_1 : slot == 2 ? R.drawable.ic_sim_2 : R.drawable.ic_sim_switch));
            // One UI did not redraw same-state SIM icon updates on the test device,
            // even with fresh bitmaps. Encode selection in state as well as the icon:
            // SIM 1 = neutral, SIM 2 = highlighted. This never disables mobile data.
            tile.setState(slot == 2 ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel("数据卡 · SIM " + b.getInt("slot"));
            tile.setSubtitle(b.getBoolean("activeKnown") && b.getInt("activeId") != b.getInt("subId")
                    ? "当前：" + b.getString("activeCard") : b.getString("card"));
        } else {
            boolean known = b.getBoolean("modeKnown"), nr = b.getBoolean("nr");
            tile.setIcon(Icon.createWithResource(this, !known ? R.drawable.ic_network : nr ? R.drawable.ic_network_5g : R.drawable.ic_network_4g));
            tile.setState(!known ? Tile.STATE_UNAVAILABLE : nr ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setLabel(!known ? "4G / 5G" : nr ? "5G 自动" : "4G 优先");
            tile.setSubtitle(!known ? "模式读取失败" : b.getString("card") + (nr ? " · 允许 5G" : " · 禁用 5G"));
        }
        tile.setContentDescription(tile.getLabel() + "，" + tile.getSubtitle());
        tile.updateTile();
    }
}
