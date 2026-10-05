package com.mtg.backend.installation

import com.mtg.backend.common.security.RateLimiterService
import com.mtg.backend.common.security.TokenGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.UUID

class InstallationServiceTest {

    private val installationRepository = mock(InstallationRepository::class.java)
    private val tokenGenerator = TokenGenerator()
    private val rateLimiterService = mock(RateLimiterService::class.java)

    private val service = InstallationService(
        installationRepository = installationRepository,
        tokenGenerator = tokenGenerator,
        rateLimiterService = rateLimiterService
    )

    @Test
    fun `registerInstallation genera token y persiste hash`() {
        val request = RegisterInstallationRequest(
            appVersion = "1.0.0",
            consentAcceptedAt = Instant.now()
        )

        `when`(installationRepository.save(any(Installation::class.java))).thenAnswer { invocation ->
            invocation.getArgument(0) as Installation
        }

        val response = service.registerInstallation(request, "127.0.0.1")

        assertNotNull(response.installationId)
        assertTrue(response.token.startsWith("mcg_"))
        verify(installationRepository).save(any(Installation::class.java))
    }

    @Test
    fun `deleteInstallation borra si existe`() {
        val id = UUID.randomUUID()
        `when`(installationRepository.existsById(id)).thenReturn(true)

        service.deleteInstallation(id)

        verify(installationRepository).deleteById(id)
    }
}
