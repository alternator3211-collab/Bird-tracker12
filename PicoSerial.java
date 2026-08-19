package com.z68.birdtracker.usb;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

public final class PicoSerial {
    public interface Listener {
        void onState(String text);
    }

    private static final String ACTION_USB_PERMISSION = SerialPermissionReceiver.ACTION;

    private final Context context;
    private final Listener listener;
    private final UsbManager usbManager;
    private UsbSerialPort port;
    private UsbDevice pendingDevice;

    public PicoSerial(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        SerialPermissionReceiver.setCallback(granted -> {
            if (granted && pendingDevice != null) {
                openDevice(pendingDevice);
            } else {
                state("Pico USB permission denied");
            }
        });
    }

    public void start() {
        state("Looking for Pico...");
        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (drivers.isEmpty()) {
            state("Pico: no USB serial device");
            return;
        }
        UsbSerialDriver driver = drivers.get(0);
        pendingDevice = driver.getDevice();

        if (!usbManager.hasPermission(pendingDevice)) {
            Intent intent = new Intent(ACTION_USB_PERMISSION);
            int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
            PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, flags);
            usbManager.requestPermission(pendingDevice, pi);
        } else {
            openDevice(pendingDevice);
        }
    }

    private void openDevice(UsbDevice device) {
        UsbSerialDriver driver = findDriver(device);
        if (driver == null) {
            state("Pico: unsupported USB serial device");
            return;
        }
        try {
            port = driver.getPorts().get(0);
            port.open(usbManager.openDevice(device));
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            state("Pico connected: " + device.getDeviceName());
            stop();
        } catch (Exception e) {
            state("Pico open failed: " + e.getMessage());
            close();
        }
    }

    private UsbSerialDriver findDriver(UsbDevice device) {
        for (UsbSerialDriver d : UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)) {
            if (d.getDevice().equals(device)) return d;
        }
        return null;
    }

    public synchronized boolean isConnected() {
        return port != null;
    }

    public synchronized void sendVelocity(float velocity) {
        if (port == null) return;
        String msg = String.format(java.util.Locale.US, "VEL:%.1f\n", velocity);
        try {
            port.write(msg.getBytes("US-ASCII"), 100);
        } catch (IOException e) {
            state("Pico write failed");
            close();
        }
    }

    public synchronized void sendFire(int durationMs) {
        if (port == null) return;
        String msg = "FIRE:" + durationMs + "\n";
        try {
            port.write(msg.getBytes("US-ASCII"), 100);
        } catch (IOException e) {
            state("Pico write failed (FIRE)");
            close();
        }
    }

    public synchronized void stop() {
        sendVelocity(0f);
    }

    public synchronized void close() {
        if (port != null) {
            try { port.close(); } catch (Exception ignored) {}
        }
        port = null;
    }

    private void state(String s) {
        if (listener != null) listener.onState(s);
    }
}
