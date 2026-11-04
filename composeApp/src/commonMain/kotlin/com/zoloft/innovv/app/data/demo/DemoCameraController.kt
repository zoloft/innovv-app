package com.zoloft.innovv.app.data.demo

import com.zoloft.innovv.app.domain.CameraController
import com.zoloft.innovv.app.domain.model.BatteryState
import com.zoloft.innovv.app.domain.model.CameraChannel
import com.zoloft.innovv.app.domain.model.CameraConnectionState
import com.zoloft.innovv.app.domain.model.ConnectionStep
import com.zoloft.innovv.app.domain.model.GForceState
import com.zoloft.innovv.app.domain.model.GpsState
import com.zoloft.innovv.app.domain.model.LiveStreamLinks
import com.zoloft.innovv.app.domain.model.RecordingState
import com.zoloft.innovv.app.domain.model.StorageState
import com.zoloft.innovv.app.domain.model.StreamState
import com.zoloft.innovv.app.domain.model.TelemetryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.math.sin

/**
 * A deterministic camera simulator used for previews, UI development, tests,
 * and the debug-only demo mode. It mirrors the real session's state transitions
 * so the UI can be exercised without a camera on the network.
 */
class DemoCameraController(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : CameraController {
    private val _connection = MutableStateFlow<CameraConnectionState>(CameraConnectionState.Disconnected)
    override val connection: StateFlow<CameraConnectionState> = _connection.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetryState())
    override val telemetry: StateFlow<TelemetryState> = _telemetry.asStateFlow()

    private val _liveLinks = MutableStateFlow<LiveStreamLinks?>(null)
    override val liveLinks: StateFlow<LiveStreamLinks?> = _liveLinks.asStateFlow()

    private var connectJob: Job? = null
    private var telemetryJob: Job? = null
    private var recordingStartedAt: Long? = null

    override fun connect(ssid: String) {
        connectJob?.cancel()
        connectJob = scope.launch {
            val startedAt = Clock.System.now().toEpochMilliseconds()
            _connection.value = CameraConnectionState.Connecting(
                ssid = ssid,
                step = ConnectionStep.JOINING_WIFI,
                startedAtMillis = startedAt,
                canCancel = false,
            )
            delay(3000)
            updateConnecting { it.copy(canCancel = true) }
            delay(600)
            updateConnecting { it.copy(step = ConnectionStep.BINDING_NETWORK) }
            delay(700)
            updateConnecting { it.copy(step = ConnectionStep.HANDSHAKING) }
            delay(900)
            updateConnecting { it.copy(step = ConnectionStep.BUFFERING_STREAM) }
            delay(700)
            _connection.value = CameraConnectionState.Connected(
                ssid = ssid,
                firmwareVersion = "v1.2.4",
                stream = StreamState.LIVE,
            )
            startRecording()
            startTelemetry()
        }
    }

    override fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
        _connection.value = CameraConnectionState.Disconnected
        _telemetry.value = TelemetryState()
    }

    override fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        stopTelemetry()
        recordingStartedAt = null
        _connection.value = CameraConnectionState.Disconnected
        _telemetry.value = TelemetryState()
    }

    override suspend fun startRecording(): Boolean {
        if (_connection.value !is CameraConnectionState.Connected) return false
        val now = Clock.System.now().toEpochMilliseconds()
        recordingStartedAt = now
        _telemetry.update { it.copy(recording = RecordingState.Recording(now, 0)) }
        return true
    }

    override suspend fun stopRecording(): Boolean {
        if (_connection.value !is CameraConnectionState.Connected) return false
        recordingStartedAt = null
        _telemetry.update { it.copy(recording = RecordingState.Stopped) }
        return true
    }

    override suspend fun captureSnapshot(): Boolean {
        if (_connection.value !is CameraConnectionState.Connected) return false
        delay(450)
        return true
    }

    override suspend fun lockEvent(): Boolean {
        if (_connection.value !is CameraConnectionState.Connected) return false
        delay(350)
        return true
    }

    override suspend fun setChannel(channel: CameraChannel) = Unit

    override suspend fun pausePreview() {
        val current = _connection.value
        if (current is CameraConnectionState.Connected) {
            _connection.value = current.copy(stream = StreamState.PAUSED_BY_USER)
        }
    }

    override suspend fun resumePreview() {
        val current = _connection.value
        if (current is CameraConnectionState.Connected) {
            _connection.value = current.copy(stream = StreamState.LIVE)
        }
    }

    override suspend fun refreshTelemetry() = Unit

    override fun openWifiSettings() = Unit

    override fun onVideoFrame() = Unit

    override fun onVideoError(message: String) = Unit

    private fun startTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            var tick = 0
            while (true) {
                tick += 1
                val recording = recordingStartedAt?.let { started ->
                    RecordingState.Recording(
                        startedAtMillis = started,
                        elapsedSeconds = ((Clock.System.now().toEpochMilliseconds() - started) / 1000).toInt(),
                    )
                } ?: RecordingState.Stopped
                _telemetry.value = TelemetryState(
                    gps = GpsState.Locked(rateHz = 5, satellites = 12, snr = 38),
                    gForce = GForceState.Live(
                        x = (sin(tick / 2.0) * 0.12).toFloat(),
                        y = (sin(tick / 3.0) * 0.18).toFloat(),
                        z = (0.92 + sin(tick / 5.0) * 0.05).toFloat(),
                        magnitudeG = (0.85 + sin(tick / 4.0) * 0.08).toFloat(),
                    ),
                    storage = StorageState.Ready(
                        usedBytes = 64L * 1024 * 1024 * 1024,
                        totalBytes = 256L * 1024 * 1024 * 1024,
                        freeBytes = 192L * 1024 * 1024 * 1024,
                    ),
                    battery = BatteryState.Level(level = 1, charging = true),
                    speedKmh = 73.0 + sin(tick / 3.0) * 4.0,
                    recording = recording,
                    lastUpdatedAtMillis = Clock.System.now().toEpochMilliseconds(),
                )
                delay(1000)
            }
        }
    }

    private fun stopTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = null
    }

    private fun updateConnecting(transform: (CameraConnectionState.Connecting) -> CameraConnectionState.Connecting) {
        val current = _connection.value
        if (current is CameraConnectionState.Connecting) {
            _connection.value = transform(current)
        }
    }
}
