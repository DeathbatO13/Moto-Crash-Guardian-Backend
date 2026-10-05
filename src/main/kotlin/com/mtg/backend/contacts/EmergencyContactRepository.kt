package com.mtg.backend.contacts

import com.mtg.backend.common.model.ContactRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EmergencyContactRepository : JpaRepository<EmergencyContact, UUID> {
    fun findByInstallationId(installationId: UUID): List<EmergencyContact>
    fun findByInstallationIdAndRole(installationId: UUID, role: ContactRole): EmergencyContact?
    fun deleteByInstallationId(installationId: UUID)
}
