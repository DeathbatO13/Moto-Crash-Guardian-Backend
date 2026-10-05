package com.mtg.backend.incidents

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "incident_traces")
class IncidentTrace(
    @Id
    @Column(name = "incident_id", nullable = false)
    var incidentId: UUID? = null,

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    var incident: Incident,

    @Column(name = "sample_rate_hz", nullable = false)
    var sampleRateHz: Int,

    @Column(name = "pre_trigger_samples", nullable = false)
    var preTriggerSamples: Int,

    @Column(name = "total_samples", nullable = false)
    var totalSamples: Int,

    @Column(name = "accel_lsb_per_g", nullable = false)
    var accelLsbPerG: Int,

    @Column(name = "gyro_lsb_per_dps_x10", nullable = false)
    var gyroLsbPerDpsX10: Int,

    @Column(name = "samples", nullable = false)
    var samples: ByteArray,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IncidentTrace) return false
        return incidentId == other.incidentId
    }

    override fun hashCode(): Int = incidentId?.hashCode() ?: 0

    override fun toString(): String =
        "IncidentTrace(incidentId=$incidentId, sampleRateHz=$sampleRateHz, totalSamples=$totalSamples, createdAt=$createdAt)"
}
