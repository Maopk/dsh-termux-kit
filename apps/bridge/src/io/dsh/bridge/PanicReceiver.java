package io.dsh.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receiver for the notification's "emergency stop" button: makes the running service kill itself
 * (disable accessibility + stop the port). Only works while a service instance is alive; when the
 * service is already stopped it does nothing.
 */
public class PanicReceiver extends BroadcastReceiver {
    public static final String ACTION_PANIC = "io.dsh.bridge.PANIC";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_PANIC.equals(intent.getAction())) return;
        BridgeService svc = BridgeService.INSTANCE;
        if (svc != null) {
            svc.panic("notification-bar emergency stop");
        } else {
            android.widget.Toast.makeText(context, "DSH Bridge is no longer running", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
