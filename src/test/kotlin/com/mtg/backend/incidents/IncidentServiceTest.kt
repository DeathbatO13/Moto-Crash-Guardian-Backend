package com.mtg.backend.incidents

import com.mtg.backend.common.model.CallStatus
import com.mtg.backend.common.model.IncidentStatus
import com.mtg.backend.common.model.IncidentType
import com.mtg.backend.common.model.LocationSource
import com.mtg.backend.common.model.SmsStatus
import com.mtg.backend.common.model.TriggerType
import com.mtg.backend.installation.Installation
import com.mtg.backend.installation.InstallationRepository
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Base64
import java.util.Optional
import java.util.UUID

class IncidentServiceTest {

    private val incidentRepository = mock(IncidentRepository::class.java)
    private val incidentTraceRepository = mock(IncidentTraceRepository::class.java)
    private val installationRepository = mock(InstallationRepository::class.java)
    private val service = IncidentService(incidentRepository, incidentTraceRepository, installationRepository)

    private val installationId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val incidentId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val installation = Installation(id = installationId, tokenHash = byteArrayOf(1, 2, 3))
    private val detectedAt = Instant.parse("2026-10-04T12:00:00Z")

    @Test
    fun `upsert crea incidente con el UUID del cliente`() {
        `when`(installationRepository.findById(installationId)).thenReturn(Optional.of(installation))
        `when`(incidentRepository.findByIdAndInstallationId(incidentId, installationId)).thenReturn(Optional.empty())
        `when`(incidentRepository.save(any(Incident::class.java))).thenAnswer { it.getArgument(0) }

        val result = service.upsertIncident(installationId, incidentId, request())

        assertTrue(result.created)
        assertEquals(incidentId, result.incident.id)
        assertEquals(IncidentStatus.NOT_CONFIRMED, result.incident.status)
        verify(incidentRepository).lockIncidentIdForUpsert(incidentId)
        verify(incidentRepository).save(any(Incident::class.java))
    }

    @Test
    fun `upsert repetido actualiza campos mutables sin cambiar el UUID`() {
        val existing = incident()
        `when`(installationRepository.findById(installationId)).thenReturn(Optional.of(installation))
        `when`(incidentRepository.findByIdAndInstallationId(incidentId, installationId))
            .thenReturn(Optional.of(existing))
        `when`(incidentRepository.save(existing)).thenReturn(existing)

        val result = service.upsertIncident(
            installationId,
            incidentId,
            request(status = IncidentStatus.DISPATCHED, resolvedAt = detectedAt.plusSeconds(30))
        )

        assertFalse(result.created)
        assertEquals(IncidentStatus.DISPATCHED, existing.status)
        assertEquals(detectedAt.plusSeconds(30), existing.resolvedAt)
        verify(incidentRepository).save(existing)
    }

    @Test
    fun `upsert rechaza cambios inmutables y no persiste`() {
        val existing = incident()
        `when`(installationRepository.findById(installationId)).thenReturn(Optional.of(installation))
        `when`(incidentRepository.findByIdAndInstallationId(incidentId, installationId))
            .thenReturn(Optional.of(existing))

        val error = assertThrows(ResponseStatusException::class.java) {
            service.upsertIncident(installationId, incidentId, request(detectedAt = detectedAt.plusSeconds(1)))
        }

        assertEquals(HttpStatus.CONFLICT, error.statusCode)
        verify(incidentRepository, never()).save(any(Incident::class.java))
    }

    @Test
    fun `upsert rechaza UUID que ya pertenece a otra instalacion`() {
        `when`(installationRepository.findById(installationId)).thenReturn(Optional.of(installation))
        `when`(incidentRepository.findByIdAndInstallationId(incidentId, installationId)).thenReturn(Optional.empty())
        `when`(incidentRepository.existsById(incidentId)).thenReturn(true)

        val error = assertThrows(ResponseStatusException::class.java) {
            service.upsertIncident(installationId, incidentId, request())
        }

        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        verify(incidentRepository, never()).save(any(Incident::class.java))
    }

