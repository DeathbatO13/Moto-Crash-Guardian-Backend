package com.mtg.backend.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun motoCrashGuardianOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Moto Crash Guardian API")
                    .version("v1")
                    .description(
                        """
                        API de respaldo para sincronizar configuración, contactos, incidentes y trazas.
                        La detección y el despacho de emergencia permanecen en la aplicación Android.

                        El registro de instalación es público. Las rutas `/api/v1/me/**` requieren el token
                        de instalación mediante `Authorization: Bearer <token>`.

                        Los errores usan RFC 9457 con `Content-Type: application/problem+json`. Los tipos
                        incluyen `validation` (400), `unauthorized` (401), `not-found` (404), `conflict` (409),
                        `payload-too-large` (413) y `rate-limited` (429). Los errores de validación añaden
                        `errors`, una lista de objetos `{ "field": "...", "code": "..." }`.

                        Ejemplo de error de validación:
                        ```json
                        {
                          "type": "https://motocrashguardian.app/problems/validation",
                          "title": "Validation failed",
                          "status": 400,
                          "detail": "La petición contiene uno o más campos inválidos",
                          "instance": "/api/v1/me/contacts",
                          "errors": [{ "field": "contacts", "code": "PRIMARY_REQUIRED" }]
                        }
                        ```
                        """.trimIndent()
                    )
            )
            .components(
                Components().addSecuritySchemes(
                    INSTALLATION_BEARER_SCHEME,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("Installation token")
                )
            )

    companion object {
        const val INSTALLATION_BEARER_SCHEME = "installationBearer"
    }
}
