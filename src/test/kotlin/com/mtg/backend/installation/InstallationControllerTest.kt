package com.mtg.backend.installation

import com.mtg.backend.common.error.GlobalExceptionHandler
import com.mtg.backend.common.security.InstallationTokenFilter
import com.mtg.backend.common.security.TokenGenerator
import com.mtg.backend.config.SecurityConfig
import com.mtg.backend.config.WebMvcConfig
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@WebMvcTest(InstallationController::class)
@AutoConfigureMockMvc(addFilters = true)
@Import(
    SecurityConfig::class,
    WebMvcConfig::class,
    GlobalExceptionHandler::class,
    InstallationTokenFilter::class,
    TokenGenerator::class
)
class InstallationControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var installationService: InstallationService

    @MockitoBean
    lateinit var installationRepository: InstallationRepository

    @Autowired
    lateinit var tokenGenerator: TokenGenerator

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T {
        Mockito.any<T>()
        return null as T
    }

    @Test
    fun `POST installations retorna 201 Created con token`() {
        val installationId = UUID.randomUUID()
        `when`(installationService.registerInstallation(anyNonNull(), anyNonNull()))
            .thenReturn(RegisterInstallationResponse(installationId, "mcg_test_token_123456"))

        val requestBody = """
            {
                "appVersion": "1.0.0",
                "consentAcceptedAt": "2026-10-04T12:00:00Z"
            }
        """.trimIndent()

        mockMvc.post("/api/v1/installations") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isCreated() }
            jsonPath("$.installationId") { value(installationId.toString()) }
            jsonPath("$.token") { value("mcg_test_token_123456") }
        }
    }

    @Test
    fun `POST installations sin consentimiento retorna 400 Bad Request`() {
        val requestBody = """
            {
                "appVersion": "1.0.0"
            }
        """.trimIndent()

        mockMvc.post("/api/v1/installations") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.type") { value("https://motocrashguardian.app/problems/validation") }
            jsonPath("$.title") { value("Validation failed") }
            jsonPath("$.instance") { value("/api/v1/installations") }
            jsonPath("$.errors[0].field") { value("consentAcceptedAt") }
        }
    }

    @Test
    fun `POST installations con JSON malformado responde Problem Details`() {
        mockMvc.post("/api/v1/installations") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"appVersion":"""
        }.andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("https://motocrashguardian.app/problems/validation") }
            jsonPath("$.status") { value(400) }
            jsonPath("$.instance") { value("/api/v1/installations") }
        }
    }

    @Test
    fun `DELETE me sin autenticacion retorna 401 Unauthorized`() {
        mockMvc.delete("/api/v1/me").andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value("https://motocrashguardian.app/problems/unauthorized") }
            jsonPath("$.status") { value(401) }
            jsonPath("$.instance") { value("/api/v1/me") }
        }
    }

    @Test
    fun `DELETE me con token invalido retorna 401 Unauthorized`() {
        mockMvc.delete("/api/v1/me") {
            header("Authorization", "Bearer mcg_token_inexistente")
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `DELETE me con token valido retorna 204 No Content`() {
        val token = "mcg_valid_token_12345"
        val tokenHash = tokenGenerator.hashToken(token)
        val installationId = UUID.randomUUID()

        val installation = Installation(
            id = installationId,
            tokenHash = tokenHash,
            appVersion = "1.0.0",
            createdAt = Instant.now(),
            lastSeenAt = Instant.now()
        )

        `when`(installationRepository.findByTokenHash(tokenHash)).thenReturn(installation)

        mockMvc.delete("/api/v1/me") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isNoContent() }
        }
    }
}
