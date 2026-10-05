package com.mtg.backend.config

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
@RequestMapping("/api/v1/me/config")
@SecurityRequirement(name = INSTALLATION_BEARER_SCHEME)
class UserConfigController(
    private val userConfigService: UserConfigService
) {

    @GetMapping
    fun getConfig(
        @CurrentInstallation installationId: UUID
    ): UserConfigResponse =
        userConfigService.getConfig(installationId)

    @PutMapping
    fun saveConfig(
        @CurrentInstallation installationId: UUID,
        @Valid @RequestBody request: UserConfigRequest
    ): UserConfigResponse =
        userConfigService.saveConfig(installationId, request)
}
