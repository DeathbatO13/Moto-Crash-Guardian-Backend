package com.mtg.backend.installation

import com.mtg.backend.common.security.CurrentInstallation
import com.mtg.backend.config.OpenApiConfig.Companion.INSTALLATION_BEARER_SCHEME
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class InstallationController(
    private val installationService: InstallationService
) {

    @PostMapping("/installations")
    fun registerInstallation(
        @Valid @RequestBody request: RegisterInstallationRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<RegisterInstallationResponse> {
        val clientIp = extractClientIp(servletRequest)
        val response = installationService.registerInstallation(request, clientIp)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = INSTALLATION_BEARER_SCHEME)
    fun deleteInstallation(
        @CurrentInstallation installationId: UUID
    ) {
        installationService.deleteInstallation(installationId)
    }

    private fun extractClientIp(request: HttpServletRequest): String {
        val forwardedFor = request.getHeader("X-Forwarded-For")
        return if (!forwardedFor.isNullOrBlank()) {
            forwardedFor.split(",").first().trim()
        } else {
            request.remoteAddr ?: "unknown"
        }
    }
}
