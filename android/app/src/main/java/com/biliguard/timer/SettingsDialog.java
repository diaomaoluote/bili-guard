package com.biliguard.timer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

/** 设置面板：上限、提醒、关闭动作、禁用时段。 */
public class SettingsDialog {

    public interface Listener {
        void onSaved();
    }

    private static final int[] PRESETS = {5, 10, 15, 20, 30, 45, 60, 90};
    private static final int[] WARN_CHOICES = {15, 10, 5, 3, 2, 1};

    private final Activity activity;
    private final Config cfg;
    private final Listener listener;

    private EditText sessionInput;
    private EditText dailyInput;
    private EditText idleInput;
    private EditText cooldownInput;
    private EditText overlayInput;
    private final Map<Integer, CheckBox> warnBoxes = new LinkedHashMap<Integer, CheckBox>();
    private RadioGroup actionGroup;
    private Switch blockSwitch;
    private Button blockStartButton;
    private Button blockEndButton;
    private TextView blockHint;

    private int blockStart;
    private int blockEnd;

    private AlertDialog dialog;

    public SettingsDialog(Activity activity, Config cfg, Listener listener) {
        this.activity = activity;
        this.cfg = cfg;
        this.listener = listener;
        this.blockStart = cfg.blockStartMinute();
        this.blockEnd = cfg.blockEndMinute();
    }

    // ---------------- 打开 ----------------

