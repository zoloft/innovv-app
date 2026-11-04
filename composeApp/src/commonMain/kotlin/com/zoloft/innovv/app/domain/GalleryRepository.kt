package com.zoloft.innovv.app.domain

import com.zoloft.innovv.app.domain.model.MediaClip

interface GalleryRepository {
    suspend fun loadClips(): Result<List<MediaClip>>

    suspend fun download(
        clips: List<MediaClip>,
        onProgress: suspend (completed: Int, total: Int, currentFile: String, bytes: Long, totalBytes: Long) -> Unit,
    ): Result<Unit>

    suspend fun delete(
        clips: List<MediaClip>,
        onProgress: suspend (completed: Int, total: Int) -> Unit,
    ): Result<Unit>
}
