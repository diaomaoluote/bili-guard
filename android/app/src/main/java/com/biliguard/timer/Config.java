package com.biliguard.timer;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 设置 + 今日统计，全部存在 SharedPreferences 里（不联网）。 */
public class Config {

    private static final String PREF = "bili_guard";

    private static final String K_SESSION = "session_minutes";
    private static final String K_DAILY = "daily_minutes";
    private static final String K_IDLE = "idle_minutes";
    private static final String K_COOLDOWN = "cooldown_minutes";
    private static final String K_WARN = "warn_minutes";
    private static final String K_ACTION = "close_action";
    private static final String K_OVERLAY = "overlay_seconds";

    private static final String K_BLOCK_ON = "block_enabled";
    private static final String K_BLOCK_START = "block_start_minute";
    private static final String K_BLOCK_END = "block_end_minute";

    private static final String K_DATE = "state_date";
    private static final String K_DAY_SECONDS = "state_daily_seconds";
    private static final String K_CLOSES = "state_closes";
    private static final String K_TOTAL = "state_total_seconds";

    public static final String ACTION_HOME = "home";       // 回桌面 + 遮罩
    public static final String ACTION_OVERLAY = "overlay"; // 只遮罩

    private final SharedPreferences sp;

    public Config(Context context) {
        this.sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ---------------- 设置 ----------------

    public int sessionMinutes() {
        return clamp(sp.getInt(K_SESSION, 20), 1, 720);
    }

    public void setSessionMinutes(int value) {
        sp.edit().putInt(K_SESSION, clamp(value, 1, 720)).apply();
    }

    public int dailyMinutes() {
        return clamp(sp.getInt(K_DAILY, 0), 0, 1440);
    }

    public void setDailyMinutes(int value) {
        sp.edit().putInt(K_DAILY, clamp(value, 0, 1440)).apply();
    }

    public int idleMinutes() {
        return clamp(sp.getInt(K_IDLE, 3), 1, 120);
    }

    public void setIdleMinutes(int value) {
        sp.edit().putInt(K_IDLE, clamp(value, 1, 120)).apply();
    }

    public int cooldownMinutes() {
        return clamp(sp.getInt(K_COOLDOWN, 5), 0, 180);
    }

    public void setCooldownMinutes(int value) {
        sp.edit().putInt(K_COOLDOWN, clamp(value, 0, 180)).apply();
    }

    public int[] warnMinutes() {
        String raw = sp.getString(K_WARN, "5,2,1");
        String[] parts = raw.split(",");
        int[] values = new int[parts.length];
        int count = 0;
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part.trim());
                if (value > 0) {
                    values[count] = value;
                    count++;
                }
            } catch (NumberFormatException ignored) {
                // 忽略坏值
            }
        }
        int[] result = new int[count];
        System.arraycopy(values, 0, result, 0, count);
        return result;
    }

    public void setWarnMinutes(int[] minutes) {
        StringBuilder builder = new StringBuilder();
        for (int value : minutes) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(value);
        }
        sp.edit().putString(K_WARN, builder.toString()).apply();
    }

    public String closeAction() {
        String value = sp.getString(K_ACTION, ACTION_HOME);
        return ACTION_OVERLAY.equals(value) ? ACTION_OVERLAY : ACTION_HOME;
    }

    public void setCloseAction(String value) {
        sp.edit().putString(K_ACTION, ACTION_OVERLAY.equals(value) ? ACTION_OVERLAY : ACTION_HOME).apply();
    }

    public int overlaySeconds() {
        return clamp(sp.getInt(K_OVERLAY, 8), 3, 30);
    }

    public void setOverlaySeconds(int value) {
        sp.edit().putInt(K_OVERLAY, clamp(value, 3, 30)).apply();
    }

    // ---------------- 禁用时段 ----------------

    public boolean blockEnabled() {
        return sp.getBoolean(K_BLOCK_ON, false);
    }

    public void setBlockEnabled(boolean value) {
        sp.edit().putBoolean(K_BLOCK_ON, value).apply();
    }

    /** 0 点起的分钟数，比如 23:00 = 1380 */
    public int blockStartMinute() {
        return clamp(sp.getInt(K_BLOCK_START, 23 * 60), 0, 1439);
    }

    public void setBlockStartMinute(int value) {
        sp.edit().putInt(K_BLOCK_START, clamp(value, 0, 1439)).apply();
    }

    public int blockEndMinute() {
        return clamp(sp.getInt(K_BLOCK_END, 7 * 60), 0, 1439);
    }

    public void setBlockEndMinute(int value) {
        sp.edit().putInt(K_BLOCK_END, clamp(value, 0, 1439)).apply();
    }

    public boolean blockWindowEnabledNow() {
        return blockEnabled() && blockStartMinute() != blockEndMinute();
    }

    /** 当前时刻是否落在禁用时段内（支持 23:00 → 07:00 这种跨零点）。 */
    public boolean inBlockedWindow(int minuteOfDay) {
        if (!blockWindowEnabledNow()) {
            return false;
        }
        int start = blockStartMinute();
        int end = blockEndMinute();
        if (start < end) {
            return minuteOfDay >= start && minuteOfDay < end;
        }
        return minuteOfDay >= start || minuteOfDay < end;
    }

    public String blockWindowLabel() {
        return Ui.clock(blockStartMinute()) + " – " + Ui.clock(blockEndMinute());
    }

    // ---------------- 今日统计 ----------------

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public String stateDate() {
        return sp.getString(K_DATE, "");
    }

    public double dailySeconds() {
        return sp.getFloat(K_DAY_SECONDS, 0f);
    }

    public void setDailySeconds(double seconds) {
        sp.edit().putFloat(K_DAY_SECONDS, (float) Math.max(0, seconds)).apply();
    }

    public void setTotalSeconds(double seconds) {
        sp.edit().putFloat(K_TOTAL, (float) Math.max(0, seconds)).apply();
    }

    public double totalSeconds() {
        return sp.getFloat(K_TOTAL, 0f);
    }

    public int closes() {
        return sp.getInt(K_CLOSES, 0);
    }

    public void bumpCloses() {
        sp.edit().putInt(K_CLOSES, closes() + 1).apply();
    }

    /** 跨天清零。 */
    public void resetDaily(String date) {
        sp.edit()
                .putString(K_DATE, date)
                .putFloat(K_DAY_SECONDS, 0f)
                .putInt(K_CLOSES, 0)
                .apply();
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
