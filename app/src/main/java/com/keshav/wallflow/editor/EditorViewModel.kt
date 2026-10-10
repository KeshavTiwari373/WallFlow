package com.keshav.wallflow.editor

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keshav.wallflow.data.repository.UpscaleResult
import com.keshav.wallflow.data.repository.WallpaperRepository
import com.keshav.wallflow.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class EditorUiState(
    val loading: Boolean = true,
    val sourcePath: String? = null,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val blurExtend: Boolean = false,
    val filter: FilterType = FilterType.NONE,
    val filterIntensity: Float = 1f,
    val lockVariantDim: Boolean = false,
    val depthEnabled: Boolean = false,
    val depthBandTop: Float = 0.28f,
    val hasExistingEdit: Boolean = false,
    val enhancing: Boolean = false,
    val saving: Boolean = false,
)

sealed interface EditorEvent {
    data class Saved(val dynamicColorHint: Boolean, val depthOmitted: Boolean) : EditorEvent

    /** [result] is null when enhancing failed. */
    data class Enhanced(val result: UpscaleResult?) : EditorEvent
    data object SaveFailed : EditorEvent
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: WallpaperRepository,
    private val imageProcessor: ImageProcessor,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val wallpaperId: Long = checkNotNull(savedStateHandle[Routes.ARG_WALLPAPER_ID])

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState = _uiState.asStateFlow()

    private val eventChannel = Channel<EditorEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val wallpaper = repository.getWallpaper(wallpaperId)
            val (w, h) = imageProcessor.currentScreenSize()
            val existing = repository.getEditParams(wallpaperId)
            _uiState.value = EditorUiState(
                loading = false,
                // Edit from the upscaled copy if one exists, else the original.
                sourcePath = wallpaper?.upscaledPath ?: wallpaper?.filePath,
                screenWidth = w,
                screenHeight = h,
                blurExtend = existing?.blurExtend ?: false,
                filter = existing?.filter ?: FilterType.NONE,
                filterIntensity = existing?.filterIntensity ?: 1f,
                lockVariantDim = existing?.lockVariantDim ?: false,
                depthEnabled = existing?.depthBandTop != null,
                depthBandTop = existing?.depthBandTop ?: 0.28f,
                hasExistingEdit = existing != null,
            )
        }
    }

    fun setBlurExtend(enabled: Boolean) = _uiState.update { it.copy(blurExtend = enabled) }
    fun setFilter(filter: FilterType) = _uiState.update { it.copy(filter = filter) }
    fun setFilterIntensity(value: Float) = _uiState.update { it.copy(filterIntensity = value) }
    fun setLockVariantDim(enabled: Boolean) = _uiState.update { it.copy(lockVariantDim = enabled) }
    fun setDepthEnabled(enabled: Boolean) = _uiState.update { it.copy(depthEnabled = enabled) }
    fun setDepthBandTop(value: Float) = _uiState.update { it.copy(depthBandTop = value.coerceIn(0f, 0.78f)) }

    /** Subject-centered crop suggestion (never throws; falls back to center). */
    suspend fun suggestCrop(screenAspect: Float): RectF? = repository.suggestCrop(wallpaperId, screenAspect)

    /** Runs the upscaler; on success the editor source switches to the enhanced copy. */
    fun enhance() {
        if (_uiState.value.enhancing) return
        viewModelScope.launch {
            _uiState.update { it.copy(enhancing = true) }
            val result = repository.applyUpscale(wallpaperId)
            val newSource = if (result != null) repository.getWallpaper(wallpaperId)?.upscaledPath else null
            _uiState.update {
                it.copy(enhancing = false, sourcePath = newSource ?: it.sourcePath)
            }
            eventChannel.send(EditorEvent.Enhanced(result))
        }
    }

    /** Renders a filtered preview off the main thread for the editor canvas. */
    suspend fun renderPreview(source: Bitmap, filter: FilterType, intensity: Float): ImageBitmap =
        withContext(Dispatchers.Default) {
            imageProcessor.applyFilterForPreview(source, filter, intensity).asImageBitmap()
        }

    /** Persists the edit; the UI passes the crop rect it computed from its gestures. */
    fun save(params: EditParams) {
        if (_uiState.value.saving) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            // runCatching guards the whole pipeline — a decode/OOM failure on a low-RAM device
            // should surface as a toast, not a crash.
            val result = runCatching { repository.saveEdit(wallpaperId, params) }
            _uiState.update { it.copy(saving = false) }
            result.fold(
                onSuccess = { depthOmitted ->
                    val hint = runCatching { repository.hasVibrantColor(wallpaperId) }.getOrDefault(false)
                    eventChannel.send(EditorEvent.Saved(dynamicColorHint = hint, depthOmitted = depthOmitted))
                },
                onFailure = { eventChannel.send(EditorEvent.SaveFailed) },
            )
        }
    }
}
