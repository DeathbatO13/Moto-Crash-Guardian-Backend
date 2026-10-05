CREATE TABLE installations (
    id              UUID PRIMARY KEY,
    token_hash      BYTEA        NOT NULL UNIQUE,          -- SHA-256 del token
    app_version     VARCHAR(32),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_config (
    installation_id             UUID PRIMARY KEY REFERENCES installations(id) ON DELETE CASCADE,
    rider_name                  VARCHAR(60)  NOT NULL,
    impact_threshold_mg         INT NOT NULL CHECK (impact_threshold_mg BETWEEN 2000 AND 6000),
    severe_impact_threshold_mg  INT NOT NULL CHECK (severe_impact_threshold_mg BETWEEN 6000 AND 16000),
    gyro_threshold_dps          INT NOT NULL CHECK (gyro_threshold_dps BETWEEN 150 AND 1000),
    tilt_threshold_deg          INT NOT NULL CHECK (tilt_threshold_deg BETWEEN 50 AND 80),
    tilt_hold_ms                INT NOT NULL CHECK (tilt_hold_ms BETWEEN 500 AND 5000),
    tilt_detection_enabled      BOOLEAN NOT NULL,
    confirm_window_ms           INT NOT NULL CHECK (confirm_window_ms BETWEEN 3000 AND 10000),
    stillness_tolerance_mg      INT NOT NULL CHECK (stillness_tolerance_mg BETWEEN 100 AND 400),
    countdown_seconds           INT NOT NULL CHECK (countdown_seconds BETWEEN 10 AND 60),
    pitch_offset_cdeg           INT NOT NULL,
    roll_offset_cdeg            INT NOT NULL,
    revision                    BIGINT NOT NULL,             -- incremento optimista (@Version)
    updated_at                  TIMESTAMPTZ NOT NULL
);

CREATE TABLE emergency_contacts (
    id               UUID PRIMARY KEY,
    installation_id  UUID NOT NULL REFERENCES installations(id) ON DELETE CASCADE,
    role             VARCHAR(16) NOT NULL CHECK (role IN ('PRIMARY','SECONDARY')),
    name             VARCHAR(60) NOT NULL,
    phone_e164       VARCHAR(16) NOT NULL CHECK (phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    UNIQUE (installation_id, role)
);

CREATE TABLE incidents (
    id                    UUID PRIMARY KEY,                 -- generado en la app
    installation_id       UUID NOT NULL REFERENCES installations(id) ON DELETE CASCADE,
    type                  VARCHAR(16) NOT NULL,
    trigger_type          VARCHAR(20) NOT NULL,
    status                VARCHAR(24) NOT NULL,
    device_event_key      VARCHAR(16),
    detected_at           TIMESTAMPTZ NOT NULL,
    resolved_at           TIMESTAMPTZ,
    received_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    peak_accel_mg         INT,
    peak_gyro_dps         INT,
    pitch_cdeg            INT,
    roll_cdeg             INT,
    latitude              NUMERIC(9,6),
    longitude             NUMERIC(9,6),
    location_accuracy_m   REAL,
    location_source       VARCHAR(16) NOT NULL,
    location_fix_at       TIMESTAMPTZ,
    sms_primary_status    VARCHAR(16) NOT NULL,
    sms_secondary_status  VARCHAR(16) NOT NULL,
    call_status           VARCHAR(16) NOT NULL,
    firmware_version      VARCHAR(16),
    app_version           VARCHAR(32)
);
CREATE INDEX ix_incidents_installation_detected ON incidents (installation_id, detected_at DESC);

CREATE TABLE incident_traces (
    incident_id            UUID PRIMARY KEY REFERENCES incidents(id) ON DELETE CASCADE,
    sample_rate_hz         INT NOT NULL,
    pre_trigger_samples    INT NOT NULL,
    total_samples          INT NOT NULL,
    accel_lsb_per_g        INT NOT NULL,
    gyro_lsb_per_dps_x10   INT NOT NULL,
    samples                BYTEA NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
