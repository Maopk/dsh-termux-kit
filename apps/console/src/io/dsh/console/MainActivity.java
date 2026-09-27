package io.dsh.console;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.InputType;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Console main screen (v0.5).
 *
 * Layout:
 *   · Top: title + version, three lamps, one detail line, busy row (spinner + "waited Ns" timer);
 *   · Shortcut row: refresh status / **log** (with an unread-count badge);
 *   · Body: **grouped into function sections** (start·stop / channels / maintenance / emergency),
 *     each with a small blue heading;
 *   · Bottom: a single Lang.t("last result") summary line (tapping it opens the log); no permanent big output
 *     box any more — that box used to sit blank when there was no output and looked broken; everything
 *     now lives in the log.
 *
 * Feedback rules (settled after users said it Lang.t("felt like a shell")):
 *   · Tap a button → immediate toast + busy state (buttons greyed out) + spinner + timer;
 *   · Result arrives → that summary line changes color (✅ green / ⚠ red) and counts toward the log badge;
 *   · No callback after 45s → list the likely causes explicitly;
 *   · The log is never blank: with no entries it says exactly where to tap.
 */
public class MainActivity extends Activity {
    /**
     * The version shown in the title bar and the log header, read from the package manager.
     *
     * ⚠ This used to be the literal "v1.8" and was forgotten on the next bump: the installed
     * package said 1.9 while the app itself still displayed v1.8 (caught on the device
     * 2026-09-27, with the 1.9 APK already installed and verified). A displayed version that
     * disagrees with the installed one is exactly the kind of claim this project must not
     * make, so it is resolved at runtime instead — the same thing the Bridge app does.
     * `getPackageManager()` needs a Context, so this is filled in onCreate and only ever
     * degrades to "?".
     */
    private String ver = "?";
    private TextView updateRow;       // 关于：项目地址 + 有没有新版本
    private TextView projRow;
    private static final String PROJECT_URL = "https://github.com/Maopk/dsh-termux-kit";
    private long lastUpdateCheck = 0;
    private static final int TIMEOUT_S = 45;
    private static final int MAX_HISTORY = 60;

    private TextView lamps, line, busyText, lastLine;
    private ProgressBar spinner;
    private LinearLayout busyRow;
    private Button logBtn;

    // "Install password authorization" switch (user requirement: authorization is a switch, not a button)
    private Switch authSwitch;
    private TextView authLine;
    /** Raw `dsh-auth-pass status` text: for the long-press sheet and the log — never for the subtitle. */
    private String authDetail = "";
    /** Last state we logged, so a plain onResume does not append the same line again and again. */
    private boolean lastAuthKnown = false, lastAuthOn = false;
    /** Don't treat onCheckedChanged as a user action when the program sets the switch state */
    private boolean authSyncing = false;
    private boolean authKnown = false;
    private boolean authOn = false;

    private final List<Button> buttons = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private int unread = 0;

    // Log panel: its own screen, no longer the permanent output box at the bottom
    private AlertDialog logDialog;
    private Switch bridgeSwitch;
    /** Category ids the user collapsed; remembered across launches. */
    private java.util.Set<String> collapsedCats = new java.util.HashSet<String>();
    private TextView bridgeStateView;
    /** Guards the switch against firing a task while render() is setting it from the status. */
    private boolean bridgeSwitchBusy;
    private TextView logBody;
    private ScrollView logScroll;

    private String lastSummary = "";
    private boolean lastOk = true;

