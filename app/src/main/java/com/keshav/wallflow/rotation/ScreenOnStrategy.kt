package com.keshav.wallflow.rotation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.keshav.wallflow.data.prefs.RotationConfig
import com.keshav.wallflow.service.ScreenOnWallpaperService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** SCREEN_ON mode: starts/stops the foreground service that listens for screen wakes. */
@Singleton
class ScreenOnStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
) : RotationStrategy {

    override fun start(config: RotationConfig) {
        val intent = Intent(context, ScreenOnWallpaperService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    override fun stop() {
        context.stopService(Intent(context, ScreenOnWallpaperService::class.java))
    }
}
