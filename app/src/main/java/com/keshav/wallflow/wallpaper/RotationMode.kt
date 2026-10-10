package com.keshav.wallflow.wallpaper

/** The active auto-rotation mode. Only one is active at a time. */
enum class RotationMode {
    /** No automatic rotation; manual apply only. */
    OFF,

    /** WorkManager periodic rotation on a fixed interval. */
    INTERVAL,

    /** Foreground service changes the lock screen on every screen wake. */
    SCREEN_ON,
}
