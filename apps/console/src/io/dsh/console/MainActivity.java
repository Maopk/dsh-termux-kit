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
 * 控制台主界面（v0.5）。
 *
 * 布局约定：
 *   · 顶部：标题 + 版本、三盏灯、一行明细、忙碌行（转圈 + 「已等待 Ns」计时）；
 *   · 快捷行：刷新状态 / **日志**（带未读条数角标）；
 *   · 主体：**按功能分类分段**（启动·停止 / 通道 / 维护 / 紧急），每段一个蓝色小标题；
 *   · 底部：只留一行「最近一次结果」摘要（可点开日志），不再有常驻的大输出框——
 *     以前那个框在没输出时就是一块空白，看着像坏了；现在内容全在「日志」里。
 *
 * 反馈约定（用户反馈"像壳子"后定下）：
 *   · 点按钮 → 立刻 Toast + 忙碌态（按钮变灰不可点）+ 转圈 + 计时；
 *   · 结果到了 → 那一行摘要变色（✅ 绿 / ⚠ 红）并计入日志未读角标；
 *   · 超过 45 秒没回传 → 明确列出可能原因；
 *   · 日志永不留白：没记录时写清楚去哪里点。
 */
public class MainActivity extends Activity {
    private static final String VER = "v1.1";
    private static final int TIMEOUT_S = 45;
    private static final int MAX_HISTORY = 60;

    private TextView lamps, line, busyText, lastLine;
    private ProgressBar spinner;
    private LinearLayout busyRow;
    private Button logBtn;

    // 「安装密码授权」开关（用户要求：授权是开关，不是按钮）
    private Switch authSwitch;
    private TextView authLine;
    /** 程序改开关状态时不要把 onCheckedChanged 当成用户操作 */
    private boolean authSyncing = false;
    private boolean authKnown = false;
    private boolean authOn = false;

    private final List<Button> buttons = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private int unread = 0;

    // 日志面板：独立一屏，不再是底下常驻的输出框
    private AlertDialog logDialog;
    private TextView logBody;
    private ScrollView logScroll;

    private String lastSummary = "";
    private boolean lastOk = true;

