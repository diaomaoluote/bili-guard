package com.biliguard.timer;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.PowerManager;
import android.os.Process;

/** 通过"使用情况访问"权限判断当前前台是不是 B 站。 */
public class UsageWatcher {

    /** UsageEvents.Event.MOVE_TO_FOREGROUND / ACTIVITY_RESUMED 都是 1 */
    private static final int EVENT_FOREGROUND = 1;

    private static final String[] BILI_PACKAGES = {
            "tv.danmaku.bili",        // 哔哩哔哩（国内版 / Play 版）
            "tv.danmaku.bilibilihd",  // 哔哩哔哩 HD
            "com.bilibili.app.in",    // 国际版
    };

    private final Context context;
    private final UsageStatsManager usageStats;
    private String lastPackage;

    public UsageWatcher(Context context) {
        this.context = context.getApplicationContext();
        this.usageStats = (UsageStatsManager) this.context.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    /** 有没有拿到"使用情况访问"权限。 */
    public static boolean hasUsageAccess(Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                return false;
            }
            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isBiliPackage(String pkg) {
        if (pkg == null) {
            return false;
        }
        for (String known : BILI_PACKAGES) {
            if (known.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    /** 当前 B 站是否在最前面。 */
    public boolean isBiliForeground() {
        if (!isScreenOn()) {
            lastPackage = null;
            return false;
        }
        String pkg = foregroundPackage(8000L);
        if (pkg == null && lastPackage == null) {
            // 服务刚启动、用户一直停在同一个页面时没有新事件，用最近使用记录兜底
            pkg = mostRecentlyUsedPackage();
        }
        if (pkg != null) {
            lastPackage = pkg;
        }
        return isBiliPackage(lastPackage);
    }

    /** 最近一小时内"最后一次被使用"的应用。 */
    private String mostRecentlyUsedPackage() {
        if (usageStats == null) {
            return null;
        }
        long end = System.currentTimeMillis();
        try {
            java.util.List<android.app.usage.UsageStats> stats =
                    usageStats.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                            end - 3600000L, end);
            if (stats == null) {
                return null;
            }
            String best = null;
            long bestTime = 0;
            for (android.app.usage.UsageStats item : stats) {
                if (item.getLastTimeUsed() > bestTime) {
                    bestTime = item.getLastTimeUsed();
                    best = item.getPackageName();
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isScreenOn() {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isInteractive();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 取最近一段时间内最后一个"切到前台"的应用包名。
     * 没有新事件时返回 null（调用方沿用上一次的结果）。
     */
    private String foregroundPackage(long windowMs) {
        if (usageStats == null) {
            return null;
        }
        long end = System.currentTimeMillis();
        long begin = end - windowMs;
        try {
            UsageEvents events = usageStats.queryEvents(begin, end);
            if (events == null) {
                return null;
            }
            UsageEvents.Event event = new UsageEvents.Event();
            String last = null;
            while (events.getNextEvent(event)) {
                if (event.getEventType() == EVENT_FOREGROUND) {
                    last = event.getPackageName();
                }
            }
            return last;
        } catch (Exception e) {
            return null;
        }
    }
}
