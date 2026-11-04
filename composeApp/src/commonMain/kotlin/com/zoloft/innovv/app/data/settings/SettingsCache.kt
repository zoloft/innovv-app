package com.zoloft.innovv.app.data.settings

import com.russhwolf.settings.Settings
import com.zoloft.innovv.app.domain.model.CameraSettingsState
import com.zoloft.innovv.app.domain.model.CameraSettingsValues
import com.zoloft.innovv.app.domain.model.GSensorLevel
import com.zoloft.innovv.app.domain.model.LoopInterval
import com.zoloft.innovv.app.domain.model.SettingsAccess
import com.zoloft.innovv.app.domain.model.VideoResolution
import com.zoloft.innovv.app.domain.model.WatermarkOptions
import com.zoloft.innovv.app.domain.model.WifiBand
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class SettingsCache(
    private val settings: Settings = Settings(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(state: CameraSettingsState) {
        val dto = CachedSettingsDto(
            wifiBand = state.applied.wifiBand.name,
            autoReconnect = state.applied.autoReconnect,
            videoResolution = state.applied.videoResolution.name,
            loop = state.applied.loop.name,
            watermarkTimestamp = state.applied.watermark.timestamp,
            watermarkGpsSpeed = state.applied.watermark.gpsSpeed,
            watermarkGSensor = state.applied.watermark.gSensor,
            watermarkText = state.applied.watermark.customText,
            gSensor = state.applied.gSensor.name,
            parkingMode = state.applied.parkingMode,
            firmwareVersion = state.firmwareVersion,
            storageFreeBytes = state.storageFreeBytes,
            cachedAtMillis = state.cachedAtMillis ?: Clock.System.now().toEpochMilliseconds(),
        )
        settings.putString(KEY, json.encodeToString(dto))
    }

    fun load(): CameraSettingsState? {
        val raw = settings.getStringOrNull(KEY) ?: return null
        val dto = runCatching { json.decodeFromString<CachedSettingsDto>(raw) }.getOrNull() ?: return null
        val values = CameraSettingsValues(
            wifiBand = enumValueOr(WifiBand.entries, dto.wifiBand, WifiBand.GHZ_5_8),
            autoReconnect = dto.autoReconnect,
            videoResolution = enumValueOr(
                VideoResolution.entries,
                dto.videoResolution,
                VideoResolution.UHD_2160_1080,
            ),
            loop = enumValueOr(LoopInterval.entries, dto.loop, LoopInterval.MIN_3),
            watermark = WatermarkOptions(
                timestamp = dto.watermarkTimestamp,
                gpsSpeed = dto.watermarkGpsSpeed,
                gSensor = dto.watermarkGSensor,
                customText = dto.watermarkText,
            ),
            gSensor = enumValueOr(GSensorLevel.entries, dto.gSensor, GSensorLevel.HIGH),
            parkingMode = dto.parkingMode,
        )
        return CameraSettingsState(
            access = SettingsAccess.OfflineCached(dto.cachedAtMillis),
            applied = values,
            draft = values,
            storageUsedBytes = null,
            storageTotalBytes = null,
            storageFreeBytes = dto.storageFreeBytes,
            firmwareVersion = dto.firmwareVersion,
            firmwareIsLatest = false,
            cachedAtMillis = dto.cachedAtMillis,
        )
    }

    private fun <T : Enum<T>> enumValueOr(entries: List<T>, name: String?, fallback: T): T =
        entries.firstOrNull { it.name == name } ?: fallback

    private companion object {
        const val KEY = "cached_camera_settings_v1"
    }
}

@Serializable
private data class CachedSettingsDto(
    val wifiBand: String,
    val autoReconnect: Boolean,
    val videoResolution: String,
    val loop: String,
    val watermarkTimestamp: Boolean,
    val watermarkGpsSpeed: Boolean,
    val watermarkGSensor: Boolean,
    val watermarkText: String?,
    val gSensor: String,
    val parkingMode: Boolean,
    val firmwareVersion: String,
    val storageFreeBytes: Long?,
    val cachedAtMillis: Long,
)
