package com.mtg.backend.config

import com.mtg.backend.installation.Installation
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_config")
class UserConfig(
    @Id
    @Column(name = "installation_id", nullable = false)
    var installationId: UUID? = null,

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installation_id", nullable = false)
    var installation: Installation,

    @Column(name = "rider_name", length = 60, nullable = false)
    var riderName: String,

    @Column(name = "impact_threshold_mg", nullable = false)
    var impactThresholdMg: Int,

    @Column(name = "severe_impact_threshold_mg", nullable = false)
    var severeImpactThresholdMg: Int,

    @Column(name = "gyro_threshold_dps", nullable = false)
    var gyroThresholdDps: Int,

    @Column(name = "tilt_threshold_deg", nullable = false)
    var tiltThresholdDeg: Int,

    @Column(name = "tilt_hold_ms", nullable = false)
    var tiltHoldMs: Int,

    @Column(name = "tilt_detection_enabled", nullable = false)
    var tiltDetectionEnabled: Boolean,

    @Column(name = "confirm_window_ms", nullable = false)
    var confirmWindowMs: Int,

    @Column(name = "stillness_tolerance_mg", nullable = false)
    var stillnessToleranceMg: Int,

    @Column(name = "countdown_seconds", nullable = false)
    var countdownSeconds: Int,

    @Column(name = "pitch_offset_cdeg", nullable = false)
    var pitchOffsetCdeg: Int,

    @Column(name = "roll_offset_cdeg", nullable = false)
    var rollOffsetCdeg: Int,

    @Version
    @Column(name = "revision", nullable = false)
    var revision: Long = 0L,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserConfig) return false
        return installationId == other.installationId
    }

    override fun hashCode(): Int = installationId?.hashCode() ?: 0

    override fun toString(): String =
        "UserConfig(installationId=$installationId, riderName='$riderName', revision=$revision, updatedAt=$updatedAt)"
}
