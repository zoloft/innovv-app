package com.zoloft.innovv.app.presentation.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoloft.innovv.app.domain.CameraController
import com.zoloft.innovv.app.domain.GalleryRepository
import com.zoloft.innovv.app.domain.model.CameraChannel
import com.zoloft.innovv.app.domain.model.ClipCategory
import com.zoloft.innovv.app.domain.model.GalleryFilter
import com.zoloft.innovv.app.domain.model.GalleryUiState
import com.zoloft.innovv.app.domain.model.MediaClip
import com.zoloft.innovv.app.domain.model.PreviewState
import com.zoloft.innovv.app.domain.model.TransferState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val camera: CameraController,
    private val repository: GalleryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(GalleryUiState(isLoading = true))
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            camera.pausePreview()
            _state.update { it.copy(isLoading = true, error = null) }
            repository.loadClips()
                .onSuccess { clips ->
                    _state.update {
                        it.copy(
                            clips = clips.sortedByDescending { clip -> clip.startEpochSeconds },
                            counts = countsOf(clips),
                            isLoading = false,
                            isRefreshing = false,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = failure.message ?: "Could not load clips",
                        )
                    }
                }
        }
    }

    fun onLeave() {
        viewModelScope.launch { camera.resumePreview() }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            repository.loadClips()
                .onSuccess { clips ->
                    _state.update {
                        it.copy(
                            clips = clips.sortedByDescending { clip -> clip.startEpochSeconds },
                            counts = countsOf(clips),
                            isRefreshing = false,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            isRefreshing = false,
                            error = failure.message ?: "Could not refresh clips",
                        )
                    }
                }
        }
    }

    fun selectFilter(filter: GalleryFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun toggleSelection(clipId: String) {
        _state.update { current ->
            val selected = if (clipId in current.selectedIds) {
                current.selectedIds - clipId
            } else {
                current.selectedIds + clipId
            }
            current.copy(
                selectionMode = selected.isNotEmpty(),
                selectedIds = selected,
            )
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectionMode = false, selectedIds = emptySet()) }
    }

    fun selectAll() {
        _state.update { current ->
            val ids = current.visibleClips.map { it.id }.toSet()
            current.copy(selectionMode = ids.isNotEmpty(), selectedIds = ids)
        }
    }

    fun openPreview(clipId: String) {
        val clip = _state.value.clips.firstOrNull { it.id == clipId } ?: return
        _state.update {
            it.copy(
                preview = PreviewState(
                    clipId = clipId,
                    activeChannel = CameraChannel.FRONT,
                    isPlaying = true,
                    durationMs = (clip.durationSeconds ?: 0) * 1000L,
                    isPiPVisible = clip.hasBothChannels,
                ),
            )
        }
    }

    fun closePreview() {
        _state.update { it.copy(preview = null) }
    }

    fun togglePreviewPlayback() {
        _state.update { current ->
            current.preview?.let { preview ->
                current.copy(preview = preview.copy(isPlaying = !preview.isPlaying))
            } ?: current
        }
    }

    fun setPreviewChannel(channel: CameraChannel) {
        _state.update { current ->
            current.preview?.let { preview ->
                current.copy(preview = preview.copy(activeChannel = channel))
            } ?: current
        }
    }

    fun togglePiP() {
        _state.update { current ->
            current.preview?.let { preview ->
                current.copy(preview = preview.copy(isPiPVisible = !preview.isPiPVisible))
            } ?: current
        }
    }

    fun seekPreview(positionMs: Long) {
        _state.update { current ->
            current.preview?.let { preview ->
                current.copy(preview = preview.copy(positionMs = positionMs.coerceIn(0L, preview.durationMs)))
            } ?: current
        }
    }

    fun downloadSelected() {
        val clips = selectedClips() ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(transfer = TransferState.Preparing(clips.size), selectionMode = false)
            }
            repository.download(clips) { completed, total, currentFile, bytes, totalBytes ->
                _state.update {
                    it.copy(
                        transfer = TransferState.Downloading(
                            completedFiles = completed,
                            totalFiles = total,
                            currentFile = currentFile,
                            bytesDownloaded = bytes,
                            totalBytes = totalBytes,
                            bytesPerSecond = 8L * 1024L * 1024L,
                        ),
                    )
                }
            }.onSuccess {
                _state.update {
                    it.copy(
                        transfer = TransferState.Complete(clips.size),
                        selectedIds = emptySet(),
                        selectionMode = false,
                    )
                }
                _messages.tryEmit("Downloaded ${clips.size} item(s)")
                delay(1_500)
                _state.update { current ->
                    if (current.transfer is TransferState.Complete) current.copy(transfer = null) else current
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(transfer = TransferState.Failed(failure.message ?: "Download failed"))
                }
            }
        }
    }

    fun deleteSelected() {
        val clips = selectedClips() ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(transfer = TransferState.Deleting(0, clips.size), selectionMode = false)
            }
            repository.delete(clips) { completed, total ->
                _state.update {
                    it.copy(transfer = TransferState.Deleting(completed, total))
                }
            }.onSuccess {
                val ids = clips.map { it.id }.toSet()
                _state.update { current ->
                    val remaining = current.clips.filterNot { it.id in ids }
                    current.copy(
                        clips = remaining,
                        counts = countsOf(remaining),
                        transfer = TransferState.Complete(clips.size),
                        selectedIds = emptySet(),
                        selectionMode = false,
                    )
                }
                _messages.tryEmit("Deleted ${clips.size} item(s) from the camera")
                delay(1_500)
                _state.update { current ->
                    if (current.transfer is TransferState.Complete) current.copy(transfer = null) else current
                }
            }.onFailure { failure ->
                _state.update {
                    it.copy(transfer = TransferState.Failed(failure.message ?: "Delete failed"))
                }
            }
        }
    }

    fun dismissTransfer() {
        _state.update { it.copy(transfer = null) }
    }

    private fun selectedClips(): List<MediaClip>? {
        val current = _state.value
        if (current.selectedIds.isEmpty()) return null
        val selected = current.clips.filter { it.id in current.selectedIds }
        return if (selected.isEmpty()) null else selected
    }

    private fun countsOf(clips: List<MediaClip>): Map<GalleryFilter, Int> = mapOf(
        GalleryFilter.ALL to clips.size,
        GalleryFilter.LOOP to clips.count { it.category == ClipCategory.LOOP },
        GalleryFilter.PARKED to clips.count { it.category == ClipCategory.PARKED },
        GalleryFilter.PROTECTED to clips.count { it.category == ClipCategory.PROTECTED },
        GalleryFilter.PHOTOS to clips.count { it.category == ClipCategory.PHOTO },
    )
}