    private boolean pending = false;
    private long pendingSince = 0;
    private String pendingLabel = "";
    /** The wait window (seconds) for this run, and whether it is a Lang.t("query") (short timeout, may auto-retry once) */
    private int pendingWaitS = 45;
    private boolean pendingQuery = false;
    private boolean queryRetried = false;
    private String pendingCmdId = "", pendingCmd = "";
    private boolean pendingIsStatus = false;
    /** Whether this wait was Lang.t("sent silently") (auto refresh): if so the completion line skips the log too, to avoid spam. */
    private boolean pendingSilent = false;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!pending) return;
            int s = (int) ((System.currentTimeMillis() - pendingSince) / 1000);
            busyText.setText(Lang.t("Running: ") + pendingLabel + Lang.t("　waited ") + s + Lang.t("s (usually ≤") + pendingWaitS + Lang.t("s)"));
            if (s >= pendingWaitS) {
                // A query (refresh status / auth query) timed out: resend once automatically first — a phone
                // Lang.t("being briefly busy") is common. Measured: during a big backup the load hit 7 and the app's
                // request silently failed once; a resend fixes it (2026-09-27 00:10).
                if (pendingQuery && !queryRetried) {
                    queryRetried = true;
                    busyText.setText(Lang.t("No reply the first time, resending: ") + pendingLabel);
                    pendingSince = System.currentTimeMillis();
                    TermuxRunner.run(MainActivity.this, pendingCmdId, pendingLabel, pendingCmd, pendingIsStatus);
                    ui.postDelayed(this, 1000);
                    return;
                }
                finishPending(Lang.t("⚠ No callback from Termux after ") + pendingWaitS + Lang.t("s (this one usually answers within ")
                        + pendingWaitS + Lang.t("s). Likely causes, most common first:")
                        + "\n   · The phone was busy at the time (a backup / self-check running in the background) — most common, just tap Refresh status and retry"
                        + "\n   · This is a long task (backups and restarts are slow anyway) — give it more time"
                        + "\n   · RUN_COMMAND permission not granted, or allow-external-apps is disabled in Termux"
                        + "\n   · The system blocked starting a service from the background (most common on widget taps)");
                return;
            }
            ui.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver refresh = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            long ms = pending ? System.currentTimeMillis() - pendingSince : 0;
            String label = pendingLabel.isEmpty() ? Lang.t("Task") : pendingLabel;
            int code = i.getIntExtra("exit", -1);
            String cmdId = i.getStringExtra("cmdId");
            if ("lang_query".equals(cmdId)) {
                // The app cannot read ~/.dsh-lang (Termux's private dir), so it asks Termux. This is the
                // piece that keeps the app, the widgets and the page panel on ONE language.
                String lv = i.getStringExtra("output");
                if (lv != null) {
                    lv = lv.trim();
                    if ((lv.equals("zh") || lv.equals("en") || lv.equals("auto")) && !lv.equals(Lang.mode())) {
                        Lang.setMode(MainActivity.this, lv);
                        recreate();
                    }
                }
                return;
            }
            if ("update_check".equals(cmdId)) {
                // Termux 侧查完仓库，把 JSON 原样回传；这里只认字段，不做网络（本 App 没有网络权限）
                showUpdateResult(i.getStringExtra("output"));
                return;
            }
            if ("installpass_query".equals(cmdId)) {
                // Auth status query: updates the switch only — no log, no summary, pending untouched
                setAuthUi(true, code == 0, i.getStringExtra("output"));
                return;
            }
            if (i.getBooleanExtra("isStatus", false)) {
                // ⚠ Read pending before clearing it (v0.6 cleared it first and read afterwards, so wasPending
                //   was always false → the status-refresh summary line **never appeared**; only caught on a
                //   real device after tapping twice).
                // Only **the run the user started** updates the Lang.t("last result"):
                //   after a task finishes, the Termux side sends one extra status refresh, and its callback
                //   rewrote a just-failed task as Lang.t("✅ Refresh status exit=0") — as if the run had never failed
                //   at all (caught on a real device in v0.5).
                boolean wasPending = pending;
                boolean silent = pendingSilent;
                String statusLabel = pendingLabel.isEmpty() ? Lang.t("Refresh status") : pendingLabel;
                if (pending) finishPending(null);
                if (wasPending) {
                    setLast(statusLabel, code, ms, code == 0);
                    // ⚠ The log needs evidence too: status queries used to have **only a "sent" line and no
                    //   completion line**, which looked like nobody ever replied (this fooled a user at
                    //   2026-09-27 00:17). Auto refresh stays silent so the log is not flooded.
                    if (!silent) {
                        pushHistory("[" + now() + "] " + (code == 0 ? "✅ " : "⚠ ") + statusLabel
                                + Lang.t(" completed (exit=") + code + (ms > 0 ? Lang.t(", took ") + (ms / 1000.0) + "s" : "")
                                + Lang.t(") — the lamps above and the summary line below are updated"));
                    }
                }
                render();
                return;
            }
            String head = "[" + now() + "] " + label + Lang.t(" completed (exit=") + code
                    + (ms > 0 ? Lang.t(", took ") + (ms / 1000.0) + "s" : "") + ")";
            String body = i.getStringExtra("output");
            if (pending) finishPending(null);
            pushHistory(head + (body == null || body.trim().isEmpty() ? "\n" + Lang.t("(no output this time)") : "\n" + body.trim()));
            setLast(label, code, ms, code == 0);
            render();
            // After revoking / writing authorization, put the switch back to the real state
            if ("installpass_revoke".equals(cmdId) || "installpass_set".equals(cmdId)) refreshAuthState();
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Lang.init(this);   // persisted choice (the app cannot read ~/.dsh-lang; it writes it through Termux)
        try { ver = "v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Throwable t) { ver = "?"; }   // never claim a version we could not read
        askTermuxLang();   // the file is the source of truth; only Termux can read it
        // Remembered folding: with no stored value, only Start/Stop starts open.
        java.util.Set<String> stored = getSharedPreferences("dsh-console", MODE_PRIVATE)
                .getStringSet("collapsed", null);
        if (stored != null) collapsedCats = new java.util.HashSet<String>(stored);
        else {
            // 首次运行：除「启动 / 停止」外全收起。**按生成表推导**，不手写分类名 ——
            // 手写的那份在新增「安全 / 设置」时会漏掉，新分类就默认展开。
            for (String[] c : UiControls.CATS) collapsedCats.add(c[0]);
            collapsedCats.remove("startstop");
        }
        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14); root.setPadding(p, p, p, p);
        root.setBackgroundColor(Palette.BG);
        // 每一层都刷同一个底色：窗口（@style/DshTheme 的 windowBackground）→ page → ScrollView → root。
        // 内容比屏幕短、或者滚到底时，露出来的就是这几层里没刷的那一层 —— 用户 2026-09-27 看到的
        // "底下一大块灰褐色"就是平台主题的 windowBackground 灰。现在四层同色，怎么滚都不会断层。
        sc.setBackgroundColor(Palette.BG);
        sc.setFillViewport(true);
        sc.addView(root);
        // ── Title row (title + version; the version is visible at a glance so an old build is obvious) ──
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.BOTTOM);
        TextView t = new TextView(this);
        t.setText(Lang.t("DSH Console")); t.setTextSize(19); t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Palette.FG);
        titleRow.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView vt = new TextView(this);
        vt.setText(ver); vt.setTextSize(12); vt.setTextColor(Palette.ACCENT);
        vt.setPadding(0, 0, 0, dp(3));
        titleRow.addView(vt);
        root.addView(titleRow);

        // ── Busy strip: directly under the header, so a press always shows progress where you look ──
        busyRow = new LinearLayout(this);
        busyRow.setOrientation(LinearLayout.HORIZONTAL);
        busyRow.setGravity(Gravity.CENTER_VERTICAL);
        busyRow.setPadding(0, dp(10), 0, dp(4));
        busyRow.setVisibility(View.GONE);
        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(22), dp(22));
        sp.setMargins(0, 0, dp(10), 0);
        busyRow.addView(spinner, sp);
        busyText = new TextView(this);
        busyText.setTextSize(12.5f); busyText.setTextColor(Palette.WARN);
        busyRow.addView(busyText);
        root.addView(busyRow);

        // ── Everything below comes from the generated control source (ui/controls.json) ──
        // One source, three surfaces: the page panel and the bridge app render the same entries, so the
        // same control cannot end up named or explained differently here than there.
        boolean firstCat = true;
        for (String[] cat : UiControls.CATS) {
            List<UiControls.C> list = UiControls.ofCat(cat[0]);
            if (list.isEmpty()) continue;
            // 分类之间画一条分割线（用户 2026-09-27：收起后下面一片空，视觉断裂）。
            // 第一个分类之前不画 —— 那时上面是标题与忙碌条，再画一条反而像多了一类。
            if (!firstCat) root.addView(divider());
            firstCat = false;
            // Collapsible, and the choice is remembered (SharedPreferences "collapsed" set). First run:
            // only Start/Stop is open — a phone screen should not open with 17 buttons at once.
            final LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            final String catId = cat[0];
            final boolean[] collapsed = { collapsedCats.contains(catId) };
            box.setVisibility(collapsed[0] ? View.GONE : View.VISIBLE);
            final LinearLayout catRoot = box;   // the section's controls go in here
            String openGroup = "";
            // 标题上的 (N) 数的是**真正加进这一类的行数**：按钮/开关/文本各一行，灯那一整块算一行，
            // 分组小标题（▸ 桥 / adb / 危险操作）不算行。
            // 为什么要在这里数：用户 2026-09-27 报"标题旁的 (N) 跟展开后数出来的条目对不上"。
            // 根因就是计数用了第二套数据（生成表里属于本分类的控件数），而渲染时灯会合并成一行、
            // 有些控件根本不在这个界面出现 —— 两个数字必然漂。现在只有一处：加一行，计一个。
            int rows = 0;
            for (UiControls.C ctrl : list) {
                if (ctrl.group.length() > 0 && !ctrl.group.equals(openGroup)) {
                    openGroup = ctrl.group;
                    catRoot.addView(subHeader(Lang.t(UiControls.groupEn(openGroup))));
                }
                if ("button".equals(ctrl.kind)) {
                    Button bt = controlButton(ctrl);
                    buttons.add(bt);
                    catRoot.addView(bt, wideLp());
                    rows++;
                } else if ("switch".equals(ctrl.kind)) {
                    catRoot.addView(switchRow(ctrl), wideLp());
                    rows++;
                } else if ("lamp".equals(ctrl.kind)) {
                    if (lamps == null) {
                        lamps = new TextView(this);
                        lamps.setTextSize(15);
                        lamps.setPadding(0, dp(8), 0, dp(2));
                        catRoot.addView(lamps);
                        line = new TextView(this);
                        line.setTextSize(12);
                        line.setTextColor(Palette.DIM);
                        line.setPadding(0, dp(4), 0, dp(8));
                        catRoot.addView(line);
                        rows++;   // 三盏灯 + 一行明细＝一块，算一行
                    }
                } else if ("text".equals(ctrl.kind)) {
                    catRoot.addView(textRow(ctrl), wideLp());
                    rows++;
                }
            }
            final int rowCount = rows;
            // 分类名走生成表（catEn 而不是手写索引 —— 桥的分类标题就是这么漏成英文的）。
            final TextView head = sectionHeader(catLabel(cat[0], rowCount));
            head.setOnClickListener(v -> {
                collapsed[0] = !collapsed[0];
                box.setVisibility(collapsed[0] ? View.GONE : View.VISIBLE);
                head.setText(catLabel(cat[0], rowCount));
                if (collapsed[0]) collapsedCats.add(catId); else collapsedCats.remove(catId);
                getSharedPreferences("dsh-console", MODE_PRIVATE).edit()
                        .putStringSet("collapsed", collapsedCats).apply();
            });
            root.addView(head);
            root.addView(box);
        }

        // ── Last-result bar: **pinned to the bottom of the screen**, outside the ScrollView ──
        // 用户 2026-09-27：这一条是全局的，以前放在根布局末尾 → 每个分类展开/收起它都跟着滚，看着像
        // "重复出现"，而且滚动到别处时看不到点下去的结果（用户最在意"点下去必须立刻有反馈"）。
        // 现在它固定在窗口底部，不随分类滚动；没结果时整条隐藏，不留空白。
        lastLine = new TextView(this);
        lastLine.setTextSize(12); lastLine.setPadding(dp(10), dp(10), dp(10), dp(10));
        lastLine.setBackgroundResource(R.drawable.box);
        lastLine.setVisibility(View.GONE);
        lastLine.setOnClickListener(v -> openLog());
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setBackgroundColor(Palette.BG);
        footer.setPadding(p, 0, p, dp(10));
        LinearLayout.LayoutParams llp = wideLp();
        llp.setMargins(0, dp(8), 0, 0);
        footer.addView(lastLine, llp);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Palette.BG);
        page.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(page);
        registerReceiver(refresh, new IntentFilter("io.dsh.console.UI_REFRESH"),
                Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_NOT_EXPORTED : 0);
        loadHistory();   // the log survives a cold start now (see saveHistory)
        render();
        autoStatus();
        checkUpdate();   // 每次打开自动问一次仓库有没有新版本（走 Termux，本 App 不要网络权限）
        refreshAuthState();
    }

    @Override protected void onResume() {
        super.onResume();
        askTermuxLang();   // pick up a change made elsewhere (e.g. `dsh-lang set zh` from a widget)
        render();
        autoStatus();
        refreshAuthState();
    }

    private long lastAuto = 0;
    /** Auto-read status once on entering the foreground: skip if one is in flight, if the cache is fresh, or if one was sent within 15s (v0.2 sent it three times). */
    private void autoStatus() {
        if (pending) return;
        long now = System.currentTimeMillis();
        if (now - Last.statusAt(this) < 20000) return;
        if (now - lastAuto < 15000) return;
        lastAuto = now;
        // v0.6: auto refresh uses different wording than a manual Lang.t("Refresh status") tap, otherwise the two
        // log/summary lines look exactly alike and you cannot tell "I tapped it" from "the app did it".
        run("status", Lang.t("Auto refresh status"), TermuxRunner.statusCmd(), true, false, true, 20, true);
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        try { unregisterReceiver(refresh); } catch (Throwable t) {}
        try { if (logDialog != null) logDialog.dismiss(); } catch (Throwable t) {}
        super.onDestroy();
    }

    // ---------- Log (its own screen) ----------
    private void openLog() {
        if (logDialog != null && logDialog.isShowing()) { refreshLogView(); return; }
        logScroll = new ScrollView(this);
        logBody = new TextView(this);
        logBody.setTextSize(11.5f);
        logBody.setTypeface(Typeface.MONOSPACE);
        logBody.setTextColor(Palette.FG);
        logBody.setTextIsSelectable(true);
        int p = dp(14); logBody.setPadding(p, p, p, p);
        logScroll.addView(logBody);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle(Lang.t("Log"))
                .setView(logScroll)
                .setPositiveButton(Lang.t("Close"), null)
                .setNeutralButton(Lang.t("Copy all"), null)
                .setNegativeButton(Lang.t("Clear"), null)
                .create();
        d.setOnShowListener(x -> {
            Button cp = d.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (cp != null) cp.setOnClickListener(v -> copyLog());
            Button cl = d.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (cl != null) cl.setOnClickListener(v -> {
                history.clear(); saveHistory(); unread = 0; updateLogBtn(); refreshLogView(); toast(Lang.t("Log cleared"));
            });
        });
        d.setOnDismissListener(x -> { logDialog = null; logBody = null; logScroll = null; });
        logDialog = d;
        unread = 0;
        updateLogBtn();
        d.show();
        refreshLogView();
    }

    /** Log body: the header states the entry count and purpose; with no entries it gives guidance (never blank). */
    private String logText() {
        StringBuilder sb = new StringBuilder();
        sb.append(Lang.t("DSH Console ")).append(ver)
          .append("　").append(history.size()).append(Lang.t(" entries (keeps up to ")).append(MAX_HISTORY).append(")\n");
        sb.append("──────────────────────────\n");
        if (history.isEmpty()) {
            sb.append("(no entries yet)\n\n")
              .append("· Tap Refresh status to read the three lamps\n")
              .append("· Tap any task button to run it: sent, callback, exit code and raw output are all recorded here\n")
              .append("· Empty output still gets a (no output this time) line, so nothing is left blank\n");
        } else {
            for (int i = 0; i < history.size(); i++) {
                if (i > 0) sb.append("\n──────────────────────────\n");
                sb.append(history.get(i));
            }
        }
        return sb.toString();
    }

    private void refreshLogView() {
        if (logBody == null) return;
        logBody.setText(logText());
        if (logScroll != null) {
            logScroll.post(() -> { try { logScroll.fullScroll(View.FOCUS_DOWN); } catch (Throwable t) {} });
        }
    }

    private void copyLog() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) { toast(Lang.t("This device has no clipboard service")); return; }
            cm.setPrimaryClip(ClipData.newPlainText(Lang.t("DSH Console log"), logText()));
            // Android 13+ pops up its own "copied" itself, so don't stack another toast
            if (Build.VERSION.SDK_INT < 33) toast(Lang.t("Log copied (") + history.size() + Lang.t(" entries)"));
        } catch (Throwable t) { toast(Lang.t("Copy failed: ") + t.getMessage()); }
    }

    private void updateLogBtn() {
        if (logBtn == null) return;
        logBtn.setText(unread > 0 ? Lang.t("Log (") + unread + Lang.t(")") : Lang.t("Log"));
    }

    /** Last-result summary: green = success, red = failure; with no result the whole line is hidden. */
    private void setLast(String label, int code, long ms, boolean ok) {
        lastOk = ok;
        lastSummary = (ok ? "✅ " : "⚠ ") + label + "　exit=" + code
                + (ms > 0 ? "　" + (ms / 1000.0) + "s" : "") + "　" + now()
                + Lang.t("　(tap here for the log)");
        if (lastLine != null) {
            lastLine.setText(lastSummary);
            lastLine.setTextColor((ok ? Palette.OK : Palette.BAD));
            lastLine.setVisibility(View.VISIBLE);
        }
    }

    // ---------- Install password authorization (a switch, not a button) ----------
    /** That row in the maintenance section: explanation on the left, switch on the right, current state on the line below. */
    /** Ask Termux for the authoritative language (~/.dsh-lang); the answer arrives via the callback. */
    private void askTermuxLang() {
        run("lang_query", "Language", TermuxRunner.HOME + "/.local/bin/dsh-lang mode", false, true);
    }

    /** Shows the current language and cycles it: auto → zh → en.
     *  The choice is stored locally (L) AND pushed into ~/.dsh-lang through Termux, so the scripts,
     *  the DSH page panel and this app always agree. recreate() redraws the screen immediately. */
    private View buildLangRow() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.box);
        int p = dp(10);
        box.setPadding(p, p, p, dp(8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(Lang.t("Language")); t.setTextSize(13.5f);
        t.setTextColor(Palette.FG); t.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(t);
        TextView sub = new TextView(this);
        sub.setText(Lang.t("auto follows the system language") + " · " + Lang.t("the widgets and the page panel follow this too"));
        sub.setTextSize(11); sub.setTextColor(Palette.DIM);
        sub.setPadding(0, dp(2), dp(8), 0);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Three choices, not a cycling button (user's call): "System / 中文 / English" — a cycle hides
        // which option you will land on, and this setting is shared by the widgets and the page panel.
        final String mode = Lang.mode();
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        String[] ids = { "auto", "zh", "en" };
        String[] names = { Lang.t("System"), "中文", "English" };
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            Button cb = new Button(this);
            cb.setText(names[i]);
            cb.setTextSize(12);
            cb.setAllCaps(false);
            cb.setMinWidth(dp(58));
            cb.setPadding(dp(8), dp(6), dp(8), dp(6));
            boolean on = mode.equals(id);
            cb.setTextColor((on ? Palette.BG : Palette.LINK));
            cb.setBackgroundColor((on ? Palette.ACCENT : Palette.CARD));
            cb.setOnClickListener(v -> {
                Lang.setMode(this, id);
                run("lang", Lang.t("Language"), TermuxRunner.HOME + "/.local/bin/dsh-lang set " + id, false, true);
                recreate();
            });
            LinearLayout.LayoutParams cbp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cbp.setMargins(0, 0, dp(6), 0);
            choices.addView(cb, cbp);
        }
        box.addView(row);
        // ⚠ 这一行以前漏了：`choices` 容器连三个按钮建好之后**从来没被 addView 到任何地方**，
        // 于是「语言」只剩标题和副标题，选项凭空消失（用户 2026-09-28 截图报的正是这个）。
        // 单独占一行而不是塞进 row 右边：三个按钮 + 一长串副标题挤在一行会被压没。
        box.addView(choices);
        return box;
    }

    private View buildAuthRow() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.box);
        int p = dp(10);
        box.setPadding(p, p, p, dp(6));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(Lang.t("Password access")); t.setTextSize(13.5f);
        t.setTextColor(Palette.FG); t.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(t);
        TextView sub = new TextView(this);
        sub.setText(Lang.t("On = the AI may use your 6-digit lock-screen password to pass identity checks for you (installing packages, lifting settings restrictions, etc.); Off = revoked at once"));
        sub.setTextSize(11); sub.setTextColor(Palette.DIM);
        sub.setPadding(0, dp(2), dp(8), 0);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        authSwitch = new Switch(this);
        authSwitch.setShowText(false);
        authSwitch.setEnabled(false);   // don't allow toggling before the state has been read
        authSwitch.setOnCheckedChangeListener((v, checked) -> {
            if (authSyncing) return;    // programmatic syncing is not a user action
            onAuthToggled(checked);
        });
        row.addView(authSwitch);
        box.addView(row);

        authLine = new TextView(this);
        authLine.setTextSize(11.5f);
        authLine.setTextColor(Palette.DIM);
        authLine.setPadding(0, dp(6), 0, 0);
        authLine.setText(Lang.t("Reading authorization state…"));
        box.addView(authLine);
        // The technical detail (path / mode / mtime / length) lives here, on long-press — the subtitle
        // stays a one-line meaning (user requirement 2026-09-27).
        box.setOnLongClickListener(v -> { showAuthDetail(); return true; });
        return box;
    }

    /** Reads the helper's status (exit 0 = authorized, 1 = not authorized) so the switch reflects the real state. */
    private void refreshAuthState() {
        run("installpass_query", Lang.t("Query password access"),
                TermuxRunner.HOME + "/.local/bin/dsh-auth-pass status", false, true);
    }

    private void setAuthUi(boolean known, boolean on, String detail) {
        authKnown = known; authOn = on;
        if (authSwitch == null) return;
        authSyncing = true;
        authSwitch.setEnabled(known);
        authSwitch.setChecked(on);
        authSyncing = false;
        if (authLine != null) {
            // ⚠ The subtitle carries the **meaning**, nothing else. It used to end with the raw output of
            //   `dsh-auth-pass status`, so the user read:
            //     「当前：已授权（AI 可代你过身份验证） Authorized: /data/data/.../.dsh-auth-pass
            //       (mode 600, written 2026-09-26 23:59:40, length 6 digits)」
            //   — a file path, a mode, a timestamp and a byte count sitting in a settings subtitle
            //   (user report 2026-09-27: "副标题只留 已授权/已收回，技术信息进长按详情或日志").
            //   The detail now goes to the long-press sheet and, only when the state actually changes,
            //   to the log.
            authDetail = detail == null ? "" : detail.trim();
            authLine.setText(authStateHead(known, on));
            authLine.setTextColor((!known ? Palette.WARN : (on ? Palette.OK : Palette.DIM)));
            if (known != lastAuthKnown || on != lastAuthOn) {
                if (!authDetail.isEmpty()) pushHistory("[" + now() + "] " + Lang.t("Password access: ") + authDetail);
                lastAuthKnown = known; lastAuthOn = on;
            }
        }
    }

    /** 「当前：已授权（AI 可代你过系统身份验证）」/「当前：已收回」/ 读不到 —— 副标题只允许这一句。 */
    private String authStateHead(boolean known, boolean on) {
        if (!known) return Lang.t("Now: unreadable (no reply from Termux; tap Refresh status to retry)");
        return on ? Lang.t("Now: authorized (the AI can pass system identity checks for you)")
                  : Lang.t("Now: revoked");
    }

    /** Long-press on the password row: the full technical detail, on request, out of the way. */
    private void showAuthDetail() {
        String head = authStateHead(authKnown, authOn);
        String d = authDetail.isEmpty() ? Lang.t("(no technical detail this time)") : authDetail;
        new AlertDialog.Builder(this)
                .setTitle(Lang.t("Password access"))
                .setMessage(head + "\n\n" + Lang.t("Technical detail (kept out of the subtitle on purpose):") + "\n" + d)
                .setPositiveButton(Lang.t("OK"), null)
                .show();
    }

    private void onAuthToggled(boolean wantOn) {
        if (!authKnown) return;
        if (wantOn == authOn) return;
        if (wantOn) { askPasswordAndAuthorize(); return; }
        new AlertDialog.Builder(this)
                .setTitle(Lang.t("Revoke password access?"))
                .setMessage(Lang.t("This revokes it: ~/.dsh-auth-pass is deleted.\n")
                        + Lang.t("Afterwards the AI can no longer use those 6 digits to pass any identity check for you (the security check when installing an APK, ")
                        + Lang.t("lifting app settings restrictions, sensitive confirmations in developer options… they all stop with you).\n")
                        + Lang.t("(Does not affect DSH / Bridge / adb — each has its own revoke entry.)"))
                .setNegativeButton(Lang.t("Cancel"), (d, w) -> setAuthUi(true, authOn, null))
                .setPositiveButton(Lang.t("Revoke"), (d, w) -> {
                    toast(Lang.t("Revoking…"));
                    run("installpass_revoke", Lang.t("Revoke password access"),
                            TermuxRunner.HOME + "/.local/bin/dsh-auth-pass revoke", false);
                })
                .show();
    }

    /** Re-authorizing needs the 6-digit password — the app cannot conjure credentials out of nothing, so you must type it once here. */
    private void askPasswordAndAuthorize() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setHint(Lang.t("6 digits"));
        et.setTextSize(16);
        int p = dp(16); et.setPadding(p, p, p, p);
        new AlertDialog.Builder(this)
                .setTitle(Lang.t("Authorize the AI to use your password"))
                .setMessage(Lang.t("Type that 6-digit lock-screen password → it is written to ~/.dsh-auth-pass (600).\n")
                        + Lang.t("Used only to pass system identity checks for you (installing packages, lifting settings restrictions, etc.).\n")
                        + Lang.t("Turn the switch off at any time to revoke."))
                .setView(et)
                .setNegativeButton(Lang.t("Cancel"), (d, w) -> setAuthUi(true, authOn, null))
                .setPositiveButton(Lang.t("Authorize"), (d, w) -> {
                    String pw = et.getText() == null ? "" : et.getText().toString().trim();
                    if (!pw.matches("[0-9]{6}")) { toast(Lang.t("Must be 6 digits")); setAuthUi(true, authOn, null); return; }
                    toast(Lang.t("Writing authorization…"));
                    // Runs once through Termux only; the password never enters the log (history records the label only)
                    run("installpass_set", Lang.t("Write password authorization"),
                            "printf '%s' '" + pw + "' | " + TermuxRunner.HOME + "/.local/bin/dsh-auth-pass set", false);
                })
                .show();
    }

    // ---------- Execution ----------
    /** One entry point for a press: text comes from the generated control source, execution from Tasks. */
    private void fire(String id) {
        UiControls.C c = UiControls.get(id);
        // ⚠ 只认表里有的 id：以前是 `c != null ? c.labelEn : id`，一旦真拿到 null 就会把控件 id
        //   当文案显示出来（英文）；现在宁可不做，也不显示一个没翻译的 id。
        //   顺带：Lang.t() 的参数必须是字面量或生成表字段，tools/i18n-audit 会拦下别的写法。
        if (c == null) { toast(Lang.t("Unknown control: ") + id); return; }
        if (c.danger) {
            new AlertDialog.Builder(this)
                    .setTitle(Lang.t("Run ") + Lang.t(c.labelEn) + "?")
                    .setMessage(Lang.t(c.hintEn) + "\n\n⚠ " + Lang.t("Confirmation required: restart/shutdown drops the current web session, and a full stop of the bridge may need a manual open on this ROM."))
                    .setNegativeButton(Lang.t("Cancel"), null)
                    .setPositiveButton(Lang.t("Run"), (d, w) -> {
                        toast(Lang.t("Sent: ") + Lang.t(c.labelEn));
                        sendTask(id);
                    })
                    .show();
            return;
        }
        toast(Lang.t("Sent: ") + Lang.t(c.labelEn));
        sendTask(id);
    }

    /** Single entry point: uses each task's own wait window (long tasks like backup/restart no longer raise false alarms). */
    private void sendTask(String id) {
        Tasks.T task = Tasks.get(id);
        UiControls.C c = UiControls.get(id);
        if (c == null) return;   // 表里没有的 id：不猜文案，也不执行
        String cmd = task != null && task.cmd != null ? task.cmd : TermuxRunner.taskCmd(id);
        run(id, Lang.t(c.labelEn), cmd,
                false, false, false, Tasks.waitOf(id), false);
    }

    private void run(String cmdId, String label, String command, boolean isStatus) {
        run(cmdId, label, command, isStatus, false, false, 90, false);
    }

    /** quiet=true: send without asking (for background queries such as reading the auth state — no log, no busy state). */
    private void run(String cmdId, String label, String command, boolean isStatus, boolean quiet) {
        run(cmdId, label, command, isStatus, quiet, false, 25, true);
    }

    /** silentLog=true: enter the busy state but skip the "sent" log line (auto refresh uses it so the log is not flooded). */
    private void run(String cmdId, String label, String command, boolean isStatus, boolean quiet,
                     boolean silentLog, int waitS, boolean isQuery) {
        if (!TermuxRunner.installed(this)) {
            pushHistory("[" + now() + "] " + Lang.t("⚠ Termux (com.termux) not found; please install Termux first."));
            render(); return;
        }
        if (!TermuxRunner.hasPermission(this)) {
            try { requestPermissions(new String[] { TermuxRunner.PERM }, 1); } catch (Throwable t) {}
            pushHistory("[" + now() + "] " + Lang.t("⚠ RUN_COMMAND permission not granted yet; a permission prompt was shown.\n")
                    + Lang.t("   If no dialog appeared: Settings → Apps → DSH Console → Permissions → allow RUN_COMMAND."));
            render(); return;
        }
        if (!quiet) {
            pending = true; pendingSince = System.currentTimeMillis(); pendingLabel = label;
            pendingWaitS = waitS; pendingQuery = isQuery; queryRetried = false; pendingSilent = silentLog;
            pendingCmdId = cmdId; pendingCmd = command; pendingIsStatus = isStatus;
            setBusy(true);
            ui.removeCallbacks(tick);
            ui.post(tick);
        }
        boolean ok = TermuxRunner.run(this, cmdId, label, command, isStatus);
        if (!ok) { if (!quiet) finishPending(Lang.t("⚠ Failed to start the Termux command.") ); return; }
        if (!quiet && !silentLog) {
            pushHistory("[" + now() + "] " + Lang.t("Sent: ") + label + Lang.t(", waiting for Termux to reply…"));
            render();
        }
    }

    private void finishPending(String message) {
        pending = false;
        ui.removeCallbacks(tick);
        setBusy(false);
        if (message != null && !message.isEmpty()) pushHistory("[" + now() + "] " + message);
        render();
    }

    private void setBusy(boolean busy) {
        busyRow.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) busyText.setText(Lang.t("Running: ") + pendingLabel + Lang.t("　waited 0s"));
        for (Button bt : buttons) bt.setEnabled(!busy);
    }

    /** 关于区：项目地址（点开就是仓库） */
    private TextView buildProjectRow() {
        projRow = new TextView(this);
        projRow.setTextSize(12.5f);
        projRow.setTextColor(Palette.LINK);
        projRow.setPadding(dp(10), dp(10), dp(10), dp(10));
        projRow.setBackgroundResource(R.drawable.box);
        projRow.setText(Lang.t("Project page: ") + "github.com/Maopk/dsh-termux-kit");
        projRow.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PROJECT_URL))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable t) {
                toast(Lang.t("No browser to open it with; the address is github.com/Maopk/dsh-termux-kit"));
            }
        });
        return projRow;
    }

    private TextView buildUpdateRow() {
        updateRow = new TextView(this);
        updateRow.setTextSize(12.5f);
        updateRow.setTextColor(Palette.DIM);
        updateRow.setPadding(dp(10), dp(10), dp(10), dp(10));
        updateRow.setBackgroundResource(R.drawable.box);
        updateRow.setText(Lang.t("Version: ") + ver + Lang.t(" · checking for a newer release…"));
        return updateRow;
    }

    /**
     * 打开时自动查一次发行版。
     *
     * 走 Termux 而不是自己发请求：这个 App 的承诺是「只用 RUN_COMMAND，不要存储/网络/无障碍权限」，
     * 加个 INTERNET 就破了这个承诺。它把自己的版本号传过去，Termux 侧比对仓库最新发行版。
     * 一分钟节流：onResume 触发很频繁，而用户要的是"每次打开"。
     */
    private void checkUpdate() {
        long now = System.currentTimeMillis();
        if (now - lastUpdateCheck < 60000) return;
        lastUpdateCheck = now;
        run("update_check", Lang.t("Check for updates"), TermuxRunner.HOME
                + "/.local/bin/dsh-update check --app console --current " + ver + " --json",
                false, true, false, 25, true);
    }

    /**
     * 版本行：**只有三种情况**，而且必须靠比较版本号判断，不能靠 `update` 这个布尔（用户 2026-09-27 抓到的矛盾）：
     *   本地 = 仓库 → 「已是最新」
     *   本地 < 仓库 → 「→ 仓库最新 vX」+ 点这一行就更新
     *   本地 > 仓库 → 直说本地比仓库新，**绝不显示"已是最新"**
     * （旧代码只认 update=true/false，于是本地 1.15 > 仓库 1.14 时照旧显示"已是最新（仓库最新 v1.14）"。）
     */
    private void showUpdateResult(String out) {
        if (updateRow == null) return;
        String latest = "", curRaw = "";
        boolean ok = false;
        try {
            org.json.JSONObject o = new org.json.JSONObject((out == null ? "" : out).trim());
            ok = o.optBoolean("ok", false);
            latest = o.optString("latest_app", "");
            // 本地版本取**同一次查询的回显**（dsh-update check --json 把 --current 原样回传），
            // 不再一边用界面字段 ver、一边用返回里的 latest —— 两次交换之间版本变了，行里就会出现
            // "你的是 vA / 仓库最新 vB" 这种半新半旧的话（用户 2026-09-27 通用约束第 3 条）。
            curRaw = o.optString("current", ver);
        } catch (Throwable t) { ok = false; }
        if (!ok || latest.isEmpty()) {
            // 查不到就直说查不到 —— 显示"已是最新"会把"没网"说成"没问题"
            updateRow.setText(Lang.t("Version: ") + ver + Lang.t(" · could not check for updates now (no network?)"));
            updateRow.setTextColor(Palette.DIM);
            updateRow.setOnClickListener(null);
            return;
        }
        String shown = curRaw.startsWith("v") ? curRaw : "v" + curRaw;
        String mine = shown.startsWith("v") ? shown.substring(1) : shown;
        if (isNewer(latest, mine)) {
            updateRow.setText(Lang.t("Version: ") + shown + Lang.t(" → the repo has ") + "v" + latest
                    + Lang.t(" · tap this line to update"));
            updateRow.setTextColor(Palette.WARN);
            updateRow.setOnClickListener(v -> confirmUpdate());
        } else if (sameVer(latest, mine)) {
            updateRow.setText(Lang.t("Version: ") + shown + Lang.t(" · up to date"));
            updateRow.setTextColor(Palette.OK);
            updateRow.setOnClickListener(null);
        } else {
            updateRow.setText(Lang.t("Version: ") + shown + Lang.t(" · local is newer than the repo (the repo only has v")
                    + latest + Lang.t(")"));
            updateRow.setTextColor(Palette.DIM);
            updateRow.setOnClickListener(null);
        }
    }

    /** 版本号比较：逐段数字比大小（"1.10" > "1.9"）。相等只能由 sameVer 判定。 */
    private static boolean isNewer(String a, String b) {
        String[] x = (a == null ? "" : a).split("\\."), y = (b == null ? "" : b).split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = num(x, i), yi = num(y, i);
            if (xi != yi) return xi > yi;
        }
        return false;
    }

    private static int num(String[] parts, int i) {
        if (i >= parts.length) return 0;
        try { return Integer.parseInt(parts[i].replaceAll("[^0-9]", "")); } catch (Throwable t) { return 0; }
    }

    private static boolean sameVer(String a, String b) {
        return !isNewer(a, b) && !isNewer(b, a);
    }

    /** 版本行上的"更新"：一次确认，然后跑和「更新两个 App」同一个任务。 */
    private void confirmUpdate() {
        new AlertDialog.Builder(this)
                .setTitle(Lang.t("Update the two apps"))
                .setMessage(Lang.t("Downloads the two APKs from GitHub Releases, verifies SHA256, then installs them.")
                        + "\n\n" + Lang.t("It takes a couple of minutes and needs your 6-digit password for the system install dialog."))
                .setNegativeButton(Lang.t("Cancel"), null)
                .setPositiveButton(Lang.t("Update"), (d, w) -> { toast(Lang.t("Sent: ") + Lang.t("Update the two apps")); sendTask("11_update-apps"); })
                .show();
    }

    private void pushHistory(String s) {
        history.add(s);
        while (history.size() > MAX_HISTORY) history.remove(0);
        saveHistory();
        if (logDialog == null) { unread++; updateLogBtn(); }
        refreshLogView();
    }

    /**
     * The log used to live only in this process: {@code history} is an ArrayList and nothing ever
     * wrote it anywhere, so every cold start began with an empty log — the header promising
     * "keeps up to 60" only held within a single run. The user saw exactly that as "why does the
     * log have only 4 entries", and it also meant the record of a failure vanished the moment the
     * app was reclaimed. It now lives in SharedPreferences (the same file Lang uses), still capped
     * at MAX_HISTORY; an unreadable value degrades to an empty log, never a crash.
     */
    private void saveHistory() {
        try {
            org.json.JSONArray a = new org.json.JSONArray();
            for (String h : history) a.put(h);
            getSharedPreferences("dsh-console", MODE_PRIVATE).edit()
                    .putString("history", a.toString()).apply();
        } catch (Throwable t) { /* a log is a convenience, never a reason to fail a task */ }
    }

    private void loadHistory() {
        try {
            String raw = getSharedPreferences("dsh-console", MODE_PRIVATE).getString("history", "");
            if (raw == null || raw.isEmpty()) return;
            org.json.JSONArray a = new org.json.JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                String line = a.optString(i, "");
                if (!line.isEmpty()) history.add(line);
            }
            while (history.size() > MAX_HISTORY) history.remove(0);
        } catch (Throwable t) { history.clear(); }
    }

    // ---------- Rendering ----------
    private void render() {
        boolean perm = TermuxRunner.hasPermission(this);
        String json = Last.status(this);
        String d = "grey", br = "grey", a = "grey";
        String detail = Lang.t("No status yet — tap Refresh status");
        JSONObject bjOut = null;
        // ── 桥的状态：**先算一次**，灯色 / 文字 / 开关都用它 ──
        // 为什么必须放在最前面：旧代码的灯色取自 status.json 的 lamps.bridge，文字取自
        // bridge.state，两处各算各的；而 producer 根本不写 state，于是同一屏上出现
        // 「绿灯 + 桥：未知」的矛盾（用户 2026-09-27 实测）。现在只有 bridgeStateKey() 一个判定。
        String bridgeNow = "";
        String dshNow = "";
        if (json != null && json.length() > 0) {
            try {
                JSONObject o = new JSONObject(json);
                JSONObject L = o.optJSONObject("lamps");
                if (L != null) { d = L.optString("dsh", "grey"); br = L.optString("bridge", "grey"); a = L.optString("adb", "grey"); }
                JSONObject dj = o.optJSONObject("dsh"), bj = o.optJSONObject("bridge"), aj = o.optJSONObject("adb");
                bjOut = bj;
                bridgeNow = bridgeStateKey(bj);
                if (!bridgeNow.isEmpty()) br = lampOf(bridgeNow);   // 有 state 时，灯色只能由 state 推出来
                // DSH 同一套做法：灯与文字都读 dsh.state（生产者算一次），不再一个按 ok、一个按 port
                dshNow = dshStateKey(dj);
                if (!dshNow.isEmpty()) d = lampOfDsh(dshNow);
                // One line, at most three facts, no timestamps and no raw HTTP codes:
                // "DSH 运行中 · 桥 v2.21 · adb 未连接（Wi-Fi 未连或无线调试未开）"
                StringBuilder sb = new StringBuilder();
                if (dj != null) sb.append("DSH ").append(dshText(dshNow));
                if (bj != null) sb.append(" · ").append(bridgeStateText(bridgeNow, bj));
                if (aj != null) {
                    JSONArray ds = aj.optJSONArray("devices");
                    boolean on = ds != null && ds.length() > 0;
                    a = on ? "green" : "red";   // 灯色与文字读同一个事实（devices），不再一处读 lamps、一处读 devices
                    sb.append(" · adb ").append(on ? ds.optString(0) : Lang.t("not connected"));
                    if (!on) sb.append(Lang.t(" (Wi-Fi off, or Wireless debugging not on)"));
                }
                detail = sb.toString();
            } catch (Throwable t) {
                    // Be honest: it is usually a truncated callback (cutting the status JSON from the tail removes its head)
                    detail = Lang.t("Status parse failed (callback may be truncated): ") + t.getMessage();
                }
        }
        // Colour the three lamps by MEASURING each label, never by hard-coded offsets.
        // History: the first version hard-coded character offsets, then a "fix" hard-coded
        // "Bridge".length() — which overran in Chinese ("● DSH　● 桥　● adb") and painted the adb DOT
        // with the bridge's colour while its label stayed red (spans applied later win on overlap).
        // Building the line from the parts and computing every offset from the actual lengths is
        // language-proof: measured on the device, the adb dot showed green while adb was disconnected.
        String dName = "DSH";
        String bName = Lang.t("Bridge");
        String aName = "adb";
        String lampText = "● " + dName + "　● " + bName + "　● " + aName;
        SpannableString s = new SpannableString(lampText);
        String[] names = {dName, bName, aName};
        String[] cols = {d, br, a};
        // Colour by *finding* each dot and the label that follows it, never by a hard-coded offset:
        // the line is built from translated names, so any fixed index breaks in the other language
        // (measured: a hard-coded "Bridge".length() painted the adb dot green while adb was offline).
        int from = 0;
        for (int k = 0; k < names.length; k++) {
            int dot = lampText.indexOf('\u25CF', from);
            if (dot < 0) break;
            int nameStart = lampText.indexOf(names[k], dot);
            int col = c(cols[k]);
            s.setSpan(new ForegroundColorSpan(col), dot, dot + 1, 0);
            if (nameStart >= 0 && nameStart + names[k].length() <= lampText.length()) {
                s.setSpan(new ForegroundColorSpan(col), nameStart, nameStart + names[k].length(), 0);
                from = nameStart + names[k].length();
            } else {
                from = dot + 1;
            }
        }
        lamps.setText(s);
        if (bridgeStateView != null) bridgeStateView.setText(bridgeStateText(bridgeNow, bjOut));
        if (bridgeSwitch != null) {
            bridgeSwitchBusy = true;
            bridgeSwitch.setEnabled(!bridgeNow.isEmpty());   // 状态读不到时不许摆出一个"关"的假象
            bridgeSwitch.setChecked("running".equals(bridgeNow));
            bridgeSwitchBusy = false;
        }
        line.setText(detail + (perm ? "" : Lang.t("　⚠ missing RUN_COMMAND permission")));
        refreshLogView();
    }

    /** 桥状态：**唯一**判定处。灯色、那一行文字、开关位置都从这里取，不许第二处再算一遍。
     *  数据源是 status.json 的 bridge.state（由 dsh-status-pub 算一次）；万一读到旧 producer 没有
     *  这个字段，就在同一处按 port/ok/note 现推一次，仍然只有一份结果。 */
    private static String bridgeStateKey(JSONObject bj) {
        if (bj == null) return "";
        String st = bj.optString("state", "");
        if (st.length() > 0) return st;
        boolean port = bj.optBoolean("port", false), ok = bj.optBoolean("ok", false);
        if (port) return ok ? "running" : "frozen";
        String note = bj.optString("note", "none");
        if (note.isEmpty() || "none".equals(note)) return "never";
        return ("soft".equals(note) || "running".equals(note)) ? "soft" : "silent";
    }

    /** DSH 状态：**唯一**判定处（生产者写 dsh.state，这里只读；老 producer 没有就按 port + http 现推一次）。 */
    private static String dshStateKey(JSONObject dj) {
        if (dj == null) return "";
        String st = dj.optString("state", "");
        if (st.length() > 0) return st;
        String code = dj.optString("http", "?");
        boolean port = dj.optBoolean("port", false);
        if (port && !"".equals(code) && !"000".equals(code) && !"404".equals(code) && !"?".equals(code)) return "running";
        return port ? "half" : "down";
    }

    /** state → 文案。文字与灯色读同一个 state，所以"黄灯 + 运行中"这种自相矛盾不可能出现。
     *  三句话**在这里就翻好**：Lang.t() 的参数必须是字面量，否则查表永远查不到
     *  （tools/i18n-audit 会拦，桥的分类标题就是栽在这上面）。 */
    private String dshText(String state) {
        if ("running".equals(state)) return Lang.t("running");
        if ("half".equals(state)) return Lang.t("half-started (the port is listening, the page does not answer yet)");
        return Lang.t("stopped");
    }

    /** 状态 → 灯色。与 dsh-status-pub 的 DSH_LAMP 是同一张表（tools/i18n-audit 逐项对拍）。 */
    private static String lampOfDsh(String state) {
        if ("running".equals(state)) return "green";
        if ("half".equals(state)) return "yellow";
        return "red";
    }

    /** 状态 → 灯色。与 dsh-status-pub 的 LAMP_OF_STATE 是同一张表（tools/i18n-audit 对拍）。 */
    private static String lampOf(String state) {
        if ("running".equals(state)) return "green";
        if ("frozen".equals(state)) return "yellow";
        return "grey";   // soft（可唤醒）/ silent（真停）/ never（没见过它活着）
    }

    private static int c(String lamp) {
        if ("green".equals(lamp)) return Palette.OK;
        if ("yellow".equals(lamp)) return Palette.WARN;
        if ("red".equals(lamp)) return Palette.BAD;
        return Palette.DIM;
    }

    /** 危险项的 ⚠ 画成红色。用户 2026-09-27：去掉红边框之后，⚠ 是危险项**唯一**的视觉信号，
     *  跟正文一个颜色等于没有提示。用 BAD（#F85149）而不是 DANGER（#B3261E）—— 后者在深色底上太暗。 */
    private CharSequence warnMark(String text, boolean danger, int at) {
        if (!danger) return text;
        SpannableString sp = new SpannableString(text);
        if (at >= 0 && at < text.length()) {
            sp.setSpan(new ForegroundColorSpan(Palette.BAD), at, at + 1, 0);
        }
        return sp;
    }

    private Button mkBtn(String text, boolean danger, View.OnClickListener l) {
        return mkBtn(text, danger, 0, l);
    }

    private Button mkBtn(String text, boolean danger, int onColor, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextSize(13);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setMinHeight(dp(48));
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        // Text color differs by enabled/disabled state (a flat color made Lang.t("greying out") invisible — the v0.2 bug)
        int on = onColor != 0 ? onColor : (danger ? Palette.BAD : Palette.FG);
        int off = Palette.DISABLED;
        b.setTextColor(new ColorStateList(
                new int[][] { new int[] { android.R.attr.state_enabled }, new int[] {} },
                new int[] { on, off }));
        b.setBackgroundResource(danger ? R.drawable.btn_danger : R.drawable.btn);
        b.setOnClickListener(l);
        return b;
    }

    private void toast(String s) {
        try { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); } catch (Throwable t) {}
    }

    private static String now() {
        return new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams x = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        x.setMargins(dp(3), dp(3), dp(3), dp(3)); return x;
    }
    private LinearLayout.LayoutParams wideLp() {
        LinearLayout.LayoutParams x = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        x.setMargins(0, dp(3), 0, dp(3)); return x;
    }
    private int dp(int v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    // ══ helpers for the generated control list (ui/controls.json → UiControls.java) ══

    /**
     * 分类标题。用户 2026-09-27：收起状态下标题不够显眼 → 13sp 提到 14.5sp 并保持加粗，
     * 上边距从 14dp 收到 11dp（分割线已经负责"断开"，不用再靠空白撑）。
     */
    private TextView sectionHeader(String text) {
        TextView h = new TextView(this);
        h.setText("▍" + text);
        h.setTextSize(14.5f); h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(Palette.ACCENT);
        h.setPadding(0, dp(11), 0, dp(2));
        return h;
    }

    /** 分类之间的分割线：收起后不再是一片空白，视线知道"这里换了一类"。 */
    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Palette.DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.setMargins(0, dp(10), 0, 0);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView subHeader(String text) {
        TextView h = new TextView(this);
        h.setText(text);
        h.setTextSize(11.5f); h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(Palette.DIM);
        h.setPadding(0, dp(9), 0, dp(3));
        return h;
    }

    /**
     * A control button: bold name, one-line consequence underneath (⚠ when dangerous).
     * The subtitle is what the spec asks every control to carry; long-press opens the full text, so a
     * clipped subtitle never hides the consequence.
     */
    private Button controlButton(final UiControls.C ctrl) {
        String label = Lang.t(ctrl.labelEn);
        String sub = (ctrl.danger ? "⚠ " : "") + Lang.t(ctrl.hintEn);
        // ⚠ 落在第二行行首：偏移 = 名字长度 + 换行
        final Button bt = mkBtn(label + "\n" + sub, false, v -> fire(ctrl.id));
        if (ctrl.danger) bt.setText(warnMark(label + "\n" + sub, true, label.length() + 1));
        bt.setTextSize(13);
        bt.setAllCaps(false);
        bt.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        bt.setPadding(dp(12), dp(9), dp(12), dp(9));
        bt.setMinHeight(dp(54));
        bt.setOnLongClickListener(v -> { showHint(ctrl); return true; });
        if (ctrl.danger) {
            // 三级，靠**形状**分而不是靠"都画红框"（用户 2026-09-27 的要求）：
            //   紧急类 = 实心红底白字（最后手段）
            //   redBorder=true = 白底红字 + 红边框 —— 只给**不可逆的那一个**（关闭 DSH）
            //   其余危险项 = 普通样式 + 说明行前的 ⚠（软重启/硬重启/真停桥/复制 token/密码使用权）
            // 为什么改：三个红边框按钮并排，反而谁都不像危险动作。
            if ("emergency".equals(ctrl.cat)) {
                bt.setBackgroundColor(Palette.DANGER);
                bt.setTextColor(Palette.ON_DANGER);
            } else if (ctrl.redBorder) {
                bt.setBackground(rounded(Palette.DANGER_FILL, Palette.DANGER));
                bt.setTextColor(Palette.DANGER);
            }
        }
        if ("project-page".equals(ctrl.id)) {
            bt.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Maopk/dsh-termux-kit"))); }
                catch (Throwable t) { toast("github.com/Maopk/dsh-termux-kit"); }
            });
        }
        return bt;
    }

    /** A rounded background with a 1.5dp border — enough to separate the two danger levels at a glance. */
    private android.graphics.drawable.Drawable rounded(int fill, int stroke) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(10));
        g.setStroke(dp(2), stroke);
        return g;
    }

    private void showHint(UiControls.C ctrl) {
        new AlertDialog.Builder(this)
                .setTitle(warnMark((ctrl.danger ? "⚠ " : "") + Lang.t(ctrl.labelEn), ctrl.danger, 0))
                .setMessage(Lang.t(ctrl.hintEn))
                .setPositiveButton(Lang.t("OK"), null)
                .show();
    }

    /** Switches are genuinely switches: they express a state, not an action (spec §二). */
    private View switchRow(UiControls.C ctrl) {
        if ("lang".equals(ctrl.id)) return buildLangRow();
        if ("password-access".equals(ctrl.id)) return buildAuthRow();
        // bridge_run — on = listening, off = soft stop. The state text beside it is the four-state line.
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.box);
        int p = dp(10); box.setPadding(p, p, p, dp(6));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Switch sw = new Switch(this);
        bridgeSwitch = sw;
        sw.setTextSize(13.5f);
        sw.setText(Lang.t(ctrl.labelEn));
        sw.setPadding(0, 0, 0, 0);
        sw.setOnCheckedChangeListener((v, on) -> {
            if (bridgeSwitchBusy) return;           // ignore the programmatic set from render()
            toast(Lang.t(on ? "Sent: Wake bridge" : "Sent: stop the bridge listening"));
            fireProgrammatic(on ? "bridge_wake" : "bridge_stop");
        });
        row.addView(sw, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(row);
        bridgeStateView = new TextView(this);
        bridgeStateView.setTextSize(12);
        bridgeStateView.setTextColor(Palette.DIM);
        bridgeStateView.setPadding(0, dp(4), 0, 0);
        box.addView(bridgeStateView);
        TextView hint = new TextView(this);
        hint.setTextSize(11); hint.setTextColor(Palette.MUTED);
        hint.setText(Lang.t(ctrl.hintEn));
        hint.setPadding(0, dp(3), 0, 0);
        hint.setOnLongClickListener(v -> { showHint(ctrl); return true; });
        box.addView(hint);
        return box;
    }

    /** No confirm dialog for a switch flip: the switch itself is the confirmation (and it can be flipped back). */
    private void fireProgrammatic(String id) {
        UiControls.C c = UiControls.get(id);
        if (c == null) return;
        toast(Lang.t("Sent: ") + Lang.t(c.labelEn));
        sendTask(id);
    }

    private View textRow(UiControls.C ctrl) {
        if ("version-update".equals(ctrl.id)) return buildUpdateRow();
        if ("bridge_state_text".equals(ctrl.id)) {
            // Rendered by render() from the status JSON; here we only need the placeholder.
            bridgeStateView = new TextView(this);
            bridgeStateView.setTextSize(12);
            bridgeStateView.setTextColor(Palette.DIM);
            bridgeStateView.setPadding(dp(2), dp(4), dp(2), dp(8));
            bridgeStateView.setText(Lang.t("Reading status…"));
            return bridgeStateView;
        }
        TextView t = new TextView(this);
        t.setTextSize(11.5f);
        t.setTextColor(Palette.MUTED);
        t.setPadding(dp(2), dp(4), dp(2), dp(8));
        t.setText(warnMark((ctrl.danger ? "⚠ " : "") + Lang.t(ctrl.hintEn), ctrl.danger, 0));
        return t;
    }

    /** 分类标题：「▾ 通道（adb 与桥分开）（8）」。数字永远来自实际渲染的行数（见上面的 rows）。 */
    private String catLabel(String catId, int rows) {
        boolean shut = collapsedCats.contains(catId);
        return (shut ? "▸ " : "▾ ") + Lang.t(UiControls.catEn(catId)) + "（" + rows + "）";
    }

    /** The bridge's states, rendered from the ONE key computed by {@link #bridgeStateKey}. */
    private String bridgeStateText(String state, JSONObject bj) {
        String ver = bj == null ? "" : bj.optString("ver", "");
        if ("running".equals(state)) return Lang.t("Bridge: running") + (ver.length() > 0 ? " · v" + ver : "");
        if ("soft".equals(state)) return Lang.t("Bridge: just dropped (wakeable)");
        if ("frozen".equals(state)) return Lang.t("Bridge: long silent (bound but not answering)");
        if ("silent".equals(state)) return Lang.t("Bridge: long silent");
        if ("never".equals(state)) return Lang.t("Bridge: not installed");
        return Lang.t("Bridge: unknown");
    }

}
