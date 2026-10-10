package com.keshav.wallflow.rotation

import com.keshav.wallflow.data.prefs.RotationConfig
import com.keshav.wallflow.data.prefs.RotationPreferences
import com.keshav.wallflow.wallpaper.RotationMode
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point the UI uses to drive auto-rotation. Owns the lifecycle of the strategies and
 * guarantees exactly one is active at a time, so the two mechanisms never fight over the wallpaper.
 */
@Singleton
class RotationEngine @Inject constructor(
    private val workManagerStrategy: WorkManagerStrategy,
    private val screenOnStrategy: ScreenOnStrategy,
    private val preferences: RotationPreferences,
) {
    /** Reconciles the running strategy with [config]'s mode. */
    fun apply(config: RotationConfig) {
        when (config.mode) {
            RotationMode.OFF -> {
                workManagerStrategy.stop()
                screenOnStrategy.stop()
            }
            RotationMode.INTERVAL -> {
                screenOnStrategy.stop()
                workManagerStrategy.start(config)
            }
            RotationMode.SCREEN_ON -> {
                workManagerStrategy.stop()
                screenOnStrategy.start(config)
            }
        }
    }

    /** Re-applies whatever mode is persisted (e.g. after reboot or on settings change). */
    suspend fun applyCurrent() = apply(preferences.config.first())
}
