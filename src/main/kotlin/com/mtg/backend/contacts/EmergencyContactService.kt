package com.mtg.backend.contacts

import com.mtg.backend.common.model.ContactRole
import com.mtg.backend.installation.InstallationRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class EmergencyContactService(
    private val emergencyContactRepository: EmergencyContactRepository,
    private val installationRepository: InstallationRepository
) {

    @Transactional(readOnly = true)
    fun getContacts(installationId: UUID): EmergencyContactsResponse {
        val contacts = emergencyContactRepository.findByInstallationId(installationId)
        return EmergencyContactsResponse(contacts = contacts.map { it.toDto() })
    }

    @Transactional
    fun updateContacts(installationId: UUID, request: UpdateContactsRequest): EmergencyContactsResponse {
        val installation = installationRepository.findById(installationId)
            .orElseThrow { ResponseStatusException(HttpStatus.UNAUTHORIZED, "Instalación no encontrada") }

        val contacts = request.contacts

        // Regla 1: Máximo 2 contactos
        if (contacts.size > 2) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Se permite como máximo dos contactos de emergencia")
        }

        // Regla 2: A lo sumo uno por rol
        val roles = contacts.map { it.role }
        if (roles.size != roles.distinct().size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permite como máximo un contacto por rol (PRIMARY o SECONDARY)")
        }

        // Regla 3: SECONDARY requiere PRIMARY
        val hasPrimary = contacts.any { it.role == ContactRole.PRIMARY }
        val hasSecondary = contacts.any { it.role == ContactRole.SECONDARY }
        if (hasSecondary && !hasPrimary) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "El contacto secundario requiere la presencia de un contacto principal")
        }

        // Regla 4: Teléfonos distintos
        val phones = contacts.map { it.phoneE164 }
        if (phones.size != phones.distinct().size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Los teléfonos de los contactos deben ser distintos")
        }

        // Reemplazo atómico
        emergencyContactRepository.deleteByInstallationId(installationId)

        val entitiesToSave = contacts.map { dto ->
            EmergencyContact(
                id = UUID.randomUUID(),
                installation = installation,
                role = dto.role,
                name = dto.name,
                phoneE164 = dto.phoneE164
            )
        }

        val saved = emergencyContactRepository.saveAll(entitiesToSave)
        return EmergencyContactsResponse(contacts = saved.map { it.toDto() })
    }
}
