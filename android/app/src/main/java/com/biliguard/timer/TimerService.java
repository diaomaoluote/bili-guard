package com.biliguard.timer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Toast;

import java.util.Calendar;

/** 常驻前台服务：每秒看一次前台应用，按规则计时并在到点时关闭 B 站。 */
public class TimerService extends Service {

    public static final String CHANNEL_ID = "bili_guard_timer";
    public static final int NOTIFICATION_ID = 7;

    /** 界面轮询用的快照 */
    public static final Snapshot SNAP = new Snapshot();

    public static class Snapshot {
        public volatile boolean running;
        public volatile boolean browsing;
        public volatile boolean blocked;
        public volatile double session;
        public volatile double daily;
        public volatile long limit;
        public volatile long dailyLimit;
        public volatile long cooldown;
        public volatile int closes;
    }

    private Config cfg;
    private Tracker tracker;
    private UsageWatcher watcher;
    private OverlayController overlay;
    private Handler handler;

    private boolean looping;
    private long lastBlockAt;
    private long lastPersistAt;
    private String lastNotificationText = "";

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!looping) {
                return;
            }
            try {
                tick();
            } catch (Exception ignored) {
                // 单次异常不能让计时停掉
            }
            handler.postDelayed(this, 1000L);
        }
    };

    public static void start(Context context) {
        context.startForegroundService(new Intent(context, TimerService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, TimerService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        cfg = new Config(this);
        tracker = new Tracker(cfg);
        watcher = new UsageWatcher(this);
        overlay = new OverlayController(this);
        handler = new Handler(Looper.getMainLooper());

        tracker.rollover(Config.today());
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("启动中…"));
        SNAP.running = true;
        lastPersistAt = SystemClock.elapsedRealtime();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!looping) {
            looping = true;
            handler.post(loop);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        looping = false;
        handler.removeCallbacks(loop);
        if (tracker != null) {
            tracker.persist();
        }
        if (overlay != null) {
            overlay.hide();
        }
        SNAP.running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------------- 主循环 ----------------

    private void tick() {
        long now = SystemClock.elapsedRealtime();
        tracker.rollover(Config.today());

        int minuteOfDay = minuteOfDay();
        boolean blockedNow = cfg.inBlockedWindow(minuteOfDay);
        boolean browsing = watcher.isBiliForeground();
        int action = tracker.update(now, browsing, minuteOfDay);

        if (action == Tracker.CLOSE) {
            enforce(tracker.closeReason);
            tracker.ackClose(now);
            tracker.persist();
            updateSnapshot(browsing, blockedNow);
            updateNotification(blockedNow);
        } else if (action == Tracker.WARN) {
            notifyWarn(tracker.warnSeconds);
            updateSnapshot(browsing, blockedNow);
            updateNotification(blockedNow);
        } else if (action == Tracker.BLOCK) {
            if (now - lastBlockAt > 2500L) {
                lastBlockAt = now;
                enforce(Tracker.REASON_BLOCKED);
            }
            updateSnapshot(browsing, blockedNow);
        } else {
            updateSnapshot(browsing, blockedNow);
            updateNotification(blockedNow);
        }

        if (now - lastPersistAt > 15000L) {
            lastPersistAt = now;
            tracker.persist();
        }
    }

    private int minuteOfDay() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }

    private void enforce(int reason) {
        String detail;
        String shortText;
        if (reason == Tracker.REASON_DAILY) {
            detail = "今天的额度用完了，明天再来";
            shortText = "今日额度用完";
        } else if (reason == Tracker.REASON_BLOCKED) {
            String window = cfg.blockWindowLabel();
            if (cfg.inBlockedWindow(minuteOfDay())) {
                detail = "现在是禁用时段（" + window + "），这段时间不许看";
                shortText = "禁用时段";
            } else {
                detail = "刚看完，先歇一会儿（冷静期 " + cfg.cooldownMinutes() + " 分钟）";
                shortText = "冷静期";
            }
        } else {
            detail = "已经连续看了 " + cfg.sessionMinutes() + " 分钟";
            shortText = "本次超时";
        }

        if (Config.ACTION_HOME.equals(cfg.closeAction())) {
            goHome();
        }
        vibrate();
        overlay.show("时间到", detail, cfg.overlaySeconds(), null);
        Toast.makeText(this, "已帮你离开 B 站（" + shortText + "）", Toast.LENGTH_SHORT).show();
    }

    private void goHome() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        } catch (Exception ignored) {
            // 个别机型禁止后台拉起桌面，遮罩仍然会挡住
        }
    }

    private void vibrate() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(350L, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Exception ignored) {
            // 没有振动也不影响主流程
        }
    }

    private void notifyWarn(int seconds) {
        int minutes = Math.max(1, Math.round(seconds / 60f));
        Toast.makeText(this, "还剩 " + minutes + " 分钟，到点会自动关闭 B 站", Toast.LENGTH_SHORT).show();
        vibrate();
    }

    // ---------------- 快照与通知 ----------------

    private void updateSnapshot(boolean browsing, boolean blocked) {
        SNAP.browsing = browsing;
        SNAP.blocked = blocked;
        SNAP.session = tracker.sessionSeconds();
        SNAP.daily = tracker.dailySeconds();
        SNAP.limit = tracker.limitSeconds();
        SNAP.dailyLimit = tracker.dailyLimitSeconds();
        SNAP.cooldown = tracker.cooldownLeftMs();
        SNAP.closes = cfg.closes();
    }

    private void updateNotification(boolean blockedNow) {
        double remaining = Math.max(0, tracker.limitSeconds() - tracker.sessionSeconds());
        String text;
        if (blockedNow) {
            text = "禁用时段 " + cfg.blockWindowLabel() + " · 打开就被关";
        } else if (tracker.cooldownLeftMs() > 0) {
            text = "冷静期中 · 还剩 " + Ui.duration(tracker.cooldownLeftMs() / 1000.0);
        } else if (SNAP.browsing) {
            text = "正在刷 B 站 · 还剩 " + Ui.duration(remaining);
        } else {
            text = "今日已刷 " + Ui.duration(tracker.dailySeconds());
        }
        if (text.equals(lastNotificationText)) {
            return;
        }
        lastNotificationText = text;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private void createChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "计时状态", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("常驻显示剩余时间");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("B站时间管家")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pending)
                .build();
    }
}
