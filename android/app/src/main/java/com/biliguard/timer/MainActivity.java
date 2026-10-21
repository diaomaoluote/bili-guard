package com.biliguard.timer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 主界面：剩余时间 + 今日统计 + 权限引导 + 设置入口。 */
public class MainActivity extends Activity {

    private Config cfg;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView bigView;
    private TextView statusView;
    private TextView sessionView;
    private TextView dailyView;
    private TextView limitView;
    private TextView stateView;
    private ProgressBar bar;
    private LinearLayout permissionBox;

    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 500L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cfg = new Config(this);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissions();
        maybeStartService();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }

    // ---------------- 界面 ----------------

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 顶部标题
        LinearLayout head = Ui.row(this);
        TextView dot = Ui.text(this, "●", 13f, Ui.ACCENT, false);
        head.addView(dot);
        TextView title = Ui.text(this, "B站时间管家", 18f, Ui.TEXT, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.leftMargin = Ui.dp(this, 8);
        head.addView(title, titleParams);
        stateView = Ui.text(this, "", 12f, Ui.MUTED, false);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stateParams.weight = 1;
        stateParams.leftMargin = Ui.dp(this, 8);
        head.addView(stateView, stateParams);
        root.addView(head);

        // 大数字
        bigView = Ui.text(this, "--:--", 56f, Ui.TEXT, true);
        LinearLayout.LayoutParams bigParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bigParams.topMargin = Ui.dp(this, 22);
        root.addView(bigView, bigParams);

        // 进度条
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 6));
        barParams.topMargin = Ui.dp(this, 14);
        root.addView(bar, barParams);

        // 本次 / 今日
        LinearLayout stats = Ui.row(this);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsParams.topMargin = Ui.dp(this, 12);
        sessionView = Ui.text(this, "本次 00:00", 13f, Ui.MUTED, false);
        stats.addView(sessionView);
        View spacer = new View(this);
        stats.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        dailyView = Ui.text(this, "今日 00:00", 13f, Ui.MUTED, false);
        stats.addView(dailyView);
        root.addView(stats, statsParams);

        // 状态行
        statusView = Ui.text(this, "启动中…", 13f, Ui.MUTED, false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = Ui.dp(this, 10);
        root.addView(statusView, statusParams);

        // 权限引导
        permissionBox = Ui.column(this);
        LinearLayout.LayoutParams permParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        permParams.topMargin = Ui.dp(this, 16);
        root.addView(permissionBox, permParams);

        // 底部说明与按钮
        limitView = Ui.text(this, "", 11f, Ui.MUTED, false);
        LinearLayout.LayoutParams limitParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        limitParams.topMargin = Ui.dp(this, 18);
        root.addView(limitView, limitParams);

        LinearLayout actions = Ui.row(this);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = Ui.dp(this, 14);
        Button settingsButton = actionButton("设置");
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });
        Button statsButton = actionButton("记录");
        statsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStats();
            }
        });
        Button quitButton = actionButton("退出");
        quitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmQuit();
            }
        });
        actions.addView(settingsButton);
        actions.addView(statsButton);
        actions.addView(quitButton);
        root.addView(actions, actionParams);

        return scroll;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13f);
        button.setTextColor(Ui.TEXT);
        button.setAllCaps(false);
        button.setBackground(rounded(Ui.CARD, 8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.rightMargin = Ui.dp(this, 8);
        button.setLayoutParams(params);
        return button;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(Ui.dp(this, radiusDp));
        return shape;
    }

    // ---------------- 刷数据 ----------------

    private void render() {
        TimerService.Snapshot snap = TimerService.SNAP;
        if (!snap.running) {
            bigView.setText("--:--");
            statusView.setText("还没开始计时（检查下面的权限）");
            statusView.setTextColor(Ui.MUTED);
            stateView.setText("未运行");
            stateView.setTextColor(Ui.DANGER);
            bar.setProgress(0);
            return;
        }

        long limit = snap.limit > 0 ? snap.limit : 1;
        double remaining = Math.max(0, limit - snap.session);
        bigView.setText(Ui.duration(remaining));
        if (remaining <= 60) {
            bigView.setTextColor(Ui.DANGER);
        } else if (remaining <= 300) {
            bigView.setTextColor(Ui.WARN);
        } else {
            bigView.setTextColor(Ui.TEXT);
        }
        bar.setProgress((int) Math.min(1000, snap.session / limit * 1000));

        sessionView.setText("本次 " + Ui.duration(snap.session));
        dailyView.setText(snap.dailyLimit > 0
                ? "今日 " + Ui.duration(snap.daily) + " / " + Ui.duration(snap.dailyLimit)
                : "今日 " + Ui.duration(snap.daily));

        if (snap.blocked) {
            statusView.setText("禁用时段 " + cfg.blockWindowLabel() + " · 打开 B 站就会被关");
            statusView.setTextColor(Ui.WARN);
            stateView.setText("禁用时段");
            stateView.setTextColor(Ui.WARN);
        } else if (snap.cooldown > 0) {
            statusView.setText("冷静期中 · 还有 " + Ui.duration(snap.cooldown / 1000.0));
            statusView.setTextColor(Ui.WARN);
            stateView.setText("冷静期");
            stateView.setTextColor(Ui.WARN);
        } else if (snap.browsing) {
            statusView.setText("正在刷 B 站 · 已连续 " + Ui.duration(snap.session));
            statusView.setTextColor(Ui.ACCENT);
            stateView.setText("监测中");
            stateView.setTextColor(Ui.MUTED);
        } else {
            statusView.setText("没在刷 B 站");
            statusView.setTextColor(Ui.MUTED);
            stateView.setText("监测中");
            stateView.setTextColor(Ui.MUTED);
        }

        String blockLine = cfg.blockWindowEnabledNow()
                ? " · 禁用时段 " + cfg.blockWindowLabel()
                : "";
        limitView.setText("单次上限 " + cfg.sessionMinutes() + " 分钟 · 今日拦下 "
                + snap.closes + " 次" + blockLine);
    }

    // ---------------- 权限 ----------------

    private void refreshPermissions() {
        permissionBox.removeAllViews();
        boolean needUsage = !UsageWatcher.hasUsageAccess(this);
        boolean needOverlay = !OverlayController.canOverlay(this);
        boolean needNotify = Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission();
        boolean needBattery = !isIgnoringBattery();

        if (!needUsage && !needOverlay && !needNotify && !needBattery) {
            return;
        }

        if (needUsage) {
            addPermissionRow("① 使用情况访问", "用来判断当前是不是在看 B 站，必须开", "去开启",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                        }
                    });
        }
        if (needOverlay) {
            addPermissionRow("② 悬浮窗", "到点后盖一层全屏提醒，必须开", "去开启",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        }
                    });
        }
        if (needNotify) {
            addPermissionRow("③ 通知", "常驻显示剩余时间，建议开", "去开启",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
                        }
                    });
        }
        if (needBattery) {
            addPermissionRow("④ 电池优化白名单", "不然锁屏一会儿后台可能被冻结", "去设置",
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                Intent intent = new Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            } catch (Exception e) {
                                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                            }
                        }
                    });
        }
    }

    private void addPermissionRow(String title, String detail, String action,
                                  View.OnClickListener listener) {
        LinearLayout card = Ui.column(this);
        card.setBackground(rounded(Ui.CARD, 10));
        int pad = Ui.dp(this, 12);
        card.setPadding(pad, pad, pad, pad);

        TextView titleView = Ui.text(this, title, 13f, Ui.TEXT, true);
        card.addView(titleView);
        TextView detailView = Ui.text(this, detail, 11f, Ui.MUTED, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = Ui.dp(this, 4);
        card.addView(detailView, detailParams);

        Button button = new Button(this);
        button.setText(action);
        button.setAllCaps(false);
        button.setTextSize(12f);
        button.setTextColor(Ui.ACCENT);
        button.setBackground(rounded(Ui.BG, 8));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = Ui.dp(this, 8);
        card.addView(button, buttonParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = Ui.dp(this, 8);
        permissionBox.addView(card, cardParams);
    }

    private boolean hasNotificationPermission() {
        return checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean isIgnoringBattery() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Exception e) {
            return true;
        }
    }

    private void maybeStartService() {
        if (!UsageWatcher.hasUsageAccess(this)) {
            return;
        }
        if (!TimerService.SNAP.running) {
            try {
                TimerService.start(this);
            } catch (Exception e) {
                Toast.makeText(this, "启动计时服务失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------------- 按钮 ----------------

    private void openSettings() {
        new SettingsDialog(this, cfg, new SettingsDialog.Listener() {
            @Override
            public void onSaved() {
                Toast.makeText(MainActivity.this, "设置已保存，立即生效", Toast.LENGTH_SHORT).show();
                render();
            }
        }).show();
    }

    private void showStats() {
        String text = "今日已刷：" + Ui.duration(TimerService.SNAP.daily)
                + "\n今日被拦下：" + TimerService.SNAP.closes + " 次"
                + "\n累计已刷：" + Ui.duration(cfg.totalSeconds())
                + "\n\n单次上限：" + cfg.sessionMinutes() + " 分钟"
                + "\n每日上限：" + (cfg.dailyMinutes() == 0 ? "不限" : cfg.dailyMinutes() + " 分钟")
                + "\n禁用时段：" + (cfg.blockWindowEnabledNow() ? cfg.blockWindowLabel() : "未开启");

        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle("使用记录")
                .setMessage(text)
                .setPositiveButton("好", null)
                .show();
    }

    private void confirmQuit() {
        new AlertDialog.Builder(this, R.style.Dialog)
                .setTitle("退出")
                .setMessage("退出后就不再帮你计时了，确定吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        TimerService.stop(MainActivity.this);
                        finish();
                    }
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshPermissions();
    }
}
