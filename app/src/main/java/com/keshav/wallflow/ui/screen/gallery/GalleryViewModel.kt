package com.keshav.wallflow.ui.screen.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keshav.wallflow.data.local.TagEntity
import com.keshav.wallflow.data.local.WallpaperWithTags
import com.keshav.wallflow.data.repository.WallpaperRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryUiState(
    val wallpapers: List<WallpaperWithTags> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val selectedTagId: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repository: WallpaperRepository,
) : ViewModel() {

    private val selectedTagId = MutableStateFlow<Long?>(null)

    val uiState = combine(
        selectedTagId.flatMapLatest { repository.observeWallpapers(it) },
        repository.observeTags(),
        selectedTagId,
    ) { wallpapers, tags, selected ->
        GalleryUiState(wallpapers = wallpapers, tags = tags, selectedTagId = selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    fun selectTag(tagId: Long?) {
        selectedTagId.value = tagId
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch { repository.importImages(uris) }
    }
}
