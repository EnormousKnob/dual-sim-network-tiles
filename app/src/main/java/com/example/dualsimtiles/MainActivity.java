package com.example.dualsimtiles;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private boolean alive,loading,dark,rendering;
    private int background,cardColor,foreground,muted,accent;
    private Bundle snapshot;
    private TextView authText,simText,networkText,monitorText,reportText;
    private Switch monitorSwitch;
    private LinearLayout[] pages=new LinearLayout[3];
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable poll=this::refresh;
    private final Shizuku.OnRequestPermissionResultListener permission=(code,result)->refresh();
    private final Shizuku.OnBinderReceivedListener received=this::refresh;
    private final Shizuku.OnBinderDeadListener died=()->runOnUiThread(this::refresh);
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        int theme=AppSettings.prefs(this).getInt("theme",0);
        dark=theme>=2 || (theme==0 && (getResources().getConfiguration().uiMode&48)==32);
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state); alive=true;
        background=theme==3 ? Color.BLACK : Color.parseColor(dark ? "#0C1425" : "#F0F4FA");
        cardColor=Color.parseColor(theme==3 ? "#111111" : dark ? "#19253B" : "#FFFFFF");
        foreground=Color.parseColor(dark ? "#F3F7FF" : "#17243D"); muted=Color.parseColor(dark ? "#B0BED5" : "#52647D"); accent=Color.parseColor(dark ? "#80E4E8" : "#006B78");
        LinearLayout frame=new LinearLayout(this); frame.setOrientation(LinearLayout.VERTICAL); frame.setBackgroundColor(background);
        frame.setOnApplyWindowInsetsListener((v,insets)-> {
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets;
        });
        LinearLayout brand=new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL); brand.setPadding(dp(20),dp(16),dp(20),dp(10));
        ImageView logo=new ImageView(this); logo.setImageResource(R.mipmap.ic_launcher); brand.addView(logo,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout titles=new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL); titles.setPadding(dp(14),0,0,0);
        text(titles,"双卡网络磁贴",24,foreground); text(titles,"更顺手地掌控每一张卡  ·  "+BuildConfig.VERSION_NAME,12,muted); brand.addView(titles); frame.addView(brand);
        LinearLayout nav=new LinearLayout(this); nav.setPadding(dp(16),0,dp(16),0);
        String[] tabs={"首页","偏好设置","诊断与帮助"};
        for(int i=0;i<3;i++) { final int index=i; Button b=new Button(this); b.setText(tabs[i]); b.setAllCaps(false); nav.addView(b,new LinearLayout.LayoutParams(0,dp(52),1)); b.setOnClickListener(v->showPage(index)); }
        frame.addView(nav);
        ScrollView scroll=new ScrollView(this); LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16),dp(8),dp(16),dp(24)); scroll.addView(content); frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=0;i<3;i++) { pages[i]=new LinearLayout(this); pages[i].setOrientation(LinearLayout.VERTICAL); content.addView(pages[i]); }
        home(); settings(); help(); showPage(0); setContentView(frame);
        frame.post(()-> {
            WindowInsetsController controller=getWindow().getInsetsController();
            int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            if(controller!=null) controller.setSystemBarsAppearance(dark ? 0 : mask,mask);
        });
        Shizuku.addRequestPermissionResultListener(permission); Shizuku.addBinderReceivedListenerSticky(received); Shizuku.addBinderDeadListener(died);
        ExternalActions.publish(this);
    }
    private void showPage(int index) { for(int i=0;i<pages.length;i++) pages[i].setVisibility(i==index ? View.VISIBLE : View.GONE); }
    private TextView text(LinearLayout parent,String value,int size,int color) {
        TextView label=new TextView(this); label.setText(value); label.setTextSize(size); label.setTextColor(color); label.setPadding(0,dp(5),0,dp(5)); parent.addView(label); return label;
    }
    private LinearLayout card(LinearLayout parent,String title,String subtitle) {
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(14),dp(18),dp(14));
        GradientDrawable shape=new GradientDrawable(); shape.setColor(cardColor); shape.setCornerRadius(dp(22)); box.setBackground(shape);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.setMargins(0,dp(6),0,dp(10)); parent.addView(box,params);
        text(box,title,20,foreground); if(!subtitle.isEmpty()) text(box,subtitle,13,muted); return box;
    }
    private void button(LinearLayout parent,String label,Runnable action) {
        Button button=new Button(this); button.setText(label); button.setAllCaps(false); button.setMinHeight(dp(48));
        parent.addView(button,new LinearLayout.LayoutParams(-1,-2)); button.setOnClickListener(v->{ try { action.run(); } catch(Exception e) { message(e.getMessage()); } });
    }
    private void spinner(LinearLayout parent,String title,String[] values,int selected,java.util.function.IntConsumer onSelect) {
        text(parent,title,14,foreground); Spinner spinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values); spinner.setAdapter(adapter); spinner.setSelection(selected);
        parent.addView(spinner,new LinearLayout.LayoutParams(-1,dp(48)));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> p) {}
            public void onItemSelected(AdapterView<?> p,View v,int position,long id) { onSelect.accept(position); }
        });
    }
    private Switch toggle(LinearLayout parent,String title,String description,boolean checked,java.util.function.Consumer<Boolean> action) {
        Switch value=new Switch(this); value.setText(title); value.setTextSize(16); value.setTextColor(foreground); value.setPadding(0,dp(12),0,dp(6)); value.setChecked(checked); parent.addView(value);
        text(parent,description,13,muted); value.setOnCheckedChangeListener((b,on)->{ if(!rendering) action.accept(on); }); return value;
    }
    private void home() {
        LinearLayout auth=card(pages[0],"连接状态","需要 Shizuku 启动并授权，才能修改网络设置");
        authText=text(auth,"正在检查…",16,accent);
        button(auth,"授权 / 重新连接 Shizuku",()-> {
            if(!Shizuku.pingBinder()) { message("请先在 Shizuku 中启动服务，再返回本应用。"); return; }
            if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED) refresh(); else Shizuku.requestPermission(10);
        });
        LinearLayout sim=card(pages[0],"双卡状态","默认数据卡与系统临时使用的活动卡可能不同");
        simText=text(sim,"正在读取…",16,foreground);
        button(sim,"切换默认数据卡",()->operate(AppSettings.command(Actions.SWITCH_SIM,0)));
        button(sim,"添加「数据卡」快捷磁贴",()->addTile(DataTile.class,"数据卡",R.drawable.ic_sim_switch));
        LinearLayout network=card(pages[0],"网络模式","显示允许使用的网络模式，不等同于实时连接制式");
        networkText=text(network,"正在读取…",16,foreground);
        button(network,"切换 4G / 5G",()->operate(AppSettings.command(Actions.TOGGLE_NR,AppSettings.target(this))));
        button(network,"添加「4G / 5G」快捷磁贴",()->addTile(NetworkTile.class,"4G / 5G",R.drawable.ic_network));
        LinearLayout recovery=card(pages[0],"原始设置与恢复","本版首次修改前自动保存每张卡的设置；恢复失败会保留备份");
        button(recovery,"恢复原始数据卡和网络设置",()->confirm("恢复本版首次修改前保存的设置？这不是出厂重置。SIM 不匹配时会跳过或报错。",()->operate(AppSettings.command(Actions.RESTORE,0))));
        button(recovery,"恢复自动降级前的设置 / 停止监控",()->operate(AppSettings.command(Actions.RESTORE_MONITOR,0)));
        button(recovery,"恢复卡已移除？放弃本次自动恢复任务",()->confirm("仅在旧 SIM 已停用或移除、无法恢复时使用。此操作停止监控并放弃这次自动恢复任务，不会修改系统网络，也不会删除首次修改前的原始备份。",()-> {
            AppSettings.prefs(this).edit().putBoolean("monitor_enabled",false).apply();
            stopService(new Intent(this,WeakSignalService.class)); MonitorSession.clear(this); refresh();
        }));
        LinearLayout monitor=card(pages[0],"弱 5G 信号监控 · 实验功能","默认关闭；仅采集本机信号，不联网测速。手动操作会暂停监控");
        monitorText=text(monitor,"未开启",14,muted);
        button(monitor,"设置与开启监控",()->showPage(1));
        text(pages[0],"已验证基线：S24 Ultra / One UI 8.5 的 0.1.4。0.2.0 新功能仍需真机验证。SIM 1 灰底 / SIM 2 亮底仅表示所选卡，不表示数据关闭。",12,muted);
    }
    private void settings() {
        LinearLayout target=card(pages[1],"网络磁贴操作对象","固定卡模式只修改该卡的网络设置，不会将它设为默认数据卡");
        spinner(target,"目标 SIM",new String[]{"跟随默认数据卡","固定 SIM 1","固定 SIM 2"},AppSettings.target(this),position->{
            if(position!=AppSettings.target(this)) { AppSettings.prefs(this).edit().putInt("target",position).apply(); refresh(); }
        });
        LinearLayout profiles=card(pages[1],"每卡网络偏好","仅在通过本应用切换数据卡后应用预设；不会持续覆盖系统设置");
        toggle(profiles,"切卡后应用新数据卡预设","保持当前模式时不修改该卡的网络设置。",AppSettings.profiles(this),on->AppSettings.prefs(this).edit().putBoolean("profiles",on).apply());
        for(int slot=1;slot<=2;slot++) {
            final int number=slot;
            spinner(profiles,"SIM "+slot+" 作为上网卡时",new String[]{"保持系统当前模式","4G 优先（禁用 5G）","5G 自动（允许 5G）"},AppSettings.mode(this,slot),position->AppSettings.prefs(this).edit().putInt("mode_"+number,position).apply());
        }
        toggle(profiles,"切卡后将非上网卡设为 4G 优先","可选。保留 LTE 和原有低代网络回退，不使用 LTE-only。",AppSettings.secondary(this),on->AppSettings.prefs(this).edit().putBoolean("secondary4g",on).apply());
        LinearLayout shortcuts=card(pages[1],"桌面快捷方式与三星自动化","快捷操作指定明确目标，重复执行不会来回切换。也可长按桌面应用图标查看快捷方式");
        for(String op:new String[]{"sim1","sim2","4g","5g","restore"}) button(shortcuts,"添加："+ExternalActions.title(op),()-> {
            if(!ExternalActions.pin(this,op)) message("当前桌面不支持固定快捷方式。");
        });
        text(shortcuts,"三星「模式和日常程序」可尝试选择启动应用快捷方式；是否提供入口取决于系统版本，尚未验证。Tasker / MacroDroid 可使用下方广播接口。",13,muted);
        LinearLayout automation=card(pages[1],"外部自动化广播","默认关闭。开启后，仅接受带独立授权令牌的明确操作");
        toggle(automation,"允许外部自动化","不要将含令牌的命令公开分享。关闭后所有外部广播立即失效。",AppSettings.prefs(this).getBoolean("automation",false),on->AppSettings.prefs(this).edit().putBoolean("automation",on).apply());
        button(automation,"复制 4G 指令示例（含令牌）",()->copy("adb shell am broadcast -a "+ExternalActions.BROADCAST+" -n "+getPackageName()+"/.AutomationReceiver --es operation 4g --ei slot 0 --es token "+AppSettings.token(this,"automation_token"),true));
        button(automation,"重置自动化令牌",()->confirm("重置后，之前配置的外部自动化命令将失效。",()-> { AppSettings.prefs(this).edit().remove("automation_token").commit(); AppSettings.token(this,"automation_token"); message("令牌已重置"); }));
        LinearLayout weak=card(pages[1],"弱 5G 信号自动降级","仅在移动数据使用中、非 Wi-Fi、通话状态已知且空闲时处理。读不到有效 NR 信号就不降级");
        monitorSwitch=toggle(weak,"启用实验性弱网监控","连续弱信号 → 4G 优先 → 冷却后试探恢复原设置。开启期间显示常驻通知；系统回收后不会强制自启。",false,this::setMonitor);
        int[] thresholds={-100,-105,-110,-115,-120}; String[] thresholdLabels={"−100 dBm","−105 dBm","−110 dBm（默认）","−115 dBm","−120 dBm"};
        int selected=2; for(int i=0;i<thresholds.length;i++) if(thresholds[i]==AppSettings.prefs(this).getInt("weak_threshold",-110)) selected=i;
        spinner(weak,"NR RSRP 不高于此值视为弱信号",thresholdLabels,selected,i->AppSettings.prefs(this).edit().putInt("weak_threshold",thresholds[i]).apply());
        spinner(weak,"连续采样次数",new String[]{"3 次","5 次"},AppSettings.prefs(this).getInt("weak_samples",3)==3 ? 0 : 1,i->AppSettings.prefs(this).edit().putInt("weak_samples",i==0 ? 3 : 5).apply());
        int[] minutes={5,10,15,30}; selected=1; for(int i=0;i<minutes.length;i++) if(minutes[i]==AppSettings.prefs(this).getInt("cooldown_minutes",10)) selected=i;
        spinner(weak,"冷却后试探恢复",new String[]{"5 分钟","10 分钟（默认）","15 分钟","30 分钟"},selected,i->AppSettings.prefs(this).edit().putInt("cooldown_minutes",minutes[i]).apply());
        text(weak,"亮屏约30秒、息屏约90秒采样，受系统调度影响。禁用5G后无法持续测量5G，因此采用定时试探恢复，不宣称已检测到5G信号恢复。不能判断“满格但速度差”。",13,muted);
        LinearLayout appearance=card(pages[1],"外观","");
        spinner(appearance,"主题",new String[]{"跟随系统","浅色","深色","纯黑"},AppSettings.prefs(this).getInt("theme",0),i->{
            if(i!=AppSettings.prefs(this).getInt("theme",0)) { AppSettings.prefs(this).edit().putInt("theme",i).apply(); recreate(); }
        });
    }
    private void help() {
        LinearLayout diag=card(pages[2],"本地诊断","可复制后提交 Issue；不包含卡名称、手机号、ICCID 或授权令牌，不会自动上传");
        button(diag,"复制诊断报告",()->copy(Diagnostics.report(this,snapshot),false));
        button(diag,"读取一次信号（不修改网络）",()-> {
            RadioClient.request(this,AppSettings.command(Actions.SAMPLE,0),b->{
                if(!alive) return;
                snapshot=b; reportText.setText(Diagnostics.report(this,b));
                Diagnostics.record(this,"手动信号采样："+b.getString("signalInfo","未知")+" callState="+b.getInt("callState",-1));
                message(b.getString("error",b.getString("signalInfo","没有可用信号读数"))+"\n双卡通话状态："+b.getInt("callState",-1));
            });
        });
        button(diag,"分享诊断报告",()->startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,Diagnostics.report(this,snapshot)),"分享诊断报告")));
        button(diag,"清空操作日志",()-> { getSharedPreferences("diagnostics",0).edit().clear().apply(); reportText.setText(Diagnostics.report(this,snapshot)); });
        reportText=text(diag,"正在读取…",12,muted); reportText.setTypeface(Typeface.MONOSPACE); reportText.setTextIsSelectable(true);
        button(diag,"Shizuku 启动说明",()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://shizuku.rikka.app/zh-hans/guide/setup/"))));
        LinearLayout guide=card(pages[2],"使用帮助","");
        button(guide,"Shizuku 启动指南",()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://shizuku.rikka.app/zh-hans/guide/setup/"))));
        button(guide,"打开系统无线设置",()->startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)));
        text(guide,"恢复备份从本版首次修改前开始记录，不能恢复升级前未保存的历史设置。备份会保留以便再次恢复；换卡或卸载会影响可用性。\n弱网监控会读取设备提供的 NR 信号；该接口可能被固件限制，未知时不修改网络。\n本应用仍无 INTERNET 权限，不联网测速；新增通知、前台服务及网络状态权限仅用于自愿开启的监控。",14,muted);
    }
    private void setMonitor(boolean on) {
        if(!on) { operate(AppSettings.command(Actions.RESTORE_MONITOR,0)); return; }
        rendering=true; monitorSwitch.setChecked(false); rendering=false;
        confirm("此实验功能会在连续弱5G信号时修改默认数据卡模式，并常驻显示通知。读数未知或通话中不会降级；冷却后试探恢复。是否开启？",()-> {
            if(RadioClient.availability()!=null) { message("请先连接并授权 Shizuku"); return; }
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},80); return;
            }
            startMonitor();
        });
    }
    private void startMonitor() {
        try { startForegroundService(new Intent(this,WeakSignalService.class)); }
        catch(RuntimeException e) { AppSettings.prefs(this).edit().putBoolean("monitor_enabled",false).apply(); message("无法启动监控："+e.getMessage()); }
        handler.postDelayed(this::refresh,500);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==80 && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED) startMonitor();
        else if(request==80) message("未允许通知，监控没有开启。");
    }
    private void addTile(Class<?> type,String label,int icon) {
        if(Build.VERSION.SDK_INT<33) { message("请在下拉快捷面板的编辑页面添加「"+label+"」。"); return; }
        getSystemService(StatusBarManager.class).requestAddTileService(new ComponentName(this,type),label,Icon.createWithResource(this,icon),getMainExecutor(),result->{
            if(alive) message(result==StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ? "已添加" : result==StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ? "磁贴已存在" : "未添加，可在快捷面板编辑页面手动添加。");
        });
    }
    private void operate(Bundle req) {
        if(loading) { message("正在读取或执行操作，请稍候。"); return; }
        loading=true; handler.removeCallbacks(poll); networkText.setText("正在执行并确认…");
        RadioClient.request(this,req,b->{ loading=false; if(!alive) return; show(b); if(b.containsKey("error")) message(b.getString("error")); handler.postDelayed(poll,1000); });
    }
    private void refresh() {
        if(!alive || authText==null || loading) return;
        loading=true;
        RadioClient.execute(this,Actions.READ,b->{ loading=false; if(!alive) return; show(b); handler.removeCallbacks(poll); handler.postDelayed(poll,3000); });
    }
    private void show(Bundle b) {
        snapshot=b;
        String unavailable=RadioClient.availability(); authText.setText(unavailable==null ? "● 已连接 · Shizuku 已授权" : "○ "+unavailable);
        String error=b.getString("error");
        if(error!=null) { simText.setText("状态暂不可用"); networkText.setText(error); }
        else {
            StringBuilder sims=new StringBuilder("默认上网：").append(b.getString("card")).append("\n活动数据：").append(b.getString("activeCard"));
            ArrayList<Bundle> cards=b.getParcelableArrayList("cards");
            if(cards!=null) for(Bundle c:cards) sims.append("\n\n").append(c.getString("name")).append("\n")
                    .append(c.getBoolean("modeKnown") ? c.getBoolean("nr") ? "5G 自动" : "4G 优先" : "模式未知")
                    .append(c.containsKey("supported") ? (c.getLong("supported")&NetworkPolicy.NR)!=0 ? " · 设备报告支持 5G" : " · 未报告 5G 支持" : " · 能力未知");
            simText.setText(sims.toString());
            networkText.setText((AppSettings.target(this)==0 ? "跟随默认数据卡" : "固定 SIM "+AppSettings.target(this))+"\n"+b.getString("networkCard","目标卡不可用")+"\n"+
                    (b.getBoolean("networkModeKnown") ? b.getBoolean("networkNr") ? "5G 自动 · 允许 5G" : "4G 优先 · 禁用 5G" : "网络模式未知"));
        }
        boolean monitor=AppSettings.prefs(this).getBoolean("monitor_enabled",false) && WeakSignalService.running;
        String status=AppSettings.prefs(this).getString("monitor_status","未开启");
        try { if(!monitor && MonitorSession.load(this)!=null) status+="\n有自动降级恢复记录，可使用上方恢复按钮"; } catch(Exception e) { status+="\n恢复记录读取失败"; }
        monitorText.setText((monitor ? "监控中\n" : "已停止\n")+status);
        rendering=true; monitorSwitch.setChecked(monitor); rendering=false;
        reportText.setText(Diagnostics.report(this,snapshot));
    }
    private void copy(String value,boolean sensitive) {
        ClipData clip=ClipData.newPlainText("双卡网络磁贴",value);
        if(sensitive) { PersistableBundle extras=new PersistableBundle(); extras.putBoolean("android.content.extra.IS_SENSITIVE",true); clip.getDescription().setExtras(extras); }
        getSystemService(ClipboardManager.class).setPrimaryClip(clip); Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show();
    }
    private void confirm(String message,Runnable action) { new AlertDialog.Builder(this).setMessage(message).setNegativeButton("取消",null).setPositiveButton("继续",(d,w)->action.run()).show(); }
    private void message(String message) { if(!isFinishing()) new AlertDialog.Builder(this).setMessage(message).setPositiveButton("知道了",null).show(); }
    @Override protected void onResume() { super.onResume(); alive=true; refresh(); }
    @Override protected void onPause() { alive=false; handler.removeCallbacks(poll); super.onPause(); }
    @Override protected void onDestroy() {
        alive=false; handler.removeCallbacksAndMessages(null); Shizuku.removeRequestPermissionResultListener(permission);
        Shizuku.removeBinderReceivedListener(received); Shizuku.removeBinderDeadListener(died); super.onDestroy();
    }
}
