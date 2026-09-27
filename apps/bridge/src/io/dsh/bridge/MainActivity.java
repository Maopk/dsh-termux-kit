package io.dsh.bridge;

import android.graphics.Color;
import android.graphics.Typeface;
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
 * DSH Bridge UI: shows the token, lets you enable accessibility, and most importantly — **one-tap emergency stop**.
 * The whole UI is built in code, with no layout resources.
 */
public class MainActivity extends Activity {

    private TextView stateView;
    private TextView tokenView;
    private TextView warnView;
    private Button idleBtn;
    private Button langBtn;   // language switch: auto → 中文 → English
    private Button projBtn;   // 本项目的网址（点开就是仓库）
    private TextView updateView;   // 打开时自动查一次发行版，有新的就显示在这里
    private String ver = "?";      // 运行时从包信息读，绝不写死
    private static final String PROJECT_URL = "https://github.com/Maopk/dsh-termux-kit";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Lang.init(this);   // persisted choice; Termux can also push it in over the loopback socket
        ensureToken(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        // Version read dynamically from package info — never hard-coded again (last time the manifest
        // went to 1.6 while the UI still said 1.5). A field now, because the update check needs it
        // from a background thread too.
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) { /* stays "?" — never claim a version we could not read */ }
        title.setText(Lang.t("DSH Bridge") + "  v" + ver);
        title.setTextSize(22f);

        TextView desc = new TextView(this);
        desc.setText(Lang.t("Runs as a foreground service so system/vendor battery savers cannot freeze it and lose contact.") + "\n"
                + Lang.t("(new in v1.5: turns wireless debugging on automatically when online, used by widget 8)") + "\n"
                + Lang.t("Lets the DSH inside Termux operate the phone for you (read screen / tap / swipe / type).\n")
                + Lang.t("The only permissions are accessibility and the local loopback port 127.0.0.1:8788; nothing is uploaded.") + "\n\n"
                + Lang.t("★ Emergency stop (any of the four):\n")
                + Lang.t("   1. The red button below\n")
                + Lang.t("   2. Hold volume + and volume − together for 3 seconds\n")
                + Lang.t("   3. Emergency stop inside the DSH Bridge is running notification\n")
                + Lang.t("   4. The Termux home-screen widget item 0_emergency-stop") + "\n"
                + Lang.t("(Also: this app does not autostart; it will not come up by itself after a phone restart)"));
        desc.setTextSize(13f);
        desc.setPadding(0, pad / 2, 0, pad / 2);

        tokenView = new TextView(this);
        tokenView.setTextSize(17f);
        tokenView.setPadding(0, pad / 2, 0, pad / 2);

