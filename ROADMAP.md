# Roadmap Backend — Moto Crash Guardian

Este roadmap convierte `docs/10-backend-architecture.md`, `docs/04-data-model.md` y las features de sincronizacion en entregas verificables. El backend respalda configuracion, contactos, incidentes y trazas; nunca participa en la deteccion ni en el despacho de una emergencia.

**Estado de partida:** Spring Boot 4.0.8, Kotlin 2.2.21 y Java 21 con Maven Wrapper. El POM incluye MVC, JPA, Validation, Security, Actuator, Flyway/PostgreSQL, springdoc y Testcontainers 2.x. El codigo de producto contiene la clase de arranque, perfiles `local`/`prod`, seguridad minima y pruebas de integracion con Testcontainers; la Fase 0 está completada.

## Fase 0 — Alinear el build y el entorno (Completada)

- [x] Resolver la discrepancia de build: se mantiene **Maven** ([`docs/ADR-005a-build-maven.md`](docs/ADR-005a-build-maven.md)); README y CI alineados.
- [x] Validar compatibilidad con Spring Boot 4.0.8: springdoc 3.0.3; Kotlin y Testcontainers provistos por el BOM de Boot; se agrego `spring-boot-starter-flyway` para la ejecucion de migraciones.
- [x] Nombre/descripcion/licencia Maven; perfiles `local` (credenciales de desarrollo, solo `spring-boot:run`), `prod` (Render) y pruebas (Testcontainers). `application.yaml` sin credenciales por defecto.
- [x] Pruebas con PostgreSQL aislado (`TestcontainersConfiguration` + `@ServiceConnection`, `postgres:17-alpine`); requisito de Docker documentado y modo manual documentado (`MCG_TEST_EXTERNAL_DB=true`).
- [x] Ejecutar `mvnw.cmd test-compile` y `mvnw.cmd package -DskipTests` en Java 21; CI configurado (`.github/workflows/backend-ci.yml`) con `mvnw verify` y `docker build`.
- [x] Dockerfile multi-stage no-root ajustado para Render y `render.yaml` preparado para despliegue en Virginia.

**Salida:** build reproducible, versiones compatibles y base de datos de prueba automatizada.

## Fase 1 — Modelo relacional y persistencia (Completada)

- [x] Crear `V1__init.sql` con `installations`, `user_config`, `emergency_contacts`, `incidents` e `incident_traces`, restricciones, claves foraneas e indices de `docs/04-data-model.md`.
- [x] Implementar entidades JPA Kotlin como clases (no `data class`), repositorios y conversiones base; mantener `ddl-auto=validate` y Flyway como unico gestor de esquema.
- [x] Definir enums/longitudes/rangos y reglas de borrado en cascada; asegurar que `incidents` nunca almacene telefonos.
- [x] Ejecutar pruebas de migracion sobre PostgreSQL real y validar esquema vacio -> V1 -> Hibernate validate (`IncidentConcurrencyIntegrationTest` comprueba Flyway V1, tablas/FK y arranque exitoso de Hibernate con `ddl-auto: validate`).

**Salida:** esquema trazable que valida en PostgreSQL desde cero.

## Fase 2 — Instalacion, autenticacion y borrado (Completada)

- [x] Implementar `POST /api/v1/installations`: UUID de instalacion, token aleatorio de 256 bits, respuesta con token una sola vez y persistencia exclusiva del SHA-256.
- [x] Implementar limite de 5 registros por hora/IP para el MVP, con proxy headers tratados solo desde el proxy confiable (`RateLimiterService`).
- [x] Crear `InstallationTokenFilter`, `SecurityFilterChain` y resolucion de instalacion actual (`@CurrentInstallation`); dejar publico solo registro, health y documentacion definida. CORS deshabilitado.
- [x] Implementar `DELETE /api/v1/me` transaccional con borrado en cascada y revocacion inmediata del token.
- [x] Probar token ausente, invalido, revocado, aislamiento entre instalaciones, rate limit y borrado completo (`InstallationControllerTest`, `RateLimiterServiceTest`, `TokenGeneratorTest`, `InstallationServiceTest`).

**Salida:** ningun dato personal se expone sin token y cada instalacion solo accede a sus propios datos.

## Fase 3 — Configuracion y contactos

- [x] Implementar DTOs y `GET/PUT /api/v1/me/config`; validar rangos de `docs/04-data-model.md`, guardar revision optimista y `updatedAt` UTC.
- [x] Implementar `GET/PUT /api/v1/me/contacts`; reemplazo completo de hasta dos contactos, como maximo un PRIMARY, SECONDARY requiere PRIMARY, E.164 valido y numeros distintos.
- [x] Aplicar Bean Validation y reglas de dominio; no exponer entidades JPA como contrato HTTP.
- [x] Probar limites min/max, listas vacias permitidas, combinaciones invalidas y aislamiento por instalacion.

