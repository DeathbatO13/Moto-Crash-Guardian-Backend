package com.mtg.backend.common.model

enum class ContactRole {
    PRIMARY,
    SECONDARY
}

enum class IncidentType {
    REAL,
    DRILL
}

enum class TriggerType {
    IMPACT,
    SEVERE_IMPACT,
    TILT,
    TEST,
    CONNECTION_LOST
}

enum class IncidentStatus {
    NOT_CONFIRMED,
    CANCELLED_BY_USER,
    DISPATCHED,
    DISPATCH_PARTIAL,
    DISPATCH_FAILED,
    STALE_EVENT
}

enum class LocationSource {
    PHONE_GPS,
    DEVICE_GPS,
    LAST_KNOWN,
    NONE
}

enum class SmsStatus {
    SENT,
    DELIVERED,
    FAILED,
    NOT_ATTEMPTED
}

enum class CallStatus {
    PLACED,
    FAILED,
    NOT_ATTEMPTED,
    SKIPPED_DRILL
}
