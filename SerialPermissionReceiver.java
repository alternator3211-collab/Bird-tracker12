package com.z68.birdtracker.usb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SerialPermissionReceiver extends BroadcastReceiver {
    public interface Callback {
        void onPermissionResult(boolean granted);
    }

    public static final String ACTION = "com.z68.birdtracker.USB_SERIAL_PERMISSION";
    private static Callback callback;

    public static synchronized void setCallback(Callback c) {
        callback = c;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION.equals(intent.getAction())) {
            boolean granted = intent.getBooleanExtra("permission", false);
            Callback c;
            synchronized (SerialPermissionReceiver.class) {
                c = callback;
            }
            if (c != null) c.onPermissionResult(granted);
        }
    }
}
