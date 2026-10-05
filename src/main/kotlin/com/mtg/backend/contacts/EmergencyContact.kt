package com.mtg.backend.contacts

import com.mtg.backend.common.model.ContactRole
import com.mtg.backend.installation.Installation
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "emergency_contacts",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_emergency_contacts_installation_role", columnNames = ["installation_id", "role"])
    ]
)
class EmergencyContact(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installation_id", nullable = false)
    var installation: Installation,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16, nullable = false)
    var role: ContactRole,

    @Column(name = "name", length = 60, nullable = false)
    var name: String,

    @Column(name = "phone_e164", length = 16, nullable = false)
    var phoneE164: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmergencyContact) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "EmergencyContact(id=$id, role=$role, name='$name', phoneE164='$phoneE164')"
}
