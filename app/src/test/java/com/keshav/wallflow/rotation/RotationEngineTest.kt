package com.keshav.wallflow.rotation

import com.keshav.wallflow.data.prefs.RotationConfig
import com.keshav.wallflow.data.prefs.RotationPreferences
import com.keshav.wallflow.wallpaper.RotationMode
import com.keshav.wallflow.wallpaper.WallpaperTarget
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * Verifies the core safety property of [RotationEngine]: exactly one rotation strategy is ever active,
 * and switching modes always stops the other one first — so WorkManager (INTERVAL) and the foreground
 * service (SCREEN_ON) can never both be driving the wallpaper at the same time.
 */
class RotationEngineTest {

    private val workManagerStrategy = mockk<WorkManagerStrategy>(relaxed = true)
    private val screenOnStrategy = mockk<ScreenOnStrategy>(relaxed = true)
    private val preferences = mockk<RotationPreferences>(relaxed = true)

    private val engine = RotationEngine(workManagerStrategy, screenOnStrategy, preferences)

    private fun config(mode: RotationMode) = RotationConfig(
        mode = mode,
        intervalMinutes = 60,
        target = WallpaperTarget.BOTH,
        filterTagId = null,
    )

    @Test
    fun off_stopsBothStrategies_andStartsNeither() {
        engine.apply(config(RotationMode.OFF))

        verify { workManagerStrategy.stop() }
        verify { screenOnStrategy.stop() }
        verify(exactly = 0) { workManagerStrategy.start(any()) }
        verify(exactly = 0) { screenOnStrategy.start(any()) }
    }

    @Test
    fun interval_startsWorkManager_andStopsScreenOn() {
        val cfg = config(RotationMode.INTERVAL)

        engine.apply(cfg)

        verify { workManagerStrategy.start(cfg) }
        verify { screenOnStrategy.stop() }
        verify(exactly = 0) { screenOnStrategy.start(any()) }
    }

    @Test
    fun screenOn_startsService_andStopsWorkManager() {
        val cfg = config(RotationMode.SCREEN_ON)

        engine.apply(cfg)

        verify { screenOnStrategy.start(cfg) }
        verify { workManagerStrategy.stop() }
        verify(exactly = 0) { workManagerStrategy.start(any()) }
    }

    @Test
    fun switchingFromIntervalToScreenOn_stopsTheIntervalWorker() {
        engine.apply(config(RotationMode.INTERVAL))
        engine.apply(config(RotationMode.SCREEN_ON))

        // The interval worker must be torn down when moving to screen-on mode.
        verify { workManagerStrategy.stop() }
        verify { screenOnStrategy.start(any()) }
    }
}
