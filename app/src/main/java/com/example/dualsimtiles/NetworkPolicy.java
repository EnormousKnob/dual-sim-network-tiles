package com.example.dualsimtiles;

/** Change only NR: retain legacy radio types for voice service and coverage fallback. */
public final class NetworkPolicy {
    public static final long NR = 1L << 19;
    public static final long LTE = 1L << 12;
    public static final long LTE_CA = 1L << 18;
    public static long toggle(long current, long supported) {
        if (current <= 0 || supported <= 0 || (supported & NR) == 0)
            throw new IllegalStateException("此卡或设备未报告 5G 支持，未修改设置");
        if ((current & NR) != 0) {
            if ((current & (LTE | LTE_CA)) == 0)
                throw new IllegalStateException("当前模式不含 4G，无法安全关闭 5G");
            return current & ~NR;
        }
        return current | NR;
    }
}
