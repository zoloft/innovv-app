package com.zoloft.innovv.app.data.demo

import com.zoloft.innovv.app.domain.GalleryRepository
import com.zoloft.innovv.app.domain.SettingsRepository
import com.zoloft.innovv.app.domain.model.ApplyStep
import com.zoloft.innovv.app.domain.model.CameraSettingsState
import com.zoloft.innovv.app.domain.model.CameraSettingsValues
import com.zoloft.innovv.app.domain.model.ClipCategory
import com.zoloft.innovv.app.domain.model.DiagnosticResult
import com.zoloft.innovv.app.domain.model.GSensorLevel
import com.zoloft.innovv.app.domain.model.LoopInterval
import com.zoloft.innovv.app.domain.model.MediaAsset
import com.zoloft.innovv.app.domain.model.MediaClip
import com.zoloft.innovv.app.domain.model.SettingsAccess
import com.zoloft.innovv.app.domain.model.VideoResolution
import com.zoloft.innovv.app.domain.model.WatermarkOptions
import com.zoloft.innovv.app.domain.model.WifiBand
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

object DemoData {
    private const val GB = 1024L * 1024L * 1024L

    val clips: List<MediaClip> = listOf(
        clip(
            id = "clip-0942",
            epoch = 1_760_000_500L,
            time = "Today 09:42",
            category = ClipCategory.LOOP,
            duration = 180,
            resolution = "2.1K Front + Rear",
            size = 428L * 1024L * 1024L,
            protected = false,
        ),
        clip(
            id = "clip-0939",
            epoch = 1_760_000_400L,
            time = "Today 09:39",
            category = ClipCategory.LOOP,
            duration = 180,
            resolution = "2.1K Front + Rear",
            size = 421L * 1024L * 1024L,
            protected = false,
        ),
        clip(
            id = "clip-0915",
            epoch = 1_760_000_300L,
            time = "Today 09:15",
            category = ClipCategory.PROTECTED,
            duration = 60,
            resolution = "2.1K Front",
            size = 176L * 1024L * 1024L,
            protected = true,
        ),
        clip(
            id = "clip-yesterday",
            epoch = 1_760_000_200L,
            time = "Yesterday 22:10",
            category = ClipCategory.PARKED,
            duration = 30,
            resolution = "1080p Front",
            size = 88L * 1024L * 1024L,
            protected = false,
        ),
        MediaClip(
            id = "photo-1805",
            displayTime = "Yesterday 18:05",
            startEpochSeconds = 1_760_000_100L,
            durationSeconds = null,
            resolutionLabel = "12MP Front",
            category = ClipCategory.PHOTO,
            front = MediaAsset(
                id = "photo-front-1805",
                name = "IMG_1805.JPG",
                remotePath = "A:\\NOVATEK\\PHOTO\\IMG_1805.JPG",
                url = "http://192.168.1.254/NOVATEK/PHOTO/IMG_1805.JPG",
                sizeBytes = 4L * 1024L * 1024L,
                width = 4000,
                height = 3000,
            ),
            rear = null,
            isProtected = false,
        ),
    )

    val settings = CameraSettingsState(
        access = SettingsAccess.Online,
        applied = CameraSettingsValues(),
        draft = CameraSettingsValues(),
        storageUsedBytes = 64L * GB,
        storageTotalBytes = 256L * GB,
        firmwareVersion = "v1.2.4",
        firmwareIsLatest = true,
        cachedAtMillis = Clock.System.now().toEpochMilliseconds(),
    )

