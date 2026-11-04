package com.zoloft.innovv.app

import com.zoloft.innovv.app.core.util.formatBytes
import com.zoloft.innovv.app.core.util.formatDuration
import com.zoloft.innovv.app.core.util.formatG
import com.zoloft.innovv.app.core.util.formatStorage
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattersTest {
    @Test
    fun durationFormatsMinutesAndSeconds() {
        assertEquals("00:00", formatDuration(0))
        assertEquals("01:30", formatDuration(90))
        assertEquals("01:01:01", formatDuration(3661))
    }

    @Test
    fun bytesUseReadableUnits() {
        assertEquals("428 MB", formatBytes(428L * 1024L * 1024L))
        assertEquals("2 GB", formatBytes(2L * 1024L * 1024L * 1024L))
    }

    @Test
    fun storageFallsBackToFreeSpace() {
        assertEquals("64 / 256G", formatStorage(64L * 1024L * 1024L * 1024L, 256L * 1024L * 1024L * 1024L))
        assertEquals("192G free", formatStorage(null, null, 192L * 1024L * 1024L * 1024L))
        assertEquals("—", formatStorage(null, null, null))
    }

    @Test
    fun gForceHasTwoDecimals() {
        assertEquals("0.85", formatG(0.85f))
        assertEquals("1.20", formatG(1.2f))
    }
}
