package com.keshav.wallflow.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.keshav.wallflow.data.local.WallpaperWithTags
import java.io.File

/** A single tile in the gallery grid. Renders the lightweight thumbnail for smooth scrolling. */
@Composable
fun WallpaperGridItem(
    item: WallpaperWithTags,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imagePath = item.wallpaper.thumbnailPath ?: item.wallpaper.filePath
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(File(imagePath))
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.62f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    )
}
