# Original Termux script -> Android mapping

Original:
    pkg install python opencv-python
        -> removed

Original:
    /dev/video*
        -> Android USB-host/UVC stack

Original:
    /dev/ttyUSB* /dev/ttyACM*
        -> Android UsbManager + usb-serial-for-android

Original:
    cv2.VideoCapture(0)
        -> UVCCamera / NV21 callback

Original:
    MOG2 + contours
        -> lightweight Y-plane running background detector

Original:
    alpha-beta filter
        -> TrackerEngine smoothing

Original:
    VEL:<speed>
        -> PicoSerial.sendVelocity()

Original:
    FIRE:<duration>
        -> deliberately omitted

Original:
    ArUco home marker
        -> manual STOP / SET HOME in v1
