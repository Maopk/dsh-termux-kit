package io.dsh.bridge;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.SecureRandom;

/**
 * DSH 桥界面：显示 token、开启无障碍、以及最重要的——**一键紧急停止**。
 * 界面全代码搭，无 layout 资源。
 */
public class MainActivity extends Activity {

    private TextView stateView;
    private TextView tokenView;
    private TextView warnView;
    private Button idleBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureToken(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        // 版本号从包信息动态读取 —— 不许再硬编码（上次就因为这个，清单升到 1.6 而界面还写着 1.5）
        String ver = "?";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) { /* 忽略 */ }
        title.setText("DSH 桥  v" + ver);
        title.setTextSize(22f);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("v1.6 关键改动：改用**前台服务**常驻，避免被系统/厂商省电策略冻结失联。\n"
                + "（v1.5 新增：联网时自动打开无线调试，供小组件 8 使用）\n"
                + "让 Termux 里的 DSH 帮你操作手机（读屏/点击/滑动/输入）。\n"
                + "权限只有「无障碍」和「本机回环端口 127.0.0.1:8788」，没有联网上传。\n\n"
                + "★ 紧急停止（四个都行）：\n"
                + "   1. 下面这个红按钮\n"
                + "   2. 音量 + 和音量 − 同时按住 3 秒\n"
                + "   3. 通知栏「DSH 桥正在运行」里的「紧急停止」\n"
                + "   4. Termux 桌面小组件「0_紧急停止」\n"
                + "（另外：本 App 不会开机自启，重启手机后不会自己跑起来）");
        desc.setTextSize(13f);
        desc.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(desc);

        tokenView = new TextView(this);
        tokenView.setTextSize(17f);
        tokenView.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(tokenView);

        Button copy = new Button(this);
        copy.setText("复制 token");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("token", token(MainActivity.this)));
                toast("token 已复制，可以粘贴发给 DSH");
            }
        });
        root.addView(copy);

        Button open = new Button(this);
        open.setText("① 打开无障碍设置");
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable t) { toast("打开设置失败：" + t.getMessage()); }
            }
        });
        root.addView(open);

        Button refresh = new Button(this);
        refresh.setText("② 刷新状态 / 恢复监听");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                BridgeService svc = BridgeService.INSTANCE;
                if (svc != null) svc.resumeListening();
                updateState();
            }
        });
        root.addView(refresh);

        idleBtn = new Button(this);
        idleBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleIdle(); }
        });
        root.addView(idleBtn);

        Button panic = new Button(this);
        panic.setText("紧急停止（立即关闭无障碍）");
        panic.setTextSize(17f);
        panic.setBackgroundColor(0xFFB3261E);
        panic.setTextColor(0xFFFFFFFF);
        int h = (int) (18 * getResources().getDisplayMetrics().density);
        panic.setPadding(pad, h, pad, h);
        panic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                BridgeService svc = BridgeService.INSTANCE;
                if (svc != null) {
                    svc.panic("App 内按钮");
                    toast("已紧急停止，无障碍服务已关闭");
                } else {
                    toast("服务当前未运行；如需彻底关闭，请在系统无障碍设置里关掉「DSH 桥」");
                }
                updateState();
            }
        });
        root.addView(panic);

        warnView = new TextView(this);
        warnView.setTextSize(14f);
        warnView.setTextColor(0xFFB3261E);
        warnView.setPadding(0, pad / 2, 0, 0);
        root.addView(warnView);

        Button notif = new Button(this);
        notif.setText("③ 打开本应用的通知设置（让「通知栏紧急停止」可用）");
        notif.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Throwable t) { toast("打不开通知设置：" + t.getMessage()); }
            }
        });
        root.addView(notif);

        stateView = new TextView(this);
        stateView.setTextSize(14f);
        stateView.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(stateView);

        ScrollView sv = new ScrollView(this);
        sv.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);

        tokenView.setText("token：" + token(this));
        requestNotifPermission();
        updateIdleBtn();
        updateState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BridgeService svc = BridgeService.INSTANCE;
        if (svc != null) svc.resumeListening();   // 打开 App 即恢复监听（配合空闲自动停止）
        updateState();
    }

    // ---------- 状态 / 设置 ----------
    private void updateState() {
        boolean on = isAccessibilityEnabled(this);
        BridgeService svc = BridgeService.INSTANCE;
        String listen = (svc != null && svc.isListening()) ? "正在监听 127.0.0.1:" + BridgeService.PORT : "未在监听";
        stateView.setText("无障碍：" + (on ? "已开启 ✅" : "未开启 ❌")
                + "\n端口：" + listen
                + (on ? "" : "\n请点「① 打开无障碍设置」，在列表里打开「DSH 桥」"));
        boolean notifOk = true;
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notifOk = nm == null || nm.areNotificationsEnabled();
        } catch (Throwable t) { notifOk = true; }
        if (warnView != null) {
            warnView.setText(notifOk ? ""
                    : "⚠️ 通知权限未开启：「通知栏紧急停止」这一层现在是失效的。\n"
                      + "   请点下面「③ 打开本应用的通知设置」把它打开。\n"
                      + "   （不影响音量键手势、App 内红按钮、Termux 小组件这三层）");
        }
    }

    private void updateIdleBtn() {
        int m = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE).getInt("idleMin", 30);
        idleBtn.setText("空闲自动停止：" + (m <= 0 ? "已关闭（一直监听）" : m + " 分钟无指令则停止") + " · 点此切换");
    }

    private void cycleIdle() {
        int m = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE).getInt("idleMin", 30);
        int next = (m == 30) ? 15 : (m == 15) ? 60 : (m == 60) ? 0 : 30;
        getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE).edit().putInt("idleMin", next).apply();
        updateIdleBtn();
        toast(next <= 0 ? "已关闭空闲自动停止" : "空闲 " + next + " 分钟自动停止");
    }

    private void requestNotifPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        } catch (Throwable t) { /* 忽略 */ }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------- token ----------
    static String token(Context ctx) {
        return ctx.getSharedPreferences(BridgeService.PREFS, Context.MODE_PRIVATE).getString("token", "");
    }

    static String ensureToken(Context ctx) {
        String t = token(ctx);
        if (t == null || t.isEmpty()) {
            byte[] b = new byte[6];
            new SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            t = sb.toString();
            ctx.getSharedPreferences(BridgeService.PREFS, Context.MODE_PRIVATE)
                    .edit().putString("token", t).apply();
        }
        return t;
    }

    static boolean isAccessibilityEnabled(Context ctx) {
        try {
            String flat = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (flat == null) return false;
            String me = ctx.getPackageName() + "/" + BridgeService.class.getName();
            String me2 = ctx.getPackageName() + "/.BridgeService";
            return flat.contains(me) || flat.contains(me2);
        } catch (Throwable t) {
            return false;
        }
    }
}
