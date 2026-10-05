package com.mtg.backend.installation

import com.mtg.backend.common.security.RateLimiterService
import com.mtg.backend.common.security.TokenGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class InstallationService(
    private val installationRepository: InstallationRepository,
    private val tokenGenerator: TokenGenerator,
    private val rateLimiterService: RateLimiterService
) {

    /**
     * Registra una nueva instalación, aplicando rate limit por IP,
     * generando un token seguro y guardando únicamente su hash SHA-256.
     */
    @Transactional
    fun registerInstallation(request: RegisterInstallationRequest, clientIp: String): RegisterInstallationResponse {
        rateLimiterService.checkRegistrationAllowed(clientIp)

        val rawToken = tokenGenerator.generateToken()
        val tokenHash = tokenGenerator.hashToken(rawToken)

        val installation = Installation(
            id = UUID.randomUUID(),
            tokenHash = tokenHash,
            appVersion = request.appVersion,
            createdAt = Instant.now(),
            lastSeenAt = Instant.now()
        )

        val saved = installationRepository.save(installation)

        return RegisterInstallationResponse(
            installationId = saved.id,
            token = rawToken
        )
    }

    /**
     * Borra la instalación y todos sus datos en cascada (ON DELETE CASCADE en PostgreSQL).
     */
    @Transactional
    fun deleteInstallation(installationId: UUID) {
        if (installationRepository.existsById(installationId)) {
            installationRepository.deleteById(installationId)
        }
    }
}
