package io.dsh.bridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;

/**
 * 前台服务：让桥的进程不被系统/vivo 冻结。
 *
 * 为什么需要它：无障碍服务本身是"后台服务"，手机闲置几分钟后会被厂商的省电策略冻结
 * （表现：8788 端口还在但不再应答、广播也收不到 → 连"自己叫醒自己"都做不到）。
 * 前台服务（带常驻通知）在 Android 的进程优先级里是最高的几档之一，不会被冻结。
 *
 * 通知内容与"紧急停止"按钮保持原样：点通知开 App，点按钮立即 panic。
 */
public class BridgeForeground extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(BridgeService.CHANNEL, "DSH 桥",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(this, 1, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent panicPi = PendingIntent.getBroadcast(this, 2,
                    new Intent(this, PanicReceiver.class).setAction(PanicReceiver.ACTION_PANIC),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                    ? new Notification.Builder(this, BridgeService.CHANNEL)
                    : new Notification.Builder(this);
            b.setSmallIcon(android.R.drawable.presence_online)
                    .setContentTitle("DSH 桥正在运行")
                    .setContentText("监听 127.0.0.1:" + BridgeService.PORT + " · 音量+/- 按住 3 秒紧急停止")
                    .setOngoing(true)
                    .setContentIntent(pi)
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                            "紧急停止", panicPi).build());
            startForeground(BridgeService.NOTIF_ID, b.build());
        } catch (Throwable t) {
            // 没有通知权限时也要能起来：退化成普通前台服务
            try {
                Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                        ? new Notification.Builder(this, BridgeService.CHANNEL)
                        : new Notification.Builder(this);
                b.setSmallIcon(android.R.drawable.presence_online).setContentTitle("DSH 桥正在运行");
                startForeground(BridgeService.NOTIF_ID, b.build());
            } catch (Throwable t2) { /* 实在不行就算了，不影响无障碍功能 */ }
        }
        // 不要 START_STICKY：前台服务只是无障碍服务的「防冻结外套」，
        // 进程被杀后由系统重新绑定无障碍服务时的 onServiceConnected 再把它起回来；
        // 若用 STICKY，进程被回收后它会自己复活并挂出「DSH 桥正在运行」通知，
        // 而那时无障碍其实已经关了 —— 用户会看到「明明关了它自己又开」。
        return START_NOT_STICKY;
    }
}
