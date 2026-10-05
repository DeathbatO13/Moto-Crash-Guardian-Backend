package com.mtg.backend.incidents

import com.mtg.backend.common.model.CallStatus
import com.mtg.backend.common.model.IncidentStatus
import com.mtg.backend.common.model.IncidentType
import com.mtg.backend.common.model.LocationSource
import com.mtg.backend.common.model.SmsStatus
import com.mtg.backend.common.model.TriggerType
import com.mtg.backend.installation.Installation
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "incidents",
    indexes = [
        Index(name = "ix_incidents_installation_detected", columnList = "installation_id, detected_at DESC")
    ]
)
class Incident(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installation_id", nullable = false, updatable = false)
    var installation: Installation,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 16, nullable = false, updatable = false)
    val type: IncidentType,

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", length = 20, nullable = false, updatable = false)
    val triggerType: TriggerType,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 24, nullable = false)
    var status: IncidentStatus,

    @Column(name = "device_event_key", length = 16, updatable = false)
    val deviceEventKey: String? = null,

    @Column(name = "detected_at", nullable = false, updatable = false)
    val detectedAt: Instant,

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,

    @Column(name = "received_at", nullable = false, updatable = false)
    val receivedAt: Instant = Instant.now(),

    @Column(name = "peak_accel_mg")
    var peakAccelMg: Int? = null,

    @Column(name = "peak_gyro_dps")
    var peakGyroDps: Int? = null,

    @Column(name = "pitch_cdeg")
    var pitchCdeg: Int? = null,

    @Column(name = "roll_cdeg")
    var rollCdeg: Int? = null,

    @Column(name = "latitude", precision = 9, scale = 6)
    var latitude: BigDecimal? = null,

    @Column(name = "longitude", precision = 9, scale = 6)
    var longitude: BigDecimal? = null,

    @Column(name = "location_accuracy_m")
    var locationAccuracyM: Float? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "location_source", length = 16, nullable = false)
    var locationSource: LocationSource,

    @Column(name = "location_fix_at")
    var locationFixAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "sms_primary_status", length = 16, nullable = false)
    var smsPrimaryStatus: SmsStatus,

    @Enumerated(EnumType.STRING)
    @Column(name = "sms_secondary_status", length = 16, nullable = false)
    var smsSecondaryStatus: SmsStatus,

    @Enumerated(EnumType.STRING)
    @Column(name = "call_status", length = 16, nullable = false)
    var callStatus: CallStatus,

    @Column(name = "firmware_version", length = 16)
    var firmwareVersion: String? = null,

    @Column(name = "app_version", length = 32)
    var appVersion: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Incident) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "Incident(id=$id, type=$type, triggerType=$triggerType, status=$status, detectedAt=$detectedAt)"
}
