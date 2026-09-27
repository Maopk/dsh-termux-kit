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
 * Foreground service: keeps the bridge process from being frozen by the system / vivo.
 *
 * Why it is needed: an accessibility service is itself a "background service" and gets frozen by the
 * vendor's battery saver after a few idle minutes (symptoms: port 8788 is still open but no longer
 * answers, and broadcasts are not received either → it cannot even wake itself up). A foreground
 * service (with a persistent notification) sits among the highest process priorities in Android and
 * is not frozen.
 *
 * The notification content and the "emergency stop" button stay as they are: tapping the notification
 * opens the app, tapping the button panics immediately.
 */
public class BridgeForeground extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(BridgeService.CHANNEL, "DSH Bridge",
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
                    .setContentTitle("DSH Bridge is running")
                    .setContentText("Listening on 127.0.0.1:" + BridgeService.PORT + " · hold volume +/- for 3s to stop")
                    .setOngoing(true)
                    .setContentIntent(pi)
                    .addAction(new Notification.Action.Builder(
                            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                            "Emergency stop", panicPi).build());
            startForeground(BridgeService.NOTIF_ID, b.build());
        } catch (Throwable t) {
            // Must still come up without notification permission: degrade to a plain foreground service
            try {
                Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                        ? new Notification.Builder(this, BridgeService.CHANNEL)
                        : new Notification.Builder(this);
                b.setSmallIcon(android.R.drawable.presence_online).setContentTitle("DSH Bridge is running");
                startForeground(BridgeService.NOTIF_ID, b.build());
            } catch (Throwable t2) { /* if it really will not work, never mind; accessibility still functions */ }
        }
        // Not START_STICKY: the foreground service is only the accessibility service's anti-freeze coat,
        // and onServiceConnected brings it back when the system rebinds the accessibility service after
        // the process is killed. With STICKY it would resurrect itself once the process is reclaimed and
        // post a "DSH Bridge is running" notification while accessibility is actually off — users would
        // see "I turned it off and it switched itself back on".
        return START_NOT_STICKY;
    }
}
