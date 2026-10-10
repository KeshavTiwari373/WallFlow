package com.keshav.wallflow.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.keshav.wallflow.data.prefs.RotationPreferences
import com.keshav.wallflow.data.repository.WallpaperRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * INTERVAL-mode worker: applies the next least-recently-used wallpaper using the persisted target
 * and tag filter. Selection + apply logic is shared with SCREEN_ON mode via the repository.
 */
@HiltWorker
class WallpaperRotationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: WallpaperRepository,
    private val preferences: RotationPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val config = preferences.config.first()
        if (repository.count() == 0) return Result.success()

        val applied = runCatching {
            repository.rotateToNext(config.target, config.filterTagId)
        }.getOrDefault(false)

        return if (applied) Result.success() else Result.retry()
    }
}
