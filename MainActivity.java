package com.z68.birdtracker;

import android.app.Activity;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.jiangdg.usbcamera.UVCCameraHelper;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.widget.CameraViewInterface;
import com.z68.birdtracker.usb.PicoSerial;

import java.util.Locale;

public class MainActivity extends Activity implements
        CameraDialog.CameraDialogParent, CameraViewInterface.Callback {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    private CameraViewInterface cameraView;
    private UVCCameraHelper cameraHelper;
    private boolean isPreview;
    private boolean isRequest;

    private TrackerOverlay overlay;
    private TextView status;
    private PicoSerial pico;
    private TrackerEngine tracker;
    private boolean trackingEnabled = true;

    private final UVCCameraHelper.OnMyDevConnectListener devListener =
        new UVCCameraHelper.OnMyDevConnectListener() {
            @Override public void onAttachDev(UsbDevice device) {
                if (!isRequest) {
                    isRequest = true;
                    if (cameraHelper != null) cameraHelper.requestPermission(0);
                }
            }

            @Override public void onDettachDev(UsbDevice device) {
                isRequest = false;
                if (cameraHelper != null) cameraHelper.closeCamera();
                setStatus("Camera disconnected");
            }

            @Override public void onConnectDev(UsbDevice device, boolean isConnected) {
                if (!isConnected) {
                    isPreview = false;
                    setStatus("Camera connect failed");
                } else {
                    setStatus("Camera connected");
                }
            }

            @Override public void onDisConnectDev(UsbDevice device) {
                isPreview = false;
                setStatus("Camera disconnected");
            }
        };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.z68.birdtracker.R.layout.activity_main);

        overlay = (TrackerOverlay) findViewById(com.z68.birdtracker.R.id.overlay);
        status = (TextView) findViewById(com.z68.birdtracker.R.id.status);
        Button btnHome = (Button) findViewById(com.z68.birdtracker.R.id.btnHome);
        Button btnTracking = (Button) findViewById(com.z68.birdtracker.R.id.btnTracking);

        pico = new PicoSerial(this, text -> runOnUiThread(() -> setStatus(text)));

        tracker = new TrackerEngine(WIDTH, HEIGHT, new TrackerEngine.Output() {
            @Override public void onTarget(boolean visible, float x, float y, float errorPx, float area, String state) {
                runOnUiThread(() -> {
                    overlay.setState(visible, x * overlay.getWidth() / WIDTH,
                            y * overlay.getHeight() / HEIGHT, trackingEnabled);
                    setStatus(String.format(Locale.US, "%s | err %.0f px | active %.0f",
                            state, errorPx, area));
                });
            }

            @Override public void onVelocity(float velocity) {
                if (trackingEnabled) pico.sendVelocity(velocity);
                else pico.sendVelocity(0f);
            }

            @Override public void onFire(int durationMs) {
                if (trackingEnabled) pico.sendFire(durationMs);
            }
        });

        btnHome.setOnClickListener(v -> {
            tracker.setHomeNow();
            pico.stop();
            setStatus("STOPPED + software home set");
        });

        btnTracking.setOnClickListener(v -> {
            trackingEnabled = !trackingEnabled;
            tracker.setEnabled(trackingEnabled);
            pico.stop();
            btnTracking.setText(trackingEnabled ? "TRACKING: ON" : "TRACKING: OFF");
            setStatus(trackingEnabled ? "Tracking enabled" : "Tracking disabled");
        });

        cameraView = (CameraViewInterface) findViewById(com.z68.birdtracker.R.id.camera_view);
        cameraView.setCallback(this);

        cameraHelper = UVCCameraHelper.getInstance();
        cameraHelper.setDefaultPreviewSize(WIDTH, HEIGHT);
        cameraHelper.setDefaultFrameFormat(UVCCameraHelper.FRAME_FORMAT_MJPEG);
        cameraHelper.initUSBMonitor(this, cameraView, devListener);

        // 2.3.x exposes the decoded preview as NV21. We process this directly,
        // which removes the Termux/OpenCV requirement from the Z68.
        cameraHelper.setOnPreviewFrameListener(
            new AbstractUVCCameraHandler.OnPreViewResultListener() {
                @Override public void onPreviewResult(byte[] nv21Yuv) {
                    if (trackingEnabled && nv21Yuv != null) {
                        tracker.onFrame(java.nio.ByteBuffer.wrap(nv21Yuv));
                    }
                }
            }
        );

        pico.start();
        setStatus("Waiting for USB camera + Pico");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (cameraHelper != null) cameraHelper.registerUSB();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (cameraHelper != null) cameraHelper.unregisterUSB();
        if (pico != null) pico.stop();
    }

    @Override
    protected void onDestroy() {
        if (pico != null) pico.close();
        if (cameraHelper != null) cameraHelper.release();
        super.onDestroy();
    }

    @Override
    public void onSurfaceCreated(CameraViewInterface view, Surface surface) {
        if (!isPreview && cameraHelper != null && cameraHelper.isCameraOpened()) {
            cameraHelper.startPreview(cameraView);
            isPreview = true;
        }
    }

    @Override
    public void onSurfaceChanged(CameraViewInterface view, Surface surface, int width, int height) {}

    @Override
    public void onSurfaceDestroy(CameraViewInterface view, Surface surface) {
        if (isPreview && cameraHelper != null && cameraHelper.isCameraOpened()) {
            cameraHelper.stopPreview();
            isPreview = false;
        }
    }

    @Override
    public USBMonitor getUSBMonitor() {
        return cameraHelper.getUSBMonitor();
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (canceled) Toast.makeText(this, "USB camera permission cancelled", Toast.LENGTH_SHORT).show();
    }

    private void setStatus(String text) {
        status.setText(text);
    }
}
