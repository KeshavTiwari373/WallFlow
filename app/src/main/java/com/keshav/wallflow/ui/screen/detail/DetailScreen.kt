package com.keshav.wallflow.ui.screen.detail

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.keshav.wallflow.R
import com.keshav.wallflow.ui.component.RemovableTagChip
import com.keshav.wallflow.wallpaper.WallpaperTarget
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val item by viewModel.wallpaper.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddTag by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DetailEvent.Applied -> Toast.makeText(
                    context,
                    context.getString(
                        if (event.success) R.string.applied_toast else R.string.apply_failed_toast,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
                DetailEvent.Deleted -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    item?.let { current ->
                        IconButton(onClick = { onEdit(current.wallpaper.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_wallpaper))
                    }
                },
            )
        },
    ) { padding ->
        val wallpaper = item ?: run {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(wallpaper.wallpaper.filePath))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )

            if (wallpaper.wallpaper.editParamsJson != null) {
                Text(
                    text = stringResource(R.string.edited_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Usage metadata
            val usageText = if (wallpaper.wallpaper.lastUsedAt == null) {
                stringResource(R.string.never_used)
            } else {
                val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(wallpaper.wallpaper.lastUsedAt!!))
                stringResource(R.string.last_used, date)
            }
            Text(text = stringResource(R.string.used_count, wallpaper.wallpaper.usageCount))
            Text(text = usageText)

            // Tags
            Text(text = stringResource(R.string.edit_tags))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                wallpaper.tags.forEach { tag ->
                    RemovableTagChip(label = tag.name, onRemove = { viewModel.removeTag(tag.id) })
                }
                TextButton(onClick = { showAddTag = true }) {
                    Text(stringResource(R.string.add_tag))
                }
            }

            // Apply actions
            Text(text = stringResource(R.string.apply_wallpaper))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { viewModel.apply(WallpaperTarget.HOME) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.target_home))
                }
                OutlinedButton(onClick = { viewModel.apply(WallpaperTarget.LOCK) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.target_lock))
                }
            }
            Button(onClick = { viewModel.apply(WallpaperTarget.BOTH) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.target_both))
            }
        }
    }

    if (showAddTag) {
        AddTagDialog(
            onDismiss = { showAddTag = false },
            onConfirm = { name ->
                viewModel.addTag(name)
                showAddTag = false
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AddTagDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_tag)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.add_tag)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.add_tag))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
