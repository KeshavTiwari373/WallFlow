package com.keshav.wallflow.ui.screen.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import android.content.pm.PackageManager
import com.keshav.wallflow.R
import com.keshav.wallflow.wallpaper.RotationMode
import com.keshav.wallflow.wallpaper.WallpaperTarget

private data class IntervalOption(val minutes: Int, val labelRes: Int)

private val intervalOptions = listOf(
    IntervalOption(15, R.string.interval_15m),
    IntervalOption(30, R.string.interval_30m),
    IntervalOption(60, R.string.interval_1h),
    IntervalOption(180, R.string.interval_3h),
    IntervalOption(360, R.string.interval_6h),
    IntervalOption(720, R.string.interval_12h),
    IntervalOption(1440, R.string.interval_1d),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RotationSettingsScreen(
    onBack: () -> Unit,
    viewModel: RotationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // SCREEN_ON needs the ongoing-notification permission on Android 13+.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.setMode(RotationMode.SCREEN_ON)
    }

    fun selectMode(mode: RotationMode) {
        if (mode == RotationMode.SCREEN_ON && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        viewModel.setMode(mode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.rotation_mode),
                style = MaterialTheme.typography.titleMedium,
            )

            ModeOption(
                title = stringResource(R.string.mode_off),
                description = stringResource(R.string.mode_off_desc),
                selected = state.config.mode == RotationMode.OFF,
                onClick = { selectMode(RotationMode.OFF) },
            )
            ModeOption(
                title = stringResource(R.string.mode_interval),
                description = stringResource(R.string.mode_interval_desc),
                selected = state.config.mode == RotationMode.INTERVAL,
                onClick = { selectMode(RotationMode.INTERVAL) },
            )
            ModeOption(
                title = stringResource(R.string.mode_screen_on),
                description = stringResource(R.string.mode_screen_on_desc),
                selected = state.config.mode == RotationMode.SCREEN_ON,
                onClick = { selectMode(RotationMode.SCREEN_ON) },
            )

            // Interval picker — only for INTERVAL mode.
            if (state.config.mode == RotationMode.INTERVAL) {
                HorizontalDivider()
                Text(stringResource(R.string.interval_label), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    intervalOptions.forEach { option ->
                        FilterChip(
                            selected = state.config.intervalMinutes == option.minutes,
                            onClick = { viewModel.setInterval(option.minutes) },
                            label = { Text(stringResource(option.labelRes)) },
                        )
                    }
                }

                // Target — only meaningful for INTERVAL (SCREEN_ON is lock-only).
                Text(stringResource(R.string.target_label), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetChip(WallpaperTarget.HOME, R.string.target_home, state.config.target, viewModel::setTarget)
                    TargetChip(WallpaperTarget.LOCK, R.string.target_lock, state.config.target, viewModel::setTarget)
                    TargetChip(WallpaperTarget.BOTH, R.string.target_both, state.config.target, viewModel::setTarget)
                }
            }

            // Tag filter — relevant for both auto modes.
            if (state.config.mode != RotationMode.OFF && state.tags.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.filter_tag_label), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.config.filterTagId == null,
                        onClick = { viewModel.setFilterTag(null) },
                        label = { Text(stringResource(R.string.filter_tag_none)) },
                    )
                    state.tags.forEach { tag ->
                        FilterChip(
                            selected = state.config.filterTagId == tag.id,
                            onClick = { viewModel.setFilterTag(tag.id) },
                            label = { Text(tag.name) },
                        )
                    }
                }
            }

            // Battery optimization — relevant for the screen-on foreground service.
            HorizontalDivider()
            Text(stringResource(R.string.battery_opt_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.battery_opt_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                },
            ) { Text(stringResource(R.string.battery_opt_action)) }
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TargetChip(
    target: WallpaperTarget,
    labelRes: Int,
    current: WallpaperTarget,
    onSelect: (WallpaperTarget) -> Unit,
) {
    FilterChip(
        selected = current == target,
        onClick = { onSelect(target) },
        label = { Text(stringResource(labelRes)) },
    )
}
