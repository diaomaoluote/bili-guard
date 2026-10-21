package com.biliguard.timer;

import java.util.HashSet;
import java.util.Set;

/**
 * 计时状态机 —— 和电脑版同一套规则，纯逻辑不碰系统：
 * 只在"B 站位于前台"时累计；离开够久清零；到上限关闭；关闭后有冷静期；
 * 处在"禁用时段"内时，一检测到 B 站就直接关闭。
 */
public class Tracker {

    public static final int NONE = 0;
    public static final int WARN = 1;
    public static final int CLOSE = 2;
    public static final int BLOCK = 3;

    public static final int REASON_SESSION = 0;
    public static final int REASON_DAILY = 1;
    public static final int REASON_BLOCKED = 2;

    private static final long MAX_DT_MS = 5000L; // 单次累计上限，防休眠唤醒后跳变

    private final Config cfg;

    private double session;
    private double daily;
    private long lastTick = -1;
    private long lastBrowsing = -1;
    private long cooldownUntil;
    private final Set<Integer> fired = new HashSet<Integer>();

    /** 供界面读取的最近一次结果 */
    public int warnSeconds;
    public int closeReason = REASON_SESSION;
    public long cooldownLeft;

    public Tracker(Config cfg) {
        this.cfg = cfg;
        this.daily = cfg.dailySeconds();
    }

    public double sessionSeconds() {
        return session;
    }

    public double dailySeconds() {
        return daily;
    }

    public long limitSeconds() {
        return cfg.sessionMinutes() * 60L;
    }

    public long dailyLimitSeconds() {
        return cfg.dailyMinutes() * 60L;
    }

    public long cooldownLeftMs() {
        return cooldownLeft;
    }

    public void rollover(String today) {
        if (!today.equals(cfg.stateDate())) {
            cfg.resetDaily(today);
            daily = 0;
            fired.clear();
        }
    }

    public int update(long nowMs, boolean browsing, int minuteOfDay) {
        long dtRaw = lastTick < 0 ? 0 : nowMs - lastTick;
        lastTick = nowMs;
        long dt = Math.max(0L, Math.min(dtRaw, MAX_DT_MS));
        cooldownLeft = Math.max(0L, cooldownUntil - nowMs);

        if (!browsing) {
            if (session > 0 && (lastBrowsing < 0 || nowMs - lastBrowsing >= idleMs())) {
                session = 0;
                fired.clear();
            }
            return NONE;
        }

        // 刚被关过：冷静期内不再重复弹，等冷却结束
        if (cooldownLeft > 0) {
            return BLOCK;
        }

        // 禁用时段：不管已经看了多久，直接关
        if (cfg.inBlockedWindow(minuteOfDay)) {
            closeReason = REASON_BLOCKED;
            return CLOSE;
        }

        if (lastBrowsing >= 0 && nowMs - lastBrowsing >= idleMs()) {
            session = 0;
            fired.clear();
        }

        double add = dt / 1000.0;
        session += add;
        daily += add;
        lastBrowsing = nowMs;

        long limit = limitSeconds();
        if (limit > 0 && session >= limit) {
            closeReason = REASON_SESSION;
            return CLOSE;
        }
        long dailyLimit = dailyLimitSeconds();
        if (dailyLimit > 0 && daily >= dailyLimit) {
            closeReason = REASON_DAILY;
            return CLOSE;
        }

        double remaining = limit - session;
        boolean anyPending = false;
        int smallest = Integer.MAX_VALUE;
        for (int minutes : cfg.warnMinutes()) {
            long at = minutes * 60L;
            if (at <= remaining && at < limit && !fired.contains(minutes)) {
                anyPending = true;
                if (minutes < smallest) {
                    smallest = minutes;
                }
            }
        }
        if (anyPending) {
            for (int minutes : cfg.warnMinutes()) {
                long at = minutes * 60L;
                if (at <= remaining && at < limit) {
                    fired.add(minutes);
                }
            }
            warnSeconds = (int) Math.max(1, remaining);
            return WARN;
        }
        return NONE;
    }

    public void ackClose(long nowMs) {
        session = 0;
        fired.clear();
        lastBrowsing = nowMs;
        cooldownUntil = nowMs + cfg.cooldownMinutes() * 60000L;
        cooldownLeft = cfg.cooldownMinutes() * 60000L;
        cfg.bumpCloses();
    }

    /** 把今日累计写回磁盘（由常驻服务定期调用，避免每秒写入）。 */
    public void persist() {
        cfg.setDailySeconds(daily);
    }

    private long idleMs() {
        return cfg.idleMinutes() * 60000L;
    }
}