    public void show() {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = Ui.column(activity);
        int pad = Ui.dp(activity, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 单次上限
        root.addView(section("单次上限"));
        sessionInput = numberInput(cfg.sessionMinutes());
        root.addView(numberRow(sessionInput, "分钟（1～720，随便填）", Ui.dp(activity, 6)));

        LinearLayout presets = Ui.row(activity);
        presets.setPadding(0, Ui.dp(activity, 8), 0, 0);
        for (int preset : PRESETS) {
            Button chip = chip(String.valueOf(preset));
            final int value = preset;
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sessionInput.setText(String.valueOf(value));
                }
            });
            presets.addView(chip);
        }
        root.addView(presets);

        // 其它数值
        root.addView(divider());

        dailyInput = numberInput(cfg.dailyMinutes());
        root.addView(labeledRow("每日上限", dailyInput, "分钟（0 = 不限）"));

        idleInput = numberInput(cfg.idleMinutes());
        root.addView(labeledRow("离开重置", idleInput, "分钟后再看算新一轮"));

        cooldownInput = numberInput(cfg.cooldownMinutes());
        root.addView(labeledRow("冷静期", cooldownInput, "分钟内重开会立刻关"));

        // 提醒
        root.addView(divider());
        root.addView(section("提前提醒"));
        LinearLayout warnRow = Ui.row(activity);
        warnRow.setPadding(0, Ui.dp(activity, 6), 0, 0);
        for (int minutes : WARN_CHOICES) {
            CheckBox box = new CheckBox(activity);
            box.setText(minutes + " 分钟");
            box.setTextSize(12f);
            box.setTextColor(Ui.TEXT);
            box.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
            box.setChecked(contains(cfg.warnMinutes(), minutes));
            box.setPadding(0, 0, Ui.dp(activity, 6), 0);
            warnBoxes.put(minutes, box);
            warnRow.addView(box);
        }
        root.addView(warnRow);

        // 到点动作
        root.addView(divider());
        root.addView(section("到点动作"));
        actionGroup = new RadioGroup(activity);
        actionGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton home = radio("回桌面并遮挡（推荐）", Config.ACTION_HOME);
        RadioButton overlayOnly = radio("只遮挡，不切桌面", Config.ACTION_OVERLAY);
        actionGroup.addView(home);
        actionGroup.addView(overlayOnly);
        actionGroup.check(Config.ACTION_OVERLAY.equals(cfg.closeAction())
                ? overlayOnly.getId() : home.getId());
        root.addView(actionGroup);

        overlayInput = numberInput(cfg.overlaySeconds());
        root.addView(labeledRow("遮挡时长", overlayInput, "秒（3～30，点一下可提前消失）"));

        // 禁用时段
        root.addView(divider());
        root.addView(section("禁用时段"));
        blockSwitch = new Switch(activity);
        blockSwitch.setText("这段时间内打开 B 站立刻关闭");
        blockSwitch.setTextSize(13f);
        blockSwitch.setTextColor(Ui.TEXT);
        blockSwitch.setChecked(cfg.blockEnabled());
        blockSwitch.setPadding(0, Ui.dp(activity, 4), 0, 0);
        blockSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                refreshBlockRows();
            }
        });
        root.addView(blockSwitch);

        LinearLayout blockRow = Ui.row(activity);
        blockRow.setPadding(0, Ui.dp(activity, 8), 0, 0);
        blockStartButton = timeButton();
        blockEndButton = timeButton();
        TextView dash = Ui.text(activity, " 到 ", 13f, Ui.MUTED, false);
        blockRow.addView(blockStartButton);
        blockRow.addView(dash);
        blockRow.addView(blockEndButton);
        root.addView(blockRow);

        blockHint = Ui.text(activity, "", 11f, Ui.MUTED, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = Ui.dp(activity, 6);
        root.addView(blockHint, hintParams);

        refreshBlockRows();

        // 对话框
        dialog = new AlertDialog.Builder(activity, R.style.Dialog)
                .setTitle("设置")
                .setView(scroll)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (save()) {
                            dialog.dismiss();
                        }
                    }
                });
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        restoreDefaults();
                    }
                });
            }
        });
        dialog.show();
    }

    // ---------------- 组件 ----------------

    private TextView section(String text) {
        TextView view = Ui.text(activity, text, 13f, Ui.MUTED, true);
        view.setPadding(0, Ui.dp(activity, 10), 0, Ui.dp(activity, 4));
        return view;
    }

    private View divider() {
        View line = new View(activity);
        line.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(activity, 1)));
        params.topMargin = Ui.dp(activity, 12);
        params.bottomMargin = Ui.dp(activity, 4);
        line.setLayoutParams(params);
        return line;
    }

    private EditText numberInput(int value) {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(value));
        input.setTextSize(16f);
        input.setTextColor(Ui.TEXT);
        input.setGravity(Gravity.CENTER);
        input.setBackgroundColor(Ui.CARD);
        input.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 6), Ui.dp(activity, 8), Ui.dp(activity, 6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Ui.dp(activity, 86), ViewGroup.LayoutParams.WRAP_CONTENT);
        input.setLayoutParams(params);
        return input;
    }

    private View labeledRow(String label, EditText input, String suffix) {
        LinearLayout row = Ui.row(activity);
        row.setPadding(0, Ui.dp(activity, 8), 0, 0);
        TextView labelView = Ui.text(activity, label, 13f, Ui.TEXT, false);
        labelView.setWidth(Ui.dp(activity, 72));
        row.addView(labelView);
        row.addView(input);
        TextView suffixView = Ui.text(activity, "  " + suffix, 11f, Ui.MUTED, false);
        row.addView(suffixView);
        return row;
    }

    private View numberRow(EditText input, String suffix, int topMargin) {
        LinearLayout row = Ui.row(activity);
        row.setPadding(0, topMargin, 0, 0);
        row.addView(input);
        row.addView(Ui.text(activity, "  " + suffix, 11f, Ui.MUTED, false));
        return row;
    }

    private Button chip(String text) {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(12f);
        button.setTextColor(Ui.TEXT);
        button.setAllCaps(false);
        button.setPadding(Ui.dp(activity, 6), 0, Ui.dp(activity, 6), 0);
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(Ui.CARD);
        shape.setCornerRadius(Ui.dp(activity, 8));
        button.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(activity, 34));
        params.rightMargin = Ui.dp(activity, 6);
        button.setLayoutParams(params);
        return button;
    }

    private RadioButton radio(String text, String value) {
        RadioButton button = new RadioButton(activity);
        button.setText(text);
        button.setTextSize(13f);
        button.setTextColor(Ui.TEXT);
        button.setId(View.generateViewId());
        button.setTag(value);
        button.setPadding(0, Ui.dp(activity, 4), 0, Ui.dp(activity, 4));
        return button;
    }

    private Button timeButton() {
        Button button = new Button(activity);
        button.setTextSize(14f);
        button.setTextColor(Ui.TEXT);
        button.setAllCaps(false);
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(Ui.CARD);
        shape.setCornerRadius(Ui.dp(activity, 8));
        button.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Ui.dp(activity, 92), Ui.dp(activity, 40));
        button.setLayoutParams(params);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(true);
            }
        });
        return button;
    }

    private void pickTime(final boolean start) {
        int current = start ? blockStart : blockEnd;
        TimePickerDialog picker = new TimePickerDialog(activity,
                new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                        int value = hourOfDay * 60 + minute;
                        if (start) {
                            blockStart = value;
                        } else {
                            blockEnd = value;
                        }
                        refreshBlockRows();
                    }
                }, current / 60, current % 60, true);
        picker.show();
    }

    private void refreshBlockRows() {
        boolean enabled = blockSwitch != null && blockSwitch.isChecked();
        blockStartButton.setText(Ui.clock(blockStart));
        blockEndButton.setText(Ui.clock(blockEnd));
        blockStartButton.setEnabled(enabled);
        blockEndButton.setEnabled(enabled);
        blockStartButton.setAlpha(enabled ? 1f : 0.4f);
        blockEndButton.setAlpha(enabled ? 1f : 0.4f);

        blockStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(true);
            }
        });
        blockEndButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(false);
            }
        });

        if (blockHint != null) {
            if (!enabled) {
                blockHint.setText("没开：只在超过上限时关闭");
            } else if (blockStart == blockEnd) {
                blockHint.setText("开始和结束时间不能一样");
            } else {
                boolean crossMidnight = blockStart > blockEnd;
                blockHint.setText(crossMidnight
                        ? "每天 " + Ui.clock(blockStart) + " 到第二天 " + Ui.clock(blockEnd) + "，打开就关"
                        : "每天 " + Ui.clock(blockStart) + " – " + Ui.clock(blockEnd) + "，打开就关");
            }
        }
    }

    // ---------------- 保存 ----------------

    private boolean save() {
        int session = parseInt(sessionInput, "单次上限", 1, 720);
        if (session < 0) {
            return false;
        }
        int daily = parseInt(dailyInput, "每日上限", 0, 1440);
        if (daily < 0) {
            return false;
        }
        int idle = parseInt(idleInput, "离开重置", 1, 120);
        if (idle < 0) {
            return false;
        }
        int cooldown = parseInt(cooldownInput, "冷静期", 0, 180);
        if (cooldown < 0) {
            return false;
        }
        int overlaySeconds = parseInt(overlayInput, "遮挡时长", 3, 30);
        if (overlaySeconds < 0) {
            return false;
        }
        if (blockSwitch.isChecked() && blockStart == blockEnd) {
            Toast.makeText(activity, "禁用时段的开始和结束不能是同一时刻", Toast.LENGTH_LONG).show();
            return false;
        }

        int[] warns = new int[warnBoxes.size()];
        int count = 0;
        for (Map.Entry<Integer, CheckBox> entry : warnBoxes.entrySet()) {
            if (entry.getValue().isChecked()) {
                warns[count] = entry.getKey();
                count++;
            }
        }
        int[] selectedWarns = new int[count];
        System.arraycopy(warns, 0, selectedWarns, 0, count);

        cfg.setSessionMinutes(session);
        cfg.setDailyMinutes(daily);
        cfg.setIdleMinutes(idle);
        cfg.setCooldownMinutes(cooldown);
        cfg.setOverlaySeconds(overlaySeconds);
        cfg.setWarnMinutes(selectedWarns);
        cfg.setCloseAction(selectedAction());
        cfg.setBlockEnabled(blockSwitch.isChecked());
        cfg.setBlockStartMinute(blockStart);
        cfg.setBlockEndMinute(blockEnd);

        if (listener != null) {
            listener.onSaved();
        }
        return true;
    }

    private String selectedAction() {
        int checked = actionGroup.getCheckedRadioButtonId();
        View view = actionGroup.findViewById(checked);
        if (view != null && Config.ACTION_OVERLAY.equals(view.getTag())) {
            return Config.ACTION_OVERLAY;
        }
        return Config.ACTION_HOME;
    }

    private int parseInt(EditText input, String label, int low, int high) {
        String raw = input.getText().toString().trim();
        if (raw.length() == 0) {
            Toast.makeText(activity, label + "不能为空", Toast.LENGTH_SHORT).show();
            return -1;
        }
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            Toast.makeText(activity, label + "要填数字", Toast.LENGTH_SHORT).show();
            return -1;
        }
        if (value < low || value > high) {
            Toast.makeText(activity, label + "要在 " + low + "～" + high + " 之间", Toast.LENGTH_LONG).show();
            return -1;
        }
        return value;
    }

    private void restoreDefaults() {
        sessionInput.setText("20");
        dailyInput.setText("0");
        idleInput.setText("3");
        cooldownInput.setText("5");
        overlayInput.setText("8");
        int[] defaults = {5, 2, 1};
        for (Map.Entry<Integer, CheckBox> entry : warnBoxes.entrySet()) {
            entry.getValue().setChecked(contains(defaults, entry.getKey()));
        }
        actionGroup.check(actionGroup.getChildAt(0).getId());
        blockSwitch.setChecked(false);
        blockStart = 23 * 60;
        blockEnd = 7 * 60;
        refreshBlockRows();
        Toast.makeText(activity, "已填入默认值，点「保存」生效", Toast.LENGTH_SHORT).show();
    }

    private static boolean contains(int[] values, int target) {
        for (int value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }
}
