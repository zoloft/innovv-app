package com.zoloft.innovv.app.domain.model

// ---------------------------------------------------------------------------
// Connection
// ---------------------------------------------------------------------------

sealed interface CameraConnectionState {
    data object Disconnected : CameraConnectionState

    data class Scanning(val targetSsid: String? = null) : CameraConnectionState

    data class Connecting(
        val ssid: String,
        val step: ConnectionStep,
        val startedAtMillis: Long,
        val canCancel: Boolean,
    ) : CameraConnectionState

    data class Connected(
        val ssid: String,
        val firmwareVersion: String,
        val stream: StreamState = StreamState.LIVE,
    ) : CameraConnectionState

    data class Reconnecting(
        val reason: ReconnectReason,
        val attempt: Int,
    ) : CameraConnectionState

    data class Failed(val failure: CameraFailure) : CameraConnectionState
}

enum class ConnectionStep {
    JOINING_WIFI,
    BINDING_NETWORK,
    HANDSHAKING,
    BUFFERING_STREAM,
}

enum class StreamState {
    STARTING,
    LIVE,
    PAUSED_FOR_GALLERY,
    PAUSED_BY_USER,
    STALLED,
}

enum class ReconnectReason {
    STREAM_STALL,
    EVENT_STREAM_LOST,
    NETWORK_LOST,
    HEARTBEAT_TIMEOUT,
}

sealed interface CameraFailure {
    data object WifiJoinDenied : CameraFailure
    data object CameraUnreachable : CameraFailure
    data class CameraCommand(val code: Int) : CameraFailure
    data object StreamTimeout : CameraFailure
    data class Unknown(val message: String) : CameraFailure
}

// ---------------------------------------------------------------------------
// Telemetry
// ---------------------------------------------------------------------------

data class TelemetryState(
    val gps: GpsState = GpsState.Searching,
    val gForce: GForceState = GForceState.Unavailable,
    val storage: StorageState = StorageState.Unknown,
    val battery: BatteryState = BatteryState.Unknown,
    val speedKmh: Double? = null,
    val recording: RecordingState = RecordingState.Unknown,
    val lastUpdatedAtMillis: Long = 0L,
)

sealed interface GpsState {
    data object Searching : GpsState
    data object Disabled : GpsState
    data object Unavailable : GpsState
    data class Locked(
        val rateHz: Int,
        val satellites: Int,
        val snr: Int,
    ) : GpsState
}

sealed interface GForceState {
    data object Unavailable : GForceState
    data object Idle : GForceState
    data class Live(
        val x: Float,
        val y: Float,
        val z: Float,
        val magnitudeG: Float,
    ) : GForceState
}

sealed interface StorageState {
    data object Unknown : StorageState
    data object NoCard : StorageState
    data object CardError : StorageState
    data class Ready(
        val usedBytes: Long?,
        val totalBytes: Long?,
        val freeBytes: Long,
    ) : StorageState
}

sealed interface BatteryState {
    data object Unknown : BatteryState
    data class Level(val level: Int, val charging: Boolean) : BatteryState
}

sealed interface RecordingState {
    data object Unknown : RecordingState
    data object Stopped : RecordingState
    data object Starting : RecordingState
    data class Recording(
        val startedAtMillis: Long,
        val elapsedSeconds: Int,
    ) : RecordingState
    data object Stopping : RecordingState
}

// ---------------------------------------------------------------------------
// Gallery
// ---------------------------------------------------------------------------

enum class GalleryFilter(val label: String) {
    ALL("All"),
    LOOP("Loop"),
    PARKED("Parked"),
    PROTECTED("Protected"),
    PHOTOS("Photos"),
}

enum class ClipCategory(val label: String) {
    LOOP("LOOP"),
    PARKED("PARKED"),
    PROTECTED("PROTECTED"),
    PHOTO("PHOTO"),
}

enum class CameraChannel { FRONT, REAR }

data class LiveStreamLinks(
    val movieUrl: String?,
    val photoUrl: String?,
)

data class MediaAsset(
    val id: String,
    val name: String,
    val remotePath: String,
    val url: String,
    val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    val durationSeconds: Int? = null,
)

data class MediaClip(
    val id: String,
    val displayTime: String,
    val startEpochSeconds: Long,
    val durationSeconds: Int?,
    val resolutionLabel: String?,
    val category: ClipCategory,
    val front: MediaAsset?,
    val rear: MediaAsset?,
    val isProtected: Boolean,
) {
    val isPhoto: Boolean get() = category == ClipCategory.PHOTO
    val hasBothChannels: Boolean get() = front != null && rear != null
}

data class GalleryUiState(
    val filter: GalleryFilter = GalleryFilter.ALL,
    val clips: List<MediaClip> = emptyList(),
    val counts: Map<GalleryFilter, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val truncated: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val transfer: TransferState? = null,
    val preview: PreviewState? = null,
) {
    val visibleClips: List<MediaClip>
        get() = when (filter) {
            GalleryFilter.ALL -> clips
            GalleryFilter.LOOP -> clips.filter { it.category == ClipCategory.LOOP }
            GalleryFilter.PARKED -> clips.filter { it.category == ClipCategory.PARKED }
            GalleryFilter.PROTECTED -> clips.filter { it.category == ClipCategory.PROTECTED }
            GalleryFilter.PHOTOS -> clips.filter { it.category == ClipCategory.PHOTO }
        }
}

