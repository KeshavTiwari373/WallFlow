package com.keshav.wallflow.ui.screen.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keshav.wallflow.R
import com.keshav.wallflow.ui.component.EmptyState
import com.keshav.wallflow.ui.component.FilterTagChip
import com.keshav.wallflow.ui.component.WallpaperGridItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onWallpaperClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> viewModel.importImages(uris) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gallery_title)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.import_wallpapers))
            }
        },
    ) { padding ->
        if (state.wallpapers.isEmpty() && state.selectedTagId == null) {
            EmptyState(
                title = stringResource(R.string.empty_gallery_title),
                subtitle = stringResource(R.string.empty_gallery_subtitle),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.tags.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TagFilterRow(
                            tags = state.tags,
                            selectedTagId = state.selectedTagId,
                            onSelect = viewModel::selectTag,
                        )
                    }
                }
                items(state.wallpapers, key = { it.wallpaper.id }) { item ->
                    WallpaperGridItem(item = item, onClick = { onWallpaperClick(item.wallpaper.id) })
                }
            }
        }
    }
}

@Composable
private fun TagFilterRow(
    tags: List<com.keshav.wallflow.data.local.TagEntity>,
    selectedTagId: Long?,
    onSelect: (Long?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item {
            FilterTagChip(
                label = stringResource(R.string.filter_all),
                selected = selectedTagId == null,
                onClick = { onSelect(null) },
            )
        }
        listItems(tags, key = { it.id }) { tag ->
            FilterTagChip(
                label = tag.name,
                selected = selectedTagId == tag.id,
                onClick = { onSelect(tag.id) },
            )
        }
    }
}
