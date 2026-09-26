package io.dsh.bridge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
 * DSH 桥 —— 无障碍服务（自建，源码全公开）。
 *
 * 通信：只监听 127.0.0.1:8788，按行收发 JSON，每个请求都要带正确 token。
 *
 * 自救设计（冗余撤销，四层都在这个文件里）：
 *   ① 音量+ 与 音量- 同时按住 3 秒 → 立即 panic()：关端口 + 关闭自身无障碍（不用看屏幕）
 *   ② 常驻通知栏「DSH 桥正在运行」，带「紧急停止」按钮
 *   ③ 空闲看门狗：超过 N 分钟没有合法指令 → 自动停止监听（默认 30 分钟，可在 App 里调/关）
 *   ④ 任何时刻 App 界面上的大红按钮也是一样的 panic()
 * 另外：本 App **没有开机自启**（无 BOOT_COMPLETED 接收器），重启手机后不会自己跑起来。
 */
public class BridgeService extends AccessibilityService {

    public static final int PORT = 8788;
    public static final String PREFS = "dsh";
    public static final String CHANNEL = "dsh-bridge";
    public static final int NOTIF_ID = 8788;

    /** 供通知栏按钮 / 外部调用的当前实例 */
    public static volatile BridgeService INSTANCE;

    /** 本版本支持的动作（caps 会原样返回，避免小组件靠猜版本） */
    public static final String[] ACTIONS = {
        "ping", "caps", "cur", "netstate", "adbwifi", "stop", "sleep", "panic",
        "shot", "tap", "longpress", "swipe", "text", "key", "ui", "start"
    };

