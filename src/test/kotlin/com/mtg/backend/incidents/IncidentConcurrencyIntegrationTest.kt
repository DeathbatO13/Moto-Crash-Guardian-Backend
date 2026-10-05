package com.mtg.backend.incidents

import com.mtg.backend.TestcontainersConfiguration
import com.mtg.backend.common.model.IncidentStatus
import com.mtg.backend.common.model.IncidentType
import com.mtg.backend.common.model.TriggerType
import com.mtg.backend.installation.Installation
import com.mtg.backend.installation.InstallationRepository
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class IncidentConcurrencyIntegrationTest {

    @Autowired
    lateinit var incidentService: IncidentService

    @Autowired
    lateinit var incidentRepository: IncidentRepository

    @Autowired
    lateinit var installationRepository: InstallationRepository

    @Autowired
    lateinit var flyway: Flyway

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `Flyway aplica V1 en esquema vacio validado por Hibernate`() {
        val migration = flyway.info().applied().single { it.version?.version == "1" }

        assertEquals("SUCCESS", migration.state.name)
        assertEquals(5, jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = current_schema()
              AND table_name IN ('installations', 'user_config', 'emergency_contacts', 'incidents', 'incident_traces')
            """.trimIndent(),
            Int::class.java
        ))

        val incidentTraceForeignKey = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.table_constraints
            WHERE constraint_schema = current_schema()
              AND table_name = 'incident_traces'
              AND constraint_type = 'FOREIGN KEY'
            """.trimIndent(),
            Int::class.java
        )
        assertEquals(1, incidentTraceForeignKey)
    }

    @Test
    fun `traza persiste y recupera metadatos y bytes exactos en PostgreSQL`() {
        val installation = createInstallation()
        val incidentId = UUID.randomUUID()
        val detectedAt = Instant.parse("2026-10-04T12:00:00Z")
        val samples = ByteArray(24) { (it * 7).toByte() }

        try {
            incidentService.upsertIncident(
                installation.id,
                incidentId,
                IncidentUpsertRequest(
                    type = IncidentType.REAL,
                    triggerType = TriggerType.IMPACT,
                    status = IncidentStatus.NOT_CONFIRMED,
                    deviceEventKey = "57:4",
                    detectedAt = detectedAt
                )
            )
            incidentService.putTrace(
                installation.id,
                incidentId,
                IncidentTraceRequest(
                    sampleRateHz = 100,
                    preTriggerSamples = 1,
                    totalSamples = 2,
                    accelLsbPerG = 16384,
                    gyroLsbPerDpsX10 = 1310,
                    samplesBase64 = Base64.getEncoder().encodeToString(samples)
                )
            )

            val trace = incidentService.getTrace(installation.id, incidentId)

            assertEquals(100, trace.sampleRateHz)
            assertEquals(1, trace.preTriggerSamples)
            assertEquals(2, trace.totalSamples)
            assertEquals(16384, trace.accelLsbPerG)
            assertEquals(1310, trace.gyroLsbPerDpsX10)
            assertEquals(samples.toList(), Base64.getDecoder().decode(trace.samplesBase64).toList())
            assertNotNull(trace.createdAt)
            assertEquals(
                24,
                jdbcTemplate.queryForObject(
                    "SELECT octet_length(samples) FROM incident_traces WHERE incident_id = ?",
                    Int::class.java,
                    incidentId
                )
            )
        } finally {
            installationRepository.deleteById(installation.id)
        }
    }

    @Test
    fun `borrar instalacion elimina incidentes y trazas por cascada en PostgreSQL`() {
        val installation = createInstallation()
        val incidentId = UUID.randomUUID()

        incidentService.upsertIncident(
            installation.id,
            incidentId,
            IncidentUpsertRequest(
                type = IncidentType.REAL,
                triggerType = TriggerType.IMPACT,
                status = IncidentStatus.NOT_CONFIRMED,
                deviceEventKey = "57:5",
                detectedAt = Instant.parse("2026-10-04T12:00:00Z")
            )
        )
        incidentService.putTrace(
            installation.id,
            incidentId,
            IncidentTraceRequest(
                sampleRateHz = 100,
                preTriggerSamples = 0,
                totalSamples = 1,
                accelLsbPerG = 16384,
                gyroLsbPerDpsX10 = 1310,
                samplesBase64 = Base64.getEncoder().encodeToString(ByteArray(12))
            )
        )

        installationRepository.deleteById(installation.id)
        installationRepository.flush()

        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM incidents WHERE id = ?",
                Int::class.java,
                incidentId
            )
        )
        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM incident_traces WHERE incident_id = ?",
                Int::class.java,
                incidentId
            )
        )
    }

    @Test
    fun `upserts concurrentes del mismo UUID crean un solo incidente`() {
        val installation = createInstallation()
        val incidentId = UUID.randomUUID()
        val request = IncidentUpsertRequest(
            type = IncidentType.REAL,
            triggerType = TriggerType.IMPACT,
            status = IncidentStatus.NOT_CONFIRMED,
            deviceEventKey = "57:3",
            detectedAt = Instant.parse("2026-10-04T12:00:00Z")
        )
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        try {
            val results = (1..2).map {
                executor.submit<UpsertIncidentResult> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS)) { "Timed out waiting to start concurrent upserts" }
                    incidentService.upsertIncident(installation.id, incidentId, request)
                }
            }

            check(ready.await(5, TimeUnit.SECONDS)) { "Concurrent upsert workers did not become ready" }
            start.countDown()
            val completed = results.map { it.get(10, TimeUnit.SECONDS) }

            assertEquals(1, completed.count { it.created })
            assertEquals(1, completed.count { !it.created })
            assertTrue(incidentRepository.existsByIdAndInstallationId(incidentId, installation.id))
        } finally {
            executor.shutdownNow()
            installationRepository.deleteById(installation.id)
        }
    }

    private fun createInstallation(): Installation =
        installationRepository.save(
            Installation(tokenHash = UUID.randomUUID().toString().toByteArray())
        )
}
