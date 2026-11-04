package com.zoloft.innovv.app.data.sdk

import com.zoloft.innovv.app.domain.GalleryRepository
import com.zoloft.innovv.app.domain.model.CameraChannel
import com.zoloft.innovv.app.domain.model.ClipCategory
import com.zoloft.innovv.app.domain.model.MediaAsset
import com.zoloft.innovv.app.domain.model.MediaClip
import com.zoloft.innovv.app.platform.DownloadFileStore
import com.zoloft.innovv.app.platform.createDownloadFileStore
import com.zoloft.innovv.sdk.model.CameraFile
import com.zoloft.innovv.sdk.model.FileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class SdkGalleryRepository(
    private val session: SdkCameraSession,
    private val fileStore: DownloadFileStore = createDownloadFileStore(),
) : GalleryRepository {
    override suspend fun loadClips(): Result<List<MediaClip>> =
        session.useClient { client -> groupFiles(client.getFiles()) }
            .mapCatching { it }

    override suspend fun download(
        clips: List<MediaClip>,
        onProgress: suspend (completed: Int, total: Int, currentFile: String, bytes: Long, totalBytes: Long) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val totalBytes = clips.sumOf { clip ->
                (clip.front?.sizeBytes ?: 0L) + (clip.rear?.sizeBytes ?: 0L)
            }.coerceAtLeast(1L)
            var downloaded = 0L
            var completed = 0

            clips.forEachIndexed { index, clip ->
                val assets = listOfNotNull(clip.front, clip.rear)
                assets.forEach { asset ->
                    val remote = toCameraFile(asset)
                    fileStore.write(remote.name) { sink ->
                        session.useClient { client ->
                            client.download(remote) { chunk ->
                                sink(chunk)
                                downloaded += chunk.size
                                onProgress(index, clips.size, remote.name, downloaded, totalBytes)
                            }
                        }.getOrThrow()
                    }.getOrThrow()
                }
                completed = index + 1
                onProgress(completed, clips.size, clip.displayTime, downloaded, totalBytes)
            }
            Unit
        }
    }

    override suspend fun delete(
        clips: List<MediaClip>,
        onProgress: suspend (completed: Int, total: Int) -> Unit,
    ): Result<Unit> = session.useClient { client ->
        clips.forEachIndexed { index, clip ->
            listOfNotNull(clip.front, clip.rear).forEach { asset ->
                val file = toCameraFile(asset)
                client.deleteFile(file)
            }
            onProgress(index + 1, clips.size)
        }
    }

    private fun groupFiles(files: List<CameraFile>): List<MediaClip> {
        val photos = files.filter { it.type == FileType.PHOTO }
            .sortedByDescending { it.timecodeEpochSeconds }
            .map { file ->
                MediaClip(
                    id = "photo:${file.path}",
                    displayTime = formatCameraTime(file.timeText),
                    startEpochSeconds = file.timecodeEpochSeconds,
                    durationSeconds = null,
                    resolutionLabel = "Photo",
                    category = ClipCategory.PHOTO,
                    front = file.toAsset(),
                    rear = null,
                    isProtected = false,
                )
            }

        val videos = files.filter { it.type == FileType.VIDEO }
            .sortedByDescending { it.timecodeEpochSeconds }
        val clips = mutableListOf<MediaClip>()

        videos.forEach { file ->
            val channel = channelOf(file)
            val asset = file.toAsset()
            val existingIndex = clips.indexOfFirst { clip ->
                abs(clip.startEpochSeconds - file.timecodeEpochSeconds) <= 3 &&
                    when (channel) {
                        CameraChannel.FRONT -> clip.front == null
                        CameraChannel.REAR -> clip.rear == null
                    }
            }
            if (existingIndex >= 0) {
                val existing = clips[existingIndex]
                clips[existingIndex] = when (channel) {
                    CameraChannel.FRONT -> existing.copy(front = asset)
                    CameraChannel.REAR -> existing.copy(rear = asset)
                }
            } else {
                val category = categoryOf(file)
                clips += MediaClip(
                    id = "clip:${file.path}",
                    displayTime = formatCameraTime(file.timeText),
                    startEpochSeconds = file.timecodeEpochSeconds,
                    durationSeconds = null,
                    resolutionLabel = if (channel == CameraChannel.REAR) "Rear camera" else "Camera clip",
                    category = category,
                    front = if (channel == CameraChannel.FRONT) asset else null,
                    rear = if (channel == CameraChannel.REAR) asset else null,
                    isProtected = category == ClipCategory.PROTECTED,
                )
            }
        }
        return (clips + photos).sortedByDescending { it.startEpochSeconds }
    }

    private fun channelOf(file: CameraFile): CameraChannel {
        val path = "${file.relativePath} ${file.name}".uppercase()
        return if (
            path.contains("REAR") ||
            path.contains("_R.") ||
            path.contains("\\R\\") ||
            path.contains("/R/")
        ) {
            CameraChannel.REAR
        } else {
            CameraChannel.FRONT
        }
    }

    private fun categoryOf(file: CameraFile): ClipCategory {
        val path = "${file.relativePath} ${file.name}".uppercase()
        return when {
            path.contains("EMR") || path.contains("PROTECT") || path.contains("EVENT") ->
                ClipCategory.PROTECTED
            path.contains("PARK") -> ClipCategory.PARKED
            else -> ClipCategory.LOOP
        }
    }

    private fun CameraFile.toAsset(): MediaAsset = MediaAsset(
        id = path,
        name = name,
        remotePath = path,
        url = url,
        sizeBytes = sizeBytes,
    )

    private fun toCameraFile(asset: MediaAsset): CameraFile = CameraFile(
        name = asset.name,
        path = asset.remotePath,
        relativePath = asset.remotePath.removePrefix("A:").replace('\\', '/').trimStart('/'),
        url = asset.url,
        sizeBytes = asset.sizeBytes,
        timecodeEpochSeconds = 0L,
        timeText = "",
        attributes = 0,
        type = if (asset.name.uppercase().endsWith(".JPG") || asset.name.uppercase().endsWith(".JPEG")) {
            FileType.PHOTO
        } else {
            FileType.VIDEO
        },
    )

    private fun formatCameraTime(timeText: String): String {
        val parts = timeText.trim().split(" ")
        return when {
            parts.size >= 2 -> "${parts[0]} ${parts[1].take(5)}"
            timeText.isBlank() -> "Camera clip"
            else -> timeText
        }
    }
}
