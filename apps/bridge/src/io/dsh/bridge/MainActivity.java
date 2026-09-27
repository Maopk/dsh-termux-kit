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
 * DSH Bridge UI: shows the token, lets you enable accessibility, and most importantly — **one-tap emergency stop**.
 * The whole UI is built in code, with no layout resources.
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
        // Version read dynamically from package info — never hard-coded again (last time the manifest went to 1.6 while the UI still said 1.5)
        String ver = "?";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) { /* ignore */ }
        title.setText("DSH Bridge  v" + ver);
        title.setTextSize(22f);
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("v1.6 key change: switched to a **foreground service** to stay resident, so system/vendor battery savers cannot freeze it and lose contact.\n"
                + "(new in v1.5: turns wireless debugging on automatically when online, used by widget 8)\n"
                + "Lets the DSH inside Termux operate the phone for you (read screen / tap / swipe / type).\n"
                + "The only permissions are accessibility and the local loopback port 127.0.0.1:8788; nothing is uploaded.\n\n"
                + "★ Emergency stop (any of the four):\n"
                + "   1. The red button below\n"
                + "   2. Hold volume + and volume − together for 3 seconds\n"
                + "   3. Emergency stop inside the DSH Bridge is running notification\n"
                + "   4. The Termux home-screen widget item 0_紧急停止\n"
                + "(Also: this app does not autostart; it will not come up by itself after a phone restart)");
        desc.setTextSize(13f);
        desc.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(desc);

        tokenView = new TextView(this);
        tokenView.setTextSize(17f);
        tokenView.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(tokenView);

        Button copy = new Button(this);
        copy.setText("Copy token");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                android.content.ClipboardManager cm =
                        (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("token", token(MainActivity.this)));
                toast("Token copied; you can paste it to DSH");
            }
        });
        root.addView(copy);

        Button open = new Button(this);
        open.setText("① Open accessibility settings");
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable t) { toast("Failed to open settings: " + t.getMessage()); }
            }
        });
        root.addView(open);

        Button refresh = new Button(this);
        refresh.setText("② Refresh state / resume listening");
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
        panic.setText("Emergency stop (turn accessibility off now)");
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
                    toast("Emergency stop done; the accessibility service is off");
                } else {
                    toast("The service is not running right now; to shut it down completely, turn DSH Bridge off in the system accessibility settings");
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
        notif.setText("③ Open this app's notification settings (so notification-bar emergency stop works)");
        notif.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Throwable t) { toast("Cannot open notification settings: " + t.getMessage()); }
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

        tokenView.setText("token: " + token(this));
        requestNotifPermission();
        updateIdleBtn();
        updateState();
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
        String listen = (svc != null && svc.isListening()) ? "Listening on 127.0.0.1:" + BridgeService.PORT : "Not listening";
        stateView.setText("Accessibility: " + (on ? "on ✅" : "off ❌")
                + "\nPort: " + listen
                + (on ? "" : "\nTap ① Open accessibility settings and switch DSH Bridge on in the list"));
        boolean notifOk = true;
        try {
            android.app.NotificationManager nm =
                    (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notifOk = nm == null || nm.areNotificationsEnabled();
        } catch (Throwable t) { notifOk = true; }
        if (warnView != null) {
            warnView.setText(notifOk ? ""
                    : "⚠️ Notification permission is off: the notification-bar emergency stop layer is currently dead.\n"
                      + "   Tap ③ Open this app's notification settings below to enable it.\n"
                      + "   (The volume-key gesture, the in-app red button and the Termux widget are unaffected)");
        }
    }

    private void updateIdleBtn() {
        int m = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE).getInt("idleMin", 30);
        idleBtn.setText("Idle auto-stop: " + (m <= 0 ? "off (always listening)" : m + " min idle → stop") + " · tap to switch");
    }

    private void cycleIdle() {
        int m = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE).getInt("idleMin", 30);
        int next = (m == 30) ? 15 : (m == 15) ? 60 : (m == 60) ? 0 : 30;
        getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE).edit().putInt("idleMin", next).apply();
        updateIdleBtn();
        toast(next <= 0 ? "Idle auto-stop is off" : "Auto-stop after " + next + " min idle");
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
