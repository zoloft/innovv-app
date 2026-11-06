package com.zoloft.innovv.app.data.sdk

import com.zoloft.innovv.app.data.debug.DebugLogManager
import com.zoloft.innovv.app.domain.CameraController
import com.zoloft.innovv.app.domain.isInnovvSsid
import com.zoloft.innovv.app.domain.model.BatteryState
import com.zoloft.innovv.app.domain.model.CameraChannel
import com.zoloft.innovv.app.domain.model.CameraConnectionState
import com.zoloft.innovv.app.domain.model.CameraFailure
import com.zoloft.innovv.app.domain.model.ConnectionStep
import com.zoloft.innovv.app.domain.model.GForceState
import com.zoloft.innovv.app.domain.model.GpsState
import com.zoloft.innovv.app.domain.model.LiveStreamLinks
import com.zoloft.innovv.app.domain.model.NetworkSelectionReason
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Real camera session built on the INNOVV KMP SDK.
 *
 * One session owns exactly one [InnovvClient] and one event-stream collector.
 * Repositories share this session for control, media and settings operations.
 */
class SdkCameraSession(
    private val binder: CameraNetworkBinder = createCameraNetworkBinder(),
    private val config: InnovvConfig = InnovvConfig(),
    private val debugLog: DebugLogManager? = null,
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
    private var watchdogJob: Job? = null
    private var lastFrameAtMillis: Long = 0L
    private var recordingStartedAt: Long? = null

    override fun connect() {
        debugLog?.logInfo("Camera discovery requested")
        val startedAt = Clock.System.now().toEpochMilliseconds()
        launchConnection(
            initial = CameraConnectionState.Connecting(
                ssid = "",
                step = ConnectionStep.DISCOVERING,
                startedAtMillis = startedAt,
                canCancel = false,
            ),
        ) {
            discoverAndConnect(startedAt)
        }
    }

    override fun connectTo(ssid: String, passphrase: String?) {
        debugLog?.logInfo("Connecting to selected network $ssid")
        val startedAt = Clock.System.now().toEpochMilliseconds()
        launchConnection(
            initial = CameraConnectionState.Connecting(
                ssid = ssid,
                step = ConnectionStep.JOINING_WIFI,
                startedAtMillis = startedAt,
                canCancel = false,
            ),
        ) {
            joinAndHandshake(
                ssid = ssid,
                passphrase = passphrase,
                startedAt = startedAt,
                alreadyBound = false,
            )
        }
    }

    private fun launchConnection(
        initial: CameraConnectionState.Connecting,
        block: suspend () -> Unit,
    ) {
        connectJob?.cancel()
        connectJob = scope.launch {
            var cancelFlagJob: Job? = null
            try {
                _connection.value = initial
                cancelFlagJob = launch {
                    delay(3_000.milliseconds)
                    val current = _connection.value
                    if (current is CameraConnectionState.Connecting) {
                        _connection.value = current.copy(canCancel = true)
                    }
                }
                block()
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

    private suspend fun discoverAndConnect(startedAt: Long) {
        val currentSsid = runCatching { binder.currentSsid() }
            .getOrNull()
            ?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
        debugLog?.logInfo("Discovery current Wi-Fi: ${currentSsid ?: "unknown"}")
        val binding = runCatching { binder.bind() }.getOrNull()
        if (binding is NetworkBindingResult.Success) {
            updateConnecting(
                startedAt = startedAt,
                ssid = currentSsid ?: "INNOVV camera",
                step = ConnectionStep.VERIFYING_NETWORK,
                canCancel = true,
            )
            val baseUrl = resolveBaseUrl()
            if (probeCamera(baseUrl)) {
                debugLog?.logInfo("INNOVV heartbeat confirmed on current network")
                joinAndHandshake(
                    ssid = currentSsid ?: "INNOVV camera",
                    passphrase = null,
                    startedAt = startedAt,
                    alreadyBound = true,
                    baseUrl = baseUrl,
                )
                return
            }
        }

        val networks = runCatching { binder.scanNetworks() }.getOrElse { emptyList() }
        val innovvNetworks = networks.filter { isInnovvSsid(it.ssid) }
        debugLog?.logInfo(
            "Discovery scan: ${networks.size} network(s), ${innovvNetworks.size} INNOVV candidate(s)",
        )
        when {
            innovvNetworks.size == 1 -> {
                val selected = innovvNetworks.single()
                debugLog?.logInfo("Selected INNOVV network ${selected.ssid}")
                joinAndHandshake(
                    ssid = selected.ssid,
                    passphrase = null,
                    startedAt = startedAt,
                    alreadyBound = currentSsid == selected.ssid,
                )
            }
            innovvNetworks.size > 1 -> {
                debugLog?.logInfo("Multiple INNOVV networks found; asking user to select")
                _connection.value = CameraConnectionState.NeedsNetworkSelection(
                    networks = innovvNetworks,
                    reason = NetworkSelectionReason.MULTIPLE_INNOVV_NETWORKS,
                    currentSsid = currentSsid,
                )
            }
            else -> {
                debugLog?.logInfo("No INNOVV network found automatically; asking user to select")
                _connection.value = CameraConnectionState.NeedsNetworkSelection(
                    networks = networks,
                    reason = if (networks.isEmpty()) {
                        NetworkSelectionReason.SCAN_EMPTY
                    } else {
                        NetworkSelectionReason.NO_INNOVV_NETWORK
                    },
                    currentSsid = currentSsid,
                )
            }
        }
    }

    private suspend fun probeCamera(baseUrl: String): Boolean = withTimeoutOrNull(4_000.milliseconds) {
        val probe = InnovvSdk.createClient(
            config.copy(
                baseUrl = baseUrl,
                connectTimeoutMillis = 1_200,
                requestTimeoutMillis = 2_500,
                socketTimeoutMillis = 2_500,
                maxRetries = 0,
                trafficLogger = debugLog,
            ),
        )
        try {
            probe.heartbeat()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        } finally {
            probe.close()
        }
    } ?: false

    private suspend fun joinAndHandshake(
        ssid: String,
        passphrase: String?,
        startedAt: Long,
        alreadyBound: Boolean,
        baseUrl: String? = null,
    ) {
        updateConnecting(
            startedAt = startedAt,
            ssid = ssid,
            step = if (alreadyBound) ConnectionStep.BINDING_NETWORK else ConnectionStep.JOINING_WIFI,
            canCancel = false,
        )

        if (!alreadyBound) {
            val current = runCatching { binder.currentSsid() }.getOrNull()
            val binding = if (current?.contains(ssid, ignoreCase = true) == true) {
                binder.bind()
            } else {
                binder.joinAndBind(ssid, passphrase)
            }
            if (binding is NetworkBindingResult.Failure) {
                fail(binding.reason.name.lowercase().replace('_', ' '))
                return
            }
        }

        updateConnecting(
            startedAt = startedAt,
            ssid = ssid,
            step = ConnectionStep.BINDING_NETWORK,
            canCancel = true,
        )

        val resolvedBaseUrl = baseUrl ?: resolveBaseUrl()
        val newClient = InnovvSdk.createClient(
            config.copy(
                baseUrl = resolvedBaseUrl,
                connectTimeoutMillis = 3_000,
                requestTimeoutMillis = 8_000,
                trafficLogger = debugLog,
            ),
        )
        client = newClient

        updateConnecting(
            startedAt = startedAt,
            ssid = ssid,
            step = ConnectionStep.HANDSHAKING,
            canCancel = true,
        )
        newClient.heartbeat()
        val info = newClient.getDeviceInfo()

        updateConnecting(
            startedAt = startedAt,
            ssid = ssid,
            step = ConnectionStep.BUFFERING_STREAM,
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
        debugLog?.logInfo("Connected to $ssid (${info.firmwareVersion})")
        startEventCollector(newClient)
        startTelemetryCollector(newClient)
        refreshTelemetryInternal(newClient)
        if (_liveLinks.value?.movieUrl != null) {
            startStreamWatchdog()
        }
    }

    private suspend fun resolveBaseUrl(): String {
        val gateway = runCatching { binder.currentGateway() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "192.168.1.254"
        debugLog?.logInfo("Camera endpoint: http://$gateway/")
        return "http://$gateway/"
    }

    private fun updateConnecting(
        startedAt: Long,
        ssid: String,
        step: ConnectionStep,
        canCancel: Boolean,
    ) {
        _connection.value = CameraConnectionState.Connecting(
            ssid = ssid,
            step = step,
            startedAtMillis = startedAt,
            canCancel = canCancel,
        )
    }

    override fun cancelConnect() {
        debugLog?.logInfo("Connection cancelled")
        connectJob?.cancel()
        connectJob = null
        cleanup()
        _connection.value = CameraConnectionState.Disconnected
    }

    override fun disconnect() {
        debugLog?.logInfo("Camera disconnected")
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
            delay(250.milliseconds)
            val links = runCatching { client.getLiveView() }.getOrNull()
            if (links != null) {
                _liveLinks.value = LiveStreamLinks(links.movieUrl, links.photoUrl)
                lastFrameAtMillis = Clock.System.now().toEpochMilliseconds()
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
            lastFrameAtMillis = Clock.System.now().toEpochMilliseconds()
        }
    }

    override suspend fun refreshTelemetry() {
        val current = client ?: return
        refreshTelemetryInternal(current)
    }

    override fun openWifiSettings() {
        binder.openSystemWifiSettings()
    }

    override fun onVideoFrame() {
        lastFrameAtMillis = Clock.System.now().toEpochMilliseconds()
    }

    override fun onVideoError(message: String) {
        _liveLinks.value = null
        _connection.value = CameraConnectionState.Disconnected
        cleanup()
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
                    debugLog?.logEvent("${event.commandId}/${event.status} ${event::class.simpleName}")
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
                delay(2_000.milliseconds)
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

    private fun startStreamWatchdog() {
        watchdogJob?.cancel()
        lastFrameAtMillis = Clock.System.now().toEpochMilliseconds()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(250.milliseconds)
                val connected = _connection.value as? CameraConnectionState.Connected ?: continue
                if (connected.stream != StreamState.LIVE) continue
                if (_liveLinks.value?.movieUrl == null) continue
                if (Clock.System.now().toEpochMilliseconds() - lastFrameAtMillis > 1_500) {
                    _liveLinks.value = null
                    _connection.value = CameraConnectionState.Disconnected
                    cleanup()
                    break
                }
            }
        }
    }

    private fun cleanup() {
        watchdogJob?.cancel()
        watchdogJob = null
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
