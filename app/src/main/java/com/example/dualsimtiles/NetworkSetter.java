package com.example.dualsimtiles;

/** Only a missing method permits fallback; a permission failure must remain visible. */
final class NetworkSetter {
    interface Direct { Object run() throws Exception; }
    interface Fallback { void run() throws Exception; }
    static void set(Direct direct, Fallback fallback) throws Exception {
        Object accepted;
        try { accepted = direct.run(); }
        catch (NoSuchMethodException missing) { fallback.run(); return; }
        if (Boolean.FALSE.equals(accepted)) throw new IllegalStateException("系统拒绝修改网络模式");
    }
    static String[] command(int slot, long mask) {
        if (slot < 0 || mask <= 0) throw new IllegalArgumentException("无效的 SIM 槽位或网络模式");
        // cmd phone uses zero-based SLOT_ID (not subscription ID) and a binary mask.
        return new String[]{"/system/bin/cmd", "phone", "set-allowed-network-types-for-users",
                "-s", Integer.toString(slot), Long.toBinaryString(mask)};
    }
}
