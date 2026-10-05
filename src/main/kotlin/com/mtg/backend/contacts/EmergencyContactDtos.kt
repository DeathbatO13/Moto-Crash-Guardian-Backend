package com.mtg.backend.contacts

import com.mtg.backend.common.model.ContactRole
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class EmergencyContactDto(
    @get:NotNull(message = "El rol del contacto es obligatorio")
    val role: ContactRole,

    @get:NotBlank(message = "El nombre del contacto es obligatorio")
    @get:Size(max = 60, message = "El nombre no puede superar 60 caracteres")
    val name: String,

    @get:NotBlank(message = "El número telefónico es obligatorio")
    @get:Pattern(
        regexp = "^\\+[1-9][0-9]{7,14}$",
        message = "El teléfono debe estar en formato internacional E.164 (+ y de 8 a 15 dígitos)"
    )
    val phoneE164: String
)

data class UpdateContactsRequest(
    @get:NotNull
    @get:Size(max = 2, message = "Se permite un máximo de 2 contactos de emergencia")
    @field:Valid
    val contacts: List<EmergencyContactDto> = emptyList()
)

data class EmergencyContactsResponse(
    val contacts: List<EmergencyContactDto>
)

fun EmergencyContact.toDto(): EmergencyContactDto =
    EmergencyContactDto(
        role = role,
        name = name,
        phoneE164 = phoneE164
    )
