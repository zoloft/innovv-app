package com.zoloft.innovv.app.data.sdk

import com.zoloft.innovv.app.domain.CameraController
import com.zoloft.innovv.app.domain.model.BatteryState
import com.zoloft.innovv.app.domain.model.CameraChannel
import com.zoloft.innovv.app.domain.model.CameraConnectionState
import com.zoloft.innovv.app.domain.model.CameraFailure
import com.zoloft.innovv.app.domain.model.ConnectionStep
import com.zoloft.innovv.app.domain.model.GForceState
import com.zoloft.innovv.app.domain.model.GpsState
import com.zoloft.innovv.app.domain.model.LiveStreamLinks
import com.zoloft.innovv.app.domain.model.RecordingState
import com.zoloft.innovv.app.domain.model.StorageState
import com.zoloft.innovv.app.domain.model.StreamState
import com.zoloft.innovv.app.domain.model.TelemetryState
import com.zoloft.innovv.app.platform.CameraNetworkBinder
import com.zoloft.innovv.app.platform.CameraNetworkState
import com.zoloft.innovv.app.platform.NetworkBindingResult
import com.zoloft.innovv.app.platform.createCameraNetworkBinder
import com.zoloft.innovv.sdk.InnovvClient
import com.zoloft.innovv.sdk.InnovvConfig
import com.zoloft.innovv.sdk.InnovvException
import com.zoloft.innovv.sdk.InnovvHttpException
import com.zoloft.innovv.sdk.InnovvNetworkException
import com.zoloft.innovv.sdk.InnovvSdk
import com.zoloft.innovv.sdk.model.CameraEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Real camera session built on the INNOVV KMP SDK.
 *
 * One session owns exactly one [InnovvClient] and one event-stream collector.
 * Repositories share this session for control, media and settings operations.
 */
