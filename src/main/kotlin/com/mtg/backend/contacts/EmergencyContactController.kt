package com.mtg.backend.contacts

import com.mtg.backend.common.security.CurrentInstallation
import com.mtg.backend.config.OpenApiConfig.Companion.INSTALLATION_BEARER_SCHEME
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/me/contacts")
@SecurityRequirement(name = INSTALLATION_BEARER_SCHEME)
class EmergencyContactController(
    private val emergencyContactService: EmergencyContactService
) {

    @GetMapping
    fun getContacts(
        @CurrentInstallation installationId: UUID
    ): EmergencyContactsResponse =
        emergencyContactService.getContacts(installationId)

    @PutMapping
    fun updateContacts(
        @CurrentInstallation installationId: UUID,
        @Valid @RequestBody request: UpdateContactsRequest
    ): EmergencyContactsResponse =
        emergencyContactService.updateContacts(installationId, request)
}
