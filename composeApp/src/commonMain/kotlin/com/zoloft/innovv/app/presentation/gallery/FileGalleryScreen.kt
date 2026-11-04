package com.zoloft.innovv.app.presentation.gallery

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zoloft.innovv.app.core.designsystem.InnovvColors
import com.zoloft.innovv.app.core.designsystem.InnovvDimens
import com.zoloft.innovv.app.core.designsystem.components.CategoryBadge
import com.zoloft.innovv.app.core.designsystem.components.InnovvSectionHeader
import com.zoloft.innovv.app.core.designsystem.components.SegmentedPill
import com.zoloft.innovv.app.core.util.formatBytes
import com.zoloft.innovv.app.core.util.formatDuration
import com.zoloft.innovv.app.core.util.formatMegabytesPerSecond
import com.zoloft.innovv.app.domain.model.CameraChannel
import com.zoloft.innovv.app.domain.model.GalleryFilter
import com.zoloft.innovv.app.domain.model.GalleryUiState
import com.zoloft.innovv.app.domain.model.MediaClip
import com.zoloft.innovv.app.domain.model.PreviewState
import com.zoloft.innovv.app.domain.model.TransferState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGalleryScreen(
    state: GalleryUiState,
    messages: Flow<String>,
    onLoad: () -> Unit,
    onLeave: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onRefresh: () -> Unit,
    onFilter: (GalleryFilter) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onOpenPreview: (String) -> Unit,
    onClosePreview: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPreviewChannel: (CameraChannel) -> Unit,
    onTogglePiP: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onDismissTransfer: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { onLoad() }
    LaunchedEffect(messages) {
        messages.collectLatest { snackbarHostState.showSnackbar(it) }
    }
    DisposableEffect(Unit) {
        onDispose { onLeave() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(InnovvColors.BackgroundPrimary)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GalleryTopBar(onBack = onBack, onSettings = onSettings)

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = InnovvDimens.screenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                items(GalleryFilter.entries.toList()) { filter ->
                    FilterPill(
                        filter = filter,
                        count = state.counts[filter] ?: 0,
                        selected = state.filter == filter,
                        onClick = { onFilter(filter) },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = InnovvDimens.screenPadding,
                    end = InnovvDimens.screenPadding,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(InnovvDimens.cardGap),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InnovvSectionHeader("Media Grid")
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                color = InnovvColors.AccentPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = InnovvColors.AccentPrimary, strokeWidth = 2.dp)
                        }
                    }
                } else if (state.error != null) {
                    item {
                        ErrorState(message = state.error, onRetry = onRefresh)
                    }
                } else if (state.visibleClips.isEmpty()) {
                    item {
                        EmptyState(onRefresh = onRefresh)
                    }
                } else {
                    items(state.visibleClips, key = { it.id }) { clip ->
                        ClipRow(
                            clip = clip,
                            selectionMode = state.selectionMode,
                            selected = clip.id in state.selectedIds,
                            onClick = {
                                if (state.selectionMode) onToggleSelection(clip.id) else onOpenPreview(clip.id)
                            },
                            onLongClick = { onToggleSelection(clip.id) },
                        )
                    }
                }
            }

            if (state.selectionMode) {
                SelectionBar(
                    count = state.selectedIds.size,
                    onClose = onClearSelection,
                    onSelectAll = onSelectAll,
                    onDownload = onDownload,
                    onDelete = onDelete,
                )
            }
        }

        state.transfer?.let { transfer ->
            TransferOverlay(
                transfer = transfer,
                onDismiss = onDismissTransfer,
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
            )
        }

        state.preview?.let { preview ->
            val clip = state.clips.firstOrNull { it.id == preview.clipId }
            if (clip != null) {
                ClipPreviewDialog(
                    clip = clip,
                    preview = preview,
                    onClose = onClosePreview,
                    onTogglePlayback = onTogglePlayback,
                    onChannel = onPreviewChannel,
                    onTogglePiP = onTogglePiP,
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

@Composable
private fun GalleryTopBar(
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InnovvDimens.screenPadding, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Back",
                tint = InnovvColors.TextPrimary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "File Gallery",
                color = InnovvColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = InnovvColors.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun FilterPill(
    filter: GalleryFilter,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) InnovvColors.AccentPrimary else InnovvColors.SurfaceCard,
        animationSpec = tween(120),
        label = "filter-background-${filter.name}",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) InnovvColors.BackgroundPrimary else InnovvColors.TextPrimary,
        animationSpec = tween(120),
        label = "filter-foreground-${filter.name}",
    )
    val label = if (filter == GalleryFilter.ALL) filter.label else "${filter.label} ($count)"
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(InnovvDimens.segmentedRadius))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ClipRow(
    clip: MediaClip,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InnovvDimens.mediaRowRadius))
            .background(if (selected) InnovvColors.SurfaceCard.copy(alpha = 0.85f) else InnovvColors.SurfaceCard)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(InnovvDimens.innerPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selectionMode) {
            Icon(
                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) InnovvColors.AccentPrimary else InnovvColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = clip.displayTime,
                color = InnovvColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = clip.specLine(),
                color = InnovvColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CategoryBadge(clip.category)
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(InnovvColors.SurfaceCard)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Cancel selection", tint = InnovvColors.TextSecondary)
            }
            Text(
                text = "$count selected",
                color = InnovvColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Select all",
                color = InnovvColors.AccentPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onSelectAll).padding(8.dp),
            )
            IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Download, contentDescription = "Download selected", tint = InnovvColors.TextPrimary)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete selected", tint = InnovvColors.SemanticDanger)
            }
        }
    }
}

