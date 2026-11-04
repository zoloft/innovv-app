package com.zoloft.innovv.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer

interface DownloadFileStore {
    suspend fun write(
        fileName: String,
        producer: suspend (suspend (ByteArray) -> Unit) -> Unit,
    ): Result<Long>
}

class OkioDownloadFileStore(
    private val directory: String = appStorageDirectory(),
) : DownloadFileStore {
    override suspend fun write(
        fileName: String,
        producer: suspend (suspend (ByteArray) -> Unit) -> Unit,
    ): Result<Long> = withContext(Dispatchers.Default) {
        runCatching {
            val root = directory.toPath()
            FileSystem.SYSTEM.createDirectories(root)
            val target = root / fileName
            val partial = root / "$fileName.part"
            var total = 0L
            val sink = FileSystem.SYSTEM.sink(partial).buffer()
            try {
                producer { chunk ->
                    sink.write(chunk)
                    total += chunk.size
                }
            } finally {
                sink.close()
            }
            FileSystem.SYSTEM.atomicMove(partial, target)
            total
        }.onFailure {
            runCatching { FileSystem.SYSTEM.delete(directory.toPath() / "$fileName.part") }
        }
    }
}

expect fun appStorageDirectory(): String
expect fun createDownloadFileStore(): DownloadFileStore
