package com.keshav.wallflow.rotation

import com.keshav.wallflow.data.prefs.RotationConfig

/**
 * A way of driving automatic wallpaper rotation. Each implementation owns one mechanism
 * (WorkManager, foreground service, ...). The [RotationEngine] guarantees only one is active.
 */
interface RotationStrategy {
    /** Begin rotating using [config]. Idempotent — safe to call when already running. */
    fun start(config: RotationConfig)

    /** Stop rotating and release any resources. Idempotent. */
    fun stop()
}
