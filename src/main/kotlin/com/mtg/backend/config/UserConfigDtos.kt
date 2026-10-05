package com.mtg.backend.config

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

data class UserConfigRequest(
    @get:NotBlank(message = "El nombre del motociclista es obligatorio")
    @get:Size(max = 60, message = "El nombre no puede superar 60 caracteres")
    val riderName: String,

    @get:NotNull
    @get:Min(value = 2000, message = "impactThresholdMg debe ser >= 2000")
    @get:Max(value = 6000, message = "impactThresholdMg debe ser <= 6000")
    val impactThresholdMg: Int,

    @get:NotNull
    @get:Min(value = 6000, message = "severeImpactThresholdMg debe ser >= 6000")
    @get:Max(value = 16000, message = "severeImpactThresholdMg debe ser <= 16000")
    val severeImpactThresholdMg: Int,

    @get:NotNull
    @get:Min(value = 150, message = "gyroThresholdDps debe ser >= 150")
    @get:Max(value = 1000, message = "gyroThresholdDps debe ser <= 1000")
    val gyroThresholdDps: Int,

    @get:NotNull
    @get:Min(value = 50, message = "tiltThresholdDeg debe ser >= 50")
    @get:Max(value = 80, message = "tiltThresholdDeg debe ser <= 80")
    val tiltThresholdDeg: Int,

    @get:NotNull
    @get:Min(value = 500, message = "tiltHoldMs debe ser >= 500")
    @get:Max(value = 5000, message = "tiltHoldMs debe ser <= 5000")
    val tiltHoldMs: Int,

    @get:NotNull(message = "tiltDetectionEnabled es obligatorio")
    val tiltDetectionEnabled: Boolean,

    @get:NotNull
    @get:Min(value = 3000, message = "confirmWindowMs debe ser >= 3000")
    @get:Max(value = 10000, message = "confirmWindowMs debe ser <= 10000")
    val confirmWindowMs: Int,

    @get:NotNull
    @get:Min(value = 100, message = "stillnessToleranceMg debe ser >= 100")
    @get:Max(value = 400, message = "stillnessToleranceMg debe ser <= 400")
    val stillnessToleranceMg: Int,

    @get:NotNull
    @get:Min(value = 10, message = "countdownSeconds debe ser >= 10")
    @get:Max(value = 60, message = "countdownSeconds debe ser <= 60")
    val countdownSeconds: Int,

    @get:NotNull
    @get:Min(value = -9000, message = "pitchOffsetCdeg debe ser >= -9000")
    @get:Max(value = 9000, message = "pitchOffsetCdeg debe ser <= 9000")
    val pitchOffsetCdeg: Int,

    @get:NotNull
    @get:Min(value = -9000, message = "rollOffsetCdeg debe ser >= -9000")
    @get:Max(value = 9000, message = "rollOffsetCdeg debe ser <= 9000")
    val rollOffsetCdeg: Int
)

data class UserConfigResponse(
    val riderName: String,
    val impactThresholdMg: Int,
    val severeImpactThresholdMg: Int,
    val gyroThresholdDps: Int,
    val tiltThresholdDeg: Int,
    val tiltHoldMs: Int,
    val tiltDetectionEnabled: Boolean,
    val confirmWindowMs: Int,
    val stillnessToleranceMg: Int,
    val countdownSeconds: Int,
    val pitchOffsetCdeg: Int,
    val rollOffsetCdeg: Int,
    val revision: Long,
    val updatedAt: Instant
)

fun UserConfig.toDto(): UserConfigResponse =
    UserConfigResponse(
        riderName = riderName,
        impactThresholdMg = impactThresholdMg,
        severeImpactThresholdMg = severeImpactThresholdMg,
        gyroThresholdDps = gyroThresholdDps,
        tiltThresholdDeg = tiltThresholdDeg,
        tiltHoldMs = tiltHoldMs,
        tiltDetectionEnabled = tiltDetectionEnabled,
        confirmWindowMs = confirmWindowMs,
        stillnessToleranceMg = stillnessToleranceMg,
        countdownSeconds = countdownSeconds,
        pitchOffsetCdeg = pitchOffsetCdeg,
        rollOffsetCdeg = rollOffsetCdeg,
        revision = revision,
        updatedAt = updatedAt
    )
