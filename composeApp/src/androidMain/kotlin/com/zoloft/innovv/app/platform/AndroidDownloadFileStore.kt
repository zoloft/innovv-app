package com.zoloft.innovv.app.platform

import android.os.Environment
import java.io.File

actual fun appStorageDirectory(): String {
    val root = AndroidPlatformContext.application.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        ?: AndroidPlatformContext.application.filesDir
    return File(root, "INNOVV").absolutePath
}

actual fun createDownloadFileStore(): DownloadFileStore = OkioDownloadFileStore()
