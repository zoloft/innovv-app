package com.zoloft.innovv.app

import com.zoloft.innovv.app.domain.model.CameraSettingsState
import com.zoloft.innovv.app.domain.model.CameraSettingsValues
import com.zoloft.innovv.app.domain.model.ClipCategory
import com.zoloft.innovv.app.domain.model.GalleryFilter
import com.zoloft.innovv.app.domain.model.GalleryUiState
import com.zoloft.innovv.app.domain.model.LoopInterval
import com.zoloft.innovv.app.domain.model.MediaClip
import com.zoloft.innovv.app.domain.model.WatermarkOptions
import com.zoloft.innovv.app.domain.model.WifiBand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelsTest {
    @Test
    fun galleryFiltersUseCategoryCounts() {
        val clips = listOf(
            clip("loop", ClipCategory.LOOP),
            clip("protected", ClipCategory.PROTECTED),
            clip("photo", ClipCategory.PHOTO),
        )
        val state = GalleryUiState(
            clips = clips,
            counts = mapOf(
                GalleryFilter.ALL to 3,
                GalleryFilter.LOOP to 1,
                GalleryFilter.PROTECTED to 1,
                GalleryFilter.PHOTOS to 1,
            ),
        )
        assertEquals(3, state.copy(filter = GalleryFilter.ALL).visibleClips.size)
        assertEquals(1, state.copy(filter = GalleryFilter.LOOP).visibleClips.size)
        assertEquals("protected", state.copy(filter = GalleryFilter.PROTECTED).visibleClips.single().id)
    }

    @Test
    fun watermarkSummaryDescribesEnabledOptions() {
        assertEquals(
            "Timestamp + GPS + Speed",
            WatermarkOptions(timestamp = true, gpsSpeed = true, gSensor = false).summary,
        )
        assertEquals("Off", WatermarkOptions(false, false, false).summary)
    }

    @Test
    fun settingsDirtyTracksDraftChanges() {
        val state = CameraSettingsState()
        assertFalse(state.isDirty)
        val changed = state.copy(
            draft = state.draft.copy(
                wifiBand = WifiBand.GHZ_2_4,
                loop = LoopInterval.MIN_1,
            ),
        )
        assertTrue(changed.isDirty)
        assertTrue(changed.draft != CameraSettingsValues())
    }

    private fun clip(id: String, category: ClipCategory) = MediaClip(
        id = id,
        displayTime = "Today 09:42",
        startEpochSeconds = 1L,
        durationSeconds = 180,
        resolutionLabel = "2K",
        category = category,
        front = null,
        rear = null,
        isProtected = category == ClipCategory.PROTECTED,
    )
}
