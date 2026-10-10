package com.keshav.wallflow.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.keshav.wallflow.wallpaper.RotationMode
import com.keshav.wallflow.wallpaper.WallpaperTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rotation_settings")

/** Snapshot of the user's rotation configuration. */
data class RotationConfig(
    val mode: RotationMode,
    val intervalMinutes: Int,
    val target: WallpaperTarget,
    /** Tag id to restrict rotation to, or null for any wallpaper. */
    val filterTagId: Long?,
)

/** Persists the rotation configuration via Jetpack DataStore. */
@Singleton
class RotationPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val config: Flow<RotationConfig> = context.dataStore.data.map { prefs ->
        RotationConfig(
            mode = prefs[KEY_MODE]?.let { runCatching { RotationMode.valueOf(it) }.getOrNull() }
                ?: RotationMode.OFF,
            intervalMinutes = prefs[KEY_INTERVAL] ?: DEFAULT_INTERVAL_MINUTES,
            target = prefs[KEY_TARGET]?.let { runCatching { WallpaperTarget.valueOf(it) }.getOrNull() }
                ?: WallpaperTarget.BOTH,
            filterTagId = prefs[KEY_FILTER_TAG]?.takeIf { it >= 0 },
        )
    }

    suspend fun setMode(mode: RotationMode) {
        context.dataStore.edit { it[KEY_MODE] = mode.name }
    }

    suspend fun setIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_INTERVAL] = minutes.coerceAtLeast(MIN_INTERVAL_MINUTES) }
    }

    suspend fun setTarget(target: WallpaperTarget) {
        context.dataStore.edit { it[KEY_TARGET] = target.name }
    }

    suspend fun setFilterTagId(tagId: Long?) {
        context.dataStore.edit { it[KEY_FILTER_TAG] = tagId ?: -1L }
    }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDED] ?: false }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { it[KEY_ONBOARDED] = true }
    }

    companion object {
        const val MIN_INTERVAL_MINUTES = 15
        const val DEFAULT_INTERVAL_MINUTES = 60

        private val KEY_MODE = stringPreferencesKey("rotation_mode")
        private val KEY_INTERVAL = intPreferencesKey("interval_minutes")
        private val KEY_TARGET = stringPreferencesKey("target")
        private val KEY_FILTER_TAG = longPreferencesKey("filter_tag_id")
        private val KEY_ONBOARDED = booleanPreferencesKey("onboarding_complete")
    }
}
