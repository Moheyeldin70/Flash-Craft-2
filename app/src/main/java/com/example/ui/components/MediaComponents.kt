package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.media.MediaStorageHelper
import com.example.domain.model.MediaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CardMediaPreview(
    mediaUri: String,
    mediaType: MediaType,
    mediaName: String,
    autoPlayAudio: Boolean = false,
    compact: Boolean = false,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (mediaType == MediaType.NONE || mediaUri.isBlank()) return

    var showZoomDialog by remember { mutableStateOf(false) }

    when (mediaType) {
        MediaType.IMAGE -> {
            Box(modifier = modifier.fillMaxWidth()) {
                AsyncImage(
                    model = Uri.parse(mediaUri),
                    contentDescription = mediaName.ifBlank { "Flashcard image" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = if (compact) 130.dp else 210.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showZoomDialog = true }
                )
                if (onRemove != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(50.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove image",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            if (showZoomDialog) {
                Dialog(onDismissRequest = { showZoomDialog = false }) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            AsyncImage(
                                model = Uri.parse(mediaUri),
                                contentDescription = mediaName.ifBlank { "Zoomed flashcard image" },
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 460.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
                            if (mediaName.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = mediaName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        MediaType.AUDIO -> {
            AudioPlayerCard(
                mediaUri = mediaUri,
                mediaName = mediaName.ifBlank { "Audio clip" },
                autoPlay = autoPlayAudio,
                onRemove = onRemove,
                modifier = modifier
            )
        }
        MediaType.NONE -> Unit
    }
}

@Composable
private fun AudioPlayerCard(
    mediaUri: String,
    mediaName: String,
    autoPlay: Boolean,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember(mediaUri) { mutableStateOf(false) }
    var progress by remember(mediaUri) { mutableFloatStateOf(0f) }
    var loadError by remember(mediaUri) { mutableStateOf<String?>(null) }

    val playerHolder = remember(mediaUri) {
        object {
            var player: MediaPlayer? = null
        }
    }

    fun startOrTogglePlayback() {
        try {
            val current = playerHolder.player
            if (current != null) {
                if (current.isPlaying) {
                    current.pause()
                    isPlaying = false
                } else {
                    current.start()
                    isPlaying = true
                }
            } else {
                val mp = MediaPlayer().apply {
                    setDataSource(context, Uri.parse(mediaUri))
                    setOnPreparedListener { ready ->
                        ready.start()
                        isPlaying = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        progress = 0f
                    }
                    setOnErrorListener { _, _, _ ->
                        loadError = "Audio unavailable"
                        isPlaying = false
                        true
                    }
                    prepareAsync()
                }
                playerHolder.player = mp
            }
        } catch (e: Exception) {
            loadError = e.localizedMessage ?: "Cannot play audio"
            isPlaying = false
        }
    }

    LaunchedEffect(mediaUri, autoPlay) {
        if (autoPlay && loadError == null) {
            startOrTogglePlayback()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val mp = playerHolder.player
            if (mp != null && mp.duration > 0) {
                progress = (mp.currentPosition.toFloat() / mp.duration.toFloat()).coerceIn(0f, 1f)
            }
            delay(150L)
        }
    }

    DisposableEffect(mediaUri) {
        onDispose {
            try {
                playerHolder.player?.release()
            } catch (_: Exception) {
            }
            playerHolder.player = null
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                IconButton(
                    onClick = { startOrTogglePlayback() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause audio" else "Play audio",
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = loadError ?: mediaName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.18f)
                )
            }
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove audio",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun MediaAttachmentPickerRow(
    sideLabel: String,
    mediaUri: String,
    mediaType: MediaType,
    mediaName: String,
    onMediaAttached: (uri: String, type: MediaType, name: String) -> Unit,
    onMediaRemoved: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                MediaStorageHelper.importMediaFromUri(context, uri, MediaType.IMAGE)
                    .onSuccess { asset ->
                        onMediaAttached(asset.uriString, asset.mediaType, asset.displayName)
                    }
                    .onFailure { err ->
                        onError(err.localizedMessage ?: "Failed to attach image")
                    }
            }
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                MediaStorageHelper.importMediaFromUri(context, uri, MediaType.AUDIO)
                    .onSuccess { asset ->
                        onMediaAttached(asset.uriString, asset.mediaType, asset.displayName)
                    }
                    .onFailure { err ->
                        onError(err.localizedMessage ?: "Failed to attach audio")
                    }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$sideLabel Media (Optional)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (mediaType != MediaType.NONE && mediaUri.isNotBlank()) {
            CardMediaPreview(
                mediaUri = mediaUri,
                mediaType = mediaType,
                mediaName = mediaName,
                compact = true,
                onRemove = onMediaRemoved
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Image")
                }
                FilledTonalButton(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AudioFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Audio")
                }
            }
        }
    }
}
