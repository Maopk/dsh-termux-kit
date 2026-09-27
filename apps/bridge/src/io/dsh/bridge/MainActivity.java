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
        // Version read dynamically from package info — never hard-coded again (last time the manifest went to 1.6 while the UI still said 1.5)
        String ver = "?";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) { /* ignore */ }
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
        layoutAll(root, title, desc, tokenView, copy, open, refresh, notif, panic);
    }

    @Override
    protected void onResume() {
        super.onResume();
        BridgeService svc = BridgeService.INSTANCE;
        if (svc != null) svc.resumeListening();   // opening the app resumes listening (pairs with idle auto-stop)
        updateState();
    }

    // ---------- State / settings ----------
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
        if (warnView != null) {
            warnView.setText(notifOk ? ""
                    : Lang.t("⚠️ Notification permission is off: the notification-bar emergency stop layer is currently dead.") + "\n"
                      + Lang.t("   Tap ③ Open this app's notification settings below to enable it.") + "\n"
                      + "   (The volume-key gesture, the in-app red button and the Termux widget are unaffected)");
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
