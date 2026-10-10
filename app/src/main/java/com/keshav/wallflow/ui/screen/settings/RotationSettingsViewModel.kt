package com.keshav.wallflow.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keshav.wallflow.data.local.TagEntity
import com.keshav.wallflow.data.prefs.RotationConfig
import com.keshav.wallflow.data.prefs.RotationPreferences
import com.keshav.wallflow.data.repository.WallpaperRepository
import com.keshav.wallflow.rotation.RotationEngine
import com.keshav.wallflow.wallpaper.RotationMode
import com.keshav.wallflow.wallpaper.WallpaperTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val config: RotationConfig = RotationConfig(
        mode = RotationMode.OFF,
        intervalMinutes = RotationPreferences.DEFAULT_INTERVAL_MINUTES,
        target = WallpaperTarget.BOTH,
        filterTagId = null,
    ),
    val tags: List<TagEntity> = emptyList(),
)

@HiltViewModel
class RotationSettingsViewModel @Inject constructor(
    private val preferences: RotationPreferences,
    private val rotationEngine: RotationEngine,
    repository: WallpaperRepository,
) : ViewModel() {

    val uiState = combine(preferences.config, repository.observeTags()) { config, tags ->
        SettingsUiState(config = config, tags = tags)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Mode change must reconcile the running strategy (start/stop services + work). */
    fun setMode(mode: RotationMode) {
        viewModelScope.launch {
            preferences.setMode(mode)
            rotationEngine.applyCurrent()
        }
    }

    /** Interval change re-enqueues periodic work if INTERVAL mode is active. */
    fun setInterval(minutes: Int) {
        viewModelScope.launch {
            preferences.setIntervalMinutes(minutes)
            rotationEngine.applyCurrent()
        }
    }

    // Target and tag filter are read at rotation time, so they only need persisting.
    fun setTarget(target: WallpaperTarget) {
        viewModelScope.launch { preferences.setTarget(target) }
    }

    fun setFilterTag(tagId: Long?) {
        viewModelScope.launch { preferences.setFilterTagId(tagId) }
    }
}
