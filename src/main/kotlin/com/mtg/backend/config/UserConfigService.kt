package com.mtg.backend.config

import com.mtg.backend.installation.InstallationRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class UserConfigService(
    private val userConfigRepository: UserConfigRepository,
    private val installationRepository: InstallationRepository
) {

    @Transactional(readOnly = true)
    fun getConfig(installationId: UUID): UserConfigResponse {
        val config = userConfigRepository.findById(installationId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Configuración no encontrada para la instalación") }
        return config.toDto()
    }

    @Transactional
    fun saveConfig(installationId: UUID, request: UserConfigRequest): UserConfigResponse {
        val installation = installationRepository.findById(installationId)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Instalación no encontrada") }

        val existing = userConfigRepository.findById(installationId).orElse(null)

        val configToSave = if (existing != null) {
            existing.apply {
                riderName = request.riderName
                impactThresholdMg = request.impactThresholdMg
                severeImpactThresholdMg = request.severeImpactThresholdMg
                gyroThresholdDps = request.gyroThresholdDps
                tiltThresholdDeg = request.tiltThresholdDeg
                tiltHoldMs = request.tiltHoldMs
                tiltDetectionEnabled = request.tiltDetectionEnabled
                confirmWindowMs = request.confirmWindowMs
                stillnessToleranceMg = request.stillnessToleranceMg
                countdownSeconds = request.countdownSeconds
                pitchOffsetCdeg = request.pitchOffsetCdeg
                rollOffsetCdeg = request.rollOffsetCdeg
                updatedAt = Instant.now()
            }
        } else {
            UserConfig(
                installationId = installationId,
                installation = installation,
                riderName = request.riderName,
                impactThresholdMg = request.impactThresholdMg,
                severeImpactThresholdMg = request.severeImpactThresholdMg,
                gyroThresholdDps = request.gyroThresholdDps,
                tiltThresholdDeg = request.tiltThresholdDeg,
                tiltHoldMs = request.tiltHoldMs,
                tiltDetectionEnabled = request.tiltDetectionEnabled,
                confirmWindowMs = request.confirmWindowMs,
                stillnessToleranceMg = request.stillnessToleranceMg,
                countdownSeconds = request.countdownSeconds,
                pitchOffsetCdeg = request.pitchOffsetCdeg,
                rollOffsetCdeg = request.rollOffsetCdeg,
                updatedAt = Instant.now()
            )
        }

        val saved = userConfigRepository.save(configToSave)
        return saved.toDto()
    }
}
