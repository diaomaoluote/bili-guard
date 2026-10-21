package com.biliguard.timer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 到点后的全屏遮罩：盖住 B 站，倒计时结束自动消失。 */
public class OverlayController {

    private final Context context;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View overlay;
    private TextView countdown;
    private Runnable tick;
    private Runnable onFinish;

    public OverlayController(Context context) {
        this.context = context.getApplicationContext();
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
    }

    public static boolean canOverlay(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public boolean isShowing() {
        return overlay != null;
    }

    public void show(String title, String detail, int seconds, Runnable finish) {
        if (!canOverlay(context)) {
            if (finish != null) {
                finish.run();
            }
            return;
        }
        hide();
        this.onFinish = finish;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0B0B0E);
        root.setClickable(true);

        TextView icon = Ui.text(context, "⏰", 44f, Ui.ACCENT, false);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon);

        TextView titleView = Ui.text(context, title, 34f, Ui.ACCENT, true);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Ui.dp(context, 10);
        root.addView(titleView, titleParams);

        TextView detailView = Ui.text(context, detail, 15f, Ui.TEXT, false);
        detailView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = Ui.dp(context, 10);
        root.addView(detailView, detailParams);

        countdown = Ui.text(context, seconds + " 秒后自动消失（点一下立刻消失）", 12f, Ui.MUTED, false);
        countdown.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cdParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cdParams.topMargin = Ui.dp(context, 22);
        root.addView(countdown, cdParams);

        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;

        try {
            windowManager.addView(root, params);
            overlay = root;
        } catch (Exception e) {
            overlay = null;
            if (finish != null) {
                finish.run();
            }
            return;
        }

        final int total = seconds;
        tick = new Runnable() {
            int left = total;

            @Override
            public void run() {
                left--;
                if (left <= 0) {
                    hide();
                    return;
                }
                if (countdown != null) {
                    countdown.setText(left + " 秒后自动消失（点一下立刻消失）");
                }
                handler.postDelayed(this, 1000L);
            }
        };
        handler.postDelayed(tick, 1000L);
    }

    public void hide() {
        if (tick != null) {
            handler.removeCallbacks(tick);
            tick = null;
        }
        if (overlay != null) {
            try {
                windowManager.removeView(overlay);
            } catch (Exception ignored) {
                // 已经移除
            }
            overlay = null;
        }
        countdown = null;
        if (onFinish != null) {
            Runnable finish = onFinish;
            onFinish = null;
            finish.run();
        }
    }
}
