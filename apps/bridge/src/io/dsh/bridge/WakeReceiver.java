package io.dsh.bridge;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 唤醒接收器：Termux 侧用
 *   am broadcast -a io.dsh.bridge.WAKE -n io.dsh.bridge/.WakeReceiver --es token <token>
 * 让桥重新开始监听（绕开 Android 10+ 的「后台启动 Activity 限制」）。
 *
 * 两种情形：
 *   ① 服务还活着（只是软停了端口）→ 直接 resumeListening，任何广播都能唤醒（旧行为不变）。
 *   ② 服务已被关闭（软停后被系统解绑 / sleep / panic）→ **必须带对 token** 才允许重新授权：
 *      把本服务写回系统的「已启用无障碍服务」列表，让系统重新绑定它。
 *      没有 token 的唤醒一律忽略 —— 否则任何 App 都能把一个已被用户关掉的无障碍服务拉起来。
 *
 * 注意：重新授权依赖 WRITE_SECURE_SETTINGS（必须由用户用 adb 显式 pm grant 过一次）。
 * 没有这个权限时第 ② 条会失败，只能由用户本人在系统设置里重新开启——这正是我们要避免的，
 * 所以权限缺失时会在日志里明确写出来。
 */
public class WakeReceiver extends BroadcastReceiver {
    public static final String ACTION_WAKE = "io.dsh.bridge.WAKE";
    private static final String TAG = "DSHBridge";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!ACTION_WAKE.equals(intent.getAction())) return;

        // ① 服务还在：最轻的路径
        BridgeService svc = BridgeService.INSTANCE;
        if (svc != null) {
            svc.resumeListening();
            return;
        }

        // ② 服务已被关闭：要 token 才允许重新授权
        String given = intent.getStringExtra("token");
        String real = context.getSharedPreferences(BridgeService.PREFS, Context.MODE_PRIVATE)
                .getString("token", "");
        if (real == null || real.isEmpty() || given == null || !real.equals(given.trim())) {
            Log.w(TAG, "WAKE 被忽略：没有带正确的 token（重新授权无障碍必须带 token）");
            return;
        }
        reenable(context);
    }

    /** 把本服务写回「已启用的无障碍服务」，让系统重新绑定（需要 WRITE_SECURE_SETTINGS） */
    private void reenable(Context ctx) {
        try {
            ComponentName me = new ComponentName(ctx, BridgeService.class);
            String longForm = me.flattenToString();          // pkg/全类名
            String shortForm = me.flattenToShortString();    // pkg/.类名

            String cur = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            Set<String> set = new LinkedHashSet<>();
            if (cur != null) {
                for (String s : cur.split(":")) if (!s.isEmpty()) set.add(s);
            }
            boolean has = false;
            for (String s : set) {
                if (s.equalsIgnoreCase(longForm) || s.equalsIgnoreCase(shortForm)) { has = true; break; }
            }
            if (!has) {
                set.add(longForm);
                Settings.Secure.putString(ctx.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, TextUtils.join(":", set));
            }
            Settings.Secure.putInt(ctx.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            Log.i(TAG, "WAKE：已重新授权无障碍" + (has ? "（本来就在列表里）" : "（已写回列表）"));
        } catch (Throwable t) {
            // 没有 WRITE_SECURE_SETTINGS 时 SecurityException 会落到这里
            Log.w(TAG, "WAKE：重新授权失败（多半缺 WRITE_SECURE_SETTINGS）：" + t);
        }
    }
}
