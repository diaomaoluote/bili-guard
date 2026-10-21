package com.biliguard.timer;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 统一的配色、尺寸和文字工具。 */
public final class Ui {

    public static final int BG = 0xFF141419;
    public static final int CARD = 0xFF1D1D25;
    public static final int LINE = 0xFF26262F;
    public static final int TEXT = 0xFFECECF2;
    public static final int MUTED = 0xFF8B8B99;
    public static final int ACCENT = 0xFFFB7299;
    public static final int WARN = 0xFFFFB02E;
    public static final int DANGER = 0xFFFF5D5D;
    public static final int OK = 0xFF43D19E;

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }

    public static TextView text(Context context, String content, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(content);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    public static LinearLayout column(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
    }

    public static LinearLayout row(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        return box;
    }

    /** 秒数 -> "12:34" / "1:02:03" */
    public static String duration(double seconds) {
        long total = Math.max(0L, Math.round(seconds));
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long secs = total % 60;
        if (hours > 0) {
            return String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, secs);
    }

    /** "0 点起的分钟数" -> "23:05" */
    public static String clock(int minuteOfDay) {
        int hour = Math.max(0, Math.min(23, minuteOfDay / 60));
        int minute = Math.max(0, Math.min(59, minuteOfDay % 60));
        return String.format(java.util.Locale.US, "%02d:%02d", hour, minute);
    }
}
