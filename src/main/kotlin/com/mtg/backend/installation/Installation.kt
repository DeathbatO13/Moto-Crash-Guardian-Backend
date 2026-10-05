package com.mtg.backend.installation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "installations")
class Installation(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "token_hash", nullable = false, unique = true)
    var tokenHash: ByteArray,

    @Column(name = "app_version", length = 32)
    var appVersion: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Installation) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "Installation(id=$id, appVersion=$appVersion, createdAt=$createdAt, lastSeenAt=$lastSeenAt)"
}
