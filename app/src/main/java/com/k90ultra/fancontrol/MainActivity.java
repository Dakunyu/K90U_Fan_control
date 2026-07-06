package com.k90ultra.fancontrol;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final String FAN_NODE = "/sys/devices/platform/soc/soc:xiaomi_fan/target_level";
    private TextView statusText;
    private TextView currentNumber;
    private TextView currentName;
    private final Button[] levelButtons = new Button[5];
    private int currentLevel = -1;
    private boolean busy = false;

    private final Level[] levels = new Level[] {
            new Level(0, "智能", "自动调频策略", "自动", 0xff22d3ee, 0xff2563eb),
            new Level(1, "静谧", "低噪声档位", "12000转", 0xff60a5fa, 0xff6366f1),
            new Level(2, "强冷", "高速强冷档", "15000转", 0xff8b5cf6, 0xffec4899),
            new Level(3, "增强", "隐藏档位", "16000转", 0xfff59e0b, 0xffef4444),
            new Level(4, "极速", "隐藏档位", "20000转", 0xffff3b5f, 0xff7c2d12)
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        readCurrentLevel();
    }

    private void buildUi() {
        int bgTop = 0xff030712;
        int bgMid = 0xff081528;
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{bgTop, bgMid, bgTop});
        scrollView.setBackground(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(50), dp(18), dp(16));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(76)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, -1, 1));

        TextView title = text("风扇控制", 32, Color.WHITE, true);
        titleBox.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));

        statusText = text("正在启动...", 13, 0xff8ea0bc, true);
        titleBox.addView(statusText, new LinearLayout.LayoutParams(-1, dp(24)));

        Button refresh = new Button(this);
        refresh.setText("刷新");
        refresh.setTextColor(0xffbffaf5);
        refresh.setTextSize(14);
        refresh.setTypeface(Typeface.DEFAULT_BOLD);
        refresh.setAllCaps(false);
        refresh.setBackground(roundStroke(0x00000000, 0xff2b6f74, dp(16), dp(1)));
        refresh.setOnClickListener(v -> readCurrentLevel());
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(dp(96), dp(52));
        top.addView(refresh, refreshLp);

        root.addView(dashboardCard(), marginLp(-1, dp(112), 0, dp(12), 0, dp(12)));

        TextView modeTitle = text("风扇模式", 15, 0xffb8c7e6, true);
        root.addView(modeTitle, marginLp(-1, dp(28), 0, 0, 0, dp(4)));

        for (int i = 0; i < levels.length; i++) {
            Button card = modeButton(levels[i]);
            levelButtons[i] = card;
            root.addView(card, marginLp(-1, dp(94), 0, 0, 0, dp(10)));
        }

        setContentView(scrollView);
    }

    private View dashboardCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20), dp(16), dp(20), dp(16));
        card.setBackground(roundStroke(0xe60d1320, 0x22ffffff, dp(30), dp(1)));

        GradientDrawable circleBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xff22d3ee, 0xff8b5cf6});
        circleBg.setShape(GradientDrawable.OVAL);
        LinearLayout circle = new LinearLayout(this);
        circle.setOrientation(LinearLayout.VERTICAL);
        circle.setGravity(Gravity.CENTER);
        circle.setBackground(circleBg);
        card.addView(circle, new LinearLayout.LayoutParams(dp(72), dp(72)));

        currentNumber = text("--", 30, Color.WHITE, true);
        currentNumber.setGravity(Gravity.CENTER);
        circle.addView(currentNumber, new LinearLayout.LayoutParams(-1, dp(36)));
        TextView levelLabel = text("Level", 10, 0xccffffff, true);
        levelLabel.setGravity(Gravity.CENTER);
        circle.addView(levelLabel, new LinearLayout.LayoutParams(-1, dp(16)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(16), 0, 0, 0);
        card.addView(info, new LinearLayout.LayoutParams(0, -1, 1));

        TextView label = text("当前档位", 12, 0xff8290aa, true);
        info.addView(label, new LinearLayout.LayoutParams(-1, dp(24)));
        currentName = text("未知", 28, Color.WHITE, true);
        info.addView(currentName, new LinearLayout.LayoutParams(-1, dp(44)));
        return card;
    }

    private Button modeButton(Level level) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setText(formatLevelText(level));
        button.setTextSize(18);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        button.setSingleLine(false);
        button.setBackground(roundStroke(0xff121a2a, 0x22ffffff, dp(24), dp(1)));
        button.setOnClickListener(v -> setLevel(level.value));
        return button;
    }

    private String formatLevelText(Level level) {
        return "Level " + level.value + "     " + level.name + "     " + level.rpm + "\n" + level.detail;
    }

    private void updateSelection() {
        for (Level level : levels) {
            Button button = levelButtons[level.value];
            if (button == null) continue;
            boolean selected = currentLevel == level.value;
            if (selected) {
                button.setBackground(roundGradient(level.startColor, level.endColor, dp(24), level.startColor, dp(2)));
                button.setTextColor(Color.WHITE);
            } else {
                button.setBackground(roundStroke(0xff121a2a, 0x22ffffff, dp(24), dp(1)));
                button.setTextColor(Color.WHITE);
            }
        }
        if (currentLevel >= 0 && currentLevel < levels.length) {
            currentNumber.setText(String.valueOf(currentLevel));
            currentName.setText(levels[currentLevel].name);
        } else {
            currentNumber.setText("--");
            currentName.setText("未知");
        }
    }

    private void readCurrentLevel() {
        if (busy) return;
        busy = true;
        statusText.setText("正在读取真实档位...");
        new Thread(() -> {
            Result result = readLevel();
            runOnUiThread(() -> {
                busy = false;
                if (result.ok) {
                    currentLevel = result.level;
                    statusText.setText("已读取真实档位：" + result.level);
                    updateSelection();
                } else {
                    statusText.setText(result.message);
                }
            });
        }).start();
    }

    private void setLevel(int level) {
        if (busy) return;
        busy = true;
        statusText.setText("正在切换到档位 " + level + "...");
        new Thread(() -> {
            Result write = runRoot("echo " + level + " > '" + FAN_NODE + "'");
            Result result = write.ok ? readLevel() : write;
            runOnUiThread(() -> {
                busy = false;
                if (result.ok) {
                    currentLevel = result.level;
                    statusText.setText("已应用并确认真实档位：" + result.level);
                    updateSelection();
                } else {
                    statusText.setText(result.message);
                }
            });
        }).start();
    }

    private Result readLevel() {
        Result result = runRoot("cat '" + FAN_NODE + "'");
        if (!result.ok) return result;
        try {
            int level = Integer.parseInt(result.output.trim());
            if (level >= 0 && level <= 4) return Result.ok(level, result.output);
            return Result.fail("读取到未知档位输出");
        } catch (Exception e) {
            return Result.fail("读取到未知档位输出");
        }
    }

    private Result runRoot(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).start();
            boolean finished = process.waitFor(8, TimeUnit.SECONDS);
            String out = readAll(process.getInputStream());
            String err = readAll(process.getErrorStream());
            if (!finished) {
                process.destroyForcibly();
                return Result.fail("ROOT 命令超时");
            }
            if (process.exitValue() == 0) return Result.ok(-1, out);
            String all = (out + "\n" + err).toLowerCase();
            if (all.contains("denied") || all.contains("permission")) return Result.fail("ROOT 权限被拒绝");
            if (all.contains("no such file") || all.contains("not found")) return Result.fail("风扇节点不存在");
            return Result.fail("命令执行失败");
        } catch (Exception e) {
            return Result.fail("无法执行 su");
        } finally {
            if (process != null) process.destroy();
        }
    }

    private String readAll(java.io.InputStream stream) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setIncludeFontPadding(true);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private LinearLayout.LayoutParams marginLp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private GradientDrawable roundStroke(int color, int strokeColor, int radius, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private GradientDrawable roundGradient(int start, int end, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        drawable.setCornerRadius(radius);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class Level {
        final int value;
        final String name;
        final String detail;
        final String rpm;
        final int startColor;
        final int endColor;
        Level(int value, String name, String detail, String rpm, int startColor, int endColor) {
            this.value = value;
            this.name = name;
            this.detail = detail;
            this.rpm = rpm;
            this.startColor = startColor;
            this.endColor = endColor;
        }
    }

    private static class Result {
        final boolean ok;
        final int level;
        final String output;
        final String message;
        private Result(boolean ok, int level, String output, String message) {
            this.ok = ok;
            this.level = level;
            this.output = output;
            this.message = message;
        }
        static Result ok(int level, String output) { return new Result(true, level, output, ""); }
        static Result fail(String message) { return new Result(false, -1, "", message); }
    }
}
