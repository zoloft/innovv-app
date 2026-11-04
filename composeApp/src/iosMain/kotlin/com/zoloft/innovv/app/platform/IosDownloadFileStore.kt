package com.zoloft.innovv.app.platform

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual fun appStorageDirectory(): String {
    val documents = NSSearchPathForDirectoriesInDomains(
        directory = NSDocumentDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).firstOrNull() as? String ?: "."
    return "$documents/INNOVV"
}

actual fun createDownloadFileStore(): DownloadFileStore = OkioDownloadFileStore()