sealed interface TransferState {
    data class Preparing(val totalFiles: Int) : TransferState
    data class Downloading(
        val completedFiles: Int,
        val totalFiles: Int,
        val currentFile: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
    ) : TransferState
    data class Deleting(val completed: Int, val total: Int) : TransferState
    data class Complete(val files: Int) : TransferState
    data class Failed(val message: String) : TransferState
}

data class PreviewState(
    val clipId: String,
    val activeChannel: CameraChannel = CameraChannel.FRONT,
    val isPlaying: Boolean = true,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPiPVisible: Boolean = false,
)

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

enum class WifiBand(val label: String, val cameraValue: Int) {
    GHZ_2_4("2.4 GHz", 1),
    GHZ_5_8("5.8 GHz", 2),
}

enum class VideoResolution(val label: String, val cameraValue: Int) {
    UHD_2160_1080("2K Front + 1080p Rear 30fps", 20),
    QHD_1440_1080("1440p Front + 1080p Rear 30fps", 22),
    FHD_1080_1080("1080p Front + 1080p Rear 30fps", 21),
}

enum class LoopInterval(val label: String, val cameraValue: Int) {
    MIN_1("1 min", 1),
    MIN_3("3 min", 2),
    MIN_5("5 min", 3),
    MIN_10("10 min", 4),
}

enum class GSensorLevel(val label: String, val cameraValue: Int) {
    OFF("Off", 0),
    LOW("Low", 3),
    MID("Mid", 6),
    HIGH("High", 9),
}

data class WatermarkOptions(
    val timestamp: Boolean = true,
    val gpsSpeed: Boolean = true,
    val gSensor: Boolean = false,
    val customText: String? = null,
) {
    val summary: String
        get() = buildList {
            if (timestamp) add("Timestamp")
            if (gpsSpeed) add("GPS + Speed")
            if (gSensor) add("G-Sensor")
            customText?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.ifEmpty { listOf("Off") }.joinToString(" + ")
}

data class CameraSettingsValues(
    val wifiBand: WifiBand = WifiBand.GHZ_5_8,
    val autoReconnect: Boolean = true,
    val videoResolution: VideoResolution = VideoResolution.UHD_2160_1080,
    val loop: LoopInterval = LoopInterval.MIN_3,
    val watermark: WatermarkOptions = WatermarkOptions(),
    val gSensor: GSensorLevel = GSensorLevel.HIGH,
    val parkingMode: Boolean = true,
)

sealed interface SettingsAccess {
    data object Online : SettingsAccess
    data class OfflineCached(val cachedAtMillis: Long?) : SettingsAccess
    data class ReadOnly(val reason: String) : SettingsAccess
}

sealed interface ApplyState {
    data object Idle : ApplyState
    data class Applying(val step: ApplyStep) : ApplyState
    data object Success : ApplyState
    data class Failed(val message: String, val retryable: Boolean = true) : ApplyState
}

enum class ApplyStep(val label: String) {
    STOPPING_RECORDING("Pausing recording..."),
    WRITING_SETTINGS("Writing camera settings..."),
    SAVING_SETTINGS("Saving settings..."),
    RESTARTING_RECORDING("Restarting recording..."),
    FORMATTING_SD("Formatting SD card..."),
    DONE("Done"),
}

data class DiagnosticResult(
    val label: String,
    val value: String,
    val ok: Boolean,
)

sealed interface DiagnosticsState {
    data object Idle : DiagnosticsState
    data object Running : DiagnosticsState
    data class Complete(val results: List<DiagnosticResult>) : DiagnosticsState
    data class Failed(val message: String) : DiagnosticsState
}

data class CameraSettingsState(
    val access: SettingsAccess = SettingsAccess.OfflineCached(cachedAtMillis = null),
    val applied: CameraSettingsValues = CameraSettingsValues(),
    val draft: CameraSettingsValues = CameraSettingsValues(),
    val storageUsedBytes: Long? = null,
    val storageTotalBytes: Long? = null,
    val storageFreeBytes: Long? = null,
    val firmwareVersion: String = "—",
    val firmwareIsLatest: Boolean = false,
    val cachedAtMillis: Long? = null,
    val apply: ApplyState = ApplyState.Idle,
    val diagnostics: DiagnosticsState = DiagnosticsState.Idle,
    val error: String? = null,
) {
    val isDirty: Boolean get() = applied != draft
    val isOnline: Boolean get() = access == SettingsAccess.Online
}

// ---------------------------------------------------------------------------
// Dashboard / UI
// ---------------------------------------------------------------------------

data class QuickActionsState(
    val galleryEnabled: Boolean = false,
    val lockEventEnabled: Boolean = false,
    val snapshotEnabled: Boolean = false,
    val pauseEnabled: Boolean = false,
    val lockEventInFlight: Boolean = false,
    val snapshotInFlight: Boolean = false,
)

data class HandshakeUiState(
    val step: ConnectionStep,
    val showCancel: Boolean,
)

sealed interface Banner {
    data class Info(val message: String) : Banner
    data class Error(val message: String) : Banner
    data class RecordingPaused(val message: String) : Banner
}

data class DashboardUiState(
    val connection: CameraConnectionState = CameraConnectionState.Disconnected,
    val telemetry: TelemetryState = TelemetryState(),
    val activeChannel: CameraChannel = CameraChannel.FRONT,
    val previewPaused: Boolean = false,
    val quickActions: QuickActionsState = QuickActionsState(),
    val handshake: HandshakeUiState? = null,
    val banner: Banner? = null,
    val shutterFlashKey: Int = 0,
    val streamUrl: String? = null,
) {
    val isConnected: Boolean get() = connection is CameraConnectionState.Connected
    val isConnecting: Boolean get() = connection is CameraConnectionState.Connecting
}
