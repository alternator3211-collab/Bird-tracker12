# Z68 Bird Tracker — Android 5.1

This project is for a Z68/RK3368-style Android TV box and is intended to be
installed as a normal APK.

## Easiest way to get the APK: GitHub Actions

You do NOT need Android Studio on your PC for this method.

### 1. Make a GitHub account

Go to GitHub and create an account if you do not already have one.

### 2. Create a new repository

Create a new repository, e.g.

    Z68BirdTracker

A private repository is fine.

### 3. Upload THIS PROJECT, not the ZIP renamed as APK

On the repository page choose:

    Add file -> Upload files

Extract this ZIP on your computer first, then upload the contents.

You should see files such as:

    build.gradle
    settings.gradle
    app/
    .github/

Do NOT upload only the ZIP file.

### 4. Start the build

Open:

    Actions

Choose:

    Build Z68 Bird Tracker APK

Then press:

    Run workflow

The workflow installs Java and Gradle on GitHub's build machine, builds the
Android project, and saves the resulting APK as a downloadable artifact.

### 5. Download the APK

When the workflow finishes successfully:

    Actions
      -> Build Z68 Bird Tracker APK
      -> latest successful run
      -> Artifacts
      -> Z68-Bird-Tracker-APK

Download the artifact ZIP, extract it, and you will have:

    app-debug.apk

That is the file to put on the USB drive.

### 6. Install on the Z68

Put:

    app-debug.apk

on your USB drive.

On the TV box:

    File Manager
      -> USB drive
      -> app-debug.apk
      -> Install

The Z68 only needs the APK. It does not need the source files, Gradle, Java,
Python, Termux, or Android Studio.

## Hardware

Connect:
- USB UVC webcam
- Raspberry Pi Pico by USB
- Your motor driver to the Pico
- Your water-pump driver to the Pico

The pump must NOT be powered directly from a Pico GPIO. Use the appropriate
driver/relay/MOSFET and separate pump supply.

## Serial commands

The app sends:

    VEL:120.0
    VEL:-120.0
    VEL:0

For the water pump it sends:

    FIRE:200

The default FIRE pulse is 200 ms, followed by a 4 second cooldown.

The Pico firmware must understand the FIRE command and activate your existing
pump driver for that requested duration.

## Changing the pump pulse

In:

    app/src/main/java/com/z68/birdtracker/TrackerEngine.java

change:

    private static final int FIRE_DURATION_MS = 200;

For example:

    100 = 0.10 second
    200 = 0.20 second
    500 = 0.50 second

## Target platform

The project uses:

    minSdk 21

so the compiled APK is intended to be installable on Android 5.1/API 22.

`minSdk` is what Android uses to decide the minimum supported OS version.
Android's documentation confirms that a higher minSdk prevents installation
on older Android versions.

## Important

The Android version currently uses a manual:

    STOP / SET HOME

button rather than the original ArUco marker homing.

The motion detector is intentionally lightweight because the Z68 is old
hardware.

The first test should be performed with the pump disconnected. Verify that
the motor movement and Pico serial communication work first.

## If GitHub build fails

Open the failed workflow and copy the error from the first red step.

A build failure is useful: it means we can fix the exact Android/compiler/library
problem rather than guessing what the Z68 installer dislikes.
