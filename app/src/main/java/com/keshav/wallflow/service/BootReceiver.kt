package com.keshav.wallflow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.keshav.wallflow.data.prefs.RotationPreferences
import com.keshav.wallflow.rotation.RotationEngine
import com.keshav.wallflow.wallpaper.RotationMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restarts the SCREEN_ON foreground service after a reboot. (WorkManager periodic work for INTERVAL
 * mode is persisted by the framework and resumes automatically, so only SCREEN_ON needs handling.)
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferences: RotationPreferences

    @Inject
    lateinit var rotationEngine: RotationEngine

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val config = preferences.config.first()
                if (config.mode == RotationMode.SCREEN_ON) {
                    rotationEngine.apply(config)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
