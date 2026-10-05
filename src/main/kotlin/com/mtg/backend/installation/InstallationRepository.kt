package com.mtg.backend.installation

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface InstallationRepository : JpaRepository<Installation, UUID> {
    fun findByTokenHash(tokenHash: ByteArray): Installation?
}
