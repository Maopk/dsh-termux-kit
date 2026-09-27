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
    private android.widget.Switch runSwitch;
    private android.widget.Switch idleSwitch;
    private TextView bridgeState;
    private boolean runGuard, idleGuard;
    /** Console palette, so the two apps stop looking like two different products. */
    static final int BG = Palette.BG, FG = Palette.FG, DIM = Palette.DIM, LINK = Palette.LINK, DANGER = Palette.DANGER;
    /** Views that carry a deliberate colour opt out of the dark pass. */
    private void keepColor(View v) { v.setTag("keepcolor"); }
    private void applyDark(View v) {
        if ("keepcolor".equals(v.getTag())) return;
        if (v instanceof android.widget.Button) {
            v.setBackgroundColor(Palette.CARD);
            ((android.widget.Button) v).setTextColor(FG);
            ((android.widget.Button) v).setAllCaps(false);
            ((android.widget.Button) v).setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        } else if (v instanceof TextView) {
            ((TextView) v).setTextColor(FG);
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyDark(g.getChildAt(i));
        }
    }
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
        open.setText(Lang.t(UiControls.get("open-accessibility").labelEn));
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable t) { toast(Lang.t("Failed to open settings: ") + t.getMessage()); }
            }
        });

        Button refresh = new Button(this);
        refresh.setText(Lang.t(UiControls.get("bridge_refresh").labelEn));
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                BridgeService svc = BridgeService.INSTANCE;
                if (svc != null) svc.resumeListening();
                updateState();
            }
        });

        // A real switch, not a "tap to cycle" button: it expresses a lasting state (spec §二).
        idleSwitch = new android.widget.Switch(this);
        idleSwitch.setTextSize(15f);
        idleSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton v, boolean on) {
                if (idleGuard) return;
                getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE).edit()
                        .putInt("idleMin", on ? 30 : 0).apply();
                toast(Lang.t(on ? "Idle auto-stop on: soft-stops after 30 idle minutes" : "Idle auto-stop off: always listening"));
                updateIdleBtn();
            }
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
        projBtn.setAllCaps(false);   // 主题默认全大写，会把网址显示成 GITHUB.COM/…
        projBtn.setText("github.com/Maopk/dsh-termux-kit");   // looks like a link, behaves like one
        projBtn.setBackgroundColor(0x00000000);
        projBtn.setTextColor(Palette.LINK);
        projBtn.setPaintFlags(projBtn.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        projBtn.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
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
        updateView.setTextColor(Palette.DIM);
        updateView.setAllCaps(false);
        updateView.setText(Lang.t("Version ") + ver + Lang.t(" · checking for a newer release…"));

        Button panic = new Button(this);
        panic.setText(Lang.t(UiControls.get("bridge_panic").labelEn));
        panic.setTextSize(17f);
        panic.setBackgroundColor(Palette.DANGER);
        panic.setTextColor(Palette.ON_DANGER);
        int h = (int) (18 * getResources().getDisplayMetrics().density);
        panic.setPadding(pad, h, pad, h);
        panic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                BridgeService svc = BridgeService.INSTANCE;
                if (svc != null) {
                    svc.panic(Lang.t("In-app button"));
                    toast(Lang.t("Emergency stop done; the accessibility service is off"));
                } else {
                    toast(Lang.t("The service is not running right now; to shut it down completely, turn DSH Bridge off in the system accessibility settings"));
                }
                updateState();
            }
        });

        runSwitch = new android.widget.Switch(this);
        runSwitch.setTextSize(15f);
        runSwitch.setText(Lang.t("Bridge running"));
        runSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton v, boolean on) {
                if (runGuard) return;
                BridgeService svc = BridgeService.INSTANCE;
                if (on) {
                    if (svc != null) svc.resumeListening();
                    toast(Lang.t("Resumed listening on 127.0.0.1:") + BridgeService.PORT);
                } else {
                    if (svc != null) svc.stopListening(null);
                    toast(Lang.t("Soft-stopped: the port is closed, the process stays, one broadcast brings it back"));
                }
                updateState();
            }
        });
        bridgeState = new TextView(this);
        bridgeState.setTextSize(12.5f);
        bridgeState.setTextColor(Palette.DIM);

        warnView = new TextView(this);
        warnView.setTextSize(14f);
        warnView.setTextColor(Palette.DANGER);
        warnView.setPadding(0, pad / 2, 0, 0);

        Button notif = new Button(this);
        notif.setText(Lang.t(UiControls.get("open-notification").labelEn));
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
        // The dark page must cover the **whole window**, not just the height of the content: with the
        // platform's default theme the area below short content stayed white, which is exactly what
        // "the bridge is a light app" looked like on the phone (user report 2026-09-27). The manifest
        // now declares the same dark theme as the console, and both the window (sv) and the content
        // (root) are painted with the shared palette.
        sv.setBackgroundColor(Palette.BG);
        sv.setFillViewport(true);
        sv.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);

        tokenView.setText(Lang.t("token: ") + "tok\u2022\u2022\u2022\u2022\u2022\u2022" + Lang.t("  (tap Copy token to copy it)"));
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

    /** Equal means equal — "up to date" may only be claimed here. */
    private static boolean sameVer(String a, String b) {
        return !isNewer(a, b) && !isNewer(b, a);
    }

    private void showUpdate(String latest, String err, String mine) {
        if (updateView == null) return;
        if (err != null || latest == null) {
            // 说不清就说说不清 —— 不能显示成"已是最新"
            updateView.setText(Lang.t("Version ") + mine + Lang.t(" · could not check for updates now (no network?)"));
            updateView.setTextColor(Palette.DIM);
            return;
        }
        if (isNewer(latest, mine)) {
            updateView.setText(Lang.t("Version ") + mine + Lang.t(" → the repo has ") + "v" + latest
                    + Lang.t(" · tap the project page to update"));
            updateView.setTextColor(Palette.WARN);
        } else if (sameVer(latest, mine)) {
            updateView.setText(Lang.t("Version ") + mine + Lang.t(" · up to date"));
            updateView.setTextColor(Palette.OK);
        } else {
            // Local is NEWER than the release (a locally built version). This branch used to fall through
            // to "已是最新" as well, so the line read "版本：v1.15 · 已是最新（仓库最新 v1.14）" while the
            // installed build was ahead of the repo — the user caught that contradiction on 2026-09-27.
            // Rule now: equal → 已是最新; ahead → say so; behind → point at the repo.
            updateView.setText(Lang.t("Version ") + mine + Lang.t(" · local is newer than the repo (the repo only has v")
                    + latest + Lang.t(")"));
            updateView.setTextColor(Palette.DIM);
        }
    }

    private void updateState() {
        boolean on = isAccessibilityEnabled(this);
        BridgeService svc = BridgeService.INSTANCE;
        String listen = (svc != null && svc.isListening()) ? Lang.t("Listening on 127.0.0.1:") + BridgeService.PORT : Lang.t("Not listening");
        stateView.setText(Lang.t("Accessibility: ") + (on ? Lang.t("on ✅") : Lang.t("off ❌"))
                + "\n" + Lang.t("Port: ") + listen
                + (on ? "" : "\n" + Lang.t("Tap ① Open accessibility settings and switch DSH Bridge on in the list")));
        if (bridgeState != null) {
            boolean listening = svc != null && svc.isListening();
            bridgeState.setText(listening ? Lang.t("Bridge: running") + " · v" + ver
                                          : Lang.t("Bridge: just dropped (wakeable)"));
            if (runSwitch != null) { runGuard = true; runSwitch.setChecked(listening); runGuard = false; }
        }
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
        root.setBackgroundColor(BG);
        android.content.SharedPreferences sp = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE);
        java.util.Set<String> collapsed = new java.util.HashSet<String>(sp.getStringSet("collapsed", java.util.Collections.<String>emptySet()));
        if (!sp.contains("collapsed")) { collapsed.add("channels"); collapsed.add("maintenance"); collapsed.add("emergency"); }
        root.addView(title);
        // ⚠ The title is NOT keepColor'd any more: on the platform's default (light) theme that kept
        //   the default BLACK text, which is why "DSH Bridge" read as a second, foreign title above
        //   the dark page (user report 2026-09-27). It is painted explicitly instead.
        title.setTextColor(Palette.FG);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(stateView);
        root.addView(warnView);
        keepColor(warnView);

        root.addView(foldSection(root, "channels", collapsed, sp,
                runSwitch, bridgeState, hintOf("bridge_run")));
        root.addView(foldSection(root, "maintenance", collapsed, sp,
                refresh, hintOf("bridge_refresh"), open, hintOf("open-accessibility"),
                notif, hintOf("open-notification"), copy, hintOf("copy-token"),
                idleSwitch, hintOf("idle-auto-stop"), langRow(), fullStopBtn(), hintOf("bridge_full_stop"),
                updateView, projBtn));
        root.addView(foldSection(root, "emergency", collapsed, sp, panic, hintOf("bridge_panic")));

        // About is folded away by default: it is a page of explanation, not something to scroll past every time.
        root.addView(foldSection(root, "about", new java.util.HashSet<String>(java.util.Arrays.asList("about")),
                getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE), desc, tokenView));
        applyDark(root);
        // Views with a deliberate colour re-apply it after the dark pass.
        stateView.setTextColor(DIM);
        warnView.setTextColor(DANGER);
        bridgeState.setTextColor(DIM);
        updateView.setTextColor(DIM);
        tokenView.setTextColor(DIM);
        desc.setTextColor(DIM);
        projBtn.setTextColor(LINK);
    }

    /** A section header that folds its body; the choice is remembered across launches. */
    private LinearLayout foldSection(LinearLayout root, String catId, java.util.Set<String> collapsed,
                                     android.content.SharedPreferences sp, View... children) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final boolean[] shut = { collapsed.contains(catId) };
        box.setVisibility(shut[0] ? View.GONE : View.VISIBLE);
        final TextView head = section(CatLabel(catId, shut[0]));
        head.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                shut[0] = !shut[0];
                box.setVisibility(shut[0] ? View.GONE : View.VISIBLE);
                head.setText(CatLabel(catId, shut[0]));
                if (shut[0]) collapsed.add(catId); else collapsed.remove(catId);
                sp.edit().putStringSet("collapsed", collapsed).apply();
            }
        });
        for (View c : children) if (c != null) box.addView(c);
        root.addView(head);
        return box;
    }

    private String CatLabel(String catId, boolean shut) {
        // ⚠ Translate FIRST, then add the fold arrow. It used to be the other way round: section()
        //   received "▸ Channels (adb and bridge separate)" and looked *that* up in the table, which
        //   can never match a key — so all three category headers stayed English while everything
        //   around them was Chinese (user report 2026-09-27). tools/i18n-audit now rejects any
        //   Lang.t() whose argument is not a plain literal, so this cannot come back.
        String name = "about".equals(catId) ? Lang.t("About this app") : Lang.t(UiControls.catEn(catId));
        return (shut ? "▸ " : "▾ ") + name;
    }

    /** A small grey consequence line under a control — every control carries one (spec §四). */
    private TextView hintOf(String id) {
        UiControls.C c = UiControls.get(id);
        TextView t = new TextView(this);
        t.setTextSize(11.5f);
        t.setTextColor(Palette.MUTED);
        t.setText(c == null ? "" : ((c.danger ? "⚠ " : "") + Lang.t(c.hintEn)));
        return t;
    }

    private TextView sub(String name) {
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(11.5f);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setTextColor(Palette.DIM);
        int p = (int) (4 * getResources().getDisplayMetrics().density);
        t.setPadding(0, p, 0, p);
        return t;
    }

    /** Language: three explicit choices — a cycling button hides which option you will land on. */
    private LinearLayout langRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] ids = { "auto", "zh", "en" };
        String[] names = { Lang.t("System"), "中文", "English" };
        String mode = Lang.mode();
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            Button b = new Button(this);
            b.setText(names[i]);
            b.setTextSize(12f);
            b.setAllCaps(false);
            boolean on = mode.equals(id);
            b.setText(on ? ("✓ " + names[i]) : names[i]);
            b.setTextColor(on ? Palette.ACCENT : Palette.DIM);
            android.graphics.drawable.GradientDrawable gg = new android.graphics.drawable.GradientDrawable();
            gg.setColor(Palette.CARD); gg.setCornerRadius(8 * getResources().getDisplayMetrics().density);
            if (on) gg.setStroke((int) (2 * getResources().getDisplayMetrics().density), Palette.ACCENT);
            b.setBackground(gg);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { Lang.setMode(MainActivity.this, id); recreate(); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, (int) (6 * getResources().getDisplayMetrics().density), 0);
            row.addView(b, lp);
        }
        return row;
    }

    /** Full stop is dangerous **and** lives under Maintenance (user's call): confirm first, then say the cost. */
    private Button fullStopBtn() {
        UiControls.C c = UiControls.get("bridge_full_stop");
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText("⚠ " + Lang.t(c == null ? "Fully stop bridge" : c.labelEn));
        b.setTextColor(Palette.DANGER);
        b.setTextSize(14f);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(Palette.DANGER_FILL); g.setCornerRadius(10 * getResources().getDisplayMetrics().density);
        g.setStroke((int) (2 * getResources().getDisplayMetrics().density), Palette.DANGER);
        b.setBackground(g);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("⚠ " + Lang.t("Fully stop the bridge?"))
                        .setMessage(Lang.t("Accessibility is unbound and the app exits. On this vivo it may not be wakeable again - you would have to open DSH Bridge by hand."))
                        .setNegativeButton(Lang.t("Cancel"), null)
                        .setPositiveButton(Lang.t("Stop it"), new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int w) {
                                BridgeService svc = BridgeService.INSTANCE;
                                if (svc != null) svc.panic(Lang.t("Full stop from the app"));
                                toast(Lang.t("Fully stopped; accessibility is off"));
                                updateState();
                            }
                        })
                        .show();
            }
        });
        return b;
    }

    /** Section header ("▍名字", blue). `label` must already be translated — see CatLabel. */
    private TextView section(String label) {
        TextView h = new TextView(this);
        h.setText("▍" + label);
        h.setTextSize(13f);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(Palette.ACCENT);
        keepColor(h);   // the dark pass must not repaint the blue headers grey
        int p = (int) (14 * getResources().getDisplayMetrics().density);
        h.setPadding(0, p, 0, (int) (2 * getResources().getDisplayMetrics().density));
        return h;
    }

    private void updateIdleBtn() {
        int m = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE).getInt("idleMin", 30);
        if (idleSwitch == null) return;
        idleGuard = true;
        idleSwitch.setChecked(m > 0);
        idleGuard = false;
        idleSwitch.setText(Lang.t("Idle auto-stop") + (m > 0 ? " (" + m + Lang.t(" min") + ")" : ""));
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
