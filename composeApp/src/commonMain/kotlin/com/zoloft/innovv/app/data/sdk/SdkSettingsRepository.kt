package com.zoloft.innovv.app.data.sdk

import com.zoloft.innovv.app.domain.SettingsRepository
import com.zoloft.innovv.app.domain.model.CameraSettingsState
import com.zoloft.innovv.app.domain.model.CameraSettingsValues
import com.zoloft.innovv.app.domain.model.DiagnosticResult
import com.zoloft.innovv.app.domain.model.GSensorLevel
import com.zoloft.innovv.app.domain.model.LoopInterval
import com.zoloft.innovv.app.domain.model.SettingsAccess
import com.zoloft.innovv.app.domain.model.VideoResolution
import com.zoloft.innovv.app.domain.model.WatermarkOptions
import com.zoloft.innovv.app.domain.model.WifiBand
import com.zoloft.innovv.sdk.K5Commands
import com.zoloft.innovv.sdk.model.CameraSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

class SdkSettingsRepository(
    private val session: SdkCameraSession,
) : SettingsRepository {
    override suspend fun load(): Result<CameraSettingsState> = withContext(Dispatchers.Default) {
        session.useClient { client ->
            val settings = client.getSettings(includeMenu = true)
            val version = client.getVersion()
            val freeBytes = runCatching { client.getFreeSpaceBytes() }.getOrNull()
            val sdState = runCatching { client.getSdCardState() }.getOrNull()
            val values = settings.toAppValues()
            CameraSettingsState(
                access = SettingsAccess.Online,
                applied = values,
                draft = values,
                storageUsedBytes = null,
                storageTotalBytes = null,
                storageFreeBytes = freeBytes,
                firmwareVersion = version,
                firmwareIsLatest = false,
                cachedAtMillis = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }

    override suspend fun apply(
        values: CameraSettingsValues,
        onStep: suspend (String) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.Default) {
        val wasRecording = session.isRecording()
        session.writeClient { client ->
            onStep("Pausing recording...")
            if (wasRecording) {
                client.stopRecording()
            }
            try {
                onStep("Writing camera settings...")
                client.setSetting(K5Commands.WIFI_BAND, values.wifiBand.cameraValue)
                client.setSetting(K5Commands.VIDEO_RESOLUTION, values.videoResolution.cameraValue)
                client.setSetting(K5Commands.LOOP_RECORDING, values.loop.cameraValue)
                client.setSetting(K5Commands.DATE_STAMP, if (values.watermark.timestamp) 1 else 0)
                client.setSetting(K5Commands.GPS_STAMP, if (values.watermark.gpsSpeed) 1 else 0)
                client.setSetting(K5Commands.GSENSOR_STAMP, if (values.watermark.gSensor) 1 else 0)
                values.watermark.customText?.takeIf { it.isNotBlank() }?.let { text ->
                    client.setSetting(K5Commands.WATERMARK, text)
                }
                client.setSetting(K5Commands.GSENSOR_SENSITIVITY, values.gSensor.cameraValue)
                client.setSetting(K5Commands.PARKING_MODE, if (values.parkingMode) 1 else 0)
                onStep("Saving settings...")
                client.saveSettings()
            } finally {
                if (wasRecording) {
                    onStep("Restarting recording...")
                    client.startRecording()
                }
            }
        }.map { Unit }
    }

    override suspend fun formatSdCard(onStep: suspend (String) -> Unit): Result<Unit> =
        withContext(Dispatchers.Default) {
            val wasRecording = session.isRecording()
            session.writeClient { client ->
                onStep("Pausing recording...")
                if (wasRecording) client.stopRecording()
                try {
                    onStep("Formatting SD card...")
                    client.formatSdCard()
                    // The camera rebuilds its filesystem asynchronously. Give it a
                    // short grace period before recording is restarted.
                    kotlinx.coroutines.delay(1_500.milliseconds)
                } finally {
                    if (wasRecording) {
                        onStep("Restarting recording...")
                        client.startRecording()
                    }
                }
            }.map { Unit }
        }

    override suspend fun runDiagnostics(): Result<List<DiagnosticResult>> =
        withContext(Dispatchers.Default) {
            session.useClient { client ->
                val heartbeatStart = Clock.System.now().toEpochMilliseconds()
                client.heartbeat()
                val latency = Clock.System.now().toEpochMilliseconds() - heartbeatStart
                val sdState = runCatching { client.getSdCardState() }.getOrNull()
                val free = runCatching { client.getFreeSpaceBytes() }.getOrNull()
                val version = runCatching { client.getVersion() }.getOrNull() ?: "unknown"
                buildList {
                    add(DiagnosticResult("Heartbeat", "$latency ms", latency < 1_500))
                    add(
                        DiagnosticResult(
                            "SD card",
                            when (sdState?.code) {
                                1 -> "Inserted / healthy"
                                2 -> "Locked or error"
                                0 -> "No card"
                                else -> "Unknown"
                            },
                            sdState?.code == 1,
                        ),
                    )
                    add(
                        DiagnosticResult(
                            "Free space",
                            free?.let { "${it / (1024 * 1024 * 1024)} GB" } ?: "—",
                            free != null && free > 0,
                        ),
                    )
                    add(DiagnosticResult("Firmware", version, true))
                    add(DiagnosticResult("Camera gateway", "192.168.1.254", true))
                }
            }
        }

    private fun CameraSettings.toAppValues(): CameraSettingsValues {
        val band = when (valueOf(K5Commands.WIFI_BAND)) {
            1 -> WifiBand.GHZ_2_4
            else -> WifiBand.GHZ_5_8
        }
        val resolution = when (valueOf(K5Commands.VIDEO_RESOLUTION)) {
            21 -> VideoResolution.FHD_1080_1080
            22 -> VideoResolution.QHD_1440_1080
            else -> VideoResolution.UHD_2160_1080
        }
        val loop = when (valueOf(K5Commands.LOOP_RECORDING)) {
            1 -> LoopInterval.MIN_1
            2 -> LoopInterval.MIN_3
            3 -> LoopInterval.MIN_5
            4 -> LoopInterval.MIN_10
            else -> LoopInterval.MIN_3
        }
        val gSensor = when (val raw = valueOf(K5Commands.GSENSOR_SENSITIVITY) ?: 9) {
            0 -> GSensorLevel.OFF
            in 1..3 -> GSensorLevel.LOW
            in 4..6 -> GSensorLevel.MID
            else -> GSensorLevel.HIGH
        }
        return CameraSettingsValues(
            wifiBand = band,
            autoReconnect = true,
            videoResolution = resolution,
            loop = loop,
            watermark = WatermarkOptions(
                timestamp = (valueOf(K5Commands.DATE_STAMP) ?: 1) == 1,
                gpsSpeed = (valueOf(K5Commands.GPS_STAMP) ?: 0) > 0,
                gSensor = (valueOf(K5Commands.GSENSOR_STAMP) ?: 0) == 1,
                customText = null,
            ),
            gSensor = gSensor,
            parkingMode = (valueOf(K5Commands.PARKING_MODE) ?: 1) == 1,
        )
    }
}