    /** 动态读版本号：改 manifest 后不会像写死字符串那样停留在旧版本 */
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
            if (volUp && volDown) panic("音量键紧急停止");
        }
    };

    private final Runnable idleWatch = new Runnable() {
        @Override public void run() {
            try {
                int idleMin = getSharedPreferences(PREFS, MODE_PRIVATE).getInt("idleMin", 30);
                if (idleMin > 0 && running
                        && System.currentTimeMillis() - lastRequestAt > idleMin * 60000L) {
                    stopListening("空闲 " + idleMin + " 分钟无指令，已自动停止监听");
                }
            } catch (Throwable t) { /* 忽略 */ }
            handler.postDelayed(this, 60000);
        }
    };

    // ---------- 生命周期 ----------
    /** 用户是否明确要求"停着"（软停/sleep 都会置位）。置位后系统重新绑定本服务时**不再自动开监听**，
     *  否则就出现"明明关了它自己又开"。清位只由 resumeListening()（广播唤醒）做。 */
    static boolean userPaused(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("userPaused", false);
    }
    static void setUserPaused(Context c, boolean v) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("userPaused", v).apply();
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
        } catch (Throwable t) { /* 忽略 */ }
        // 关键：被用户停过（软停/sleep）→ 系统重绑也不许自己开监听/起常驻通知
        if (userPaused(this)) {
            running = false;
            Toast.makeText(this, "DSH 桥保持停止（你之前关过它）；点组件 1/7/8 可恢复", Toast.LENGTH_SHORT).show();
            return;
        }
        startListening();
        // 起前台服务：防止被系统/厂商省电策略冻结（否则连广播唤醒都收不到）
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(new Intent(this, BridgeForeground.class));
            else startService(new Intent(this, BridgeForeground.class));
        } catch (Throwable t) { /* 失败不影响无障碍功能 */ }
        handler.removeCallbacks(idleWatch);
        handler.postDelayed(idleWatch, 60000);
        Toast.makeText(this, "DSH 桥已启动：音量+/- 同时按住 3 秒可紧急停止", Toast.LENGTH_LONG).show();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override
    public boolean onUnbind(Intent intent) {
        stopListening("无障碍已关闭");
        INSTANCE = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        stopListening("服务销毁");
        INSTANCE = null;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---------- 自救 ----------
    /** 硬停止：关端口 + 关闭自身无障碍服务（需要重新在系统设置里开启才能再用） */
    public void panic(String reason) {
        stopListening("紧急停止：" + reason);
        try {
            Toast.makeText(this, "DSH 桥已紧急停止（" + reason + "）", Toast.LENGTH_LONG).show();
        } catch (Throwable t) { /* 忽略 */ }
        try { disableSelf(); } catch (Throwable t) { /* 忽略 */ }
    }

    /** 软停止：只关端口，保留无障碍服务（重新打开 App 即可恢复监听） */
    public void stopListening(String why) {
        running = false;
        lastRequestAt = System.currentTimeMillis();
        try { if (serverSocket != null) serverSocket.close(); } catch (Throwable t) { /* 忽略 */ }
        serverSocket = null;
        try { stopService(new Intent(this, BridgeForeground.class)); } catch (Throwable t) { /* 忽略 */ }
        if (why != null && !why.isEmpty()) {
            try { Toast.makeText(this, "DSH 桥：" + why, Toast.LENGTH_SHORT).show(); } catch (Throwable t) { }
        }
    }

    /** 重新开始监听（广播唤醒/App 回到前台时调用）。会清掉"用户要求停着"的标记。 */
    public void resumeListening() {
        setUserPaused(this, false);
        lastRequestAt = System.currentTimeMillis();
        startListening();
        // 起前台服务：防止被系统/厂商省电策略冻结（否则连广播唤醒都收不到）
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(new Intent(this, BridgeForeground.class));
            else startService(new Intent(this, BridgeForeground.class));
        } catch (Throwable t) { /* 失败不影响无障碍功能 */ }
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


    // ---------- 音量键救援手势 ----------
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int c = event.getKeyCode();
        if (c != KeyEvent.KEYCODE_VOLUME_UP && c != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (c == KeyEvent.KEYCODE_VOLUME_UP) volUp = down; else volDown = down;
        handler.removeCallbacks(holdCheck);
        if (volUp && volDown) handler.postDelayed(holdCheck, 3000);
        return false;   // 不拦截，音量键照常工作
    }

    // ---------- 服务端循环 ----------
    private void serve() {
        try {
            serverSocket = new ServerSocket(PORT, 16, InetAddress.getByName("127.0.0.1"));
            while (running) {
                Socket s = null;
                try {
                    s = serverSocket.accept();
                    handle(s);
                } catch (Throwable t) {
                    // 端口被 close 时会抛异常，正常退出
                } finally {
                    if (s != null) { try { s.close(); } catch (Throwable t) { } }
                }
            }
        } catch (Throwable t) {
            // 端口占用等
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
            if (token.isEmpty()) throw new Exception("App 还没初始化，请先打开 DSH 桥 一次");
            if (!token.equals(req.optString("token"))) throw new Exception("token 不对");
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

    // ---------- 动作 ----------
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
            // 服务上下文不能 getDisplay()（会抛 "Context not associated with a display"），改用资源度量
            android.util.DisplayMetrics m = getResources().getDisplayMetrics();
            d.put("w", m.widthPixels);
            d.put("h", m.heightPixels);
            return d;
        }

        if (a.equals("caps")) {           // 能力清单：小组件据此判断新旧版本，不靠猜
            JSONArray arr = new JSONArray();
            for (String s : ACTIONS) arr.put(s);
            d.put("actions", arr);
            d.put("ver", verName(this));
            d.put("sleep", true);         // 支持「真停」（关端口+停前台+关无障碍，不会自恢复）
            d.put("wake_reauth", true);   // 支持带 token 的唤醒重新授权无障碍
            d.put("stop_is_durable", true); // stop 软停后**不会**被系统重绑自动拉起来（v1.8）
            return d;
        }

        if (a.equals("sleep")) {          // 真停：关端口 + 停前台服务 + 关闭自身无障碍
            setUserPaused(this, true);
            handler.post(new Runnable() { @Override public void run() {
                stopListening("收到休眠指令：已彻底停止（不会自恢复）");
                try { disableSelf(); } catch (Throwable t) { /* 忽略 */ }
            }});
            d.put("slept", true);
            return d;
        }

        if (a.equals("stop")) {           // 软停：关端口 + 记住"别再自己开"（进程留着，广播一叫就回来）
            setUserPaused(this, true);
            handler.post(new Runnable() { @Override public void run() { stopListening("收到停止指令（已记住：不自动恢复）"); } });
            d.put("stopped", true);
            d.put("paused", true);
            return d;
        }

        if (a.equals("panic")) {          // 硬停：关端口 + 关闭无障碍
            handler.post(new Runnable() { @Override public void run() { panic("收到遥控紧急停止"); } });
            d.put("panicked", true);
            return d;
        }

        if (a.equals("cur")) { d.put("pkg", currentPkg()); return d; }

        // ---- 网络状态：Wi-Fi 开关 + 是否真的能上网（直连测试，不依赖任何 API）----
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

        // ---- 开关「无线调试」：唯一需要 WRITE_SECURE_SETTINGS 的地方 ----
        if (a.equals("adbwifi")) {
            int v = req.optInt("value", 1);
            try {
                android.provider.Settings.Global.putInt(getContentResolver(), "adb_wifi_enabled", v == 0 ? 0 : 1);
            } catch (Throwable t2) {
                throw new Exception("需要 WRITE_SECURE_SETTINGS（用 adb: pm grant io.dsh.bridge android.permission.WRITE_SECURE_SETTINGS）: " + t2.getMessage());
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
            if (focus == null) throw new Exception("当前没有聚焦的输入框");
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
            String pkg = req.optString("value", "");
            Intent it = getPackageManager().getLaunchIntentForPackage(pkg);
            if (it == null) throw new Exception("找不到应用: " + pkg);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
            d.put("launched", pkg);
            return d;
        }

        throw new Exception("未知动作: " + a);
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
        if (Build.VERSION.SDK_INT < 30) throw new Exception("系统低于 Android 11，不支持无障碍截图");
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] out = new String[1];
        final String[] err = new String[1];
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult result) {
                try {
                    Bitmap hw = Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                    Bitmap sw = hw == null ? null : hw.copy(Bitmap.Config.ARGB_8888, false);
                    result.getHardwareBuffer().close();
                    if (sw == null) { err[0] = "截图转换失败"; }
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
                err[0] = "截图失败 code=" + errorCode;
                latch.countDown();
            }
        });
        latch.await(10, TimeUnit.SECONDS);
        if (out[0] == null) throw new Exception(err[0] == null ? "截图超时" : err[0]);
        return out[0];
    }
}
