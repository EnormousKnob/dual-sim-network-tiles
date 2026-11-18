package com.example.dualsimtiles;

import android.os.*;
import android.telephony.*;
import java.lang.reflect.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

/** Runs only in Shizuku's privileged UserService. */
public class RadioService extends IRadioService.Stub {
    private Object sub,phone;
    private final Reflector reflector=new Reflector();
    private String trace="";
    public RadioService() {}
    private Object service(String name,String type) throws Exception {
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,name);
        if(binder==null) throw new IllegalStateException("系统服务不可用："+name);
        return Class.forName(type+"$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
    }
    private Object call(Object receiver,String name,Object... args) throws Exception { return reflector.call(receiver,name,args); }
    @SuppressWarnings("unchecked") private List<SubscriptionInfo> cards() throws Exception {
        Object result=reflector.variants(sub,"getActiveSubscriptionInfoList",
                new Object[]{"com.android.shell",null,true},new Object[]{"com.android.shell",null});
        List<SubscriptionInfo> out=new ArrayList<>();
        if(result!=null) for(SubscriptionInfo card:(List<SubscriptionInfo>)result)
            if(!card.isOpportunistic() && card.getSimSlotIndex()>=0) out.add(card);
        out.sort(Comparator.comparingInt(SubscriptionInfo::getSimSlotIndex)); return out;
    }
    private int dataId() throws Exception { return ((Number)call(sub,"getDefaultDataSubId")).intValue(); }
    private SubscriptionInfo target(int slot) throws Exception {
        int id=slot==0 ? dataId() : -1;
        for(SubscriptionInfo card:cards())
            if(slot==0 ? card.getSubscriptionId()==id : card.getSimSlotIndex()+1==slot) return card;
        throw new IllegalStateException("目标 SIM 不可用，请检查启用状态");
    }
    private String identity(SubscriptionInfo card) throws Exception {
        String icc=card.getIccId();
        String source=icc==null || icc.isEmpty() ? "sub:"+card.getSubscriptionId()+":"+card.getSimSlotIndex()+":"+card.getCarrierId() : "icc:"+icc;
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
        StringBuilder text=new StringBuilder(); for(byte value:hash) text.append(String.format(Locale.ROOT,"%02x",value&255));
        return text.toString(); // Raw ICCID never leaves this process or enters diagnostics.
    }
    private long mask(int id) throws Exception {
        long value=((Number)reflector.variants(phone,"getAllowedNetworkTypesForReason",
                new Object[]{id,0},new Object[]{id,0,"com.android.shell"})).longValue();
        if(value<=0) throw new IllegalStateException("系统未返回有效网络模式"); return value;
    }
    private long supported(SubscriptionInfo card) throws Exception {
        return ((Number)call(phone,"getRadioAccessFamily",card.getSimSlotIndex(),"com.android.shell")).longValue();
    }
    private void setMask(SubscriptionInfo card,long next) throws Exception {
        int id=card.getSubscriptionId();
        if(next<=0) throw new IllegalArgumentException("无效网络模式");
        NetworkSetter.set(()-> {
            trace+=" Binder写入;";
            Object result=reflector.variants(phone,"setAllowedNetworkTypesForReason",new Object[]{id,0,next},new Object[]{id,0,next,"com.android.shell"});
            trace+=reflector.lastSignature()+";"; return result;
        },()-> {
            trace+=" cmd兼容写入;";
            if(target(card.getSimSlotIndex()+1).getSubscriptionId()!=id) throw new IllegalStateException("SIM 已变化，请重试");
            runNetworkCommand(NetworkSetter.command(card.getSimSlotIndex(),next));
        });
        boolean confirmed=false;
        for(int i=0;i<16;i++) { if(mask(id)==next) { confirmed=true; break; } Thread.sleep(250); }
        if(!confirmed) throw new IllegalStateException("写入后读回不一致，请检查系统设置");
        trace+=" 读回确认;";
    }
    private void runNetworkCommand(String[] command) throws Exception {
        java.lang.Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuffer output=new StringBuffer();
        Thread reader=new Thread(()-> {
            try(Reader stream=new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8)) {
                char[] chunk=new char[512]; int count;
                while((count=stream.read(chunk))!=-1) { int room=4096-output.length(); if(room>0) output.append(chunk,0,Math.min(room,count)); }
            } catch(IOException ignored) { }
        },"network-command-output"); reader.setDaemon(true); reader.start();
        try {
            if(!process.waitFor(6,TimeUnit.SECONDS)) throw new IllegalStateException("系统网络命令超时，请确认设置");
            reader.join(1000); String result=output.toString().trim();
            trace+=" exit="+process.exitValue()+" output="+result+";";
            if(process.exitValue()!=0 || result.toLowerCase(Locale.ROOT).contains("failed"))
                throw new IllegalStateException("系统命令返回 "+process.exitValue()+": "+result);
        } finally { process.destroyForcibly(); }
    }
    private Bundle read(boolean sample) throws Exception {
        Bundle out=new Bundle(); int data=dataId();
        out.putInt("subId",data); out.putString("card","未选择默认数据卡");
        ArrayList<Bundle> list=new ArrayList<>();
        for(SubscriptionInfo card:cards()) {
            Bundle item=new Bundle(); int id=card.getSubscriptionId();
            item.putInt("id",id); item.putInt("slot",card.getSimSlotIndex()+1);
            item.putString("identity",identity(card));
            item.putString("name",String.valueOf(card.getDisplayName()));
            item.putString("card","SIM "+(card.getSimSlotIndex()+1)+" · "+card.getDisplayName());
            try { long value=mask(id); item.putLong("mask",value); item.putBoolean("nr",(value&NetworkPolicy.NR)!=0); item.putBoolean("modeKnown",true); }
            catch(Exception e) { item.putString("modeError",error(e)); }
            try { item.putLong("supported",supported(card)); } catch(Exception e) { item.putString("supportError",error(e)); }
            list.add(item);
            if(id==data) { out.putAll(item); out.putInt("subId",id); }
        }
        out.putInt("count",list.size()); out.putParcelableArrayList("cards",list);
        out.putString("activeCard","活动数据卡未知");
        try {
            int active=((Number)call(sub,"getActiveDataSubscriptionId")).intValue(); out.putInt("activeId",active); out.putBoolean("activeKnown",true);
            out.putString("activeCard","暂无活动数据卡");
            for(Bundle item:list) if(item.getInt("id")==active) out.putString("activeCard",item.getString("card"));
        } catch(Exception ignored) { }
        StringBuilder interfaces=new StringBuilder();
        for(Method method:phone.getClass().getMethods()) if(method.getName().equals("setAllowedNetworkTypesForReason") || method.getName().equals("getAllowedNetworkTypesForReason"))
            interfaces.append(method.getName()).append(Arrays.toString(method.getParameterTypes())).append(';');
        out.putString("interfaces",interfaces.toString());
        try { out.putString("oneui",String.valueOf(Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,"ro.build.version.oneui"))); } catch(Exception ignored) { }
        if(sample && data>=0) signal(out,data);
        return out;
    }
    private void signal(Bundle out,int id) {
        out.putInt("nrDbm",Integer.MAX_VALUE);
        out.putInt("callState",callState());
        try { out.putBoolean("mobileData",(Boolean)call(phone,"isDataEnabled",id)); } catch(Exception ignored) { }
        try {
            SignalStrength signal=(SignalStrength)call(phone,"getSignalStrength",id);
            if(signal==null) throw new IllegalStateException("没有信号读数");
            for(CellSignalStrength cell:signal.getCellSignalStrengths()) if(cell instanceof CellSignalStrengthNr) {
                CellSignalStrengthNr nr=(CellSignalStrengthNr)cell;
                int dbm=nr.getSsRsrp(); if(dbm>=-140 && dbm<=-44) out.putInt("nrDbm",dbm);
            }
            out.putInt("signalLevel",signal.getLevel());
            int type=((Number)call(phone,"getDataNetworkTypeForSubscriber",id,"com.android.shell",null)).intValue();
            out.putInt("dataType",type);
            // LTE anchor + valid NR measurement is not proof of NSA attachment; qualify as an NR measurement only.
            out.putString("signalInfo","NR RSRP: "+(out.getInt("nrDbm")==Integer.MAX_VALUE ? "未知" : out.getInt("nrDbm")+" dBm"));
        } catch(Exception e) { out.putString("signalInfo",error(e)); }
    }
    private int callState() {
        try {
            List<SubscriptionInfo> list=cards(); if(list.isEmpty()) return -1;
            for(SubscriptionInfo card:list) {
                int value=((Number)reflector.variants(phone,"getCallStateForSubscription",
                        new Object[]{card.getSubscriptionId(),"com.android.shell",null},
                        new Object[]{card.getSubscriptionId(),"com.android.shell"})).intValue();
                if(value!=0) return value;
            }
            return 0;
        } catch(Exception e) { return -1; }
    }
    @Override public synchronized Bundle execute(int action) { return request(AppSettings.command(action,0)); }
    @Override public synchronized Bundle request(Bundle req) {
        trace="";
        try {
            if(sub==null) sub=service("isub","com.android.internal.telephony.ISub");
            if(phone==null) phone=service("phone","com.android.internal.telephony.ITelephony");
            int action=req.getInt("action"),slot=req.getInt("slot");
            if(req.containsKey("deadline") && SystemClock.elapsedRealtime()>req.getLong("deadline")) throw new IllegalStateException("自动化请求已过期，未执行");
            if(action==Actions.SWITCH_SIM || action==Actions.SET_SIM) {
                int next;
                if(action==Actions.SWITCH_SIM) {
                    List<SubscriptionInfo> list=cards();
                    if(list.size()!=2) throw new IllegalStateException("需要恰好两张已启用的普通 SIM");
                    int previous=dataId();
                    if(list.stream().noneMatch(c->c.getSubscriptionId()==previous)) throw new IllegalStateException("默认数据卡无效");
                    next=list.get(0).getSubscriptionId()==previous ? list.get(1).getSubscriptionId() : list.get(0).getSubscriptionId();
                } else { if(slot<1) throw new IllegalArgumentException("必须选择 SIM"); next=target(slot).getSubscriptionId(); }
                if(dataId()!=next) call(sub,"setDefaultDataSubId",next); boolean confirmed=false;
                for(int i=0;i<16;i++) { if(dataId()==next) { confirmed=true; break; } Thread.sleep(250); }
                if(!confirmed) throw new IllegalStateException("系统未确认切换数据卡");
                trace="数据卡切换已读回确认;";
            } else if(action==Actions.TOGGLE_NR || action==Actions.SET_NR || action==Actions.SET_MASK) {
                SubscriptionInfo card=target(slot); long current=mask(card.getSubscriptionId());
                if(req.getBoolean("requireIdle") && callState()!=0) throw new IllegalStateException("通话中或通话状态未知，取消自动修改");
                if(req.containsKey("expectedData") && dataId()!=req.getInt("expectedData")) throw new IllegalStateException("默认数据卡已变化，取消本轮降级");
                if(req.containsKey("identity") && !identity(card).equals(req.getString("identity"))) throw new IllegalStateException("SIM身份已变化，取消操作");
                if(req.containsKey("expectedMask") && current!=req.getLong("expectedMask")) throw new IllegalStateException("网络设置已被外部修改，取消自动操作");
                long next;
                if(action==Actions.SET_MASK) next=req.getLong("mask");
                else {
                    boolean enable=action==Actions.TOGGLE_NR ? (current&NetworkPolicy.NR)==0 : req.getBoolean("nr");
                    next=NetworkPolicy.setNr(current,supported(card),enable);
                }
                if(next!=current) setMask(card,next); else trace="目标模式已满足，未重复写入;";
            } else if(action!=Actions.READ && action!=Actions.SAMPLE) throw new IllegalArgumentException("未知操作");
            Bundle result=read(action==Actions.SAMPLE); result.putString("trace",trace); return result;
        } catch(Exception e) {
            Bundle result;
            try { result=read(false); } catch(Exception ignored) { result=new Bundle(); }
            result.putString("error",error(e)); result.putString("trace",trace); return result;
        }
    }
    static String error(Throwable e) { while(e.getCause()!=null) e=e.getCause(); return e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()); }
    @Override public void destroy() { System.exit(0); }
}
