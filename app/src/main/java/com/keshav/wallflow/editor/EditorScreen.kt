package com.keshav.wallflow.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keshav.wallflow.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private enum class OverlayMode { NONE, HOME, LOCK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.Saved -> {
                    if (event.depthOmitted) {
                        Toast.makeText(context, context.getString(R.string.depth_no_subject), Toast.LENGTH_LONG).show()
                    } else if (event.dynamicColorHint) {
                        Toast.makeText(context, context.getString(R.string.dynamic_color_hint), Toast.LENGTH_LONG).show()
                    }
                    onBack()
                }
                is EditorEvent.Enhanced -> {
                    val r = event.result
                    val msg = when {
                        r == null -> context.getString(R.string.enhance_failed)
                        r.gainedResolution -> context.getString(
                            R.string.enhanced_dims,
                            r.fromWidth, r.fromHeight, r.toWidth, r.toHeight,
                        )
                        else -> context.getString(R.string.enhanced_already_hd)
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
                EditorEvent.SaveFailed -> Toast.makeText(
                    context,
                    context.getString(R.string.save_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var imageSize by remember { mutableStateOf(Size.Zero) }
    var overlay by remember { mutableStateOf(OverlayMode.HOME) }
    var autoCropDone by remember { mutableStateOf(false) }

    fun applyCrop(rect: RectF) {
        if (viewport == Size.Zero || imageSize == Size.Zero) return
        val (s, o) = cropToTransform(rect, viewport, imageSize)
        scale = s
        offset = o
    }

    val sourcePath = state.sourcePath
    val rawPreview: Bitmap? by produceState<Bitmap?>(initialValue = null, sourcePath) {
        value = sourcePath?.let { loadPreviewBitmap(it) }
    }
    val displayImage: ImageBitmap? by produceState<ImageBitmap?>(
        initialValue = null,
        rawPreview,
        state.filter,
        state.filterIntensity,
    ) {
        val raw = rawPreview
        value = if (raw == null) null else viewModel.renderPreview(raw, state.filter, state.filterIntensity)
    }

    // On open with no prior edit, seed the crop with the subject-centered suggestion.
    LaunchedEffect(viewport, imageSize, state.hasExistingEdit, displayImage) {
        if (!autoCropDone && !state.hasExistingEdit && viewport != Size.Zero && imageSize != Size.Zero &&
            displayImage != null && state.screenHeight != 0
        ) {
            autoCropDone = true
            viewModel.suggestCrop(state.screenWidth.toFloat() / state.screenHeight.toFloat())?.let(::applyCrop)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        enabled = !state.saving && !state.enhancing && displayImage != null && viewport != Size.Zero,
                        onClick = {
                            viewModel.save(
                                buildEditParams(
                                    viewport = viewport,
                                    image = imageSize,
                                    scale = scale,
                                    offset = offset,
                                    blurExtend = state.blurExtend,
                                    filter = state.filter,
                                    filterIntensity = state.filterIntensity,
                                    lockVariantDim = state.lockVariantDim,
                                    depthBandTop = if (state.depthEnabled) state.depthBandTop else null,
                                ),
                            )
                        },
                    ) { Text(stringResource(R.string.save_edit)) }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val img = displayImage
                if (img == null || state.screenWidth == 0) {
                    CircularProgressIndicator()
                } else {
                    CropViewport(
                        image = img,
                        screenAspect = state.screenWidth.toFloat() / state.screenHeight.toFloat(),
                        scale = scale,
                        offset = offset,
                        blurExtend = state.blurExtend,
                        overlay = overlay,
                        depthEnabled = state.depthEnabled,
                        depthBandTop = state.depthBandTop,
                        onDepthBandChange = viewModel::setDepthBandTop,
                        onTransform = { newScale, newOffset, vp ->
                            viewport = vp
                            imageSize = Size(img.width.toFloat(), img.height.toFloat())
                            scale = newScale
                            offset = newOffset
                        },
                    )
                }
            }

            EditorControls(
                overlay = overlay,
                onOverlayChange = { overlay = it },
                blurExtend = state.blurExtend,
                onBlurExtendChange = viewModel::setBlurExtend,
                filter = state.filter,
                onFilterChange = viewModel::setFilter,
                intensity = state.filterIntensity,
                onIntensityChange = viewModel::setFilterIntensity,
                lockVariantDim = state.lockVariantDim,
                onLockVariantDimChange = viewModel::setLockVariantDim,
                depthEnabled = state.depthEnabled,
                onDepthEnabledChange = viewModel::setDepthEnabled,
                enhancing = state.enhancing,
                onEnhance = viewModel::enhance,
                onAutoCrop = {
                    scope.launch {
                        viewModel.suggestCrop(state.screenWidth.toFloat() / state.screenHeight.toFloat())
                            ?.let(::applyCrop)
                    }
                },
            )
        }
    }
}

@Composable
private fun CropViewport(
    image: ImageBitmap,
    screenAspect: Float,
    scale: Float,
    offset: Offset,
    blurExtend: Boolean,
    overlay: OverlayMode,
    depthEnabled: Boolean,
    depthBandTop: Float,
    onDepthBandChange: (Float) -> Unit,
    onTransform: (scale: Float, offset: Offset, viewport: Size) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val vpW: Float
        val vpH: Float
        if (maxW / maxH > screenAspect) {
            vpH = maxH
            vpW = vpH * screenAspect
        } else {
            vpW = maxW
            vpH = vpW / screenAspect
        }
        val viewport = Size(vpW, vpH)
        val currentScale by rememberUpdatedState(scale)
        val currentOffset by rememberUpdatedState(offset)
        LaunchedEffect(vpW, vpH) { onTransform(scale, offset, viewport) }

        val density = LocalDensity.current
        val widthDp = with(density) { vpW.toDp() }
        val heightDp = with(density) { vpH.toDp() }

        Box(
            modifier = Modifier
                .size(widthDp, heightDp)
                .clipToBounds()
                .pointerInput(blurExtend, vpW, vpH, image) {
                    val imageDimens = Size(image.width.toFloat(), image.height.toFloat())
                    detectTransformGestures { _, pan, zoom, _ ->
                        val minScale = if (blurExtend) 0.3f else 1f
                        val newScale = (currentScale * zoom).coerceAtLeast(minScale)
                        var newOffset = currentOffset + pan
                        if (!blurExtend) {
                            newOffset = clampOffset(newOffset, viewport, imageDimens, newScale)
                        }
                        onTransform(newScale, newOffset, viewport)
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val d = displayRect(viewport, Size(image.width.toFloat(), image.height.toFloat()), scale, offset)
                drawImage(
                    image = image,
                    dstOffset = IntOffset(d.left.roundToInt(), d.top.roundToInt()),
                    dstSize = IntSize(d.width.roundToInt().coerceAtLeast(1), d.height.roundToInt().coerceAtLeast(1)),
                )
            }
            when (overlay) {
                OverlayMode.HOME -> HomeOverlay()
                OverlayMode.LOCK -> LockOverlay()
                OverlayMode.NONE -> Unit
            }
            if (overlay == OverlayMode.LOCK && depthEnabled) {
                DepthBandHandle(depthBandTop = depthBandTop, viewportHeightPx = vpH, onChange = onDepthBandChange)
            }
        }
    }
}

/** Draggable band marking where the clock sits, for the depth effect (lock preview). */
@Composable
private fun DepthBandHandle(depthBandTop: Float, viewportHeightPx: Float, onChange: (Float) -> Unit) {
    val density = LocalDensity.current
    val current by rememberUpdatedState(depthBandTop)
    val topDp = with(density) { (depthBandTop * viewportHeightPx).toDp() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = topDp)
            .height(48.dp)
            .background(Color.White.copy(alpha = 0.18f))
            .pointerInput(viewportHeightPx) {
                detectDragGestures { _, dragAmount ->
                    onChange(current + dragAmount.y / viewportHeightPx)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.depth_band_hint), color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EditorControls(
    overlay: OverlayMode,
    onOverlayChange: (OverlayMode) -> Unit,
    blurExtend: Boolean,
    onBlurExtendChange: (Boolean) -> Unit,
    filter: FilterType,
    onFilterChange: (FilterType) -> Unit,
    intensity: Float,
    onIntensityChange: (Float) -> Unit,
    lockVariantDim: Boolean,
    onLockVariantDimChange: (Boolean) -> Unit,
    depthEnabled: Boolean,
    onDepthEnabledChange: (Boolean) -> Unit,
    enhancing: Boolean,
    onEnhance: () -> Unit,
    onAutoCrop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onAutoCrop, enabled = !enhancing) {
                Text(stringResource(R.string.auto_crop))
            }
            OutlinedButton(onClick = onEnhance, enabled = !enhancing) {
                if (enhancing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(stringResource(R.string.enhance_hd))
                }
            }
        }

        Text(stringResource(R.string.preview_overlay), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(overlay == OverlayMode.HOME, { onOverlayChange(OverlayMode.HOME) }, { Text(stringResource(R.string.overlay_home)) })
            FilterChip(overlay == OverlayMode.LOCK, { onOverlayChange(OverlayMode.LOCK) }, { Text(stringResource(R.string.overlay_lock)) })
            FilterChip(overlay == OverlayMode.NONE, { onOverlayChange(OverlayMode.NONE) }, { Text(stringResource(R.string.overlay_none)) })
        }

        Text(stringResource(R.string.filter_label), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterType.entries.forEach { type ->
                FilterChip(filter == type, { onFilterChange(type) }, { Text(filterLabel(type)) })
            }
        }

        if (filter != FilterType.NONE) {
            Text(stringResource(R.string.intensity), style = MaterialTheme.typography.titleSmall)
            Slider(value = intensity, onValueChange = onIntensityChange, valueRange = 0f..1f)
        }

        ToggleRow(stringResource(R.string.blur_fill), blurExtend, onBlurExtendChange)
        ToggleRow(stringResource(R.string.dim_lock), lockVariantDim, onLockVariantDimChange)
        if (overlay == OverlayMode.LOCK) {
            ToggleRow(stringResource(R.string.depth_effect), depthEnabled, onDepthEnabledChange)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun filterLabel(type: FilterType): String = stringResource(
    when (type) {
        FilterType.NONE -> R.string.filter_none
        FilterType.AMOLED -> R.string.filter_amoled
        FilterType.VIBRANT -> R.string.filter_vibrant
        FilterType.SOFT -> R.string.filter_soft
        FilterType.DUOTONE -> R.string.filter_duotone
        FilterType.GRAIN_VIGNETTE -> R.string.filter_grain
    },
)

/** Translucent mock home screen: a clock and a grid of app-icon placeholders. */
@Composable
private fun HomeOverlay() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("12:30", style = MaterialTheme.typography.displaySmall, color = Color.White.copy(alpha = 0.85f))
        Box(modifier = Modifier.weight(1f))
        repeat(4) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.22f)),
                    )
                }
            }
        }
    }
}

