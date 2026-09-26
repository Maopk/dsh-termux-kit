package io.dsh.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 通知栏「紧急停止」按钮的接收器：直接让正在运行的服务自杀（关闭无障碍 + 停止端口）。
 * 只在服务实例存活时有效；服务已停时它什么也不做。
 */
public class PanicReceiver extends BroadcastReceiver {
    public static final String ACTION_PANIC = "io.dsh.bridge.PANIC";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_PANIC.equals(intent.getAction())) return;
        BridgeService svc = BridgeService.INSTANCE;
        if (svc != null) {
            svc.panic("通知栏紧急停止");
        } else {
            android.widget.Toast.makeText(context, "DSH 桥已不在运行", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
