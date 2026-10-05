package com.mtg.backend.config

import com.mtg.backend.common.error.GlobalExceptionHandler
import com.mtg.backend.common.security.InstallationTokenFilter
import com.mtg.backend.common.security.TokenGenerator
import com.mtg.backend.config.SecurityConfig
import com.mtg.backend.config.WebMvcConfig
import com.mtg.backend.installation.Installation
import com.mtg.backend.installation.InstallationController
import com.mtg.backend.installation.InstallationRepository
import com.mtg.backend.installation.InstallationService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.core.properties.SwaggerUiOAuthProperties
import org.springdoc.core.properties.SwaggerUiConfigProperties
import org.springdoc.core.configuration.SpringDocConfiguration
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration
import org.springdoc.webmvc.ui.SwaggerConfig
import java.time.Instant
import java.util.UUID

@WebMvcTest(UserConfigController::class, InstallationController::class)
@AutoConfigureMockMvc(addFilters = true)
@ImportAutoConfiguration(
    SpringDocConfiguration::class,
    SpringDocWebMvcConfiguration::class,
    SwaggerConfig::class
)
@EnableConfigurationProperties(
    SpringDocConfigProperties::class,
    SwaggerUiConfigProperties::class,
    SwaggerUiOAuthProperties::class
)
@Import(
    OpenApiConfig::class,
    SecurityConfig::class,
    WebMvcConfig::class,
    GlobalExceptionHandler::class,
    InstallationTokenFilter::class,
    TokenGenerator::class
)
class UserConfigControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var tokenGenerator: TokenGenerator

    @MockitoBean
    lateinit var userConfigService: UserConfigService

    @MockitoBean
    lateinit var installationRepository: InstallationRepository

    @MockitoBean
    lateinit var installationService: InstallationService

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T {
        Mockito.any<T>()
        return null as T
    }

    private fun validAuthHeader(): String {
        val installationId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val token = "mcg-config-token"
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
    fun `GET me config devuelve la configuracion actual`() {
        val installationId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val response = UserConfigResponse(
            riderName = "Ana",
            impactThresholdMg = 3500,
            severeImpactThresholdMg = 9000,
            gyroThresholdDps = 420,
            tiltThresholdDeg = 65,
            tiltHoldMs = 1200,
            tiltDetectionEnabled = true,
            confirmWindowMs = 5000,
            stillnessToleranceMg = 200,
            countdownSeconds = 30,
            pitchOffsetCdeg = 120,
            rollOffsetCdeg = -90,
            revision = 1L,
            updatedAt = Instant.parse("2026-10-04T12:00:00Z")
        )
        `when`(userConfigService.getConfig(installationId)).thenReturn(response)

        mockMvc.get("/api/v1/me/config") {
            header("Authorization", validAuthHeader())
        }.andExpect {
            status { isOk() }
            jsonPath("$.riderName") { value("Ana") }
            jsonPath("$.countdownSeconds") { value(30) }
        }
    }

    @Test
    fun `PUT me config con payload invalido responde 400`() {
        val requestBody = """
            {
              "riderName": "",
              "impactThresholdMg": 1500,
              "severeImpactThresholdMg": 5000,
              "gyroThresholdDps": 100,
              "tiltThresholdDeg": 40,
              "tiltHoldMs": 300,
              "tiltDetectionEnabled": true,
              "confirmWindowMs": 2000,
              "stillnessToleranceMg": 50,
              "countdownSeconds": 5,
              "pitchOffsetCdeg": -10000,
              "rollOffsetCdeg": 11000
            }
        """.trimIndent()

        mockMvc.put("/api/v1/me/config") {
            header("Authorization", validAuthHeader())
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.type") { value("https://motocrashguardian.app/problems/validation") }
            jsonPath("$.title") { value("Validation failed") }
            jsonPath("$.instance") { value("/api/v1/me/config") }
            jsonPath("$.errors[0].field") { value("confirmWindowMs") }
        }
    }

    @Test
    fun `GET OpenAPI documenta endpoints y autenticacion`() {
        mockMvc.get("/v3/api-docs") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.info.title") { value("Moto Crash Guardian API") }
            jsonPath("$.info.description") {
                value(org.hamcrest.Matchers.containsString("RFC 9457"))
            }
            jsonPath("$.paths['/api/v1/me/config']") { exists() }
            jsonPath("$.paths['/api/v1/me/config'].get.security[0].installationBearer") { exists() }
            jsonPath("$.paths['/api/v1/me'].delete.security[0].installationBearer") { exists() }
            jsonPath("$.paths['/api/v1/installations'].post") { exists() }
            jsonPath("$.components.securitySchemes.installationBearer.scheme") { value("bearer") }
        }
    }

    @Test
    fun `GET Swagger UI esta publicado`() {
        mockMvc.get("/swagger-ui/index.html").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
        }
    }

}
