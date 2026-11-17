package com.example.dualsimtiles;

import android.os.Bundle;
import android.os.IBinder;
import android.telephony.SubscriptionInfo;
import java.lang.reflect.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Runs only inside Shizuku's shell/root UserService. No raw Binder transaction numbers. */
public class RadioService extends IRadioService.Stub {
    private Object sub, phone;
    public RadioService() {}
    private Object service(String name, String type) throws Exception {
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, name);
        if (binder == null) throw new IllegalStateException("系统服务不可用：" + name);
        return Class.forName(type + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
    }
    private Object call(Object receiver, String name, Object... args) throws Exception {
        for (Method m : receiver.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] types = m.getParameterTypes();
            boolean match = true;
            for (int i=0;i<args.length;i++) {
                Class<?> t = types[i];
                if (t == int.class) t = Integer.class;
                if (t == long.class) t = Long.class;
                if (t == boolean.class) t = Boolean.class;
                if (args[i] != null && !t.isInstance(args[i])) match = false;
            }
            if (match) { m.setAccessible(true); return m.invoke(receiver, args); }
        }
        throw new NoSuchMethodException(name + "/" + args.length);
    }
    @SuppressWarnings("unchecked")
    private List<SubscriptionInfo> cards() throws Exception {
        Object list;
        try { list = call(sub, "getActiveSubscriptionInfoList", "com.android.shell", null, true); }
        catch (NoSuchMethodException e) { list = call(sub, "getActiveSubscriptionInfoList", "com.android.shell", null); }
        List<SubscriptionInfo> cards = new ArrayList<>();
        if (list != null) for (SubscriptionInfo info : (List<SubscriptionInfo>) list)
            if (!info.isOpportunistic() && info.getSimSlotIndex() >= 0) cards.add(info);
        cards.sort(Comparator.comparingInt(SubscriptionInfo::getSimSlotIndex));
        return cards;
    }
    private int dataId() throws Exception { return (Integer) call(sub, "getDefaultDataSubId"); }
    private long mask(int id) throws Exception {
        long value = (Long) call(phone, "getAllowedNetworkTypesForReason", id, 0);
        if (value <= 0) throw new IllegalStateException("系统未返回有效网络模式");
        return value;
    }
    private long supported(int id) throws Exception {
        for (SubscriptionInfo card : cards()) if (card.getSubscriptionId() == id)
            return ((Number) call(phone, "getRadioAccessFamily", card.getSimSlotIndex(), "com.android.shell")).longValue();
        throw new IllegalStateException("数据卡已变更，请重试");
    }
    private void setMask(int id, long next) throws Exception {
        NetworkSetter.set(() -> call(phone, "setAllowedNetworkTypesForReason", id, 0, next), () -> {
            int slot = -1;
            for (SubscriptionInfo card : cards()) if (card.getSubscriptionId() == id) slot = card.getSimSlotIndex();
            if (slot < 0 || dataId() != id) throw new IllegalStateException("数据卡已变更，请重试");
            try { runNetworkCommand(NetworkSetter.command(slot, next)); }
            catch (Exception failure) {
                StringBuilder signatures = new StringBuilder();
                for (Method m : phone.getClass().getMethods())
                    if (m.getName().equals("setAllowedNetworkTypesForReason")) {
                        if (signatures.length() > 0) signatures.append("; ");
                        signatures.append(m.getName()).append(Arrays.toString(m.getParameterTypes()));
                    }
                throw new IllegalStateException("兼容切换失败：" + error(failure) + "\n设备接口：" +
                        (signatures.length() == 0 ? "未提供" : signatures));
            }
        });
    }
    private void runNetworkCommand(String[] command) throws Exception {
        java.lang.Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuffer output = new StringBuffer();
        Thread reader = new Thread(() -> {
            try (Reader stream = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
                char[] chunk = new char[512];
                int count;
                while ((count = stream.read(chunk)) != -1) {
                    int room = 8192 - output.length();
                    if (room > 0) output.append(chunk, 0, Math.min(room, count));
                }
            } catch (IOException ignored) { /* The process may be terminated after a timeout. */ }
        }, "network-command-output");
        reader.setDaemon(true);
        reader.start();
        try {
            if (!process.waitFor(6, TimeUnit.SECONDS))
                throw new IllegalStateException("系统网络命令超时，请在设置中确认当前模式");
            reader.join(1000);
            String result = output.toString().trim();
            if (process.exitValue() != 0 || result.toLowerCase(Locale.ROOT).contains("failed"))
                throw new IllegalStateException("cmd phone 返回 " + process.exitValue() + ": " + result);
            // Even exit code zero is not proof of success. execute() verifies using mask().
        } finally { process.destroyForcibly(); }
    }
    private Bundle read() throws Exception {
        Bundle b = new Bundle();
        List<SubscriptionInfo> cards = cards();
        int id = dataId();
        b.putInt("count", cards.size());
        b.putInt("subId", id);
        SubscriptionInfo current = null;
        for (SubscriptionInfo card : cards) if (card.getSubscriptionId() == id) current = card;
        if (current == null) throw new IllegalStateException("没有可用的默认数据卡，请先在系统设置中选择");
        b.putString("card", "SIM " + (current.getSimSlotIndex()+1) + " · " + current.getDisplayName());
        b.putInt("slot", current.getSimSlotIndex()+1);
        try {
            int activeId = (Integer) call(sub, "getActiveDataSubscriptionId");
            b.putInt("activeId", activeId);
            b.putBoolean("activeKnown", true);
            b.putString("activeCard", "暂无活动数据卡");
            for (SubscriptionInfo card : cards) if (card.getSubscriptionId() == activeId)
                b.putString("activeCard", "SIM " + (card.getSimSlotIndex()+1) + " · " + card.getDisplayName());
        } catch (Exception ignored) { b.putString("activeCard", "活动数据卡未知"); }
        try {
            b.putBoolean("nr", (mask(id) & NetworkPolicy.NR) != 0);
            b.putBoolean("modeKnown", true);
        } catch (Exception e) { b.putString("modeError", error(e)); }
        return b;
    }
    @Override public synchronized Bundle execute(int action) {
        try {
            if (sub == null) sub = service("isub", "com.android.internal.telephony.ISub");
            if (phone == null) phone = service("phone", "com.android.internal.telephony.ITelephony");
            if (action == 1) {
                List<SubscriptionInfo> cards = cards();
                if (cards.size() != 2) throw new IllegalStateException("需要恰好两张已启用的普通 SIM 卡，当前为 " + cards.size());
                int previous = dataId();
                if (cards.stream().noneMatch(c -> c.getSubscriptionId() == previous))
                    throw new IllegalStateException("默认数据卡无效，请先在系统设置中选择");
                int next = cards.get(0).getSubscriptionId() == previous ? cards.get(1).getSubscriptionId() : cards.get(0).getSubscriptionId();
                call(sub, "setDefaultDataSubId", next);
                boolean confirmed = false;
                for (int i=0;i<12;i++) { if (dataId() == next) { confirmed=true; break; } Thread.sleep(250); }
                if (!confirmed) throw new IllegalStateException("系统未确认切换数据卡，请检查系统设置");
            } else if (action == 2) {
                int id = dataId();
                if (id < 0) throw new IllegalStateException("未选择默认数据卡");
                long next = NetworkPolicy.toggle(mask(id), supported(id));
                setMask(id, next);
                boolean confirmed = false;
                for (int i=0;i<12;i++) { if (mask(id) == next) { confirmed=true; break; } Thread.sleep(250); }
                if (!confirmed) throw new IllegalStateException("系统未确认网络模式，请检查移动网络设置");
            } else if (action != 0) throw new IllegalArgumentException("未知操作");
            return read();
        } catch (Throwable e) {
            sub = null; phone = null;
            Bundle b = new Bundle(); b.putString("error", error(e)); return b;
        }
    }
    private static String error(Throwable e) {
        while (e.getCause() != null) e = e.getCause();
        return e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
    }
    @Override public void destroy() { System.exit(0); }
}
