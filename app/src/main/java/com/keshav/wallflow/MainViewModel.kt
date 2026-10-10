package com.keshav.wallflow

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keshav.wallflow.data.prefs.RotationPreferences
import com.keshav.wallflow.data.repository.WallpaperRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity-scoped state: gates onboarding and routes a shared image into the editor. Lives at the
 * activity level so it survives navigation and a shared-intent re-launch.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: WallpaperRepository,
    private val preferences: RotationPreferences,
) : ViewModel() {

    /** null = still loading the flag; true/false = resolved. */
    val onboardingComplete = preferences.onboardingComplete
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _pendingEditorId = MutableStateFlow<Long?>(null)
    val pendingEditorId = _pendingEditorId.asStateFlow()

    fun handleSharedImage(uri: Uri) {
        viewModelScope.launch {
            repository.importAndGetId(uri)?.let { _pendingEditorId.value = it }
        }
    }

    fun consumePendingEditor() {
        _pendingEditorId.value = null
    }

    fun completeOnboarding() {
        viewModelScope.launch { preferences.setOnboardingComplete() }
    }
}
