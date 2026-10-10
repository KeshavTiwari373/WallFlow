package com.keshav.wallflow.editor

import kotlinx.serialization.Serializable

/**
 * Non-destructive edit description for one wallpaper. Stored as JSON in
 * [com.keshav.wallflow.data.local.WallpaperEntity.editParamsJson] so an edit can be re-opened and
 * re-applied. The crop rect is normalized (0..1) in the source image's coordinate space and is
 * always locked to the screen aspect ratio by the editor; values may fall slightly outside 0..1
 * when [blurExtend] is on (the image is zoomed out and the gaps are filled with a blurred copy).
 */
@Serializable
data class EditParams(
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    /** Fill the area the image doesn't cover with a blurred copy instead of leaving bars. */
    val blurExtend: Boolean = false,
    /** Filter applied after crop/scale. Used from Phase 2; [FilterType.NONE] is the identity. */
    val filter: FilterType = FilterType.NONE,
    val filterIntensity: Float = 1f,
    /** Phase 2: render an extra dimmed+blurred variant for the lock screen. */
    val lockVariantDim: Boolean = false,
    /** Phase 3 depth effect: normalized top of the clock band (null = depth effect off). */
    val depthBandTop: Float? = null,
)

@Serializable
enum class FilterType { NONE, AMOLED, VIBRANT, SOFT, DUOTONE, GRAIN_VIGNETTE }
