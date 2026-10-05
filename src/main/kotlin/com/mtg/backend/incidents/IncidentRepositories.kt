package com.mtg.backend.incidents

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface IncidentRepository : JpaRepository<Incident, UUID> {
    @Query(
        value = """
            SELECT 1
            FROM (SELECT pg_advisory_xact_lock(hashtextextended(CAST(:incidentId AS text), 0))) AS incident_lock
        """,
        nativeQuery = true
    )
    fun lockIncidentIdForUpsert(@Param("incidentId") incidentId: UUID): Int

    fun findByIdAndInstallationId(id: UUID, installationId: UUID): Optional<Incident>
    fun findAllByInstallationIdOrderByDetectedAtDesc(installationId: UUID, pageable: Pageable): Page<Incident>
    fun existsByIdAndInstallationId(id: UUID, installationId: UUID): Boolean
}

@Repository
interface IncidentTraceRepository : JpaRepository<IncidentTrace, UUID> {
    fun findByIncidentIdAndIncidentInstallationId(incidentId: UUID, installationId: UUID): Optional<IncidentTrace>
}
