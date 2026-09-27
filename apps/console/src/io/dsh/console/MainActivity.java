package io.dsh.console;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
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
    private static final String VER = "v1.8";
    private static final int TIMEOUT_S = 45;
    private static final int MAX_HISTORY = 60;

    private TextView lamps, line, busyText, lastLine;
    private ProgressBar spinner;
    private LinearLayout busyRow;
    private Button logBtn;

    // "Install password authorization" switch (user requirement: authorization is a switch, not a button)
    private Switch authSwitch;
    private TextView authLine;
    /** Don't treat onCheckedChanged as a user action when the program sets the switch state */
    private boolean authSyncing = false;
    private boolean authKnown = false;
    private boolean authOn = false;

    private final List<Button> buttons = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private int unread = 0;

    // Log panel: its own screen, no longer the permanent output box at the bottom
    private AlertDialog logDialog;
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
                                + ") — the lamps above and the summary line below are updated");
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
        askTermuxLang();   // the file is the source of truth; only Termux can read it
        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14); root.setPadding(p, p, p, p);
        root.setBackgroundColor(Color.parseColor("#0D1117"));
        sc.addView(root);

        // ── Title row (title + version; the version is visible at a glance so an old build is obvious) ──
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.BOTTOM);
        TextView t = new TextView(this);
        t.setText(Lang.t("DSH Console")); t.setTextSize(19); t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#E6EDF3"));
        titleRow.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView vt = new TextView(this);
        vt.setText(VER); vt.setTextSize(12); vt.setTextColor(Color.parseColor("#58A6FF"));
        vt.setPadding(0, 0, 0, dp(3));
        titleRow.addView(vt);
        root.addView(titleRow);

        lamps = new TextView(this);
        lamps.setText(Lang.t("● DSH　● Bridge　● adb")); lamps.setTextSize(15);
        lamps.setPadding(0, dp(10), 0, 0); root.addView(lamps);

        line = new TextView(this);
        line.setTextSize(12); line.setPadding(0, dp(6), 0, dp(10));
        line.setTextColor(Color.parseColor("#8B949E")); root.addView(line);

        busyRow = new LinearLayout(this);
        busyRow.setOrientation(LinearLayout.HORIZONTAL);
        busyRow.setGravity(Gravity.CENTER_VERTICAL);
        busyRow.setPadding(0, 0, 0, dp(8));
        busyRow.setVisibility(View.GONE);
        spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(22), dp(22));
        sp.setMargins(0, 0, dp(10), 0);
        busyRow.addView(spinner, sp);
        busyText = new TextView(this);
        busyText.setTextSize(12.5f); busyText.setTextColor(Color.parseColor("#D29922"));
        busyRow.addView(busyText);
        root.addView(busyRow);

        // ── Shortcut row: refresh status + log (the log is never disabled while busy, so you can read it while waiting) ──
        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL);
        Button bRefresh = mkBtn(Lang.t("Refresh status"), false, v -> { toast(Lang.t("Reading status…"));
            run("status", Lang.t("Refresh status"), TermuxRunner.statusCmd(), true, false, false, 25, true); });
        logBtn = mkBtn(Lang.t("Log"), false, 0xFF79C0FF, v -> { toast("Opening log"); openLog(); });
        buttons.add(bRefresh);
        top.addView(bRefresh, lp());
        top.addView(logBtn, lp());
        root.addView(top);

        // ── Sections grouped by function ──
        for (String cat : Tasks.CATS) {
            int n = 0;
            for (Tasks.T x : Tasks.ALL) if (cat.equals(x.cat)) n++;
            if (n == 0) continue;
            TextView h = new TextView(this);
            h.setText("▍" + Lang.t(cat));   // same static-array trap as the task labels: translate at render
            h.setTextSize(13); h.setTypeface(Typeface.DEFAULT_BOLD);
            h.setTextColor(Color.parseColor("#58A6FF"));
            h.setPadding(0, dp(14), 0, dp(2));
            root.addView(h);
            for (Tasks.T task : Tasks.ALL) {
                if (!cat.equals(task.cat)) continue;
                // Translate at RENDER time: Tasks.ALL is static, so wrapping there would freeze the
                // language that happened to be active when the class was first loaded.
                Button bt = mkBtn(Lang.t(task.label) + "　·　" + Lang.t(task.hint), task.danger, v -> fire(task));
                buttons.add(bt);
                root.addView(bt, wideLp());
            }
            // The "install password authorization" switch hangs under the maintenance section (user requirement: a switch, not a button)
            if (Tasks.CARE.equals(cat)) {
                root.addView(buildAuthRow(), wideLp());
                root.addView(buildLangRow(), wideLp());
            }
        }

        // ── Last-result summary (tapping it also opens the log); hidden when there is no result, so no blank space ──
        lastLine = new TextView(this);
        lastLine.setTextSize(12); lastLine.setPadding(dp(10), dp(10), dp(10), dp(10));
        lastLine.setBackgroundResource(R.drawable.box);
        lastLine.setVisibility(View.GONE);
        LinearLayout.LayoutParams llp = wideLp();
        llp.setMargins(0, dp(12), 0, 0);
        lastLine.setOnClickListener(v -> openLog());
        root.addView(lastLine, llp);

        TextView tip = new TextView(this);
        tip.setTextSize(11); tip.setTextColor(Color.parseColor("#6E7681"));
        tip.setPadding(0, dp(10), 0, 0);
        tip.setText("Commands run through Termux (RUN_COMMAND channel); this app has no storage, network, or accessibility permission.\n"
                + Lang.t("Every run is recorded in the log: sent, callback, exit code, raw output."));
        root.addView(tip);

        setContentView(sc);
        registerReceiver(refresh, new IntentFilter("io.dsh.console.UI_REFRESH"),
                Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_NOT_EXPORTED : 0);
        render();
        autoStatus();
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
        run("status", "Auto refresh status", TermuxRunner.statusCmd(), true, false, true, 20, true);
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
        logBody.setTextColor(Color.parseColor("#C9D1D9"));
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
                history.clear(); unread = 0; updateLogBtn(); refreshLogView(); toast(Lang.t("Log cleared"));
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
        sb.append("DSH Console ").append(VER)
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
            if (cm == null) { toast("This device has no clipboard service"); return; }
            cm.setPrimaryClip(ClipData.newPlainText("DSH Console log", logText()));
            // Android 13+ pops up its own "copied" itself, so don't stack another toast
            if (Build.VERSION.SDK_INT < 33) toast("Log copied (" + history.size() + " entries)");
        } catch (Throwable t) { toast("Copy failed: " + t.getMessage()); }
    }

    private void updateLogBtn() {
        if (logBtn == null) return;
        logBtn.setText(unread > 0 ? "Log (" + unread + ")" : Lang.t("Log"));
    }

    /** Last-result summary: green = success, red = failure; with no result the whole line is hidden. */
    private void setLast(String label, int code, long ms, boolean ok) {
        lastOk = ok;
        lastSummary = (ok ? "✅ " : "⚠ ") + label + "　exit=" + code
                + (ms > 0 ? "　" + (ms / 1000.0) + "s" : "") + "　" + now()
                + Lang.t("　(tap here for the log)");
        if (lastLine != null) {
            lastLine.setText(lastSummary);
            lastLine.setTextColor(Color.parseColor(ok ? "#3FB950" : "#F85149"));
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
        t.setTextColor(Color.parseColor("#E6EDF3")); t.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(t);
        TextView sub = new TextView(this);
        sub.setText(Lang.t("auto follows the system language") + " · " + Lang.t("the widgets and the page panel follow this too"));
        sub.setTextSize(11); sub.setTextColor(Color.parseColor("#8B949E"));
        sub.setPadding(0, dp(2), dp(8), 0);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String mode = Lang.mode();
        String shown = mode.equals("auto") ? Lang.t("System") : (mode.equals("zh") ? Lang.t("Chinese") : "English");
        Button b = mkBtn(shown, false, 0xFF79C0FF, v -> {
            String next = mode.equals("auto") ? "zh" : (mode.equals("zh") ? "en" : "auto");
            Lang.setMode(this, next);
            run("lang", Lang.t("Language"), TermuxRunner.HOME + "/.local/bin/dsh-lang set " + next, false, true);
            recreate();
        });
        row.addView(b);
        box.addView(row);
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
        t.setTextColor(Color.parseColor("#E6EDF3")); t.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(t);
        TextView sub = new TextView(this);
        sub.setText(Lang.t("On = the AI may use your 6-digit lock-screen password to pass identity checks for you (installing packages, lifting settings restrictions, etc.); Off = revoked at once"));
        sub.setTextSize(11); sub.setTextColor(Color.parseColor("#8B949E"));
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
        authLine.setTextColor(Color.parseColor("#8B949E"));
        authLine.setPadding(0, dp(6), 0, 0);
        authLine.setText(Lang.t("Reading authorization state…"));
        box.addView(authLine);
        return box;
    }

    /** Reads the helper's status (exit 0 = authorized, 1 = not authorized) so the switch reflects the real state. */
    private void refreshAuthState() {
        run("installpass_query", "Query password access",
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
            String head = !known ? Lang.t("State: unreadable (no callback from Termux; tap Refresh status to retry)")
                    : (on ? Lang.t("Now: authorized (the AI can pass identity checks for you)") : Lang.t("Now: not authorized (any password-protected check needs you in person)"));
            String d = detail == null ? "" : detail.trim();
            authLine.setText(head + (d.isEmpty() ? "" : "　" + d));
            authLine.setTextColor(Color.parseColor(!known ? "#D29922" : (on ? "#3FB950" : "#8B949E")));
        }
    }

    private void onAuthToggled(boolean wantOn) {
        if (!authKnown) return;
        if (wantOn == authOn) return;
        if (wantOn) { askPasswordAndAuthorize(); return; }
        new AlertDialog.Builder(this)
                .setTitle("Revoke password access?")
                .setMessage("This revokes it: ~/.dsh-auth-pass is deleted.\n"
                        + "Afterwards the AI can no longer use those 6 digits to pass any identity check for you (the security check when installing an APK, "
                        + "lifting app settings restrictions, sensitive confirmations in developer options… they all stop with you).\n"
                        + "(Does not affect DSH / Bridge / adb — each has its own revoke entry.)")
                .setNegativeButton(Lang.t("Cancel"), (d, w) -> setAuthUi(true, authOn, null))
                .setPositiveButton("Revoke", (d, w) -> {
                    toast("Revoking…");
                    run("installpass_revoke", "Revoke password access",
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
                .setMessage("Type that 6-digit lock-screen password → it is written to ~/.dsh-auth-pass (600).\n"
                        + "Used only to pass system identity checks for you (installing packages, lifting settings restrictions, etc.).\n"
                        + Lang.t("Turn the switch off at any time to revoke."))
                .setView(et)
                .setNegativeButton(Lang.t("Cancel"), (d, w) -> setAuthUi(true, authOn, null))
                .setPositiveButton(Lang.t("Authorize"), (d, w) -> {
                    String pw = et.getText() == null ? "" : et.getText().toString().trim();
                    if (!pw.matches("[0-9]{6}")) { toast("Must be 6 digits"); setAuthUi(true, authOn, null); return; }
                    toast("Writing authorization…");
                    // Runs once through Termux only; the password never enters the log (history records the label only)
                    run("installpass_set", "Write password authorization",
                            "printf '%s' '" + pw + "' | " + TermuxRunner.HOME + "/.local/bin/dsh-auth-pass set", false);
                })
                .show();
    }

    // ---------- Execution ----------
    private void fire(Tasks.T task) {
        if (task.danger) {
            new AlertDialog.Builder(this)
                    .setTitle(Lang.t("Run ") + Lang.t(task.label) + "?")
                    .setMessage(Lang.t(task.hint) + "\n\n⚠ Confirmation required: restart/shutdown drops the current web session; "
                            + Lang.t("revoke-style actions do not come back on their own (re-authorize to restore)."))
                    .setNegativeButton(Lang.t("Cancel"), null)
                    .setPositiveButton(Lang.t("Run"), (d, w) -> {
                        toast(Lang.t("Sent: ") + Lang.t(task.label));
                        sendTask(task);
                    })
                    .show();
            return;
        }
        toast(Lang.t("Sent: ") + Lang.t(task.label));
        sendTask(task);
    }

    /** Single entry point: uses each task's own wait window (long tasks like backup/restart no longer raise false alarms). */
    private void sendTask(Tasks.T task) {
        run(task.id, Lang.t(task.label), task.cmd != null ? task.cmd : TermuxRunner.taskCmd(task.id),
                false, false, false, task.waitS, false);
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
            pushHistory("[" + now() + "] ⚠ RUN_COMMAND permission not granted yet; a permission prompt was shown.\n"
                    + "   If no dialog appeared: Settings → Apps → DSH Console → Permissions → allow RUN_COMMAND.");
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

    private void pushHistory(String s) {
        history.add(s);
        while (history.size() > MAX_HISTORY) history.remove(0);
        if (logDialog == null) { unread++; updateLogBtn(); }
        refreshLogView();
    }

    // ---------- Rendering ----------
    private void render() {
        boolean perm = TermuxRunner.hasPermission(this);
        String json = Last.status(this);
        String d = "grey", br = "grey", a = "grey";
        String detail = Lang.t("No status yet — tap Refresh status");
        if (json != null && json.length() > 0) {
            try {
                JSONObject o = new JSONObject(json);
                JSONObject L = o.optJSONObject("lamps");
                if (L != null) { d = L.optString("dsh", "grey"); br = L.optString("bridge", "grey"); a = L.optString("adb", "grey"); }
                JSONObject dj = o.optJSONObject("dsh"), bj = o.optJSONObject("bridge"), aj = o.optJSONObject("adb");
                detail = o.optString("ts", "");
                if (dj != null) detail += "　DSH " + (dj.optBoolean("ok") ? "running (HTTP " + dj.optString("http") + ")" : (dj.optBoolean("port") ? "port open but not ready" : Lang.t("stopped")));
                if (bj != null) detail += "　Bridge " + (bj.optBoolean("ok") ? "v" + bj.optString("ver") : (bj.optBoolean("port") ? "port open but not answering" : "none"));
                if (aj != null) {
                    JSONArray ds = aj.optJSONArray("devices");
                    detail += "　adb " + (ds != null && ds.length() > 0 ? ds.optString(0) : Lang.t("not connected"));
                }
                long ago = (System.currentTimeMillis() - Last.statusAt(this)) / 1000;
                detail += "　(" + (ago < 2 ? "just now" : ago + "s ago") + ")";
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
        int cursor = 0;
        for (int k = 0; k < names.length; k++) {
            int dot = cursor;                       // the ● of this segment
            int nameStart = dot + 2;                // "● " is two characters
            int nameEnd = nameStart + names[k].length();
            int col = c(cols[k]);
            if (dot + 1 <= lampText.length()) s.setSpan(new ForegroundColorSpan(col), dot, dot + 1, 0);
            if (nameEnd <= lampText.length()) s.setSpan(new ForegroundColorSpan(col), nameStart, nameEnd, 0);
            cursor = nameEnd + 1;                   // skip the full-width separator
        }
        lamps.setText(s);
        line.setText(detail + (perm ? "" : "　⚠ missing RUN_COMMAND permission"));
        refreshLogView();
    }

    private static int c(String lamp) {
        if ("green".equals(lamp)) return Color.parseColor("#3FB950");
        if ("yellow".equals(lamp)) return Color.parseColor("#D29922");
        if ("red".equals(lamp)) return Color.parseColor("#F85149");
        return Color.parseColor("#8B949E");
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
        int on = onColor != 0 ? onColor : (danger ? 0xFFFFB4A9 : 0xFFE6EDF3);
        int off = 0xFF4A5058;
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
}