@Composable
private fun TransferOverlay(
    transfer: TransferState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InnovvColors.SurfaceCard)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (transfer) {
            is TransferState.Preparing -> {
                Text("Preparing ${transfer.totalFiles} item(s)...", color = InnovvColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = InnovvColors.AccentPrimary,
                    trackColor = InnovvColors.BackgroundPrimary,
                )
            }
            is TransferState.Downloading -> {
                Text(
                    text = "Downloading ${transfer.currentFile}",
                    color = InnovvColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatMegabytesPerSecond(transfer.bytesPerSecond)} • ${transfer.completedFiles}/${transfer.totalFiles} complete",
                    color = InnovvColors.SemanticSuccess,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                LinearProgressIndicator(
                    progress = { if (transfer.totalBytes > 0) transfer.bytesDownloaded.toFloat() / transfer.totalBytes else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = InnovvColors.AccentPrimary,
                    trackColor = InnovvColors.BackgroundPrimary,
                )
            }
            is TransferState.Deleting -> {
                Text("Deleting ${transfer.completed}/${transfer.total}...", color = InnovvColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(
                    progress = { if (transfer.total > 0) transfer.completed.toFloat() / transfer.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = InnovvColors.SemanticDanger,
                    trackColor = InnovvColors.BackgroundPrimary,
                )
            }
            is TransferState.Complete -> {
                Text("Transfer complete (${transfer.files} item(s))", color = InnovvColors.SemanticSuccess, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            is TransferState.Failed -> {
                Text(transfer.message, color = InnovvColors.SemanticDanger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (transfer is TransferState.Complete || transfer is TransferState.Failed) {
            Text(
                text = "Dismiss",
                color = InnovvColors.AccentPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.End).clickable(onClick = onDismiss).padding(4.dp),
            )
        }
    }
}

@Composable
private fun ClipPreviewDialog(
    clip: MediaClip,
    preview: PreviewState,
    onClose: () -> Unit,
    onTogglePlayback: () -> Unit,
    onChannel: (CameraChannel) -> Unit,
    onTogglePiP: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(InnovvColors.BackgroundPrimary)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(clip.displayTime, color = InnovvColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(clip.specLine(), color = InnovvColors.TextSecondary, fontSize = 11.sp)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close preview", tint = InnovvColors.TextPrimary)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(InnovvColors.SurfaceCard),
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF1C2228), Color(0xFF090B0D)),
                        ),
                    )
                }
                Text(
                    text = "${if (preview.activeChannel == CameraChannel.FRONT) "FRONT" else "REAR"} • ${clip.resolutionLabel ?: "Video"}",
                    color = InnovvColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
                if (preview.isPiPVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .width(120.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(InnovvColors.BackgroundPrimary.copy(alpha = 0.88f))
                            .clickable(onClick = onTogglePiP),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (preview.activeChannel == CameraChannel.FRONT) "REAR" else "FRONT",
                            color = InnovvColors.AccentPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (clip.hasBothChannels) {
                SegmentedPill(
                    options = listOf("Front", "Rear"),
                    selectedIndex = if (preview.activeChannel == CameraChannel.FRONT) 0 else 1,
                    onSelect = { onChannel(if (it == 0) CameraChannel.FRONT else CameraChannel.REAR) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = onTogglePlayback, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (preview.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (preview.isPlaying) "Pause" else "Play",
                        tint = InnovvColors.AccentPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Slider(
                    value = if (preview.durationMs > 0) preview.positionMs.toFloat() / preview.durationMs else 0f,
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = InnovvColors.AccentPrimary,
                        activeTrackColor = InnovvColors.AccentPrimary,
                        inactiveTrackColor = InnovvColors.BorderSubtle,
                    ),
                )
                Text(
                    text = formatDuration((preview.durationMs / 1000).toInt()),
                    color = InnovvColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = "Recorded clip • Tap the PiP window to swap front/rear",
                color = InnovvColors.TextSecondary,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("No clips found", color = InnovvColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("The camera returned no media for this filter.", color = InnovvColors.TextSecondary, fontSize = 11.sp)
        Text(
            text = "Refresh",
            color = InnovvColors.AccentPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onRefresh).padding(8.dp),
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(message, color = InnovvColors.SemanticDanger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(
            text = "Retry",
            color = InnovvColors.AccentPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onRetry).padding(8.dp),
        )
    }
}

private fun MediaClip.specLine(): String {
    val resolution = resolutionLabel ?: if (isPhoto) "Photo" else "Video"
    val detail = when {
        isPhoto -> front?.sizeBytes?.let { formatBytes(it) } ?: "JPEG"
        durationSeconds != null -> formatDuration(durationSeconds)
        else -> "—"
    }
    return "$resolution • $detail"
}
