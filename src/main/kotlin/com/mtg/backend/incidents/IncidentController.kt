package com.mtg.backend.incidents

import com.mtg.backend.common.security.CurrentInstallation
import com.mtg.backend.config.OpenApiConfig.Companion.INSTALLATION_BEARER_SCHEME
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/api/v1/me")
@SecurityRequirement(name = INSTALLATION_BEARER_SCHEME)
class IncidentController(
    private val incidentService: IncidentService
) {

    @GetMapping("/incidents")
    fun getIncidents(
        @CurrentInstallation installationId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): IncidentListResponse {
        if (page < 0 || size !in 1..100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "page debe ser >= 0 y size debe estar entre 1 y 100")
        }
        val pageable: Pageable = PageRequest.of(page, size)
        return incidentService.getIncidents(installationId, pageable)
    }

    @GetMapping("/incidents/{incidentId}")
    fun getIncident(
        @CurrentInstallation installationId: UUID,
        @PathVariable incidentId: UUID
    ): IncidentResponse =
        incidentService.getIncident(installationId, incidentId)

    @PutMapping("/incidents/{incidentId}")
    fun upsertIncident(
        @CurrentInstallation installationId: UUID,
        @PathVariable incidentId: UUID,
        @Valid @RequestBody request: IncidentUpsertRequest
    ): ResponseEntity<IncidentResponse> {
        val result = incidentService.upsertIncident(installationId, incidentId, request)
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(result.incident)
    }

    @PutMapping("/incidents/{incidentId}/trace")
    fun putTrace(
        @CurrentInstallation installationId: UUID,
        @PathVariable incidentId: UUID,
        @Valid @RequestBody request: IncidentTraceRequest
    ): ResponseEntity<Unit> {
        incidentService.putTrace(installationId, incidentId, request)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/incidents/{incidentId}/trace")
    fun getTrace(
        @CurrentInstallation installationId: UUID,
        @PathVariable incidentId: UUID
    ): IncidentTraceResponse =
        incidentService.getTrace(installationId, incidentId)
}
