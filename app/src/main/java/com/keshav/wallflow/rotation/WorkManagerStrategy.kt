package com.keshav.wallflow.rotation

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.keshav.wallflow.data.prefs.RotationConfig
import com.keshav.wallflow.data.prefs.RotationPreferences
import com.keshav.wallflow.work.WallpaperRotationWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** INTERVAL mode: schedules a periodic [WallpaperRotationWorker] via WorkManager. */
@Singleton
class WorkManagerStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
) : RotationStrategy {

    private val workManager get() = WorkManager.getInstance(context)

    override fun start(config: RotationConfig) {
        val minutes = config.intervalMinutes.toLong()
            .coerceAtLeast(RotationPreferences.MIN_INTERVAL_MINUTES.toLong())

        val request = PeriodicWorkRequestBuilder<WallpaperRotationWorker>(minutes, TimeUnit.MINUTES)
            .build()

        // CANCEL_AND_REENQUEUE so a changed interval re-times immediately; UPDATE can keep the
        // original schedule's next-run instant, leaving an interval change without visible effect.
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    override fun stop() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "wallpaper_rotation"
    }
}
