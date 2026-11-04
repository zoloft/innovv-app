package com.zoloft.innovv.app.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoloft.innovv.app.core.designsystem.InnovvColors
import com.zoloft.innovv.app.core.designsystem.InnovvDimens
import com.zoloft.innovv.app.core.designsystem.components.CapsuleButton
import com.zoloft.innovv.app.core.designsystem.components.CapsuleStyle
import com.zoloft.innovv.app.core.designsystem.components.InnovvSectionHeader
import com.zoloft.innovv.app.core.designsystem.components.SettingsRow
import com.zoloft.innovv.app.core.designsystem.components.SettingsSegmentedRow
import com.zoloft.innovv.app.core.util.formatStorage
import com.zoloft.innovv.app.domain.model.ApplyState
import com.zoloft.innovv.app.domain.model.ApplyStep
import com.zoloft.innovv.app.domain.model.CameraSettingsState
import com.zoloft.innovv.app.domain.model.CameraSettingsValues
import com.zoloft.innovv.app.domain.model.DiagnosticsState
import com.zoloft.innovv.app.domain.model.GSensorLevel
import com.zoloft.innovv.app.domain.model.LoopInterval
import com.zoloft.innovv.app.domain.model.SettingsAccess
import com.zoloft.innovv.app.domain.model.VideoResolution
import com.zoloft.innovv.app.domain.model.WatermarkOptions
import com.zoloft.innovv.app.domain.model.WifiBand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsModalSheet(
    state: CameraSettingsState,
    onLoad: () -> Unit,
    onDismiss: () -> Unit,
    onUpdateDraft: ((CameraSettingsValues) -> CameraSettingsValues) -> Unit,
    onApply: () -> Unit,
    onFormatSd: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onDismissApplyState: () -> Unit,
    onDismissError: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val online = state.access is SettingsAccess.Online
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<SettingsPicker?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onLoad() }

    ModalBottomSheet(
        onDismissRequest = {
            if (state.isDirty) showDiscardDialog = true else onDismiss()
        },
        sheetState = sheetState,
        containerColor = InnovvColors.BackgroundPrimary,
        contentColor = InnovvColors.TextPrimary,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = InnovvDimens.screenPadding),
        ) {
            SettingsHeader(
                onClose = {
                    if (state.isDirty) showDiscardDialog = true else onDismiss()
                },
            )

            if (state.access !is SettingsAccess.Online) {
                OfflineBanner(state)
                Spacer(Modifier.height(10.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InnovvSectionHeader(
                    text = "Device Wi-Fi",
                    color = InnovvColors.AccentPrimary,
                    fontSize = 12,
                )
                SettingsRow(
                    label = "Wi-Fi Band",
                    subtitle = if (state.draft.autoReconnect) "Auto-reconnect enabled" else "Auto-reconnect disabled",
                    value = "${state.draft.wifiBand.label} Active",
                    onClick = { picker = SettingsPicker.Wifi },
                )
                Spacer(Modifier.height(4.dp))
                InnovvSectionHeader(
                    text = "Video Settings",
                    color = InnovvColors.AccentPrimary,
                    fontSize = 12,
                )
                SettingsRow(
                    label = "Video Resolution",
                    value = state.draft.videoResolution.label,
                    enabled = online,
                    locked = !online,
                    onClick = { if (online) picker = SettingsPicker.Resolution },
                )
                SettingsRow(
                    label = "Loop Recording",
                    value = state.draft.loop.label,
                    enabled = online,
                    locked = !online,
                    onClick = { if (online) picker = SettingsPicker.Loop },
                )
                SettingsRow(
                    label = "Watermark Overlay",
                    value = state.draft.watermark.summary,
                    enabled = online,
                    locked = !online,
                    onClick = { if (online) picker = SettingsPicker.Watermark },
                )

                Spacer(Modifier.height(4.dp))
                InnovvSectionHeader(
                    text = "Hardware & Sensors",
                    color = InnovvColors.AccentPrimary,
                    fontSize = 12,
                )
                SettingsRow(
                    label = "G-Sensor Sensitivity",
                    value = state.draft.gSensor.label,
                    enabled = online,
                    locked = !online,
                    onClick = { if (online) picker = SettingsPicker.GSensor },
                )
                SettingsRow(
                    label = "Parking Mode",
                    subtitle = "Battery drain protection enabled",
                    value = if (state.draft.parkingMode) "Active (12.2V Cutoff)" else "Off",
                    enabled = online,
                    locked = !online,
                    onClick = {
                        if (online) onUpdateDraft { it.copy(parkingMode = !it.parkingMode) }
                    },
                )

                Spacer(Modifier.height(4.dp))
                InnovvSectionHeader(
                    text = "Storage & Firmware",
                    color = InnovvColors.AccentPrimary,
                    fontSize = 12,
                )
                SettingsRow(
                    label = "Card Utility: Format SD",
                    value = formatStorage(state.storageUsedBytes, state.storageTotalBytes, state.storageFreeBytes),
                    enabled = online,
                    locked = !online,
                    onClick = {
                        if (online) showFormatDialog = true
                    },
                )
                SettingsRow(
                    label = "Firmware Version",
                    value = if (state.firmwareVersion == "—") {
                        "—"
                    } else {
                        "${state.firmwareVersion}${if (state.firmwareIsLatest) " (Latest)" else " (Installed)"}"
                    },
                    onClick = { showDiagnostics = true },
                )
                Spacer(Modifier.height(8.dp))
            }

            if (state.error != null) {
                Text(
                    text = state.error,
                    color = InnovvColors.SemanticDanger,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CapsuleButton(
                    text = "Done",
                    onClick = {
                        if (state.isDirty) showDiscardDialog = true else onDismiss()
                    },
                    style = CapsuleStyle.Surface,
                    modifier = Modifier.weight(1f),
                )
                CapsuleButton(
                    text = "Apply Settings",
                    onClick = onApply,
                    style = CapsuleStyle.Amber,
                    enabled = state.isDirty && state.apply !is ApplyState.Applying,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor = InnovvColors.SurfaceCard,
            title = { Text("Discard changes?", color = InnovvColors.TextPrimary) },
            text = { Text("Uncommitted camera settings will be lost.", color = InnovvColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onDismiss()
                }) { Text("Discard", color = InnovvColors.SemanticDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing", color = InnovvColors.AccentPrimary) }
            },
        )
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            containerColor = InnovvColors.SurfaceCard,
            title = { Text("Format SD card?", color = InnovvColors.TextPrimary) },
            text = {
                Text(
                    "This permanently deletes every clip and photo on the camera. Recording will pause while the card is formatted.",
                    color = InnovvColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showFormatDialog = false
                    onFormatSd()
                }) { Text("Format", color = InnovvColors.SemanticDanger) }
            },
            dismissButton = {
                TextButton(onClick = { showFormatDialog = false }) { Text("Cancel", color = InnovvColors.AccentPrimary) }
            },
        )
    }

    picker?.let { selected ->
        SettingsPickerDialog(
            picker = selected,
            state = state,
            onDismiss = { picker = null },
            onUpdateDraft = onUpdateDraft,
        )
    }

    if (showDiagnostics) {
        DiagnosticsDialog(
            state = state,
            onDismiss = { showDiagnostics = false },
            onRun = onRunDiagnostics,
        )
    }

    if (state.apply is ApplyState.Applying) {
        ApplyOverlay(state.apply)
    }

    if (state.apply is ApplyState.Failed) {
        val failure = state.apply as ApplyState.Failed
        AlertDialog(
            onDismissRequest = onDismissApplyState,
            containerColor = InnovvColors.SurfaceCard,
            title = { Text("Could not apply settings", color = InnovvColors.TextPrimary) },
            text = { Text(failure.message, color = InnovvColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = onDismissApplyState) { Text("Close", color = InnovvColors.AccentPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { onDismissApplyState(); onApply() }) {
                    Text("Retry", color = InnovvColors.AccentPrimary)
                }
            },
        )
    }

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            containerColor = InnovvColors.SurfaceCard,
            title = { Text("Camera settings", color = InnovvColors.TextPrimary) },
            text = { Text(state.error, color = InnovvColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = onDismissError) { Text("Close", color = InnovvColors.AccentPrimary) }
            },
        )
    }
}

@Composable
private fun SettingsHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Settings",
            color = InnovvColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close settings",
                tint = InnovvColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun OfflineBanner(state: CameraSettingsState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(InnovvColors.AccentPrimary.copy(alpha = 0.12f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = InnovvColors.AccentPrimary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Offline — showing cached settings. Hardware changes require a camera connection.",
            color = InnovvColors.AccentPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private enum class SettingsPicker { Wifi, Resolution, Loop, Watermark, GSensor }

@Composable
private fun SettingsPickerDialog(
    picker: SettingsPicker,
    state: CameraSettingsState,
    onDismiss: () -> Unit,
    onUpdateDraft: ((CameraSettingsValues) -> CameraSettingsValues) -> Unit,
) {
    val title = when (picker) {
        SettingsPicker.Wifi -> "Wi-Fi Band"
        SettingsPicker.Resolution -> "Video Resolution"
        SettingsPicker.Loop -> "Loop Recording"
        SettingsPicker.Watermark -> "Watermark Overlay"
        SettingsPicker.GSensor -> "G-Sensor Sensitivity"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InnovvColors.SurfaceCard,
        title = { Text(title, color = InnovvColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (picker) {
                    SettingsPicker.Wifi -> {
                        WifiBand.entries.forEach { band ->
                            ChoiceRow(
                                label = band.label,
                                selected = state.draft.wifiBand == band,
                                onClick = {
                                    onUpdateDraft { it.copy(wifiBand = band) }
                                    onDismiss()
                                },
                            )
                        }
                        HorizontalDivider(color = InnovvColors.BorderSubtle, modifier = Modifier.padding(vertical = 6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Auto-reconnect", color = InnovvColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Switch(
                                checked = state.draft.autoReconnect,
                                onCheckedChange = { checked ->
                                    onUpdateDraft { values -> values.copy(autoReconnect = checked) }
                                },
                            )
                        }
                    }
                    SettingsPicker.Resolution -> {
                        VideoResolution.entries.forEach { resolution ->
                            ChoiceRow(
                                label = resolution.label,
                                selected = state.draft.videoResolution == resolution,
                                onClick = {
                                    onUpdateDraft { it.copy(videoResolution = resolution) }
                                    onDismiss()
                                },
                            )
                        }
                    }
                    SettingsPicker.Loop -> {
                        LoopInterval.entries.forEach { interval ->
                            ChoiceRow(
                                label = interval.label,
                                selected = state.draft.loop == interval,
                                onClick = {
                                    onUpdateDraft { it.copy(loop = interval) }
                                    onDismiss()
                                },
                            )
                        }
                    }
                    SettingsPicker.GSensor -> {
                        GSensorLevel.entries.forEach { level ->
                            ChoiceRow(
                                label = level.label,
                                selected = state.draft.gSensor == level,
                                onClick = {
                                    onUpdateDraft { it.copy(gSensor = level) }
                                    onDismiss()
                                },
                            )
                        }
                    }
                    SettingsPicker.Watermark -> {
                        WatermarkRow(
                            label = "Timestamp",
                            checked = state.draft.watermark.timestamp,
                            onCheckedChange = { checked ->
                                onUpdateDraft { it.copy(watermark = it.watermark.copy(timestamp = checked)) }
                            },
                        )
                        WatermarkRow(
                            label = "GPS + Speed",
                            checked = state.draft.watermark.gpsSpeed,
                            onCheckedChange = { checked ->
                                onUpdateDraft { it.copy(watermark = it.watermark.copy(gpsSpeed = checked)) }
                            },
                        )
                        WatermarkRow(
                            label = "G-Sensor",
                            checked = state.draft.watermark.gSensor,
                            onCheckedChange = { checked ->
                                onUpdateDraft { it.copy(watermark = it.watermark.copy(gSensor = checked)) }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = InnovvColors.AccentPrimary) }
        },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, color = InnovvColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WatermarkRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, color = InnovvColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DiagnosticsDialog(
    state: CameraSettingsState,
    onDismiss: () -> Unit,
    onRun: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InnovvColors.SurfaceCard,
        title = { Text("Connection diagnostics", color = InnovvColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val diagnostics = state.diagnostics) {
                    DiagnosticsState.Idle -> Text(
                        "Check heartbeat, event socket, SD card and firmware status.",
                        color = InnovvColors.TextSecondary,
                        fontSize = 11.sp,
                    )
                    DiagnosticsState.Running -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(color = InnovvColors.AccentPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text("Running diagnostics...", color = InnovvColors.TextSecondary, fontSize = 11.sp)
                    }
                    is DiagnosticsState.Complete -> diagnostics.results.forEach { result ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(result.label, color = InnovvColors.TextSecondary, fontSize = 11.sp)
                            Text(
                                result.value,
                                color = if (result.ok) InnovvColors.SemanticSuccess else InnovvColors.SemanticDanger,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    is DiagnosticsState.Failed -> Text(diagnostics.message, color = InnovvColors.SemanticDanger, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRun) { Text("Run again", color = InnovvColors.AccentPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = InnovvColors.TextSecondary) }
        },
    )
}

@Composable
private fun ApplyOverlay(state: ApplyState.Applying) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(InnovvColors.SurfaceCard)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(color = InnovvColors.AccentPrimary, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
                Text(
                    text = "Applying settings to camera...",
                    color = InnovvColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = state.step.label,
                    color = InnovvColors.TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = InnovvColors.AccentPrimary,
                    trackColor = InnovvColors.BackgroundPrimary,
                )
            }
        }
    }
}
