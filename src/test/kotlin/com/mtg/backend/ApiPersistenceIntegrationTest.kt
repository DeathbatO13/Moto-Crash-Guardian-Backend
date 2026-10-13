package com.mtg.backend

import com.mtg.backend.common.security.TokenGenerator
import com.mtg.backend.installation.Installation
import com.mtg.backend.installation.InstallationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import java.time.Instant
import java.util.UUID

/**
 * Contrato HTTP de config, contactos y borrado contra PostgreSQL real (Testcontainers).
 *
 * Las pruebas MVC mockean el servicio y no ejercitan el orden de flush de Hibernate ni las
 * restricciones UNIQUE/ON DELETE CASCADE del esquema; estas si.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class ApiPersistenceIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var installationRepository: InstallationRepository
    @Autowired lateinit var tokenGenerator: TokenGenerator
    @Autowired lateinit var jdbcTemplate: JdbcTemplate

    /** Instalacion con su propio token; no pasa por el registro para no consumir el rate limit. */
    private fun newInstallation(): Pair<UUID, String> {
        val token = "it-token-${UUID.randomUUID()}"
        val installation = installationRepository.save(
            Installation(
                id = UUID.randomUUID(),
                tokenHash = tokenGenerator.hashToken(token),
                appVersion = "1.0.0",
                createdAt = Instant.now(),
                lastSeenAt = Instant.now()
            )
        )
        return installation.id to token
    }

    private fun contactsJson(vararg contacts: Triple<String, String, String>): String =
        contacts.joinToString(prefix = """{"contacts":[""", postfix = "]}") { (role, name, phone) ->
            """{"role":"$role","name":"$name","phoneE164":"$phone"}"""
        }

    private fun putContacts(token: String, body: String) =
        mockMvc.put("/api/v1/me/contacts") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun configJson(riderName: String = "Carlos", impact: Int = 3500) = """
        {"riderName":"$riderName","impactThresholdMg":$impact,"severeImpactThresholdMg":8000,
         "gyroThresholdDps":300,"tiltThresholdDeg":65,"tiltHoldMs":2000,"tiltDetectionEnabled":true,
         "confirmWindowMs":5000,"stillnessToleranceMg":200,"countdownSeconds":20,
         "pitchOffsetCdeg":0,"rollOffsetCdeg":0}
    """.trimIndent()

    private fun putConfig(token: String, body: String) =
        mockMvc.put("/api/v1/me/config") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    @Test
    fun `reemplazar contactos conservando los mismos roles funciona repetidamente`() {
        val (_, token) = newInstallation()
        val first = contactsJson(
            Triple("PRIMARY", "Ana", "+573001112233"),
            Triple("SECONDARY", "Luis", "+573004445566")
        )
        putContacts(token, first).andExpect { status { isOk() } }

        // Mismo rol con datos nuevos: el INSERT no debe chocar con el contacto que se reemplaza.
        val second = contactsJson(
            Triple("PRIMARY", "Ana Maria", "+573009998877"),
            Triple("SECONDARY", "Luis Perez", "+573001231234")
        )
        putContacts(token, second).andExpect {
            status { isOk() }
            jsonPath("$.contacts.length()") { value(2) }
        }

        mockMvc.get("/api/v1/me/contacts") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.contacts[?(@.role=='PRIMARY')].name") { value("Ana Maria") }
            jsonPath("$.contacts[?(@.role=='SECONDARY')].phoneE164") { value("+573001231234") }
        }
    }

    @Test
    fun `contactos admite vaciar la lista`() {
        val (_, token) = newInstallation()
        putContacts(token, contactsJson(Triple("PRIMARY", "Ana", "+573001112233")))
            .andExpect { status { isOk() } }

        putContacts(token, contactsJson()).andExpect {
            status { isOk() }
            jsonPath("$.contacts.length()") { value(0) }
        }
    }

    @Test
    fun `contactos rechaza combinaciones invalidas sin tocar lo guardado`() {
        val (_, token) = newInstallation()
        putContacts(token, contactsJson(Triple("PRIMARY", "Ana", "+573001112233")))
            .andExpect { status { isOk() } }

        val invalid = listOf(
            contactsJson(Triple("SECONDARY", "Luis", "+573004445566")),
            contactsJson(
                Triple("PRIMARY", "Ana", "+573001112233"),
                Triple("SECONDARY", "Luis", "+573001112233")
            ),
            contactsJson(
                Triple("PRIMARY", "Ana", "+573001112233"),
                Triple("PRIMARY", "Luis", "+573004445566")
            ),
            contactsJson(Triple("PRIMARY", "Ana", "3001112233"))
        )
        invalid.forEach { body -> putContacts(token, body).andExpect { status { isBadRequest() } } }

        mockMvc.get("/api/v1/me/contacts") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            jsonPath("$.contacts.length()") { value(1) }
            jsonPath("$.contacts[0].name") { value("Ana") }
        }
    }

    @Test
    fun `config se crea y se actualiza incrementando la revision`() {
        val (_, token) = newInstallation()
        mockMvc.get("/api/v1/me/config") { header("Authorization", "Bearer $token") }
            .andExpect { status { isNotFound() } }

        putConfig(token, configJson()).andExpect {
            status { isOk() }
            jsonPath("$.revision") { value(0) }
        }
        putConfig(token, configJson(riderName = "Carlos Perez", impact = 4200)).andExpect {
            status { isOk() }
            jsonPath("$.revision") { value(1) }
            jsonPath("$.riderName") { value("Carlos Perez") }
            jsonPath("$.impactThresholdMg") { value(4200) }
        }
    }

    @Test
    fun `una instalacion no ve ni modifica los datos de otra`() {
        val (_, tokenA) = newInstallation()
        val (_, tokenB) = newInstallation()
        putContacts(tokenA, contactsJson(Triple("PRIMARY", "Ana", "+573001112233")))
            .andExpect { status { isOk() } }
        putConfig(tokenA, configJson()).andExpect { status { isOk() } }

        mockMvc.get("/api/v1/me/contacts") { header("Authorization", "Bearer $tokenB") }
            .andExpect { jsonPath("$.contacts.length()") { value(0) } }
        mockMvc.get("/api/v1/me/config") { header("Authorization", "Bearer $tokenB") }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `borrar la instalacion elimina todos sus datos y revoca el token`() {
        val (id, token) = newInstallation()
        putContacts(token, contactsJson(Triple("PRIMARY", "Ana", "+573001112233")))
            .andExpect { status { isOk() } }
        putConfig(token, configJson()).andExpect { status { isOk() } }

        mockMvc.delete("/api/v1/me") { header("Authorization", "Bearer $token") }
            .andExpect { status { isNoContent() } }

        listOf("installations" to "id", "user_config" to "installation_id",
            "emergency_contacts" to "installation_id", "incidents" to "installation_id")
            .forEach { (table, column) ->
                assertEquals(
                    0,
                    jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM $table WHERE $column = ?", Int::class.java, id
                    ),
                    "quedaron filas en $table"
                )
            }
        mockMvc.get("/api/v1/me/config") { header("Authorization", "Bearer $token") }
            .andExpect { status { isUnauthorized() } }
    }
}
