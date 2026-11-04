package com.zoloft.innovv.app.domain

import com.zoloft.innovv.app.domain.model.CameraSettingsState
import com.zoloft.innovv.app.domain.model.CameraSettingsValues
import com.zoloft.innovv.app.domain.model.DiagnosticResult

interface SettingsRepository {
    suspend fun load(): Result<CameraSettingsState>

    suspend fun apply(
        values: CameraSettingsValues,
        onStep: suspend (String) -> Unit,
    ): Result<Unit>

    suspend fun formatSdCard(
        onStep: suspend (String) -> Unit,
    ): Result<Unit>

    suspend fun runDiagnostics(): Result<List<DiagnosticResult>>
}