    private fun clip(
        id: String,
        epoch: Long,
        time: String,
        category: ClipCategory,
        duration: Int,
        resolution: String,
        size: Long,
        protected: Boolean,
    ): MediaClip {
        val baseName = id.removePrefix("clip-")
        return MediaClip(
            id = id,
            displayTime = time,
            startEpochSeconds = epoch,
            durationSeconds = duration,
            resolutionLabel = resolution,
            category = category,
            front = MediaAsset(
                id = "$id-front",
                name = "FRONT_$baseName.MP4",
                remotePath = "A:\\NOVATEK\\MOVIE\\FRONT_$baseName.MP4",
                url = "http://192.168.1.254/NOVATEK/MOVIE/FRONT_$baseName.MP4",
                sizeBytes = size,
                width = 2560,
                height = 1440,
                durationSeconds = duration,
            ),
            rear = if (resolution.contains("Rear")) {
                MediaAsset(
                    id = "$id-rear",
                    name = "REAR_$baseName.MP4",
                    remotePath = "A:\\NOVATEK\\MOVIE\\REAR_$baseName.MP4",
                    url = "http://192.168.1.254/NOVATEK/MOVIE/REAR_$baseName.MP4",
                    sizeBytes = size,
                    width = 1920,
                    height = 1080,
                    durationSeconds = duration,
                )
            } else {
                null
            },
            isProtected = protected,
        )
    }
}

class DemoGalleryRepository : GalleryRepository {
    private var clips = DemoData.clips

    override suspend fun loadClips(): Result<List<MediaClip>> {
        delay(450)
        return Result.success(clips)
    }

    override suspend fun download(
        clips: List<MediaClip>,
        onProgress: suspend (completed: Int, total: Int, currentFile: String, bytes: Long, totalBytes: Long) -> Unit,
    ): Result<Unit> {
        val total = clips.size
        clips.forEachIndexed { index, clip ->
            val file = clip.front ?: clip.rear ?: return@forEachIndexed
            val steps = 8
            repeat(steps) { step ->
                delay(80)
                onProgress(
                    index,
                    total,
                    file.name,
                    file.sizeBytes * (step + 1) / steps,
                    file.sizeBytes,
                )
            }
        }
        return Result.success(Unit)
    }

    override suspend fun delete(
        clips: List<MediaClip>,
        onProgress: suspend (completed: Int, total: Int) -> Unit,
    ): Result<Unit> {
        val ids = clips.map { it.id }.toSet()
        clips.forEachIndexed { index, _ ->
            delay(200)
            onProgress(index + 1, clips.size)
        }
        this.clips = this.clips.filterNot { it.id in ids }
        return Result.success(Unit)
    }
}

class DemoSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(DemoData.settings)
    val current: StateFlow<CameraSettingsState> = state.asStateFlow()

    override suspend fun load(): Result<CameraSettingsState> {
        delay(350)
        state.value = state.value.copy(
            access = SettingsAccess.Online,
            cachedAtMillis = Clock.System.now().toEpochMilliseconds(),
        )
        return Result.success(state.value)
    }

    override suspend fun apply(
        values: CameraSettingsValues,
        onStep: suspend (String) -> Unit,
    ): Result<Unit> {
        onStep(ApplyStep.STOPPING_RECORDING.label)
        delay(350)
        onStep(ApplyStep.WRITING_SETTINGS.label)
        delay(500)
        onStep(ApplyStep.SAVING_SETTINGS.label)
        delay(350)
        onStep(ApplyStep.RESTARTING_RECORDING.label)
        delay(350)
        state.value = state.value.copy(applied = values, draft = values)
        return Result.success(Unit)
    }

    override suspend fun formatSdCard(onStep: suspend (String) -> Unit): Result<Unit> {
        onStep(ApplyStep.STOPPING_RECORDING.label)
        delay(350)
        onStep(ApplyStep.FORMATTING_SD.label)
        delay(1200)
        onStep(ApplyStep.RESTARTING_RECORDING.label)
        delay(300)
        state.value = state.value.copy(
            storageUsedBytes = 0L,
            storageTotalBytes = state.value.storageTotalBytes,
        )
        return Result.success(Unit)
    }

    override suspend fun runDiagnostics(): Result<List<DiagnosticResult>> {
        delay(900)
        return Result.success(
            listOf(
                DiagnosticResult("Heartbeat", "84 ms", true),
                DiagnosticResult("Event socket :3333", "Connected", true),
                DiagnosticResult("SD card", "Inserted / healthy", true),
                DiagnosticResult("Free space", "192 GB", true),
                DiagnosticResult("Firmware", "v1.2.4", true),
                DiagnosticResult("Wi-Fi signal", "-48 dBm", true),
            ),
        )
    }
}
