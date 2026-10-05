package com.mtg.backend.installation

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class RegisterInstallationRequest(
    @get:Size(max = 32, message = "appVersion no puede superar 32 caracteres")
    val appVersion: String? = null,

    @get:NotNull(message = "consentAcceptedAt es obligatorio para cumplir la Ley 1581 de protección de datos")
    val consentAcceptedAt: Instant? = null
)

data class RegisterInstallationResponse(
    val installationId: UUID,
    val token: String
)
