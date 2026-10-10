package com.keshav.wallflow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Runtime-registered receiver for screen wakes. [Intent.ACTION_SCREEN_ON] cannot be declared in the
 * manifest, which is why this lives inside the foreground service and is registered dynamically.
 */
class ScreenOnReceiver(
    private val onScreenOn: () -> Unit,
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> onScreenOn()
        }
    }
}