        Button copy = new Button(this);
        copy.setText(Lang.t("Copy token"));
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("token", token(MainActivity.this)));
                toast(Lang.t("Token copied; you can paste it to DSH"));
            }
        });

        Button open = new Button(this);
        open.setText(Lang.t("① Open accessibility settings"));
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable t) { toast(Lang.t("Failed to open settings: ") + t.getMessage()); }
            }
        });

        Button refresh = new Button(this);
        refresh.setText(Lang.t("② Refresh state / resume listening"));
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                BridgeService svc = BridgeService.INSTANCE;
                if (svc != null) svc.resumeListening();
                updateState();
            }
        });

        idleBtn = new Button(this);
        idleBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleIdle(); }
        });

        langBtn = new Button(this);
        langBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String m = Lang.mode();
                String next = m.equals("auto") ? "zh" : (m.equals("zh") ? "en" : "auto");
                Lang.setMode(MainActivity.this, next);
                recreate();   // redraw with the new language; the choice is persisted
            }
        });

        // ── About: the project URL, and whether a newer release exists ──
        projBtn = new Button(this);
        projBtn.setText(Lang.t("Project page: ") + "github.com/Maopk/dsh-termux-kit");
        projBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PROJECT_URL))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable t) {
                    toast(Lang.t("No browser to open it with; the address is github.com/Maopk/dsh-termux-kit"));
                }
            }
        });
        updateView = new TextView(this);
        updateView.setTextSize(13f);
        updateView.setPadding(0, (int) (6 * getResources().getDisplayMetrics().density), 0, 0);
        updateView.setTextColor(0xFF8B949E);
        updateView.setText(Lang.t("Version: ") + ver + Lang.t(" · checking for a newer release…"));

        Button panic = new Button(this);
        panic.setText(Lang.t("Emergency stop (turn accessibility off now)"));
        panic.setTextSize(17f);
        panic.setBackgroundColor(0xFFB3261E);
        panic.setTextColor(0xFFFFFFFF);
        int h = (int) (18 * getResources().getDisplayMetrics().density);
        panic.setPadding(pad, h, pad, h);
        panic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                BridgeService svc = BridgeService.INSTANCE;
                if (svc != null) {
                    svc.panic("In-app button");
                    toast(Lang.t("Emergency stop done; the accessibility service is off"));
                } else {
                    toast(Lang.t("The service is not running right now; to shut it down completely, turn DSH Bridge off in the system accessibility settings"));
                }
                updateState();
            }
        });

        warnView = new TextView(this);
        warnView.setTextSize(14f);
        warnView.setTextColor(0xFFB3261E);
        warnView.setPadding(0, pad / 2, 0, 0);

        Button notif = new Button(this);
        notif.setText(Lang.t("③ Open this app's notification settings (so notification-bar emergency stop works)"));
        notif.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Throwable t) { toast(Lang.t("Cannot open notification settings: ") + t.getMessage()); }
            }
        });

        stateView = new TextView(this);
        stateView.setTextSize(14f);
        stateView.setPadding(0, pad / 2, 0, pad / 2);

        ScrollView sv = new ScrollView(this);
        sv.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);

        tokenView.setText(Lang.t("token: ") + token(this));
        requestNotifPermission();
        updateIdleBtn();
        updateState();
        layoutAll(root, title, desc, tokenView, copy, open, refresh, notif, panic);   // projBtn/updateView are fields, built above
    }

    @Override
    protected void onResume() {
        super.onResume();
        BridgeService svc = BridgeService.INSTANCE;
        if (svc != null) svc.resumeListening();   // opening the app resumes listening (pairs with idle auto-stop)
        updateState();
    }

    // ---------- State / settings ----------
    /** 上一次查更新的时刻：onResume 会频繁触发，但用户要的是「每次打开」而不是每次返回都发请求。 */
    private long lastUpdateCheck = 0;

    /**
     * 打开 App 时自动问一次仓库有没有新发行版。
     *
     * 放在后台线程里：onCreate/onResume 上做网络请求会把界面卡住，而这台手机上已经有过
     * 「界面没反应 = 这 App 是空壳」的教训。失败**不报错**（没网是常态），只把状态写清楚，
     * 免得用户以为"检查过了、没有新版本"。
     */
    private void checkUpdate() {
        long now = System.currentTimeMillis();
        if (now - lastUpdateCheck < 60000) return;      // 一分钟内不重复
        lastUpdateCheck = now;
        final String mine = ver;
        new Thread(new Runnable() {
            @Override public void run() {
                String latest = null, err = null;
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                            new java.net.URL("https://api.github.com/repos/Maopk/dsh-termux-kit/releases/latest")
                                    .openConnection();
                    c.setRequestProperty("User-Agent", "dsh-bridge");
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    java.io.BufferedReader r = new java.io.BufferedReader(
                            new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("dsh-bridge-v([0-9.]+)\\.apk").matcher(sb.toString());
                    if (m.find()) latest = m.group(1);
                    else err = Lang.t("no bridge APK in the latest release");
                } catch (Throwable t) {
                    err = t.getClass().getSimpleName();
                }
                final String L = latest, E = err;
                runOnUiThread(new Runnable() {
                    @Override public void run() { showUpdate(L, E, mine); }
                });
            }
        }).start();
    }

    private static int[] vparts(String v) {
        String[] p = (v == null ? "" : v).split("\\.");
        int[] out = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            try { out[i] = Integer.parseInt(p[i].replaceAll("[^0-9]", "")); } catch (Throwable t) { out[i] = 0; }
        }
        return out;
    }

    private static boolean isNewer(String a, String b) {
        int[] x = vparts(a), y = vparts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? x[i] : 0, yi = i < y.length ? y[i] : 0;
            if (xi != yi) return xi > yi;
        }
        return false;
    }

    private void showUpdate(String latest, String err, String mine) {
        if (updateView == null) return;
        if (err != null || latest == null) {
            // 说不清就说说不清 —— 不能显示成"已是最新"
            updateView.setText(Lang.t("Version: ") + mine + Lang.t(" · could not check for updates now (no network?)"));
            updateView.setTextColor(0xFF8B949E);
            return;
        }
        if (isNewer(latest, mine)) {
            updateView.setText(Lang.t("⬆ New release available: ") + "v" + latest
                    + Lang.t(" (you have ") + mine + Lang.t(") · tap the project page to get it"));
            updateView.setTextColor(0xFFD29922);
        } else {
            updateView.setText(Lang.t("Version: ") + mine + Lang.t(" · up to date (latest v") + latest + ")");
            updateView.setTextColor(0xFF3FB950);
        }
    }

    private void updateState() {
        boolean on = isAccessibilityEnabled(this);
        BridgeService svc = BridgeService.INSTANCE;
        String listen = (svc != null && svc.isListening()) ? Lang.t("Listening on 127.0.0.1:") + BridgeService.PORT : Lang.t("Not listening");
        stateView.setText(Lang.t("Accessibility: ") + (on ? Lang.t("on ✅") : Lang.t("off ❌"))
                + "\n" + Lang.t("Port: ") + listen
                + (on ? "" : "\n" + Lang.t("Tap ① Open accessibility settings and switch DSH Bridge on in the list")));
        boolean notifOk = true;
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notifOk = nm == null || nm.areNotificationsEnabled();
        } catch (Throwable t) { notifOk = true; }
        checkUpdate();   // 每次打开自动查一次发行版（内部有节流与失败降级）

        if (warnView != null) {
            warnView.setText(notifOk ? ""
                    : Lang.t("⚠️ Notification permission is off: the notification-bar emergency stop layer is currently dead.") + "\n"
                      + Lang.t("   Tap ③ Open this app's notification settings below to enable it.") + "\n"
                      + Lang.t("   (The volume-key gesture, the in-app red button and the Termux widget are unaffected)"));
        }
    }

    /** Section header, same convention as the Console app: "▍Name" in blue. */
    /** Final layout order. Views are created above; this decides what the user actually sees.
     *  Requested by the user 2026-09-27: ③ used to sit at the very bottom (after the emergency button),
     *  so ①②③ were not together, and the buttons had no grouping at all. Status first (it is what you
     *  look for), then setup ①②③, then behaviour, then emergency last but most prominent. */
    private void layoutAll(LinearLayout root, TextView title, TextView desc, TextView tokenView,
                           Button copy, Button open, Button refresh, Button notif, Button panic) {
        root.addView(title);
        root.addView(stateView);                 // live status first: it is the thing you check
        root.addView(desc);
        root.addView(tokenView);
        root.addView(copy);

        root.addView(section("Setup"));
        root.addView(open);
        root.addView(refresh);
        root.addView(notif);                     // ③ right after ②

        root.addView(section("Behaviour"));
        root.addView(idleBtn);
        root.addView(langBtn);

        root.addView(warnView);

        root.addView(section("About"));
        root.addView(updateView);
        root.addView(projBtn);

        root.addView(section("Emergency"));
        root.addView(panic);
    }

    private TextView section(String label) {
        TextView h = new TextView(this);
        h.setText("▍" + Lang.t(label));
        h.setTextSize(13f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(Color.parseColor("#58A6FF"));
        int p = (int) (14 * getResources().getDisplayMetrics().density);
        h.setPadding(0, p, 0, (int) (2 * getResources().getDisplayMetrics().density));
        return h;
    }

    private void updateIdleBtn() {
        int m = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE).getInt("idleMin", 30);
        idleBtn.setText(Lang.t("Idle auto-stop: ") + (m <= 0 ? Lang.t("off (always listening)") : m + Lang.t(" min idle → stop")) + Lang.t(" · tap to switch"));
        if (langBtn != null) {
            String mo = Lang.mode();
            String shown = mo.equals("auto") ? Lang.t("System") : (mo.equals("zh") ? Lang.t("Chinese") : "English");
            langBtn.setText(Lang.t("Language: ") + shown + " · " + Lang.t("tap to switch"));
        }
    }

    private void cycleIdle() {
        int m = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE).getInt("idleMin", 30);
        int next = (m == 30) ? 15 : (m == 15) ? 60 : (m == 60) ? 0 : 30;
        getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE).edit().putInt("idleMin", next).apply();
        updateIdleBtn();
        toast(next <= 0 ? Lang.t("Idle auto-stop is off") : Lang.t("Auto-stop after ") + next + Lang.t(" min idle"));
    }

    private void requestNotifPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        } catch (Throwable t) { /* ignore */ }
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
