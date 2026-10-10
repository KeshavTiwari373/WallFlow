package com.keshav.wallflow.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keshav.wallflow.data.local.WallpaperWithTags
import com.keshav.wallflow.data.repository.WallpaperRepository
import com.keshav.wallflow.ui.navigation.Routes
import com.keshav.wallflow.wallpaper.WallpaperTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DetailEvent {
    data class Applied(val success: Boolean) : DetailEvent
    data object Deleted : DetailEvent
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: WallpaperRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val wallpaperId: Long = checkNotNull(savedStateHandle[Routes.ARG_WALLPAPER_ID])

    val wallpaper = repository.observeWallpaper(wallpaperId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val eventChannel = Channel<DetailEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun apply(target: WallpaperTarget) {
        viewModelScope.launch {
            val ok = repository.applyWallpaper(wallpaperId, target)
            eventChannel.send(DetailEvent.Applied(ok))
        }
    }

    fun addTag(name: String) {
        viewModelScope.launch { repository.addTagToWallpaper(wallpaperId, name) }
    }

    fun removeTag(tagId: Long) {
        viewModelScope.launch { repository.removeTagFromWallpaper(wallpaperId, tagId) }
    }

    fun delete() {
        viewModelScope.launch {
            wallpaper.value?.let { repository.deleteWallpaper(it.wallpaper) }
            eventChannel.send(DetailEvent.Deleted)
        }
    }

    val current: WallpaperWithTags? get() = wallpaper.value
}