**Salida:** config y contactos soportan el contrato del cliente con errores 400 consistentes.

## Fase 4 — Incidentes idempotentes

- [x] Implementar `PUT/GET /api/v1/me/incidents/{id}` y listado paginado ordenado por `detectedAt` descendente.
- [x] Tratar UUID del cliente como clave idempotente: crear devuelve 201; reintento o actualizacion mutable devuelve 200.
- [x] Hacer inmutables `type`, `triggerType`, `detectedAt` y `deviceEventKey`; cambios incompatibles responden 409 sin persistir.
- [x] Restringir las consultas a la instalacion autenticada; validar coordenadas, estados/enums y coherencia temporal. El upsert usa advisory lock transaccional por UUID para serializar reintentos concurrentes.
- [x] Agregar pruebas MVC/servicio para creacion, actualizacion, conflicto, paginacion, traza y acceso a datos de otra instalacion (`IncidentControllerTest`, `IncidentServiceTest`).
- [x] Ejecutar `IncidentConcurrencyIntegrationTest` contra PostgreSQL 17 real mediante Testcontainers: upserts concurrentes del mismo UUID, migracion Flyway, round-trip de bytes/metadatos de traza y borrado en cascada.

**Salida:** sincronizacion con reintentos no duplica ni corrompe incidentes.

## Fase 5 — Trazas y errores API

- [x] Implementar `PUT/GET /api/v1/me/incidents/{id}/trace`; exigir incidente propio existente y validar `samplesBase64` decodificado = `totalSamples * 12` bytes.
- [x] Rechazar cuerpos HTTP de traza mayores de 64 KB y limitar el Base64 antes de decodificar; guardar metadatos/bytes identicos y responder 413/404 segun el contrato.
- [x] Crear `@RestControllerAdvice` con RFC 9457 Problem Details y codigos de validacion estables; los 401 de Spring Security usan el mismo formato sin filtrar excepciones internas.
- [x] Publicar OpenAPI en `/v3/api-docs` y Swagger UI en `/swagger-ui`; documentar autenticacion Bearer, tipos de error y ejemplo de validacion.
- [x] Probar Base64 malformado, longitud inconsistente, limite de tamano, round-trip de bytes y acceso a traza inexistente (`IncidentServiceTest`).
- [x] Validar persistencia/migracion del round-trip contra PostgreSQL real con Testcontainers (T-3.07); la prueba verifica bytes y metadatos tras leerlos desde la BD.

La suite completa del backend paso: 50 pruebas, incluyendo `/v3/api-docs`, Swagger UI, errores RFC 9457 y cuatro pruebas de integracion con PostgreSQL 17/Testcontainers. T-3.07 queda completada.

**Salida:** contrato documentado y trazas seguras, acotadas e integras.

## Fase 6 — Operacion y despliegue

- [ ] Confirmar que `/actuator/health` y probes funcionen sin exponer otros endpoints Actuator (health 200, resto 403). Prueba automatizada escrita en `BackendApplicationTests` (MockMvc + Spring Security).
- [x] Crear Dockerfile multi-stage no-root con JVM ajustada al plan de Render; health check via `healthCheckPath` de Render.
- [x] Crear `render.yaml` y `docs/DEPLOY_RENDER.md`; la URL JDBC se arma en `application-prod.yaml` con `DB_*` de `fromDatabase`.
- [ ] Ejecutar el primer deploy real en Render y registrar la URL publica; cronograma en `docs/DEPLOY_RENDER.md` §9.
- [ ] Configurar HTTPS/proxy headers, HSTS, limites de conexion, timeouts y pool pequeno; confirmar retencion de datos y region informada en consentimiento.
- [ ] Ejecutar suite completa en CI, escaneo de dependencias y ensayo de despliegue; verificar Swagger y sincronizacion de un incidente de demo.

**Salida MVP:** servicio desplegado y observable, esquema migrado y contrato de API disponible.

## Gates de aceptacion

- [ ] Ningun endpoint del backend forma parte de la ruta de countdown/SMS/llamada; app opera con backend apagado.
- [ ] Pruebas de contrato cubren `POST /installations`, `/me/config`, `/me/contacts`, incidentes, trazas, borrado y Problem Details.
- [ ] La suite de integracion corre contra PostgreSQL real con Testcontainers.
- [ ] Logs no incluyen token, telefonos ni coordenadas; solo se expone health de Actuator.
