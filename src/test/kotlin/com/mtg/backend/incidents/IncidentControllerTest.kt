package com.mtg.backend.incidents

import com.mtg.backend.common.error.GlobalExceptionHandler
import com.mtg.backend.common.model.CallStatus
import com.mtg.backend.common.model.IncidentStatus
import com.mtg.backend.common.model.IncidentType
import com.mtg.backend.common.model.LocationSource
import com.mtg.backend.common.model.SmsStatus
import com.mtg.backend.common.model.TriggerType
import com.mtg.backend.common.security.InstallationTokenFilter
import com.mtg.backend.common.security.TokenGenerator
import com.mtg.backend.config.SecurityConfig
import com.mtg.backend.config.WebMvcConfig
import com.mtg.backend.installation.Installation
import com.mtg.backend.installation.InstallationRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@WebMvcTest(IncidentController::class)
@AutoConfigureMockMvc(addFilters = true)
@Import(
    SecurityConfig::class,
    WebMvcConfig::class,
    GlobalExceptionHandler::class,
    InstallationTokenFilter::class,
    IncidentTraceBodySizeFilter::class,
    TokenGenerator::class
)
class IncidentControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var tokenGenerator: TokenGenerator

    @MockitoBean
    lateinit var incidentService: IncidentService

    @MockitoBean
    lateinit var installationRepository: InstallationRepository

    private fun validAuthHeader(): String {
        val installationId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val token = "mcg-incident-token"
        val installation = Installation(
            id = installationId,
            tokenHash = tokenGenerator.hashToken(token),
            appVersion = "1.0.0",
            createdAt = Instant.now(),
            lastSeenAt = Instant.now()
        )
        `when`(installationRepository.findByTokenHash(tokenGenerator.hashToken(token))).thenReturn(installation)
        return "Bearer $token"
    }

    @Test
    fun `PUT incident crea un incidente nuevo`() {
        val incidentId = UUID.randomUUID()
        val requestBody = """
            {
              "type": "REAL",
              "triggerType": "IMPACT",
              "status": "DISPATCHED",
              "deviceEventKey": "57:3",
              "detectedAt": "2026-09-23T21:43:32.017Z",
              "resolvedAt": "2026-09-23T21:43:58.410Z",
              "peakAccelMg": 5230,
              "peakGyroDps": 412,
              "pitchCdeg": -350,
              "rollCdeg": 8420,
              "location": {
                "latitude": 4.927597,
                "longitude": -74.020355,
                "accuracyM": 8.0,
                "source": "PHONE_GPS",
                "fixAt": "2026-09-23T21:43:51Z"
              },
              "dispatch": { "smsPrimary": "SENT", "smsSecondary": "SENT", "call": "PLACED" },
              "firmwareVersion": "1.0.0",
              "appVersion": "1.0.0 (12)"
            }
        """.trimIndent()

        val response = IncidentResponse(
            id = incidentId,
            type = IncidentType.REAL,
            triggerType = TriggerType.IMPACT,
            status = IncidentStatus.DISPATCHED,
            deviceEventKey = "57:3",
            detectedAt = Instant.parse("2026-09-23T21:43:32.017Z"),
            resolvedAt = Instant.parse("2026-09-23T21:43:58.410Z"),
            receivedAt = Instant.parse("2026-09-23T21:44:00Z"),
            peakAccelMg = 5230,
            peakGyroDps = 412,
            pitchCdeg = -350,
            rollCdeg = 8420,
            location = IncidentLocationResponse(
                latitude = BigDecimal("4.927597"),
                longitude = BigDecimal("-74.020355"),
                accuracyM = 8.0f,
                source = LocationSource.PHONE_GPS,
                fixAt = Instant.parse("2026-09-23T21:43:51Z")
            ),
            dispatch = IncidentDispatchResponse(
                smsPrimary = SmsStatus.SENT,
                smsSecondary = SmsStatus.SENT,
                call = CallStatus.PLACED
            ),
            firmwareVersion = "1.0.0",
            appVersion = "1.0.0 (12)"
        )

        val installationId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val request = IncidentUpsertRequest(
            type = IncidentType.REAL,
            triggerType = TriggerType.IMPACT,
            status = IncidentStatus.DISPATCHED,
            deviceEventKey = "57:3",
            detectedAt = Instant.parse("2026-09-23T21:43:32.017Z"),
            resolvedAt = Instant.parse("2026-09-23T21:43:58.410Z"),
            peakAccelMg = 5230,
            peakGyroDps = 412,
            pitchCdeg = -350,
            rollCdeg = 8420,
            location = IncidentLocationRequest(
                latitude = BigDecimal("4.927597"),
                longitude = BigDecimal("-74.020355"),
                accuracyM = 8.0f,
                source = LocationSource.PHONE_GPS,
                fixAt = Instant.parse("2026-09-23T21:43:51Z")
            ),
            dispatch = IncidentDispatchRequest(
                smsPrimary = SmsStatus.SENT,
                smsSecondary = SmsStatus.SENT,
                call = CallStatus.PLACED
            ),
            firmwareVersion = "1.0.0",
            appVersion = "1.0.0 (12)"
        )
        `when`(incidentService.upsertIncident(installationId, incidentId, request)).thenReturn(UpsertIncidentResult(true, response))

        mockMvc.put("/api/v1/me/incidents/$incidentId") {
            header("Authorization", validAuthHeader())
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(incidentId.toString()) }
            jsonPath("$.status") { value("DISPATCHED") }
        }
    }

    @Test
    fun `PUT trace con payload invalido responde 400`() {
        val incidentId = UUID.randomUUID()
        val requestBody = """
            {
              "sampleRateHz": 200,
              "preTriggerSamples": 100,
              "totalSamples": 200,
              "accelLsbPerG": 2048,
              "gyroLsbPerDpsX10": 164,
              "samplesBase64": "invalid!"
            }
        """.trimIndent()

        mockMvc.put("/api/v1/me/incidents/$incidentId/trace") {
            header("Authorization", validAuthHeader())
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.type") { value("https://motocrashguardian.app/problems/validation") }
            jsonPath("$.title") { value("Validation failed") }
            jsonPath("$.instance") { value("/api/v1/me/incidents/$incidentId/trace") }
            jsonPath("$.errors[0].field") { value("samplesBase64") }
        }
    }

    @Test
    fun `PUT incident existente responde 200`() {
        val incidentId = UUID.randomUUID()
        val installationId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val request = IncidentUpsertRequest(
            type = IncidentType.REAL,
            triggerType = TriggerType.IMPACT,
            status = IncidentStatus.DISPATCHED,
            deviceEventKey = "57:3",
            detectedAt = Instant.parse("2026-09-23T21:43:32.017Z")
        )
        `when`(incidentService.upsertIncident(installationId, incidentId, request))
            .thenReturn(UpsertIncidentResult(false, response(incidentId)))

        mockMvc.put("/api/v1/me/incidents/$incidentId") {
            header("Authorization", validAuthHeader())
            contentType = MediaType.APPLICATION_JSON
            content = """{"type":"REAL","triggerType":"IMPACT","status":"DISPATCHED","deviceEventKey":"57:3","detectedAt":"2026-09-23T21:43:32.017Z"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(incidentId.toString()) }
        }
    }

    @Test
    fun `PUT incident con conflicto de campos inmutables responde 409`() {
        val incidentId = UUID.randomUUID()
        val installationId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val request = IncidentUpsertRequest(
            type = IncidentType.REAL,
            triggerType = TriggerType.IMPACT,
            status = IncidentStatus.DISPATCHED,
            deviceEventKey = "57:3",
            detectedAt = Instant.parse("2026-09-23T21:43:32.017Z")
        )
        `when`(incidentService.upsertIncident(installationId, incidentId, request))
            .thenThrow(ResponseStatusException(HttpStatus.CONFLICT, "immutable fields changed"))

        mockMvc.put("/api/v1/me/incidents/$incidentId") {
            header("Authorization", validAuthHeader())
            contentType = MediaType.APPLICATION_JSON
            content = """{"type":"REAL","triggerType":"IMPACT","status":"DISPATCHED","deviceEventKey":"57:3","detectedAt":"2026-09-23T21:43:32.017Z"}"""
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `GET incidents rechaza paginacion fuera de rango`() {
        mockMvc.get("/api/v1/me/incidents?page=-1&size=101") {
            header("Authorization", validAuthHeader())
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET incidents devuelve pagina solicitada`() {
        val installationId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        `when`(incidentService.getIncidents(installationId, PageRequest.of(1, 5)))
            .thenReturn(IncidentListResponse(emptyList(), page = 1, size = 5, totalElements = 7, totalPages = 2))

        mockMvc.get("/api/v1/me/incidents?page=1&size=5") {
            header("Authorization", validAuthHeader())
        }.andExpect {
            status { isOk() }
            jsonPath("$.page") { value(1) }
            jsonPath("$.size") { value(5) }
            jsonPath("$.totalElements") { value(7) }
        }
    }

    @Test
    fun `PUT trace valida contrato HTTP y devuelve 204`() {
        val incidentId = UUID.randomUUID()
        mockMvc.put("/api/v1/me/incidents/$incidentId/trace") {
            header("Authorization", validAuthHeader())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "sampleRateHz": 200,
                  "preTriggerSamples": 1,
                  "totalSamples": 1,
                  "accelLsbPerG": 2048,
                  "gyroLsbPerDpsX10": 164,
                  "samplesBase64": "AAECAwQFBgcICQoL"
                }
            """.trimIndent()
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `GET trace devuelve la traza persistida`() {
        val incidentId = UUID.randomUUID()
        val response = IncidentTraceResponse(
            sampleRateHz = 200,
            preTriggerSamples = 1,
            totalSamples = 1,
            accelLsbPerG = 2048,
            gyroLsbPerDpsX10 = 164,
            samplesBase64 = "AAECAwQFBgcICQoL",
            createdAt = Instant.parse("2026-10-04T12:00:00Z")
        )
        `when`(incidentService.getTrace(UUID.fromString("33333333-3333-3333-3333-333333333333"), incidentId))
            .thenReturn(response)

        mockMvc.get("/api/v1/me/incidents/$incidentId/trace") {
            header("Authorization", validAuthHeader())
        }.andExpect {
            status { isOk() }
            jsonPath("$.samplesBase64") { value("AAECAwQFBgcICQoL") }
            jsonPath("$.totalSamples") { value(1) }
        }
    }

    @Test
    fun `PUT incident rechaza coordenadas fuera de rango`() {
        val incidentId = UUID.randomUUID()
        mockMvc.put("/api/v1/me/incidents/$incidentId") {
            header("Authorization", validAuthHeader())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "type": "REAL",
                  "triggerType": "IMPACT",
                  "status": "NOT_CONFIRMED",
                  "detectedAt": "2026-09-23T21:43:32.017Z",
                  "location": {
                    "latitude": 91,
                    "longitude": -74,
                    "source": "PHONE_GPS"
                  }
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `PUT trace rechaza cuerpo mayor a 64 KB`() {
        val incidentId = UUID.randomUUID()
        val oversizedSamples = "A".repeat(64 * 1024)
        mockMvc.put("/api/v1/me/incidents/$incidentId/trace") {
            header("Authorization", validAuthHeader())
            contentType = MediaType.APPLICATION_JSON
            content = """{"sampleRateHz":200,"preTriggerSamples":1,"totalSamples":1,"accelLsbPerG":2048,"gyroLsbPerDpsX10":164,"samplesBase64":"$oversizedSamples"}"""
        }.andExpect {
            status { isEqualTo(413) }
        }
    }

    private fun response(incidentId: UUID) = IncidentResponse(
        id = incidentId,
        type = IncidentType.REAL,
        triggerType = TriggerType.IMPACT,
        status = IncidentStatus.DISPATCHED,
        deviceEventKey = "57:3",
        detectedAt = Instant.parse("2026-09-23T21:43:32.017Z"),
        resolvedAt = null,
        receivedAt = Instant.parse("2026-09-23T21:44:00Z"),
        peakAccelMg = null,
        peakGyroDps = null,
        pitchCdeg = null,
        rollCdeg = null,
        location = null,
        dispatch = IncidentDispatchResponse(
            smsPrimary = SmsStatus.NOT_ATTEMPTED,
            smsSecondary = SmsStatus.NOT_ATTEMPTED,
            call = CallStatus.NOT_ATTEMPTED
        ),
        firmwareVersion = null,
        appVersion = null
    )
}






