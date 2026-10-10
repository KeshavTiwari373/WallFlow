package com.keshav.wallflow.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Selectable chip used for the gallery tag filter. */
@Composable
fun FilterTagChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
    )
}

/** Removable chip used on the detail screen to show/remove a wallpaper's tags. */
@Composable
fun RemovableTagChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InputChip(
        selected = false,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove $label")
        },
        modifier = modifier,
    )
}