    private boolean pending = false;
    private long pendingSince = 0;
    private String pendingLabel = "";
    /** 当前这次等待的窗口（秒）与是不是"查询类"（查询超时短、且可以自动重试一次） */
    private int pendingWaitS = 45;
    private boolean pendingQuery = false;
    private boolean queryRetried = false;
    private String pendingCmdId = "", pendingCmd = "";
    private boolean pendingIsStatus = false;
    /** 这次等待是不是"静默发出"的（自动刷新）：是的话完成行也不写日志，免得刷屏。 */
    private boolean pendingSilent = false;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!pending) return;
            int s = (int) ((System.currentTimeMillis() - pendingSince) / 1000);
            busyText.setText("正在执行：" + pendingLabel + "　已等待 " + s + "s（这条通常 ≤" + pendingWaitS + "s）");
            if (s >= pendingWaitS) {
                // 查询类（刷新状态/授权查询）超时：先自动补发一次 —— 手机上"忙一下"很常见，
                // 实测：跑大备份时 load 冲到 7，App 发的请求会静默失败一次，重发就好（2026-09-27 00:10）。
                if (pendingQuery && !queryRetried) {
                    queryRetried = true;
                    busyText.setText("第一次没回音，正在补发：" + pendingLabel);
                    pendingSince = System.currentTimeMillis();
                    TermuxRunner.run(MainActivity.this, pendingCmdId, pendingLabel, pendingCmd, pendingIsStatus);
                    ui.postDelayed(this, 1000);
                    return;
                }
                finishPending("⚠ 等了 " + pendingWaitS + " 秒没收到 Termux 回传（这条通常 "
                        + pendingWaitS + " 秒内回来）。按可能性排："
                        + "\n   · 手机当时很忙（后台在跑备份/自检之类）—— 最常见，点「刷新状态」再试即可"
                        + "\n   · 这次是长任务（备份/重启本来就慢），可以再等等看"
                        + "\n   · 未授予 RUN_COMMAND 权限、或 Termux 里 allow-external-apps 被关掉"
                        + "\n   · 系统拦了「从后台启动服务」（小部件点击最容易遇到）");
                return;
            }
            ui.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver refresh = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            long ms = pending ? System.currentTimeMillis() - pendingSince : 0;
            String label = pendingLabel.isEmpty() ? "任务" : pendingLabel;
            int code = i.getIntExtra("exit", -1);
            String cmdId = i.getStringExtra("cmdId");
            if ("installpass_query".equals(cmdId)) {
                // 授权状态查询：只更新开关，不进日志、不写摘要、不碰 pending
                setAuthUi(true, code == 0, i.getStringExtra("output"));
                return;
            }
            if (i.getBooleanExtra("isStatus", false)) {
                // ⚠ 先取 pending 再清（v0.6 就是先清了 pending 才取，导致 wasPending 永远是 false
                //   → 状态刷新的摘要行**从来没出现过**；实机点两次才看出来）。
                // 只有**用户点的那次**才更新"最近一次结果"：
                //   任务跑完后 Termux 侧会自动再补一次状态刷新，它的回传会把刚失败的任务
                //   刷成"✅ 刷新状态 exit=0"——看起来就像刚才那次根本没失败（v0.5 实机抓到）。
                boolean wasPending = pending;
                boolean silent = pendingSilent;
                String statusLabel = pendingLabel.isEmpty() ? "刷新状态" : pendingLabel;
                if (pending) finishPending(null);
                if (wasPending) {
                    setLast(statusLabel, code, ms, code == 0);
                    // ⚠ 日志也要留证据：以前状态查询**只有"已发送"没有完成行**，
                    //   看着就像"没人回传"（用户 2026-09-27 00:17 就是被这个骗到的）。
                    //   自动刷新仍然静默（silent），免得把日志刷满。
                    if (!silent) {
                        pushHistory("[" + now() + "] " + (code == 0 ? "✅ " : "⚠ ") + statusLabel
                                + " 完成（exit=" + code + (ms > 0 ? "，用时 " + (ms / 1000.0) + "s" : "")
                                + "）—— 结果已更新到顶上的灯和下面那行摘要");
                    }
                }
                render();
                return;
            }
            String head = "[" + now() + "] " + label + " 完成（exit=" + code
                    + (ms > 0 ? "，用时 " + (ms / 1000.0) + "s" : "") + "）";
            String body = i.getStringExtra("output");
            if (pending) finishPending(null);
            pushHistory(head + (body == null || body.trim().isEmpty() ? "\n（这次没有输出）" : "\n" + body.trim()));
            setLast(label, code, ms, code == 0);
            render();
            // 撤销/写入授权之后，让开关回到真实状态
            if ("installpass_revoke".equals(cmdId) || "installpass_set".equals(cmdId)) refreshAuthState();
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14); root.setPadding(p, p, p, p);
        root.setBackgroundColor(Color.parseColor("#0D1117"));
        sc.addView(root);

        // ── 标题行（标题 + 版本，版本一眼可见，免得又是旧包） ──
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.BOTTOM);
        TextView t = new TextView(this);
        t.setText("DSH 控制台"); t.setTextSize(19); t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#E6EDF3"));
        titleRow.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView vt = new TextView(this);
        vt.setText(VER); vt.setTextSize(12); vt.setTextColor(Color.parseColor("#58A6FF"));
        vt.setPadding(0, 0, 0, dp(3));
        titleRow.addView(vt);
        root.addView(titleRow);

        lamps = new TextView(this);
        lamps.setText("● DSH　● 桥　● adb"); lamps.setTextSize(15);
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

        // ── 快捷行：刷新状态 + 日志（日志不参与忙碌禁用，等结果时也能翻看） ──
        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL);
        Button bRefresh = mkBtn("刷新状态", false, v -> { toast("正在读取状态…");
            run("status", "刷新状态", TermuxRunner.statusCmd(), true, false, false, 25, true); });
        logBtn = mkBtn("日志", false, 0xFF79C0FF, v -> { toast("打开日志"); openLog(); });
        buttons.add(bRefresh);
        top.addView(bRefresh, lp());
        top.addView(logBtn, lp());
        root.addView(top);

        // ── 按功能分类分段 ──
        for (String cat : Tasks.CATS) {
            int n = 0;
            for (Tasks.T x : Tasks.ALL) if (cat.equals(x.cat)) n++;
            if (n == 0) continue;
            TextView h = new TextView(this);
            h.setText("▍" + cat);
            h.setTextSize(13); h.setTypeface(Typeface.DEFAULT_BOLD);
            h.setTextColor(Color.parseColor("#58A6FF"));
            h.setPadding(0, dp(14), 0, dp(2));
            root.addView(h);
            for (Tasks.T task : Tasks.ALL) {
                if (!cat.equals(task.cat)) continue;
                Button bt = mkBtn(task.label + "　·　" + task.hint, task.danger, v -> fire(task));
                buttons.add(bt);
                root.addView(bt, wideLp());
            }
            // 维护类后面挂「安装密码授权」开关（用户要求：授权要是个开关，不是按钮）
            if (Tasks.CARE.equals(cat)) root.addView(buildAuthRow(), wideLp());
        }

        // ── 最近一次结果摘要（点它也能开日志）；没有结果时不显示，不留空白 ──
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
        tip.setText("命令经 Termux 执行（RUN_COMMAND 通道）；本 App 无存储/网络/无障碍权限。\n"
                + "所有执行记录都在「日志」里：发送、回传、退出码、原始输出。");
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
        render();
        autoStatus();
        refreshAuthState();
    }

    private long lastAuto = 0;
    /** 进前台自动读一次状态：有在飞的不发、缓存新鲜不发、15 秒内不重复发（v0.2 会重复三遍）。 */
    private void autoStatus() {
        if (pending) return;
        long now = System.currentTimeMillis();
        if (now - Last.statusAt(this) < 20000) return;
        if (now - lastAuto < 15000) return;
        lastAuto = now;
        // v0.6：自动刷新的文案与手动点「刷新状态」分开，否则日志/摘要里两条长得一模一样，
        // 分不清"是我点的"还是"App 进前台自己刷的"。
        run("status", "自动刷新状态", TermuxRunner.statusCmd(), true, false, true, 20, true);
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        try { unregisterReceiver(refresh); } catch (Throwable t) {}
        try { if (logDialog != null) logDialog.dismiss(); } catch (Throwable t) {}
        super.onDestroy();
    }

    // ---------- 日志（独立一屏） ----------
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
                .setTitle("日志")
                .setView(logScroll)
                .setPositiveButton("关闭", null)
                .setNeutralButton("复制全部", null)
                .setNegativeButton("清空", null)
                .create();
        d.setOnShowListener(x -> {
            Button cp = d.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (cp != null) cp.setOnClickListener(v -> copyLog());
            Button cl = d.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (cl != null) cl.setOnClickListener(v -> {
                history.clear(); unread = 0; updateLogBtn(); refreshLogView(); toast("日志已清空");
            });
        });
        d.setOnDismissListener(x -> { logDialog = null; logBody = null; logScroll = null; });
        logDialog = d;
        unread = 0;
        updateLogBtn();
        d.show();
        refreshLogView();
    }

    /** 日志正文：头部写清楚条数与用途，没记录时给指引（不留白）。 */
    private String logText() {
        StringBuilder sb = new StringBuilder();
        sb.append("DSH 控制台 ").append(VER)
          .append("　共 ").append(history.size()).append(" 条（最多留 ").append(MAX_HISTORY).append(" 条）\n");
        sb.append("──────────────────────────\n");
        if (history.isEmpty()) {
            sb.append("（还没有记录）\n\n")
              .append("· 点「刷新状态」读三个灯\n")
              .append("· 点任意任务按钮执行：发送、回传、退出码、原始输出都记在这里\n")
              .append("· 输出为空也会写明「这次没有输出」，不会留白\n");
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
            if (cm == null) { toast("这台设备没有剪贴板服务"); return; }
            cm.setPrimaryClip(ClipData.newPlainText("DSH 控制台日志", logText()));
            // Android 13+ 系统自己会弹「已复制」，不用再叠一个 Toast
            if (Build.VERSION.SDK_INT < 33) toast("日志已复制（" + history.size() + " 条）");
        } catch (Throwable t) { toast("复制失败：" + t.getMessage()); }
    }

    private void updateLogBtn() {
        if (logBtn == null) return;
        logBtn.setText(unread > 0 ? "日志 (" + unread + ")" : "日志");
    }

    /** 最近一次结果摘要：绿=成功、红=失败；没有结果就整行隐藏。 */
    private void setLast(String label, int code, long ms, boolean ok) {
        lastOk = ok;
        lastSummary = (ok ? "✅ " : "⚠ ") + label + "　exit=" + code
                + (ms > 0 ? "　" + (ms / 1000.0) + "s" : "") + "　" + now()
                + "　（点这里看日志）";
        if (lastLine != null) {
            lastLine.setText(lastSummary);
            lastLine.setTextColor(Color.parseColor(ok ? "#3FB950" : "#F85149"));
            lastLine.setVisibility(View.VISIBLE);
        }
    }

    // ---------- 安装密码授权（开关，不是按钮） ----------
    /** 维护类里那一行：左边说明、右边开关，下面一行写当前状态。 */
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
        t.setText("密码使用权"); t.setTextSize(13.5f);
        t.setTextColor(Color.parseColor("#E6EDF3")); t.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(t);
        TextView sub = new TextView(this);
        sub.setText("打开＝AI 可用你那 6 位锁屏密码替你过身份验证（装包、解除设置限制等）；关掉＝立刻收回");
        sub.setTextSize(11); sub.setTextColor(Color.parseColor("#8B949E"));
        sub.setPadding(0, dp(2), dp(8), 0);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        authSwitch = new Switch(this);
        authSwitch.setShowText(false);
        authSwitch.setEnabled(false);   // 状态读到之前不许乱拨
        authSwitch.setOnCheckedChangeListener((v, checked) -> {
            if (authSyncing) return;    // 程序同步状态时不算用户操作
            onAuthToggled(checked);
        });
        row.addView(authSwitch);
        box.addView(row);

        authLine = new TextView(this);
        authLine.setTextSize(11.5f);
        authLine.setTextColor(Color.parseColor("#8B949E"));
        authLine.setPadding(0, dp(6), 0, 0);
        authLine.setText("正在读取授权状态…");
        box.addView(authLine);
        return box;
    }

    /** 读 helper 的 status（退出 0=已授权，1=未授权），让开关反映真实状态。 */
    private void refreshAuthState() {
        run("installpass_query", "查询密码使用权",
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
            String head = !known ? "状态：读不到（Termux 没回传，点「刷新状态」重试）"
                    : (on ? "当前：已授权（AI 可代你过身份验证）" : "当前：未授权（任何需要密码的验证都得你本人来）");
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
                .setTitle("收回「密码使用权」？")
                .setMessage("等于撤销：删掉 ~/.dsh-auth-pass。\n"
                        + "之后 AI 不能再用这 6 位替你过任何身份验证（装 APK 的「安全验证」、"
                        + "解除应用设置限制、开发者选项里的敏感确认…都会停在你这儿）。\n"
                        + "（不影响 DSH / 桥 / adb —— 它们各有自己的撤销入口。）")
                .setNegativeButton("取消", (d, w) -> setAuthUi(true, authOn, null))
                .setPositiveButton("撤销", (d, w) -> {
                    toast("正在撤销…");
                    run("installpass_revoke", "收回密码使用权",
                            TermuxRunner.HOME + "/.local/bin/dsh-auth-pass revoke", false);
                })
                .show();
    }

    /** 重新授权要 6 位密码 —— App 不能凭空造出凭据，所以这里必须你输一次。 */
    private void askPasswordAndAuthorize() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setHint("6 位数字");
        et.setTextSize(16);
        int p = dp(16); et.setPadding(p, p, p, p);
        new AlertDialog.Builder(this)
                .setTitle("授权 AI 使用你的密码")
                .setMessage("输入那 6 位锁屏密码 → 写进 ~/.dsh-auth-pass（600）。\n"
                        + "用途仅限：替你过系统身份验证（装包、解除设置限制等）。\n"
                        + "随时把开关关掉即可收回。")
                .setView(et)
                .setNegativeButton("取消", (d, w) -> setAuthUi(true, authOn, null))
                .setPositiveButton("授权", (d, w) -> {
                    String pw = et.getText() == null ? "" : et.getText().toString().trim();
                    if (!pw.matches("[0-9]{6}")) { toast("要 6 位数字"); setAuthUi(true, authOn, null); return; }
                    toast("正在写入授权…");
                    // 只经 Termux 执行一次；密码不进日志（history 只记 label）
                    run("installpass_set", "写入密码授权",
                            "printf '%s' '" + pw + "' | " + TermuxRunner.HOME + "/.local/bin/dsh-auth-pass set", false);
                })
                .show();
    }

    // ---------- 执行 ----------
    private void fire(Tasks.T task) {
        if (task.danger) {
            new AlertDialog.Builder(this)
                    .setTitle("确认执行「" + task.label + "」？")
                    .setMessage(task.hint + "\n\n⚠ 需要二次确认：重启/关闭会断开当前网页会话；"
                            + "撤销类动作不会自动恢复（要恢复得重新授权）。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("执行", (d, w) -> {
                        toast("已发送：" + task.label);
                        sendTask(task);
                    })
                    .show();
            return;
        }
        toast("已发送：" + task.label);
        sendTask(task);
    }

    /** 统一入口：用任务自带的等待窗口（备份/重启这类长任务不再假报警）。 */
    private void sendTask(Tasks.T task) {
        run(task.id, task.label, task.cmd != null ? task.cmd : TermuxRunner.taskCmd(task.id),
                false, false, false, task.waitS, false);
    }

    private void run(String cmdId, String label, String command, boolean isStatus) {
        run(cmdId, label, command, isStatus, false, false, 90, false);
    }

    /** quiet=true：只发不问（用于读授权状态这类后台查询，不进日志、不动忙碌态）。 */
    private void run(String cmdId, String label, String command, boolean isStatus, boolean quiet) {
        run(cmdId, label, command, isStatus, quiet, false, 25, true);
    }

    /** silentLog=true：进忙碌态但不写「已发送」日志（自动刷新用它，免得日志被刷屏）。 */
    private void run(String cmdId, String label, String command, boolean isStatus, boolean quiet,
                     boolean silentLog, int waitS, boolean isQuery) {
        if (!TermuxRunner.installed(this)) {
            pushHistory("[" + now() + "] ⚠ 找不到 Termux（com.termux），请先安装 Termux。");
            render(); return;
        }
        if (!TermuxRunner.hasPermission(this)) {
            try { requestPermissions(new String[] { TermuxRunner.PERM }, 1); } catch (Throwable t) {}
            pushHistory("[" + now() + "] ⚠ 还没拿到 RUN_COMMAND 权限，已弹出授权请求。\n"
                    + "   若没有弹窗：设置 → 应用 → DSH 控制台 → 权限 → 允许 RUN_COMMAND。");
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
        if (!ok) { if (!quiet) finishPending("⚠ 启动 Termux 命令失败。" ); return; }
        if (!quiet && !silentLog) {
            pushHistory("[" + now() + "] 已发送：" + label + "，等待 Termux 回传…");
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
        if (busy) busyText.setText("正在执行：" + pendingLabel + "　已等待 0s");
        for (Button bt : buttons) bt.setEnabled(!busy);
    }

    private void pushHistory(String s) {
        history.add(s);
        while (history.size() > MAX_HISTORY) history.remove(0);
        if (logDialog == null) { unread++; updateLogBtn(); }
        refreshLogView();
    }

    // ---------- 渲染 ----------
    private void render() {
        boolean perm = TermuxRunner.hasPermission(this);
        String json = Last.status(this);
        String d = "grey", br = "grey", a = "grey";
        String detail = "还没有状态 —— 点「刷新状态」";
        if (json != null && json.length() > 0) {
            try {
                JSONObject o = new JSONObject(json);
                JSONObject L = o.optJSONObject("lamps");
                if (L != null) { d = L.optString("dsh", "grey"); br = L.optString("bridge", "grey"); a = L.optString("adb", "grey"); }
                JSONObject dj = o.optJSONObject("dsh"), bj = o.optJSONObject("bridge"), aj = o.optJSONObject("adb");
                detail = o.optString("ts", "");
                if (dj != null) detail += "　DSH " + (dj.optBoolean("ok") ? "在跑(HTTP " + dj.optString("http") + ")" : (dj.optBoolean("port") ? "端口在但没就绪" : "停了"));
                if (bj != null) detail += "　桥 " + (bj.optBoolean("ok") ? "v" + bj.optString("ver") : (bj.optBoolean("port") ? "端口在不应答" : "无"));
                if (aj != null) {
                    JSONArray ds = aj.optJSONArray("devices");
                    detail += "　adb " + (ds != null && ds.length() > 0 ? ds.optString(0) : "未连");
                }
                long ago = (System.currentTimeMillis() - Last.statusAt(this)) / 1000;
                detail += "　（" + (ago < 2 ? "刚刚" : ago + "s 前") + "）";
            } catch (Throwable t) {
                    // 说实话：多半是回传被截断（状态 JSON 从尾部切会把头切掉）
                    detail = "状态解析失败（回传可能被截断）：" + t.getMessage();
                }
        }
        // 上色：**动态找每个圆点的位置**（写死 0/4/8 会涂到字母和汉字上——v0.2 的 bug）
        String lampText = "● DSH　● 桥　● adb";
        SpannableString s = new SpannableString(lampText);
        int i1 = lampText.indexOf('●');
        int i2 = lampText.indexOf('●', i1 + 1);
        int i3 = lampText.indexOf('●', i2 + 1);
        if (i1 >= 0) s.setSpan(new ForegroundColorSpan(c(d)), i1, i1 + 1, 0);
        if (i2 >= 0) s.setSpan(new ForegroundColorSpan(c(br)), i2, i2 + 1, 0);
        if (i3 >= 0) s.setSpan(new ForegroundColorSpan(c(a)), i3, i3 + 1, 0);
        // 灯后面的名字也跟着上色，一眼能对上是哪一路
        if (i1 >= 0) s.setSpan(new ForegroundColorSpan(c(d)), i1 + 2, Math.min(i1 + 5, lampText.length()), 0);
        if (i2 >= 0) s.setSpan(new ForegroundColorSpan(c(br)), i2 + 2, i2 + 3, 0);
        if (i3 >= 0) s.setSpan(new ForegroundColorSpan(c(a)), i3 + 2, Math.min(i3 + 5, lampText.length()), 0);
        lamps.setText(s);
        line.setText(detail + (perm ? "" : "　⚠ 缺 RUN_COMMAND 权限"));
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
        // 文字按启用/禁用两态给色（纯色会让"变灰"看不出来——v0.2 的 bug）
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
