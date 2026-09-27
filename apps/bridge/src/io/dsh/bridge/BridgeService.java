package io.dsh.bridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Display;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * DSH Bridge — the accessibility service (self-built, source fully public).
 *
 * Communication: listens on 127.0.0.1:8788 only, exchanges line-based JSON, and every request must carry the right token.
 *
 * Self-rescue design (redundant revocation, all four layers live in this file):
 *   ① Hold volume-up and volume-down together for 3s → panic() at once: close the port + disable its own accessibility (no need to look at the screen)
 *   ② A persistent "DSH Bridge is running" notification, with an "emergency stop" button
 *   ③ Idle watchdog: no valid command for N minutes → stop listening automatically (default 30 min, adjustable/disableable in the app)
 *   ④ The big red button on the app screen performs the same panic() at any time
 * Also: this app has **no boot autostart** (no BOOT_COMPLETED receiver); it does not come up by itself after a phone restart.
 */
public class BridgeService extends AccessibilityService {

    public static final int PORT = 8788;
    public static final String PREFS = "dsh";
    /** 「不息屏」用的值：30 分钟 —— 系统设置菜单里真有这一档，ROM 不会当非法值丢掉。 */
    private static final int KEEP_MS = 30 * 60 * 1000;
    public static final String CHANNEL = "dsh-bridge";
    public static final int NOTIF_ID = 8788;

    /** Current instance, for the notification button / outside callers */
    public static volatile BridgeService INSTANCE;

    /** Actions supported by this version (caps returns them verbatim so widgets need not guess the version) */
    public static final String[] ACTIONS = {
        "ping", "caps", "cur", "netstate", "adbwifi", "stop", "sleep", "panic", "lang",
        "shot", "tap", "longpress", "swipe", "text", "key", "ui", "start"
    };

