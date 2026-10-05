package com.mtg.backend.incidents

import com.mtg.backend.common.model.LocationSource
import com.mtg.backend.installation.InstallationRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class UpsertIncidentResult(
    val created: Boolean,
    val incident: IncidentResponse
)

@Service
class IncidentService(
    private val incidentRepository: IncidentRepository,
    private val incidentTraceRepository: IncidentTraceRepository,
    private val installationRepository: InstallationRepository
) {

    @Transactional(readOnly = true)
    fun getIncidents(installationId: UUID, pageable: Pageable): IncidentListResponse {
        val page: Page<Incident> = incidentRepository.findAllByInstallationIdOrderByDetectedAtDesc(installationId, pageable)
        return IncidentListResponse(
            items = page.content.map { it.toDto() },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun getIncident(installationId: UUID, incidentId: UUID): IncidentResponse =
        incidentRepository.findByIdAndInstallationId(incidentId, installationId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Incidente no encontrado") }
            .toDto()

    @Transactional
    fun upsertIncident(installationId: UUID, incidentId: UUID, request: IncidentUpsertRequest): UpsertIncidentResult {
        validateIncidentRequest(request)
        val installation = installationRepository.findById(installationId)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Instalación no encontrada") }

        incidentRepository.lockIncidentIdForUpsert(incidentId)
        val existing = incidentRepository.findByIdAndInstallationId(incidentId, installationId).orElse(null)
        if (existing == null) {
            if (incidentRepository.existsById(incidentId)) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Incidente no encontrado")
            }
            val created = Incident(
                id = incidentId,
                installation = installation,
                type = request.type,
                triggerType = request.triggerType,
                status = request.status,
                deviceEventKey = request.deviceEventKey,
                detectedAt = request.detectedAt,
                resolvedAt = request.resolvedAt,
                peakAccelMg = request.peakAccelMg,
                peakGyroDps = request.peakGyroDps,
                pitchCdeg = request.pitchCdeg,
                rollCdeg = request.rollCdeg,
                latitude = request.location?.latitude,
                longitude = request.location?.longitude,
                locationAccuracyM = request.location?.accuracyM,
                locationSource = request.location?.source ?: LocationSource.NONE,
                locationFixAt = request.location?.fixAt,
                smsPrimaryStatus = request.dispatch.smsPrimary,
                smsSecondaryStatus = request.dispatch.smsSecondary,
                callStatus = request.dispatch.call,
                firmwareVersion = request.firmwareVersion,
                appVersion = request.appVersion
            )

            val saved = incidentRepository.save(created)
            return UpsertIncidentResult(created = true, incident = saved.toDto())
        }

        val immutableChanged = existing.type != request.type ||
            existing.triggerType != request.triggerType ||
            existing.detectedAt != request.detectedAt ||
            existing.deviceEventKey != request.deviceEventKey
        if (immutableChanged) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "El incidente ya existe con campos inmutables diferentes")
        }

        existing.status = request.status
        existing.resolvedAt = request.resolvedAt
        existing.peakAccelMg = request.peakAccelMg
        existing.peakGyroDps = request.peakGyroDps
        existing.pitchCdeg = request.pitchCdeg
        existing.rollCdeg = request.rollCdeg
        existing.latitude = request.location?.latitude
        existing.longitude = request.location?.longitude
        existing.locationAccuracyM = request.location?.accuracyM
        existing.locationSource = request.location?.source ?: LocationSource.NONE
        existing.locationFixAt = request.location?.fixAt
        existing.smsPrimaryStatus = request.dispatch.smsPrimary
        existing.smsSecondaryStatus = request.dispatch.smsSecondary
        existing.callStatus = request.dispatch.call
        existing.firmwareVersion = request.firmwareVersion
        existing.appVersion = request.appVersion

        val saved = incidentRepository.save(existing)
        return UpsertIncidentResult(created = false, incident = saved.toDto())
    }

    @Transactional
    fun putTrace(installationId: UUID, incidentId: UUID, request: IncidentTraceRequest) {
        if (request.preTriggerSamples > request.totalSamples) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "preTriggerSamples no puede superar totalSamples")
        }

        val incident = incidentRepository.findByIdAndInstallationId(incidentId, installationId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Incidente no encontrado") }

        if (request.samplesBase64.length > 64 * 1024) {
            throw ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La traza supera el límite de 64 KB")
        }

        val decoded = try {
            request.decodedBytes()
        } catch (ex: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "samplesBase64 no contiene una traza Base64 válida", ex)
        }
        if (decoded.size.toLong() != request.bytesExpected()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El contenido de samplesBase64 no coincide con totalSamples * 12 bytes")
        }

        val existing = incidentTraceRepository.findByIncidentIdAndIncidentInstallationId(incidentId, installationId).orElse(null)

        val trace = if (existing != null) {
            existing.apply {
                sampleRateHz = request.sampleRateHz
                preTriggerSamples = request.preTriggerSamples
                totalSamples = request.totalSamples
                accelLsbPerG = request.accelLsbPerG
                gyroLsbPerDpsX10 = request.gyroLsbPerDpsX10
                samples = decoded
            }
        } else {
            IncidentTrace(
                incident = incident,
                sampleRateHz = request.sampleRateHz,
                preTriggerSamples = request.preTriggerSamples,
                totalSamples = request.totalSamples,
                accelLsbPerG = request.accelLsbPerG,
                gyroLsbPerDpsX10 = request.gyroLsbPerDpsX10,
                samples = decoded
            )
        }

        incidentTraceRepository.save(trace)
    }

    private fun validateIncidentRequest(request: IncidentUpsertRequest) {
        if (request.resolvedAt != null && request.resolvedAt.isBefore(request.detectedAt)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "resolvedAt no puede ser anterior a detectedAt")
        }

        val location = request.location ?: return
        if ((location.latitude == null) != (location.longitude == null)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude y longitude deben enviarse juntas")
        }
        if (location.source == LocationSource.NONE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "location.source no puede ser NONE cuando se envía location")
        }
    }

    @Transactional(readOnly = true)
    fun getTrace(installationId: UUID, incidentId: UUID): IncidentTraceResponse =
        incidentTraceRepository.findByIncidentIdAndIncidentInstallationId(incidentId, installationId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Traza no encontrada") }
            .toDto()
}
