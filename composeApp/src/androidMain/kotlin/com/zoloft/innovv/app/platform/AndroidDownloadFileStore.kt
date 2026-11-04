package com.zoloft.innovv.app.platform

import android.os.Environment
import com.zoloft.innovv.app.InnovvApplication
import java.io.File

actual fun appStorageDirectory(): String {
    val root = InnovvApplication.instance.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        ?: InnovvApplication.instance.filesDir
    return File(root, "INNOVV").absolutePath
}

actual fun createDownloadFileStore(): DownloadFileStore = OkioDownloadFileStore()