    @Test
    fun `upsert rechaza resolvedAt anterior a detectedAt`() {
        val error = assertThrows(ResponseStatusException::class.java) {
            service.upsertIncident(
                installationId,
                incidentId,
                request(resolvedAt = detectedAt.minusSeconds(1))
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        verify(incidentRepository, never()).save(any(Incident::class.java))
    }

    @Test
    fun `listado conserva paginacion recibida y total`() {
        val pageable = PageRequest.of(1, 5)
        `when`(incidentRepository.findAllByInstallationIdOrderByDetectedAtDesc(installationId, pageable))
            .thenReturn(PageImpl(emptyList(), pageable, 7))

        val response = service.getIncidents(installationId, pageable)

        assertEquals(1, response.page)
        assertEquals(5, response.size)
        assertEquals(7, response.totalElements)
        assertEquals(2, response.totalPages)
        verify(incidentRepository).findAllByInstallationIdOrderByDetectedAtDesc(installationId, pageable)
    }

    @Test
    fun `PUT trace guarda bytes decodificados y metadatos`() {
        val bytes = ByteArray(12) { it.toByte() }
        val encoded = Base64.getEncoder().encodeToString(bytes)
        `when`(incidentRepository.findByIdAndInstallationId(incidentId, installationId))
            .thenReturn(Optional.of(incident()))
        `when`(incidentTraceRepository.findByIncidentIdAndIncidentInstallationId(incidentId, installationId))
            .thenReturn(Optional.empty())
        `when`(incidentTraceRepository.save(any(IncidentTrace::class.java))).thenAnswer { it.getArgument(0) }

        service.putTrace(installationId, incidentId, traceRequest(encoded))

        val saved = ArgumentCaptor.forClass(IncidentTrace::class.java)
        verify(incidentTraceRepository).save(saved.capture())
        assertArrayEquals(bytes, saved.value.samples)
        assertEquals(1, saved.value.totalSamples)
    }

    @Test
    fun `GET trace devuelve bytes almacenados como Base64`() {
        val bytes = ByteArray(12) { (it * 2).toByte() }
        val trace = IncidentTrace(
            incidentId = incidentId,
            incident = incident(),
            sampleRateHz = 200,
            preTriggerSamples = 1,
            totalSamples = 1,
            accelLsbPerG = 2048,
            gyroLsbPerDpsX10 = 164,
            samples = bytes
        )
        `when`(incidentTraceRepository.findByIncidentIdAndIncidentInstallationId(incidentId, installationId))
            .thenReturn(Optional.of(trace))

        val response = service.getTrace(installationId, incidentId)

        assertEquals(Base64.getEncoder().encodeToString(bytes), response.samplesBase64)
        assertEquals(200, response.sampleRateHz)
    }

    @Test
    fun `PUT trace rechaza Base64 malformado y longitud inconsistente`() {
        `when`(incidentRepository.findByIdAndInstallationId(incidentId, installationId))
            .thenReturn(Optional.of(incident()))

        val malformedError = assertThrows(ResponseStatusException::class.java) {
            service.putTrace(installationId, incidentId, traceRequest("not-base64!"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, malformedError.statusCode)

        val lengthError = assertThrows(ResponseStatusException::class.java) {
            service.putTrace(installationId, incidentId, traceRequest(Base64.getEncoder().encodeToString(byteArrayOf(1))))
        }
        assertEquals(HttpStatus.BAD_REQUEST, lengthError.statusCode)
        verify(incidentTraceRepository, never()).save(any(IncidentTrace::class.java))
    }

    @Test
    fun `PUT trace comprueba el limite antes de decodificar`() {
        `when`(incidentRepository.findByIdAndInstallationId(incidentId, installationId))
            .thenReturn(Optional.of(incident()))

        val error = assertThrows(ResponseStatusException::class.java) {
            service.putTrace(installationId, incidentId, traceRequest("A".repeat(64 * 1024 + 1)))
        }

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.statusCode)
        verify(incidentTraceRepository, never()).save(any(IncidentTrace::class.java))
    }

    @Test
    fun `PUT trace rechaza mas muestras pre-trigger que el total`() {
        `when`(incidentRepository.findByIdAndInstallationId(incidentId, installationId))
            .thenReturn(Optional.of(incident()))

        val error = assertThrows(ResponseStatusException::class.java) {
            service.putTrace(
                installationId,
                incidentId,
                traceRequest(Base64.getEncoder().encodeToString(ByteArray(12))).copy(preTriggerSamples = 2)
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        verify(incidentTraceRepository, never()).save(any(IncidentTrace::class.java))
    }

    @Test
    fun `consultar incidente de otra instalacion responde no encontrado`() {
        `when`(incidentRepository.findByIdAndInstallationId(incidentId, installationId)).thenReturn(Optional.empty())

        val error = assertThrows(ResponseStatusException::class.java) {
            service.getIncident(installationId, incidentId)
        }

        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
    }

    private fun request(
        type: IncidentType = IncidentType.REAL,
        triggerType: TriggerType = TriggerType.IMPACT,
        detectedAt: Instant = this.detectedAt,
        deviceEventKey: String? = "57:3",
        status: IncidentStatus = IncidentStatus.NOT_CONFIRMED,
        resolvedAt: Instant? = null
    ) = IncidentUpsertRequest(
        type = type,
        triggerType = triggerType,
        status = status,
        deviceEventKey = deviceEventKey,
        detectedAt = detectedAt,
        resolvedAt = resolvedAt,
        dispatch = IncidentDispatchRequest(
            smsPrimary = SmsStatus.NOT_ATTEMPTED,
            smsSecondary = SmsStatus.NOT_ATTEMPTED,
            call = CallStatus.NOT_ATTEMPTED
        )
    )

    private fun incident() = Incident(
        id = incidentId,
        installation = installation,
        type = IncidentType.REAL,
        triggerType = TriggerType.IMPACT,
        status = IncidentStatus.NOT_CONFIRMED,
        deviceEventKey = "57:3",
        detectedAt = detectedAt,
        locationSource = LocationSource.NONE,
        smsPrimaryStatus = SmsStatus.NOT_ATTEMPTED,
        smsSecondaryStatus = SmsStatus.NOT_ATTEMPTED,
        callStatus = CallStatus.NOT_ATTEMPTED
    )

    private fun traceRequest(samplesBase64: String) = IncidentTraceRequest(
        sampleRateHz = 200,
        preTriggerSamples = 0,
        totalSamples = 1,
        accelLsbPerG = 2048,
        gyroLsbPerDpsX10 = 164,
        samplesBase64 = samplesBase64
    )
}
