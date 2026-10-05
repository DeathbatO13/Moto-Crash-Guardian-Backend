package com.mtg.backend.incidents

import com.mtg.backend.common.model.CallStatus
import com.mtg.backend.common.model.IncidentStatus
import com.mtg.backend.common.model.IncidentType
import com.mtg.backend.common.model.LocationSource
import com.mtg.backend.common.model.SmsStatus
import com.mtg.backend.common.model.TriggerType
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.Base64

private const val INCIDENT_TRACE_BYTES_PER_SAMPLE = 12

fun Incident.toDto(): IncidentResponse = IncidentResponse(
    id = id,
    type = type,
    triggerType = triggerType,
    status = status,
    deviceEventKey = deviceEventKey,
    detectedAt = detectedAt,
    resolvedAt = resolvedAt,
    receivedAt = receivedAt,
    peakAccelMg = peakAccelMg,
    peakGyroDps = peakGyroDps,
    pitchCdeg = pitchCdeg,
    rollCdeg = rollCdeg,
    location = if (locationSource == LocationSource.NONE) null else IncidentLocationResponse(
        latitude = latitude,
        longitude = longitude,
        accuracyM = locationAccuracyM,
        source = locationSource,
        fixAt = locationFixAt
    ),
    dispatch = IncidentDispatchResponse(
        smsPrimary = smsPrimaryStatus,
        smsSecondary = smsSecondaryStatus,
        call = callStatus
    ),
    firmwareVersion = firmwareVersion,
    appVersion = appVersion
)

fun IncidentTrace.toDto(): IncidentTraceResponse = IncidentTraceResponse(
    sampleRateHz = sampleRateHz,
    preTriggerSamples = preTriggerSamples,
    totalSamples = totalSamples,
    accelLsbPerG = accelLsbPerG,
    gyroLsbPerDpsX10 = gyroLsbPerDpsX10,
    samplesBase64 = Base64.getEncoder().encodeToString(samples),
    createdAt = createdAt
)

data class IncidentListResponse(
    val items: List<IncidentResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class IncidentResponse(
    val id: java.util.UUID,
    val type: IncidentType,
    val triggerType: TriggerType,
    val status: IncidentStatus,
    val deviceEventKey: String?,
    val detectedAt: Instant,
    val resolvedAt: Instant?,
    val receivedAt: Instant,
    val peakAccelMg: Int?,
    val peakGyroDps: Int?,
    val pitchCdeg: Int?,
    val rollCdeg: Int?,
    val location: IncidentLocationResponse?,
    val dispatch: IncidentDispatchResponse,
    val firmwareVersion: String?,
    val appVersion: String?
)

data class IncidentLocationResponse(
    val latitude: BigDecimal?,
    val longitude: BigDecimal?,
    val accuracyM: Float?,
    val source: LocationSource,
    val fixAt: Instant?
)

data class IncidentDispatchResponse(
    val smsPrimary: SmsStatus,
    val smsSecondary: SmsStatus,
    val call: CallStatus
)

data class IncidentUpsertRequest(
    @get:NotNull(message = "El tipo del incidente es obligatorio")
    val type: IncidentType,

    @get:NotNull(message = "El tipo de disparador es obligatorio")
    val triggerType: TriggerType,

    @get:NotNull(message = "El estado del incidente es obligatorio")
    val status: IncidentStatus,

    @field:Size(max = 16, message = "deviceEventKey no puede superar 16 caracteres")
    val deviceEventKey: String? = null,

    @get:NotNull(message = "detectedAt es obligatorio")
    val detectedAt: Instant,

    val resolvedAt: Instant? = null,
    val peakAccelMg: Int? = null,
    val peakGyroDps: Int? = null,
    val pitchCdeg: Int? = null,
    val rollCdeg: Int? = null,

    @field:Valid
    val location: IncidentLocationRequest? = null,

    @field:Valid
    val dispatch: IncidentDispatchRequest = IncidentDispatchRequest(),

    @field:Size(max = 16, message = "firmwareVersion no puede superar 16 caracteres")
    val firmwareVersion: String? = null,

    @field:Size(max = 32, message = "appVersion no puede superar 32 caracteres")
    val appVersion: String? = null
)

data class IncidentLocationRequest(
    @field:DecimalMin(value = "-90.0", message = "latitude debe ser >= -90")
    @field:DecimalMax(value = "90.0", message = "latitude debe ser <= 90")
    val latitude: BigDecimal? = null,

    @field:DecimalMin(value = "-180.0", message = "longitude debe ser >= -180")
    @field:DecimalMax(value = "180.0", message = "longitude debe ser <= 180")
    val longitude: BigDecimal? = null,

    @field:PositiveOrZero(message = "accuracyM no puede ser negativo")
    val accuracyM: Float? = null,
    @get:NotNull(message = "La fuente de ubicación es obligatoria")
    val source: LocationSource,
    val fixAt: Instant? = null
)

data class IncidentDispatchRequest(
    val smsPrimary: SmsStatus = SmsStatus.NOT_ATTEMPTED,
    val smsSecondary: SmsStatus = SmsStatus.NOT_ATTEMPTED,
    val call: CallStatus = CallStatus.NOT_ATTEMPTED
)

data class IncidentTraceRequest(
    @get:NotNull(message = "sampleRateHz es obligatorio")
    @get:Positive(message = "sampleRateHz debe ser positivo")
    val sampleRateHz: Int,

    @get:NotNull(message = "preTriggerSamples es obligatorio")
    @get:PositiveOrZero(message = "preTriggerSamples no puede ser negativo")
    val preTriggerSamples: Int,

    @get:NotNull(message = "totalSamples es obligatorio")
    @get:Positive(message = "totalSamples debe ser positivo")
    val totalSamples: Int,

    @get:NotNull(message = "accelLsbPerG es obligatorio")
    @get:Positive(message = "accelLsbPerG debe ser positivo")
    val accelLsbPerG: Int,

    @get:NotNull(message = "gyroLsbPerDpsX10 es obligatorio")
    @get:Positive(message = "gyroLsbPerDpsX10 debe ser positivo")
    val gyroLsbPerDpsX10: Int,

    @get:NotBlank(message = "samplesBase64 es obligatorio")
    @field:Pattern(
        regexp = "^[A-Za-z0-9+/=\\r\\n]+$",
        message = "samplesBase64 debe ser una cadena base64 válida"
    )
    val samplesBase64: String
) {
    fun decodedBytes(): ByteArray {
        val raw = samplesBase64.replace("\\s".toRegex(), "")
        return Base64.getDecoder().decode(raw)
    }

    fun bytesExpected(): Long = totalSamples.toLong() * INCIDENT_TRACE_BYTES_PER_SAMPLE
}

data class IncidentTraceResponse(
    val sampleRateHz: Int,
    val preTriggerSamples: Int,
    val totalSamples: Int,
    val accelLsbPerG: Int,
    val gyroLsbPerDpsX10: Int,
    val samplesBase64: String,
    val createdAt: Instant
)