class SdkCameraSession(
    private val binder: CameraNetworkBinder = createCameraNetworkBinder(),
    private val config: InnovvConfig = InnovvConfig(),
) : CameraController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val writeMutex = Mutex()

    private val _connection = MutableStateFlow<CameraConnectionState>(CameraConnectionState.Disconnected)
    override val connection: StateFlow<CameraConnectionState> = _connection.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetryState())
    override val telemetry: StateFlow<TelemetryState> = _telemetry.asStateFlow()

    private val _liveLinks = MutableStateFlow<LiveStreamLinks?>(null)
    override val liveLinks: StateFlow<LiveStreamLinks?> = _liveLinks.asStateFlow()

    private var client: InnovvClient? = null
    private var connectJob: Job? = null
    private var telemetryJob: Job? = null
    private var eventJob: Job? = null
    private var recordingStartedAt: Long? = null

    override fun connect(ssid: String) {
        connectJob?.cancel()
        connectJob = scope.launch {
            var cancelFlagJob: Job? = null
            try {
                val startedAt = Clock.System.now().toEpochMilliseconds()
                _connection.value = CameraConnectionState.Connecting(
                    ssid = ssid,
                    step = ConnectionStep.JOINING_WIFI,
                    startedAtMillis = startedAt,
                    canCancel = false,
                )
                cancelFlagJob = launch {
                    delay(3_000)
                    val current = _connection.value
                    if (current is CameraConnectionState.Connecting) {
                        _connection.value = current.copy(canCancel = true)
                    }
                }

                val currentSsid = binder.currentSsid()
                val binding = if (currentSsid?.contains(ssid, ignoreCase = true) == true) {
                    binder.bind()
                } else {
                    binder.joinAndBind(ssid)
                }
                if (binding is NetworkBindingResult.Failure) {
                    fail(binding.reason.name.lowercase().replace('_', ' '))
                    return@launch
                }

                _connection.value = CameraConnectionState.Connecting(
                    ssid = ssid,
                    step = ConnectionStep.BINDING_NETWORK,
                    startedAtMillis = startedAt,
                    canCancel = false,
                )

                val newClient = InnovvSdk.createClient(
                    config.copy(
                        baseUrl = "http://192.168.1.254/",
                        connectTimeoutMillis = 3_000,
                        requestTimeoutMillis = 8_000,
                    ),
                )
                client = newClient

                _connection.value = CameraConnectionState.Connecting(
                    ssid = ssid,
                    step = ConnectionStep.HANDSHAKING,
                    startedAtMillis = startedAt,
                    canCancel = true,
                )
                newClient.heartbeat()
                val info = newClient.getDeviceInfo()

                _connection.value = CameraConnectionState.Connecting(
                    ssid = ssid,
                    step = ConnectionStep.BUFFERING_STREAM,
                    startedAtMillis = startedAt,
                    canCancel = true,
                )
                val links = runCatching { newClient.getLiveView() }.getOrNull()
                _liveLinks.value = links?.let {
                    LiveStreamLinks(movieUrl = it.movieUrl, photoUrl = it.photoUrl)
                }

                _connection.value = CameraConnectionState.Connected(
                    ssid = ssid,
                    firmwareVersion = info.firmwareVersion,
                    stream = StreamState.LIVE,
                )
                startEventCollector(newClient)
                startTelemetryCollector(newClient)
                refreshTelemetryInternal(newClient)
            } catch (cancelled: CancellationException) {
                cleanup()
                _connection.value = CameraConnectionState.Disconnected
                throw cancelled
            } catch (failure: Throwable) {
                cleanup()
                _connection.value = CameraConnectionState.Failed(failure.toCameraFailure())
            } finally {
                cancelFlagJob?.cancel()
            }
        }
    }

    override fun cancelConnect() {
        connectJob?.cancel()
        connectJob = null
        cleanup()
        _connection.value = CameraConnectionState.Disconnected
    }

    override fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        cleanup()
        _connection.value = CameraConnectionState.Disconnected
        _telemetry.value = TelemetryState()
    }

    override suspend fun startRecording(): Boolean = write { client ->
        client.startRecording()
        val now = Clock.System.now().toEpochMilliseconds()
        recordingStartedAt = now
        _telemetry.update { it.copy(recording = RecordingState.Recording(now, 0)) }
        true
    } ?: false

    override suspend fun stopRecording(): Boolean = write { client ->
        client.stopRecording()
        recordingStartedAt = null
        _telemetry.update { it.copy(recording = RecordingState.Stopped) }
        true
    } ?: false

    override suspend fun captureSnapshot(): Boolean = write { client ->
        client.capturePhoto()
        true
    } ?: false

    override suspend fun lockEvent(): Boolean = false

    override suspend fun setChannel(channel: CameraChannel) {
        write { client ->
            client.setLiveViewSource(if (channel == CameraChannel.FRONT) 0 else 1)
            delay(250)
            val links = runCatching { client.getLiveView() }.getOrNull()
            if (links != null) {
                _liveLinks.value = LiveStreamLinks(links.movieUrl, links.photoUrl)
            }
        }
    }

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

    override suspend fun refreshTelemetry() {
        val current = client ?: return
        refreshTelemetryInternal(current)
    }

    override fun openWifiSettings() {
        binder.openSystemWifiSettings()
    }

    suspend fun <T> useClient(block: suspend (InnovvClient) -> T): Result<T> {
        val current = client ?: return Result.failure(IllegalStateException("Camera is not connected"))
        return runCatching { block(current) }
    }

    suspend fun <T> writeClient(block: suspend (InnovvClient) -> T): Result<T> =
        writeMutex.withLock {
            val current = client ?: return@withLock Result.failure(IllegalStateException("Camera is not connected"))
            runCatching { block(current) }
        }

    fun isRecording(): Boolean = recordingStartedAt != null

    private suspend fun <T> write(block: suspend (InnovvClient) -> T): T? =
        writeClient(block).getOrNull()

    private fun startEventCollector(innovvClient: InnovvClient) {
        eventJob?.cancel()
        eventJob = scope.launch {
            runCatching {
                innovvClient.events().collect { event ->
                    when (event) {
                        CameraEvent.RecordStarted -> {
                            val now = Clock.System.now().toEpochMilliseconds()
                            recordingStartedAt = now
                            _telemetry.update { it.copy(recording = RecordingState.Recording(now, 0)) }
                        }
                        CameraEvent.RecordStopped -> {
                            recordingStartedAt = null
                            _telemetry.update { it.copy(recording = RecordingState.Stopped) }
                        }
                        is CameraEvent.RecordingTime -> {
                            val now = Clock.System.now().toEpochMilliseconds()
                            recordingStartedAt = now - event.seconds * 1000L
                            _telemetry.update {
                                it.copy(
                                    recording = RecordingState.Recording(
                                        startedAtMillis = recordingStartedAt ?: now,
                                        elapsedSeconds = event.seconds,
                                    ),
                                )
                            }
                        }
                        CameraEvent.CardRemoved -> _telemetry.update {
                            it.copy(storage = StorageState.NoCard)
                        }
                        CameraEvent.CardInserted -> refreshTelemetry()
                        is CameraEvent.BatteryChanged -> _telemetry.update {
                            it.copy(battery = BatteryState.Level(event.level, event.level == 5))
                        }
                        is CameraEvent.GpsChanged -> Unit
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun startTelemetryCollector(innovvClient: InnovvClient) {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            while (isActive) {
                refreshTelemetryInternal(innovvClient)
                delay(2_000)
            }
        }
    }

    private suspend fun refreshTelemetryInternal(innovvClient: InnovvClient) {
        val gpsReady = runCatching { innovvClient.isGpsReady() }.getOrDefault(false)
        val gpsInfo = runCatching { innovvClient.getGpsInfo() }.getOrNull()
        val sdState = runCatching { innovvClient.getSdCardState() }.getOrNull()
        val freeBytes = runCatching { innovvClient.getFreeSpaceBytes() }.getOrNull()
        val battery = runCatching { innovvClient.getBattery() }.getOrNull()

        val gps = when {
            gpsInfo?.hasFix == true -> GpsState.Locked(
                rateHz = 5,
                satellites = gpsInfo.satellites,
                snr = gpsInfo.snr,
            )
            gpsReady -> GpsState.Searching
            else -> GpsState.Searching
        }
        val storage = when {
            sdState == null -> StorageState.Unknown
            sdState.code == 0 -> StorageState.NoCard
            sdState.code == 2 -> StorageState.CardError
            freeBytes != null -> StorageState.Ready(usedBytes = null, totalBytes = null, freeBytes = freeBytes)
            else -> StorageState.Unknown
        }
        val recording = recordingStartedAt?.let { started ->
            RecordingState.Recording(
                startedAtMillis = started,
                elapsedSeconds = ((Clock.System.now().toEpochMilliseconds() - started) / 1000).toInt().coerceAtLeast(0),
            )
        } ?: _telemetry.value.recording

        _telemetry.value = _telemetry.value.copy(
            gps = gps,
            gForce = GForceState.Unavailable,
            storage = storage,
            battery = battery?.let { BatteryState.Level(it.level, it.isCharging) } ?: BatteryState.Unknown,
            speedKmh = gpsInfo?.speed,
            recording = recording,
            lastUpdatedAtMillis = Clock.System.now().toEpochMilliseconds(),
        )
    }

    private fun cleanup() {
        telemetryJob?.cancel()
        telemetryJob = null
        eventJob?.cancel()
        eventJob = null
        _liveLinks.value = null
        runCatching { client?.close() }
        client = null
        recordingStartedAt = null
        runCatching { binder.unbind() }
    }

    private fun fail(reason: String) {
        cleanup()
        _connection.value = CameraConnectionState.Failed(CameraFailure.Unknown(reason))
    }

    private fun Throwable.toCameraFailure(): CameraFailure = when (this) {
        is InnovvNetworkException -> CameraFailure.CameraUnreachable
        is InnovvHttpException -> CameraFailure.Unknown("Camera HTTP error ${statusCode}")
        is com.zoloft.innovv.sdk.InnovvCommandException -> CameraFailure.CameraCommand(status)
        is InnovvException -> CameraFailure.Unknown(message ?: "Camera protocol error")
        else -> CameraFailure.Unknown(message ?: "Camera connection failed")
    }
}
