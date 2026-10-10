package com.keshav.wallflow.service

import android.service.quicksettings.TileService
import com.keshav.wallflow.data.prefs.RotationPreferences
import com.keshav.wallflow.data.repository.WallpaperRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick Settings tile: tap to immediately rotate to the next wallpaper using the configured target
 * and tag filter — the same selection path as the rotation modes.
 */
@AndroidEntryPoint
class RotateTileService : TileService() {

    @Inject
    lateinit var repository: WallpaperRepository

    @Inject
    lateinit var preferences: RotationPreferences

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onClick() {
        super.onClick()
        scope.launch {
            if (repository.count() == 0) return@launch
            val config = preferences.config.first()
            repository.rotateToNext(config.target, config.filterTagId)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
