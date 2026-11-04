package com.zoloft.innovv.app.data.debug

import com.zoloft.innovv.app.domain.model.DebugLogUiState
import com.russhwolf.settings.Settings
import com.zoloft.innovv.app.platform.appStorageDirectory
import com.zoloft.innovv.sdk.InnovvTrafficLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.time.Clock

/**
 * Debug-only traffic logger. Requests and responses are queued and written to
 * an app-private file; the UI can inspect or clear the recent tail.
 *
 * The logger is always installed in the SDK so toggling it in Settings takes
 * effect immediately. In production builds it starts disabled and writes
 * nothing.
 */
class DebugLogManager(
    private val isDebugBuild: Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : InnovvTrafficLogger {
    private val logFile = appStorageDirectory().toPath() / "debug" / "traffic.log"
    private val preferences = Settings()
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val recentLines = ArrayDeque<String>()

    private val _state = MutableStateFlow(
        DebugLogUiState(
            enabled = isDebugBuild && (preferences.getBooleanOrNull(PREF_ENABLED) ?: true),
            text = "",
            isDebugBuild = isDebugBuild,
        ),
    )
    val state: StateFlow<DebugLogUiState> = _state.asStateFlow()

    init {
        scope.launch {
            loadRecent().lineSequence()
                .filter { it.isNotBlank() }
                .forEach { recentLines.addLast(it) }
            while (recentLines.size > MAX_RECENT_LINES) recentLines.removeFirst()
            _state.update { it.copy(text = recentLines.joinToString("\n")) }
            FileSystem.SYSTEM.createDirectories(logFile.parent ?: ".".toPath())
            for (line in queue) {
                if (!_state.value.enabled) continue
                append(line)
                recentLines.addLast(line)
                while (recentLines.size > MAX_RECENT_LINES) recentLines.removeFirst()
                _state.update { it.copy(text = recentLines.joinToString("\n")) }
            }
        }
        if (isDebugBuild) {
            logInfo("Debug traffic logging started")
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (!isDebugBuild) return
        if (enabled && !_state.value.enabled) {
            logInfo("Debug traffic logging enabled")
        }
        if (!enabled && _state.value.enabled) {
            logInfo("Debug traffic logging disabled")
        }
        preferences.putBoolean(PREF_ENABLED, enabled)
        _state.update { it.copy(enabled = enabled) }
    }

    fun clear() {
        recentLines.clear()
        _state.update { it.copy(text = "") }
        scope.launch {
            runCatching { FileSystem.SYSTEM.delete(logFile, mustExist = false) }
        }
    }

    fun logInfo(message: String) {
        emit("• ${timestamp()} $message")
    }

    fun logEvent(event: String) {
        emit("• ${timestamp()} EVENT $event")
    }

    override fun logRequest(method: String, url: String) {
        emit("→ ${timestamp()} $method $url")
    }

    override fun logResponse(method: String, url: String, statusCode: Int, body: String) {
        emit("← ${timestamp()} $statusCode $method $url\n$body")
    }

    override fun logStream(method: String, url: String, statusCode: Int, bytes: Long) {
        emit("← ${timestamp()} $statusCode $method $url ($bytes bytes streamed)")
    }

    override fun logError(method: String, url: String, message: String, throwable: Throwable?) {
        val detail = throwable?.stackTraceToString()?.take(MAX_ERROR_DETAIL) ?: ""
        emit("!! ${timestamp()} $method $url\n$message\n$detail".trimEnd())
    }

    private fun emit(line: String) {
        if (!_state.value.enabled) return
        queue.trySend(line)
    }

    private fun append(line: String) {
        runCatching {
            val dir = logFile.parent ?: return
            FileSystem.SYSTEM.createDirectories(dir)
            val size = FileSystem.SYSTEM.metadataOrNull(logFile)?.size ?: 0L
            if (size > MAX_FILE_BYTES) {
                FileSystem.SYSTEM.delete(logFile, mustExist = false)
            }
            val sink = FileSystem.SYSTEM.appendingSink(logFile).buffer()
            try {
                sink.writeUtf8(line)
                sink.writeUtf8("\n")
            } finally {
                sink.close()
            }
        }
    }

    private fun loadRecent(): String = runCatching {
        if (!FileSystem.SYSTEM.exists(logFile)) return ""
        val source = FileSystem.SYSTEM.source(logFile).buffer()
        val text = try {
            source.readUtf8()
        } finally {
            source.close()
        }
        text.lineSequence().toList().takeLast(MAX_RECENT_LINES).joinToString("\n")
    }.getOrDefault("")

    private fun timestamp(): String = Clock.System.now().toString()

    private companion object {
        const val PREF_ENABLED = "debug_traffic_logging_enabled"
        const val MAX_RECENT_LINES = 200
        const val MAX_FILE_BYTES = 2L * 1024L * 1024L
        const val MAX_ERROR_DETAIL = 4_000
    }
}
