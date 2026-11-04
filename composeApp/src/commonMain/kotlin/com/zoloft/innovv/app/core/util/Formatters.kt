package com.zoloft.innovv.app.core.util

import kotlin.math.roundToInt

fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}

fun formatBytes(bytes: Long): String {
    val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return "${gb.roundToInt()} GB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return "${mb.roundToInt()} MB"
}

fun formatStorage(usedBytes: Long?, totalBytes: Long?, freeBytes: Long? = null): String {
    val used = usedBytes?.let { (it.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt() }
    val total = totalBytes?.let { (it.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt() }
    val free = freeBytes?.let { (it.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt() }
    return when {
        used != null && total != null -> "$used / ${total}G"
        free != null -> "${free}G free"
        total != null -> "${total}G"
        used != null -> "${used}G"
        else -> "—"
    }
}

fun formatMegabytesPerSecond(bytesPerSecond: Long): String {
    val mb = bytesPerSecond.toDouble() / (1024.0 * 1024.0)
    return "${(mb * 10).roundToInt() / 10.0} MB/s"
}

fun formatG(value: Float): String {
    val hundredths = (value * 100f).roundToInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}
