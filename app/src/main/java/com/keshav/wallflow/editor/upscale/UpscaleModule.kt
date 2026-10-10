package com.keshav.wallflow.editor.upscale

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Picks the [Upscaler] implementation: Real-ESRGAN (TFLite model bundled in assets) with
 * [LanczosUpscaler] as the fallback — used for sources too large for AI tiling to pay off and
 * whenever the model/interpreter is unavailable. Everything else depends only on the [Upscaler]
 * interface.
 */
@Module
@InstallIn(SingletonComponent::class)
object UpscaleModule {

    @Provides
    @Singleton
    fun provideUpscaler(@ApplicationContext context: Context): Upscaler =
        RealEsrganUpscaler(context, LanczosUpscaler())
}