/** Translucent mock lock screen: a large clock and two notification cards. */
@Composable
private fun LockOverlay() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("12:30", style = MaterialTheme.typography.displayLarge, color = Color.White.copy(alpha = 0.9f))
        Box(modifier = Modifier.weight(1f))
        repeat(2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
            )
        }
    }
}

// --- Geometry helpers (viewport-space px) ---

private data class DispRect(val left: Float, val top: Float, val width: Float, val height: Float)

private fun displayRect(viewport: Size, image: Size, scale: Float, offset: Offset): DispRect {
    val cover = max(viewport.width / image.width, viewport.height / image.height)
    val w = image.width * cover * scale
    val h = image.height * cover * scale
    val left = (viewport.width - w) / 2f + offset.x
    val top = (viewport.height - h) / 2f + offset.y
    return DispRect(left, top, w, h)
}

private fun clampOffset(offset: Offset, viewport: Size, image: Size, scale: Float): Offset {
    val d = displayRect(viewport, image, scale, Offset.Zero)
    val maxX = (d.width - viewport.width).coerceAtLeast(0f) / 2f
    val maxY = (d.height - viewport.height).coerceAtLeast(0f) / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/** Inverse of [buildEditParams]'s crop math: turn a normalized crop into scale + pan offset. */
private fun cropToTransform(crop: RectF, viewport: Size, image: Size): Pair<Float, Offset> {
    val cover = max(viewport.width / image.width, viewport.height / image.height)
    val cropWidthNorm = (crop.right - crop.left).coerceAtLeast(0.01f)
    val dispW = viewport.width / cropWidthNorm
    val scale = (dispW / (image.width * cover)).coerceAtLeast(0.3f)
    val d = displayRect(viewport, image, scale, Offset.Zero)
    // Solve offset so that cropLeft/cropTop match: cropLeft = (0 - dispLeft)/dispW.
    val dispLeftWanted = -crop.left * d.width
    val dispTopWanted = -crop.top * d.height
    val baseLeft = (viewport.width - d.width) / 2f
    val baseTop = (viewport.height - d.height) / 2f
    return scale to Offset(dispLeftWanted - baseLeft, dispTopWanted - baseTop)
}

private fun buildEditParams(
    viewport: Size,
    image: Size,
    scale: Float,
    offset: Offset,
    blurExtend: Boolean,
    filter: FilterType,
    filterIntensity: Float,
    lockVariantDim: Boolean,
    depthBandTop: Float?,
): EditParams {
    if (viewport == Size.Zero || image == Size.Zero) {
        return EditParams(
            blurExtend = blurExtend,
            filter = filter,
            filterIntensity = filterIntensity,
            lockVariantDim = lockVariantDim,
            depthBandTop = depthBandTop,
        )
    }
    val d = displayRect(viewport, image, scale, offset)
    return EditParams(
        cropLeft = (0f - d.left) / d.width,
        cropTop = (0f - d.top) / d.height,
        cropRight = (viewport.width - d.left) / d.width,
        cropBottom = (viewport.height - d.top) / d.height,
        blurExtend = blurExtend,
        filter = filter,
        filterIntensity = filterIntensity,
        lockVariantDim = lockVariantDim,
        depthBandTop = depthBandTop,
    )
}

/** Decode a downsampled preview (longest side ~2000px) off the main thread. */
private suspend fun loadPreviewBitmap(path: String): Bitmap? = withContext(Dispatchers.IO) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
    var sample = 1
    val longest = max(bounds.outWidth, bounds.outHeight)
    while (longest / sample > 2000) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}
