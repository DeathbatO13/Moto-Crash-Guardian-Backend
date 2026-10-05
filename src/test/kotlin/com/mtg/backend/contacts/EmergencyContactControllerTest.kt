package com.mtg.backend.contacts

import com.mtg.backend.common.error.GlobalExceptionHandler
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
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import java.time.Instant
import java.util.UUID

@WebMvcTest(EmergencyContactController::class)
@AutoConfigureMockMvc(addFilters = true)
@Import(
    SecurityConfig::class,
    WebMvcConfig::class,
    GlobalExceptionHandler::class,
    InstallationTokenFilter::class,
    TokenGenerator::class
)
class EmergencyContactControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var tokenGenerator: TokenGenerator

    @MockitoBean
    lateinit var emergencyContactService: EmergencyContactService

    @MockitoBean
    lateinit var installationRepository: InstallationRepository

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T {
        Mockito.any<T>()
        return null as T
    }

    private fun validAuthHeader(): String {
        val installationId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val token = "mcg-contact-token"
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
    fun `GET me contacts devuelve los contactos guardados`() {
        val installationId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val response = EmergencyContactsResponse(
            contacts = listOf(
                EmergencyContactDto(
                    role = com.mtg.backend.common.model.ContactRole.PRIMARY,
                    name = "Maria",
                    phoneE164 = "+573001234567"
                )
            )
        )
        `when`(emergencyContactService.getContacts(installationId)).thenReturn(response)

        mockMvc.get("/api/v1/me/contacts") {
            header("Authorization", validAuthHeader())
        }.andExpect {
            status { isOk() }
            jsonPath("$.contacts[0].name") { value("Maria") }
            jsonPath("$.contacts[0].phoneE164") { value("+573001234567") }
        }
    }

    @Test
    fun `PUT me contacts con contacto invalido responde 400`() {
        val requestBody = """
            {
              "contacts": [
                {
                  "role": "SECONDARY",
                  "name": "",
                  "phoneE164": "573001234567"
                }
              ]
            }
        """.trimIndent()

        mockMvc.put("/api/v1/me/contacts") {
            header("Authorization", validAuthHeader())
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.type") { value("https://motocrashguardian.app/problems/validation") }
            jsonPath("$.title") { value("Validation failed") }
            jsonPath("$.instance") { value("/api/v1/me/contacts") }
            jsonPath("$.errors[0].field") { value("contacts[0].name") }
        }
    }
}




