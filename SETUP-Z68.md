# Z68 setup

## What you need

- Z68 TV box running Android 5.1 / Android API 21+.
- USB UVC webcam.
- Raspberry Pi Pico connected by USB.
- Your Pico firmware must understand:
  - `VEL:120.0`
  - `VEL:-120.0`
  - `VEL:0`
  - `FIRE:200`
- A USB flash drive.
- A computer with Android Studio to build the APK.

## Wiring

1. Plug the webcam into one Z68 USB host port.
2. Plug the Pico into the other USB host port.
3. Connect the pump to the Pico exactly as your existing Pico hardware expects.

Do not power a pump directly from a Pico GPIO. Use the existing properly-rated
driver/MOSFET/relay stage and a separate pump supply.

## Build

1. Open this folder in Android Studio.
2. Let Gradle download:
   - `com.github.jiangdongguo:AndroidUSBCamera:2.3.4`
   - `com.github.mik3y:usb-serial-for-android:3.10.0`
3. Build > Build APK(s).
4. Copy `app/build/outputs/apk/debug/app-debug.apk` to the USB drive.

## Install

1. Insert the USB drive into the Z68.
2. Open the file manager.
3. Open `app-debug.apk`.
4. Allow installation from unknown sources if Android asks.
5. Launch **Z68 Bird Tracker**.
6. Grant any USB/camera permission prompts.

## First test

Start with the pump disconnected.

1. Put the camera where it will be used.
2. Start the app.
3. Check that the status says the camera is connected.
4. Check that the status says the Pico is connected.
5. Press `STOP / SET HOME`.
6. Move a test object through the camera view.
7. Verify that the Pico receives `VEL:` commands.
8. Only after motor tracking is reliable, reconnect the pump.
9. A `FIRE:200` pulse is generated after a target remains centered for about
   100 ms, and then a 4-second cooldown prevents repeated pulses.

## Adjusting the pump pulse

Edit in `TrackerEngine.java`:

    private static final int FIRE_DURATION_MS = 200;

Examples:

    100 = 0.10 second
    200 = 0.20 second
    500 = 0.50 second

Only change this after testing the actual pump/valve hardware.

## Important

The Android project is source code, not a prebuilt APK. This environment does
not contain an Android SDK/Gradle installation and cannot download those build
tools, so I cannot truthfully claim that an APK was compiled and tested here.
