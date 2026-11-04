package com.zoloft.innovv.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoloft.innovv.app.domain.CameraController
import com.zoloft.innovv.app.data.settings.SettingsCache
import com.zoloft.innovv.app.domain.SettingsRepository
import com.zoloft.innovv.app.domain.model.ApplyState
import com.zoloft.innovv.app.domain.model.ApplyStep
import com.zoloft.innovv.app.domain.model.CameraConnectionState
import com.zoloft.innovv.app.domain.model.CameraSettingsState
import com.zoloft.innovv.app.domain.model.CameraSettingsValues
import com.zoloft.innovv.app.domain.model.DiagnosticsState
import com.zoloft.innovv.app.domain.model.SettingsAccess
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data object Close : SettingsEvent
    data class Message(val text: String) : SettingsEvent
}

class SettingsViewModel(
    private val camera: CameraController,
    private val repository: SettingsRepository,
    private val cache: SettingsCache = SettingsCache(),
) : ViewModel() {
    private val _state = MutableStateFlow(CameraSettingsState())
    val state: StateFlow<CameraSettingsState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    fun load() {
        viewModelScope.launch {
            val connected = camera.connection.value is CameraConnectionState.Connected
            if (!connected) {
                val cached = cache.load()
                _state.value = (cached ?: _state.value).copy(
                    access = SettingsAccess.OfflineCached(cached?.cachedAtMillis),
                    apply = ApplyState.Idle,
                )
                return@launch
            }
            repository.load()
                .onSuccess { loaded ->
                    val online = loaded.copy(
                        access = SettingsAccess.Online,
                        apply = ApplyState.Idle,
                        diagnostics = DiagnosticsState.Idle,
                    )
                    _state.value = online
                    cache.save(online)
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            access = SettingsAccess.OfflineCached(it.cachedAtMillis),
                            error = failure.message ?: "Could not load settings",
                        )
                    }
                }
        }
    }

    fun updateDraft(transform: (CameraSettingsValues) -> CameraSettingsValues) {
        _state.update { it.copy(draft = transform(it.draft), error = null) }
    }

    fun discard() {
        _state.update {
            it.copy(
                draft = it.applied,
                apply = ApplyState.Idle,
                error = null,
            )
        }
    }

    fun apply() {
        val current = _state.value
        if (!current.isDirty || current.apply is ApplyState.Applying) return
        viewModelScope.launch {
            if (!current.isOnline) {
                val local = current.applied.copy(
                    wifiBand = current.draft.wifiBand,
                    autoReconnect = current.draft.autoReconnect,
                )
                _state.update {
                    it.copy(
                        applied = local,
                        draft = local,
                        apply = ApplyState.Success,
                    )
                }
                _events.tryEmit(
                    SettingsEvent.Message(
                        "Preferences saved. Hardware settings require a camera connection.",
                    ),
                )
                _events.tryEmit(SettingsEvent.Close)
                return@launch
            }

            _state.update { it.copy(apply = ApplyState.Applying(ApplyStep.STOPPING_RECORDING)) }
            repository.apply(current.draft) { label ->
                val step = ApplyStep.entries.firstOrNull { it.label == label } ?: ApplyStep.WRITING_SETTINGS
                _state.update { it.copy(apply = ApplyState.Applying(step)) }
            }.onSuccess {
                _state.update {
                    it.copy(
                        applied = it.draft,
                        apply = ApplyState.Success,
                        cachedAtMillis = it.cachedAtMillis,
                    )
                }
                cache.save(_state.value)
                _events.tryEmit(SettingsEvent.Message("Settings applied"))
                _events.tryEmit(SettingsEvent.Close)
            }.onFailure { failure ->
                _state.update {
                    it.copy(
                        apply = ApplyState.Failed(
                            failure.message ?: "Could not apply settings",
                        ),
                    )
                }
            }
        }
    }

    fun formatSdCard() {
        val current = _state.value
        if (!current.isOnline || current.apply is ApplyState.Applying) return
        viewModelScope.launch {
            _state.update { it.copy(apply = ApplyState.Applying(ApplyStep.FORMATTING_SD)) }
            repository.formatSdCard { label ->
                val step = ApplyStep.entries.firstOrNull { it.label == label } ?: ApplyStep.FORMATTING_SD
                _state.update { it.copy(apply = ApplyState.Applying(step)) }
            }.onSuccess {
                _state.update {
                    it.copy(
                        storageUsedBytes = 0L,
                        apply = ApplyState.Success,
                    )
                }
                cache.save(_state.value)
                _events.tryEmit(SettingsEvent.Message("SD card formatted"))
            }.onFailure { failure ->
                _state.update {
                    it.copy(apply = ApplyState.Failed(failure.message ?: "Format failed"))
                }
            }
        }
    }

    fun runDiagnostics() {
        if (_state.value.diagnostics is DiagnosticsState.Running) return
        viewModelScope.launch {
            _state.update { it.copy(diagnostics = DiagnosticsState.Running) }
            repository.runDiagnostics()
                .onSuccess { results ->
                    _state.update { it.copy(diagnostics = DiagnosticsState.Complete(results)) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(diagnostics = DiagnosticsState.Failed(failure.message ?: "Diagnostics failed"))
                    }
                }
        }
    }

    fun dismissApplyState() {
        _state.update { it.copy(apply = ApplyState.Idle) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
}
