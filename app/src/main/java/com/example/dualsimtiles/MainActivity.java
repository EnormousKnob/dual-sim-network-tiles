package com.example.dualsimtiles;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.*;
import android.view.*;
import android.widget.*;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private TextView status;
    private boolean alive;
    private boolean loading;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::refresh;
    private final Shizuku.OnRequestPermissionResultListener permission = (code, result) -> refresh();
    private final Shizuku.OnBinderReceivedListener received = this::refresh;
    private final Shizuku.OnBinderDeadListener died = () -> runOnUiThread(this::refresh);
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        alive = true;
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(17,24,39));
        scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((v,insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        setContentView(scroll);
        text(root, "双卡网络磁贴", 30);
        text(root, "数据卡与 4G / 5G，放进下拉快捷面板", 17);
        status = text(root, "正在读取…", 18);
        button(root, "1. 授予 Shizuku 权限", () -> {
            try {
                if (!Shizuku.pingBinder()) { message("请先安装并启动 Shizuku，再回到本应用授权。"); return; }
                Shizuku.requestPermission(10);
            } catch (RuntimeException e) { message("授权失败：" + e.getMessage()); }
        });
        button(root, "Shizuku 启动说明", () -> startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://shizuku.rikka.app/zh-hans/guide/setup/"))));
        button(root, "2. 添加「数据卡」磁贴", () -> addTile(DataTile.class, "数据卡", R.drawable.ic_sim_switch));
        button(root, "3. 添加「4G / 5G」磁贴", () -> addTile(NetworkTile.class, "4G / 5G", R.drawable.ic_network));
        button(root, "切换数据卡", () -> operate(1));
        button(root, "切换 4G / 5G", () -> operate(2));
        button(root, "打开系统无线设置", () -> {
            try { startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)); }
            catch (ActivityNotFoundException e) { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
        });
        text(root, "使用说明", 22);
        text(root, "• 需安装并启动 Shizuku。重启手机后通常需要重新启动 Shizuku。\n• 数据卡磁贴显示默认上网 SIM 卡及名称；点击在两张已启用的普通卡间切换。SIM 1 为灰底，SIM 2 为亮底，仅区分所选卡，不代表移动数据关闭。系统自动数据切换可能临时使用另一张卡。\n• 网络磁贴操作当前默认数据卡。亮起表示允许 5G，关闭表示禁用 5G、保留 4G 与原有低代网络回退，不强制 LTE-only。\n• 网络模式不是实时信号制式。允许 5G 不代表已连接 5G。\n• 展开面板时约每 3 秒读取系统状态；切换后读回确认。切换过程中数据连接可能短暂中断。\n• 锁屏操作需解锁。长按磁贴可回到本页。\n• One UI、运营商及企业管理策略可能限制操作；失败时会显示具体错误，请以系统设置为准。", 16);
        button(root, "隐私说明", () -> message("本应用不收集或上传个人信息，不含广告、分析 SDK 或互联网权限。通过 Shizuku 在本机读取 SIM 显示名称、默认数据卡、网络允许类型，并仅在你点击时修改相关设置。不会读取手机号、短信、联系人或位置。"));
        Shizuku.addRequestPermissionResultListener(permission);
        Shizuku.addBinderReceivedListenerSticky(received);
        Shizuku.addBinderDeadListener(died);
    }
    private TextView text(LinearLayout root, String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(Color.WHITE); view.setPadding(0, dp(12), 0, dp(12)); root.addView(view); return view;
    }
    private void button(LinearLayout root, String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false);
        root.addView(button, new LinearLayout.LayoutParams(-1, -2)); button.setOnClickListener(v -> action.run());
    }
    private void message(String text) { new AlertDialog.Builder(this).setMessage(text).setPositiveButton("知道了", null).show(); }
    private void addTile(Class<?> type, String label, int icon) {
        if (Build.VERSION.SDK_INT < 33) { message("请下拉快捷面板，点击编辑按钮，将「" + label + "」拖入面板。"); return; }
        getSystemService(StatusBarManager.class).requestAddTileService(new ComponentName(this, type), label,
                Icon.createWithResource(this, icon), getMainExecutor(), result -> {
                    if (alive) message(result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ? "已添加" :
                            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ? "磁贴已存在" : "未添加。你也可以在快捷面板的编辑页面手动添加。");
                });
    }
    private void operate(int action) {
        if (loading) return;
        loading = true; handler.removeCallbacks(poll); status.setText("正在切换…");
        RadioClient.execute(this, action, b -> {
            loading = false;
            if (!alive) return;
            show(b);
            if (b.containsKey("error")) message(b.getString("error"));
            refreshTiles();
            handler.postDelayed(poll, 3000);
        });
    }
    private void refreshTiles() {
        android.service.quicksettings.TileService.requestListeningState(this, new ComponentName(this, DataTile.class));
        android.service.quicksettings.TileService.requestListeningState(this, new ComponentName(this, NetworkTile.class));
    }
    private void refresh() {
        if (!alive || status == null || loading) return;
        loading = true;
        RadioClient.execute(this, 0, b -> {
            loading = false;
            if (!alive) return;
            show(b); handler.removeCallbacks(poll); handler.postDelayed(poll, 3000);
        });
    }
    private void show(Bundle b) {
        String unavailable = RadioClient.availability();
        if (unavailable != null || b.containsKey("error")) { status.setText(unavailable != null ? unavailable : "读取失败\n" + b.getString("error")); return; }
        status.setText("默认数据卡：" + b.getString("card") + "\n活动数据卡：" + b.getString("activeCard") + "\n已启用卡数：" + b.getInt("count") + "\n默认数据卡网络模式：" +
                (b.getBoolean("modeKnown") ? b.getBoolean("nr") ? "5G 自动" : "4G 优先（禁用 5G）" : b.getString("modeError")));
    }
    @Override protected void onResume() { super.onResume(); alive = true; refresh(); }
    @Override protected void onPause() { alive = false; handler.removeCallbacks(poll); super.onPause(); }
    @Override protected void onDestroy() {
        alive = false; handler.removeCallbacksAndMessages(null);
        Shizuku.removeRequestPermissionResultListener(permission);
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(died);
        super.onDestroy();
    }
}