    /** Reads the version dynamically: after a manifest bump it will not stay stuck on the old version the way a hard-coded string does */
    static String verName(android.content.Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Throwable t) { return "?"; }
    }

    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile long lastRequestAt = System.currentTimeMillis();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean volUp = false;
    private volatile boolean volDown = false;

    private final Runnable holdCheck = new Runnable() {
        @Override public void run() {
            if (volUp && volDown) panic(Lang.t("volume-key emergency stop"));
        }
    };

    private final Runnable idleWatch = new Runnable() {
        @Override public void run() {
            try {
                int idleMin = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("idleMin", 30);
                if (idleMin > 0 && running
                        && System.currentTimeMillis() - lastRequestAt > idleMin * 60000L) {
                    // Silent on purpose (user, 2026-09-27): an idle stop is the app deciding to save power, not an
                    // event the user needs to be told about — it just waits to be started again.
                    stopListening(null);
                }
            } catch (Throwable t) { /* ignore */ }
            handler.postDelayed(this, 60000);
        }
    };

    // ---------- Lifecycle ----------
    /** Whether the user explicitly asked for it to "stay stopped" (both a soft stop and sleep set this).
     *  Once set, rebinding this service no longer starts listening automatically, otherwise you get
     *  "I turned it off and it switched itself back on". Only resumeListening() (the wake broadcast) clears it. */
    static boolean userPaused(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("userPaused", false);
    }
    static void setUserPaused(Context c, boolean v) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("userPaused", v).apply();
    }

    /**
     * Show a toast at most once per `minMs`, keyed by `key`.
     *
     * Why: this ROM rebinds the accessibility service periodically, and onServiceConnected used to
     * replay the same LENGTH_LONG "started" toast (and the "stays stopped" one) on every rebind — the
     * user reported it as a repeating prompt. The state did not change, so the message should not
     * reappear either. The persistent notification gets setOnlyAlertOnce for the same reason.
     */
    void toastOnce(String key, String text, long minMs) {
        try {
            android.content.SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            long now = System.currentTimeMillis();
            if (now - sp.getLong("toast_at_" + key, 0L) < minMs) return;
            sp.edit().putLong("toast_at_" + key, now).apply();
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) { /* a missing toast must never break the service */ }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        INSTANCE = this;
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info != null) {
                info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                setServiceInfo(info);
            }
        } catch (Throwable t) { /* ignore */ }
        // Key: if the user stopped it (soft stop / sleep), a system rebind must not start listening or raise the persistent notification by itself
        if (userPaused(this)) {
            running = false;
            toastOnce("staysStopped", Lang.t("DSH Bridge stays stopped (you turned it off earlier); tap widgets 1/7/8 to resume"), 30 * 60 * 1000L);
            return;
        }
        startListening();
        // Start the foreground service: prevents freezing by system/vendor battery savers (otherwise even the wake broadcast is missed)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(new Intent(this, BridgeForeground.class));
            else startService(new Intent(this, BridgeForeground.class));
        } catch (Throwable t) { /* failure does not affect accessibility */ }
        handler.removeCallbacks(idleWatch);
        handler.postDelayed(idleWatch, 60000);
        toastOnce("started", Lang.t("DSH Bridge started: hold volume +/- together for 3s for emergency stop"), 10 * 60 * 1000L);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override
    public boolean onUnbind(Intent intent) {
        stopListening(Lang.t("accessibility turned off"));
        INSTANCE = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        stopListening(Lang.t("service destroyed"));
        INSTANCE = null;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------- Self-rescue ----------
    /** Hard stop: close the port + disable its own accessibility service (it must be re-enabled in system settings before it can be used again) */
    /** @param reason already translated (call sites pass Lang.t("…")): the table is keyed by the exact
     *  English literal, so translating a *variable* can never hit an entry — tools/i18n-audit rejects it. */
    public void panic(String reason) {
        stopListening(null);   // one message, not two: panic() speaks for this stop
        try {
            Toast.makeText(this, Lang.t("DSH Bridge emergency-stopped: ") + reason, Toast.LENGTH_LONG).show();
        } catch (Throwable t) { /* ignore */ }
        try { disableSelf(); } catch (Throwable t) { /* ignore */ }
    }

    /** Soft stop: closes the port only, keeps the accessibility service (reopen the app to resume listening) */
    public void stopListening(String why) {
        running = false;
        lastRequestAt = System.currentTimeMillis();
        try { if (serverSocket != null) serverSocket.close(); } catch (Throwable t) { /* ignore */ }
        serverSocket = null;
        try { stopService(new Intent(this, BridgeForeground.class)); } catch (Throwable t) { /* ignore */ }
        if (why != null && !why.isEmpty()) {
            try { Toast.makeText(this, Lang.t("DSH Bridge: ") + why, Toast.LENGTH_SHORT).show(); } catch (Throwable t) { }
        }
    }

    /** Starts listening again (called on a wake broadcast / when the app returns to the foreground). Clears the "user asked it to stay stopped" flag. */
    public void resumeListening() {
        setUserPaused(this, false);
        lastRequestAt = System.currentTimeMillis();
        startListening();
        // Start the foreground service: prevents freezing by system/vendor battery savers (otherwise even the wake broadcast is missed)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(new Intent(this, BridgeForeground.class));
            else startService(new Intent(this, BridgeForeground.class));
        } catch (Throwable t) { /* failure does not affect accessibility */ }
    }

    public boolean isListening() {
        return running;
    }

    private void startListening() {
        if (running) return;
        running = true;
        if (serverThread == null || !serverThread.isAlive()) {
            serverThread = new Thread(new Runnable() {
                @Override public void run() { serve(); }
            }, "dsh-bridge");
            serverThread.setDaemon(true);
            serverThread.start();
        }
    }


    // ---------- Volume-key rescue gesture ----------
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int c = event.getKeyCode();
        if (c != KeyEvent.KEYCODE_VOLUME_UP && c != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (c == KeyEvent.KEYCODE_VOLUME_UP) volUp = down; else volDown = down;
        handler.removeCallbacks(holdCheck);
        if (volUp && volDown) handler.postDelayed(holdCheck, 3000);
        return false;   // don't intercept; volume keys keep working
    }

    // ---------- Server loop ----------
    private void serve() {
        try {
            serverSocket = new ServerSocket(PORT, 16, InetAddress.getByName("127.0.0.1"));
            while (running) {
                Socket s = null;
                try {
                    s = serverSocket.accept();
                    handle(s);
                } catch (Throwable t) {
                    // Closing the port throws here; that is a normal exit
                } finally {
                    if (s != null) { try { s.close(); } catch (Throwable t) { } }
                }
            }
        } catch (Throwable t) {
            // port already in use, etc.
        }
    }

    private void handle(Socket s) throws Exception {
        s.setSoTimeout(20000);
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        String line = in.readLine();
        if (line == null) return;
        JSONObject req = new JSONObject(line);
        JSONObject res = new JSONObject();
        String token = getSharedPreferences(PREFS, MODE_PRIVATE).getString("token", "");
        try {
            if (token.isEmpty()) throw new Exception("The app is not initialized yet; open DSH Bridge once first");
            if (!token.equals(req.optString("token"))) throw new Exception("Wrong token");
            lastRequestAt = System.currentTimeMillis();
            res.put("ok", true);
            res.put("data", exec(req));
        } catch (Throwable t) {
            res.put("ok", false);
            res.put("error", t.getMessage() == null ? t.toString() : t.getMessage());
        }
        OutputStream os = s.getOutputStream();
        os.write((res.toString() + "\n").getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    // ---------- Actions ----------
    private JSONObject exec(JSONObject req) throws Exception {
        String a = req.optString("action");
        JSONObject d = new JSONObject();

        if (a.equals("ping")) {
            d.put("pong", true);
            d.put("sdk", Build.VERSION.SDK_INT);
            d.put("pkg", currentPkg());
            d.put("listening", running);
            d.put("ver", verName(this));
            d.put("paused", userPaused(this));
            // A service context cannot call getDisplay() (it throws "Context not associated with a display"); use resource metrics instead
            android.util.DisplayMetrics m = getResources().getDisplayMetrics();
            d.put("w", m.widthPixels);
            d.put("h", m.heightPixels);
            return d;
        }

        if (a.equals("caps")) {           // capability list: widgets use this to tell versions apart instead of guessing
            JSONArray arr = new JSONArray();
            for (String s : ACTIONS) arr.put(s);
            d.put("actions", arr);
            d.put("ver", verName(this));
            d.put("sleep", true);         // supports a "real stop" (close port + stop foreground + disable accessibility, no self-recovery)
            d.put("wake_reauth", true);   // supports a token-carrying wake to re-authorize accessibility
            d.put("stop_is_durable", true); // after a soft stop the system rebind does **not** pull it back up (v1.8)
            return d;
        }

        if (a.equals("lang")) {           // the Termux side pushes the language in (the bridge cannot read ~/.dsh-lang)
            String lv = req.optString("value", "");
            if (lv.equals("zh") || lv.equals("en") || lv.equals("auto")) Lang.setMode(this, lv);
            d.put("lang", Lang.mode());
            return d;
        }

        if (a.equals("sleep")) {          // real stop: close port + stop foreground service + disable its own accessibility
            setUserPaused(this, true);
            handler.post(new Runnable() { @Override public void run() {
                stopListening(Lang.t("sleep command received: fully stopped (no self-recovery)"));
                try { disableSelf(); } catch (Throwable t) { /* ignore */ }
            }});
            d.put("slept", true);
            return d;
        }

        if (a.equals("stop")) {           // soft stop: close port + remember "do not start yourself again" (process stays; one broadcast brings it back)
            setUserPaused(this, true);
            handler.post(new Runnable() { @Override public void run() { stopListening(Lang.t("stop command received (remembered: no automatic recovery)")); } });
            d.put("stopped", true);
            d.put("paused", true);
            return d;
        }

        if (a.equals("panic")) {          // hard stop: close port + disable accessibility
            handler.post(new Runnable() { @Override public void run() { panic(Lang.t("remote emergency stop received")); } });
            d.put("panicked", true);
            return d;
        }

        if (a.equals("cur")) { d.put("pkg", currentPkg()); return d; }

        // ---- Network state: Wi-Fi switch + whether the internet really works (direct connect test, no API involved) ----
        if (a.equals("netstate")) {
            try {
                d.put("wifi_on", android.provider.Settings.Global.getInt(getContentResolver(), "wifi_on", -1));
            } catch (Throwable t2) { d.put("wifi_on", -1); }
            try {
                d.put("adb_wifi", android.provider.Settings.Global.getInt(getContentResolver(), "adb_wifi_enabled", -1));
            } catch (Throwable t2) { d.put("adb_wifi", -1); }
            boolean online = false; long ms = -1;
            try {
                long t0 = System.currentTimeMillis();
                java.net.Socket sk = new java.net.Socket();
                sk.connect(new java.net.InetSocketAddress("1.1.1.1", 443), 2500);
                sk.close();
                online = true; ms = System.currentTimeMillis() - t0;
            } catch (Throwable t2) { online = false; }
            d.put("online", online);
            d.put("rttMs", ms);
            return d;
        }

        // ---- Toggling wireless debugging: the only place that needs WRITE_SECURE_SETTINGS ----
        if (a.equals("screen")) {
            // 用桥做自动化之前把屏幕「不息屏」，用完调回来。
            //
            // Why it lives here: setting the screen timeout needs a permission only this app holds
            // (WRITE_SECURE_SETTINGS, granted once over adb). The user asked for exactly this
            // practice after a run was wasted on a screen that had gone to sleep mid-way.
            //
            // Two mechanisms, because they need different permissions:
            //   ① Settings.System.SCREEN_OFF_TIMEOUT  — the real "息屏时间"; needs WRITE_SETTINGS,
            //      which this app does not hold, so it usually throws.
            //   ② Settings.Global.STAY_ON_WHILE_PLUGGED_IN — writable with WRITE_SECURE_SETTINGS;
            //      keeps the screen on while charging, which is the usual state during a bridge run.
            // ① 是用户说的那个设置项，所以先试它；失败就退到 ②，并在回复里说明用的是哪个，
            // 不假装设成了用户要的那个。
            String mode = req.optString("value", "status");
            android.content.SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            android.content.ContentResolver cr = getContentResolver();
            if (mode.equals("keep")) {
                // ⚠ 幂等：已经 keep 过就不要再存一次 —— 再存存到的是"已经被改过的值"，
                //   还原时就会把屏幕永久设成最大值（实测踩过：手动 keep 一次 + 装包工具又 keep 一次，
                //   原值 60000 被覆盖成 2147483647）。原值只认第一次。
                boolean alreadyKept = sp.getBoolean("screen_off_kept", false) || sp.getBoolean("stay_on_kept", false);
                // **先试「充电时屏幕不休眠」**（Settings.Global.STAY_ON_WHILE_PLUGGED_IN）：
                // 它需要的是本 App 真有的 WRITE_SECURE_SETTINGS，而且**完全不碰用户在设置里看得见的
                // 「自动锁屏」**——之前动不动去改那一项，用户看到它 1 分钟 ↔ 30 分钟来回跳，自然觉得"又有问题"。
                // 手机在装包时基本都在充电（实测这台就是），所以这条足够用；没充电才退回去改超时。
                boolean charging = false;
                try {
                    android.content.Intent bt = registerReceiver(null,
                            new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
                    int st = bt == null ? -1 : bt.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
                    charging = st == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                            || st == android.os.BatteryManager.BATTERY_STATUS_FULL;
                } catch (Throwable t) { /* 判断不了就当没充电 */ }
                if (charging) {
                    try {
                        int cur = android.provider.Settings.Global.getInt(cr, android.provider.Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0);
                        if (!alreadyKept) sp.edit().putInt("stay_on_saved", cur).putBoolean("stay_on_kept", true).apply();
                        android.provider.Settings.Global.putInt(cr, android.provider.Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 7);
                        d.put("method", "stay_on_while_plugged_in");
                        d.put("was", cur);
                        d.put("kept", true);
                        d.put("note", "charging: kept awake via stay-on-while-plugged-in; SCREEN_OFF_TIMEOUT (自动锁屏) untouched");
                        return d;
                    } catch (Throwable t) { /* 退回去改超时 */ }
                }
                try {
                    int cur = android.provider.Settings.System.getInt(cr, android.provider.Settings.System.SCREEN_OFF_TIMEOUT);
                    if (!alreadyKept) sp.edit().putInt("screen_off_saved", cur).putBoolean("screen_off_kept", true).apply();
                    // ⚠ 用**系统菜单里真实存在的值**（30 分钟），不要写 Integer.MAX_VALUE：
                    //   实测写 2147483647 读回来是那个数，但屏幕照旧按原时间熄屏 —— ROM 把它当非法值忽略了。
                    //   用户的原话是"你这个息屏时间调节没有效果"，根因就在这里。
                    android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_OFF_TIMEOUT, KEEP_MS);
                    d.put("method", "screen_off_timeout");
                    d.put("was", cur);
                    d.put("now", KEEP_MS);
                    d.put("minutes", KEEP_MS / 60000);
                } catch (Throwable t1) {
                    try {
                        int cur = android.provider.Settings.Global.getInt(cr, android.provider.Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0);
                        sp.edit().putInt("stay_on_saved", cur).putBoolean("stay_on_kept", true).apply();
                        android.provider.Settings.Global.putInt(cr, android.provider.Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 7);
                        d.put("method", "stay_on_while_plugged_in");
                        d.put("was", cur);
                        d.put("note", "SCREEN_OFF_TIMEOUT needs WRITE_SETTINGS; used stay-awake-while-charging instead");
                    } catch (Throwable t2) {
                        throw new Exception("cannot keep the screen on: " + t1.getMessage() + " / " + t2.getMessage());
                    }
                }
                d.put("kept", true);
                return d;
            }
            if (mode.equals("restore")) {
                boolean did = false;
                if (sp.getBoolean("screen_off_kept", false)) {
                    try {
                        android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_OFF_TIMEOUT,
                                sp.getInt("screen_off_saved", 60000));
                        sp.edit().putBoolean("screen_off_kept", false).apply();
                        d.put("method", "screen_off_timeout"); d.put("restored", sp.getInt("screen_off_saved", 60000));
                        did = true;
                    } catch (Throwable t) { /* fall through to the other mechanism */ }
                }
                if (sp.getBoolean("stay_on_kept", false)) {
                    try {
                        android.provider.Settings.Global.putInt(cr, android.provider.Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                                sp.getInt("stay_on_saved", 0));
                        sp.edit().putBoolean("stay_on_kept", false).apply();
                        if (!did) { d.put("method", "stay_on_while_plugged_in"); d.put("restored", sp.getInt("stay_on_saved", 0)); }
                        did = true;
                    } catch (Throwable t) { /* nothing else to try */ }
                }
                d.put("restored_ok", did);
                return d;
            }
            if (mode.startsWith("set:")) {
                // 显式设成某个毫秒值 —— 用于把设置改回用户原来的样子（幂等/还原都救不了被覆盖过的原值）
                int ms = Integer.parseInt(mode.substring(4));
                try {
                    android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_OFF_TIMEOUT, ms);
                    sp.edit().putBoolean("screen_off_kept", false).apply();
                    d.put("set", ms);
                    d.put("screen_off_timeout", android.provider.Settings.System.getInt(cr, android.provider.Settings.System.SCREEN_OFF_TIMEOUT));
                } catch (Throwable t) {
                    throw new Exception("cannot set the screen timeout: " + t.getMessage());
                }
                return d;
            }
            // status
            try {
                d.put("screen_off_timeout", android.provider.Settings.System.getInt(cr, android.provider.Settings.System.SCREEN_OFF_TIMEOUT));
            } catch (Throwable t) { d.put("screen_off_timeout", -1); }
            try {
                d.put("stay_on_while_plugged_in", android.provider.Settings.Global.getInt(cr, android.provider.Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0));
            } catch (Throwable t) { d.put("stay_on_while_plugged_in", -1); }
            d.put("kept", sp.getBoolean("screen_off_kept", false) || sp.getBoolean("stay_on_kept", false));
            return d;
        }
        if (a.equals("adbwifi")) {
            int v = req.optInt("value", 1);
            try {
                android.provider.Settings.Global.putInt(getContentResolver(), "adb_wifi_enabled", v == 0 ? 0 : 1);
            } catch (Throwable t2) {
                throw new Exception("Needs WRITE_SECURE_SETTINGS (via adb: pm grant io.dsh.bridge android.permission.WRITE_SECURE_SETTINGS): " + t2.getMessage());
            }
            int now = android.provider.Settings.Global.getInt(getContentResolver(), "adb_wifi_enabled", -1);
            d.put("adb_wifi", now);
            return d;
        }
        if (a.equals("shot")) { d.put("png", screenshotBase64()); return d; }

        if (a.equals("tap") || a.equals("longpress")) {
            float x = (float) req.optDouble("x"), y = (float) req.optDouble("y");
            long ms = a.equals("longpress") ? (long) req.optDouble("ms", 800) : 60L;
            Path p = new Path(); p.moveTo(x, y);
            d.put("done", gesture(p, 0, ms));
            d.put("at", new JSONArray().put((int) x).put((int) y));
            return d;
        }

        if (a.equals("swipe")) {
            float x1 = (float) req.optDouble("x1"), y1 = (float) req.optDouble("y1");
            float x2 = (float) req.optDouble("x2"), y2 = (float) req.optDouble("y2");
            long ms = (long) req.optDouble("ms", 300);
            Path p = new Path(); p.moveTo(x1, y1); p.lineTo(x2, y2);
            d.put("done", gesture(p, 0, ms));
            return d;
        }

        if (a.equals("text")) {
            String v = req.optString("value", "");
            AccessibilityNodeInfo focus = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focus == null) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                focus = root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            }
            if (focus == null) throw new Exception("No input field is focused right now");
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, v);
            d.put("ok", focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args));
            return d;
        }

        if (a.equals("key")) {
            String k = req.optString("value", "back").toLowerCase();
            int act = AccessibilityService.GLOBAL_ACTION_BACK;
            if (k.equals("home")) act = AccessibilityService.GLOBAL_ACTION_HOME;
            else if (k.equals("recents")) act = AccessibilityService.GLOBAL_ACTION_RECENTS;
            else if (k.equals("notifications")) act = AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS;
            d.put("done", performGlobalAction(act));
            return d;
        }

        if (a.equals("ui")) {
            String kw = req.optString("keyword", "").toLowerCase();
            int limit = req.optInt("limit", 40);
            AccessibilityNodeInfo root = getRootInActiveWindow();
            JSONArray arr = new JSONArray();
            if (root != null) {
                ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<AccessibilityNodeInfo>();
                queue.add(root);
                int guard = 0;
                while (!queue.isEmpty() && arr.length() < limit && guard++ < 4000) {
                    AccessibilityNodeInfo n = queue.poll();
                    if (n == null) continue;
                    String txt = n.getText() == null ? "" : n.getText().toString();
                    String desc = n.getContentDescription() == null ? "" : n.getContentDescription().toString();
                    String id = n.getViewIdResourceName() == null ? "" : n.getViewIdResourceName();
                    String cls = n.getClassName() == null ? "" : n.getClassName().toString();
                    String blob = (txt + " " + desc + " " + id + " " + cls).toLowerCase();
                    if (kw.isEmpty() || blob.contains(kw)) {
                        if (!txt.isEmpty() || !desc.isEmpty() || !id.isEmpty()) {
                            Rect r = new Rect();
                            n.getBoundsInScreen(r);
                            JSONObject o = new JSONObject();
                            o.put("text", cut(txt, 60));
                            o.put("desc", cut(desc, 40));
                            o.put("id", cut(id, 60));
                            o.put("cls", cls.contains(".") ? cls.substring(cls.lastIndexOf('.') + 1) : cls);
                            o.put("center", new JSONArray().put(r.centerX()).put(r.centerY()));
                            o.put("bounds", new JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom));
                            o.put("clickable", n.isClickable());
                            arr.put(o);
                        }
                    }
                    for (int i = 0; i < n.getChildCount(); i++) {
                        AccessibilityNodeInfo c = n.getChild(i);
                        if (c != null) queue.add(c);
                    }
                }
            }
            d.put("nodes", arr);
            d.put("count", arr.length());
            return d;
        }

        if (a.equals("start")) {
            String v = req.optString("value", "").trim();
            if (v.isEmpty()) throw new Exception("start needs value=<pkg> or value=<pkg>/<activity>");
            int slash = v.indexOf('/');
            String pkg = slash > 0 ? v.substring(0, slash) : v;
            String before = currentPkg();
            Intent it;
            if (slash > 0) {
                // Explicit component. Some apps only come up through their **activity-alias**: for
                // Clash Meta a plain MAIN/LAUNCHER intent resolves and startActivity() reports no
                // error, yet no window ever appears, while the alias brings the same task to the
                // front (measured 2026-09-27 on V2463A: `start com.github.metacubex.clash.meta` did
                // nothing, `am start -n <pkg>/com.github.kr328.clash.MainActivityAlias` worked).
                String c = v.substring(slash + 1);
                if (c.startsWith(".")) c = pkg + c;
                it = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(new ComponentName(pkg, c));
            } else {
                it = getPackageManager().getLaunchIntentForPackage(pkg);
                if (it == null) {
                    // Fallback for anything whose launcher entry is not a plain MAIN/LAUNCHER intent
                    // (a WebAPK/PWA, or an app the <queries> block still hides).
                    it = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg);
                    if (getPackageManager().resolveActivity(it, 0) == null)
                        throw new Exception("App not found: " + pkg
                                + " (no launchable activity; if it is an activity-alias, pass value=<pkg>/<activity>)");
                }
            }
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
            d.put("launched", v);
            // Telling whether the window actually came up matters: a start can be accepted and still
            // produce nothing visible, and a bare "launched" would then be a lie (that is exactly how
            // this action looked "fixed" while doing nothing). Poll the foreground for up to ~1.2s.
            String after = before;
            for (int i = 0; i < 12 && !after.equals(pkg); i++) {
                try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
                after = currentPkg();
            }
            d.put("foreground_before", before);
            d.put("foreground", after);
            d.put("on_top", after.equals(pkg));
            return d;
        }

        throw new Exception("Unknown action: " + a);
    }

    private static String cut(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }

    private String currentPkg() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return "";
        return root.getPackageName().toString();
    }

    private boolean gesture(Path path, long startTime, long duration) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] done = new boolean[1];
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, startTime, duration);
        GestureDescription gd = new GestureDescription.Builder().addStroke(stroke).build();
        GestureResultCallback cb = new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription d) { done[0] = true; latch.countDown(); }
            @Override public void onCancelled(GestureDescription d) { done[0] = false; latch.countDown(); }
        };
        if (!dispatchGesture(gd, cb, null)) return false;
        latch.await(6, TimeUnit.SECONDS);
        return done[0];
    }

    private String screenshotBase64() throws Exception {
        if (Build.VERSION.SDK_INT < 30) throw new Exception("Android 11 or newer is required for accessibility screenshots");
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] out = new String[1];
        final String[] err = new String[1];
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult result) {
                try {
                    Bitmap hw = Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                    Bitmap sw = hw == null ? null : hw.copy(Bitmap.Config.ARGB_8888, false);
                    result.getHardwareBuffer().close();
                    if (sw == null) { err[0] = "Screenshot conversion failed"; }
                    else {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        sw.compress(Bitmap.CompressFormat.PNG, 100, bos);
                        sw.recycle();
                        out[0] = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                    }
                } catch (Throwable t) { err[0] = String.valueOf(t.getMessage()); }
                finally { latch.countDown(); }
            }
            @Override public void onFailure(int errorCode) {
                err[0] = "Screenshot failed code=" + errorCode;
                latch.countDown();
            }
        });
        latch.await(10, TimeUnit.SECONDS);
        if (out[0] == null) throw new Exception(err[0] == null ? "Screenshot timed out" : err[0]);
        return out[0];
    }
}
