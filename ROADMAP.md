# Roadmap Backend — Moto Crash Guardian

Este roadmap convierte `docs/10-backend-architecture.md`, `docs/04-data-model.md` y las features de sincronizacion en entregas verificables. El backend respalda configuracion, contactos, incidentes y trazas; nunca participa en la deteccion ni en el despacho de una emergencia.


**Estado de partida:** Spring Boot 4.0.8, Kotlin 2.2.21 y Java 21 con Maven Wrapper. El POM incluye MVC, JPA, Validation, Security, Actuator, Flyway/PostgreSQL, springdoc y Testcontainers 2.x. El codigo de producto se limita a la clase de arranque, perfiles `local`/`prod`, seguridad minima y pruebas de integracion con Testcontainers; no hay migraciones, entidades, autenticacion propia ni endpoints `/api/v1`.

## Fase 0 — Alinear el build y el entorno

- [x] Resolver la discrepancia de build: se mantiene **Maven** ([`docs/ADR-005a-build-maven.md`](docs/ADR-005a-build-maven.md)); README y CI alineados. Pendiente fuera de este repo: enlazar el ADR desde `../docs/decisions/ADR-005` y `../docs/10-backend-architecture.md`.
- [x] Validar compatibilidad con Spring Boot 4.0.8: springdoc 3.0.3; Kotlin y Testcontainers ahora vienen del BOM (el override a Testcontainers 1.21.3 era incompatible con `spring-boot-testcontainers` 4 → artefactos `testcontainers-postgresql`/`testcontainers-junit-jupiter` 2.0.5); se agrego `spring-boot-starter-flyway`, sin el cual Boot 4 no ejecuta migraciones.
- [x] Nombre/descripcion/licencia Maven; perfiles `local` (credenciales de desarrollo, solo `spring-boot:run`), `prod` (Render) y pruebas (Testcontainers). `application.yaml` ya no trae credenciales por defecto.
- [x] Pruebas con PostgreSQL aislado (`TestcontainersConfiguration` + `@ServiceConnection`, `postgres:17-alpine`); requisito de Docker documentado. Modo manual separado y explicito: `MCG_TEST_EXTERNAL_DB=true` + `SPRING_DATASOURCE_*`.
- [ ] Ejecutar `mvnw.cmd test` en Java 21 con Docker (verificado 29-sep: `test-compile` y `package -DskipTests` OK; el contexto llega hasta Testcontainers y el modo externo hasta la conexion, pero la maquina de desarrollo no tiene Docker ni el rol `mcg`). Se valida en CI al subir el repo o al instalar Docker Desktop.
- [x] CI (`.github/workflows/backend-ci.yml`): `mvnw verify` con Testcontainers (sin servicio Postgres) y `docker build`.
=======
**Estado de partida:** Spring Boot 4.0.8, Kotlin 2.2.21 y Java 21 ya estan configurados con Maven Wrapper. El POM incluye MVC, JPA, Validation, Security, Actuator, Flyway/PostgreSQL, springdoc y Testcontainers. El codigo de producto aun se limita a la clase de arranque, configuracion de datasource/JPA/health y una prueba de carga de contexto; no hay migraciones, entidades, autenticacion propia ni endpoints `/api/v1`.

## Fase 0 — Alinear el build y el entorno

- [ ] Resolver y registrar la discrepancia de build antes de agregar capas: ADR-005 y `docs/10-backend-architecture.md` prescriben Gradle Kotlin DSL, pero el repositorio actual usa Maven (`pom.xml`, `mvnw.cmd`). Comparar el costo de migracion con el valor de mantener Maven y actualizar ADR, documentacion y CI al build elegido.
- [ ] Validar compatibilidad de todas las dependencias con Spring Boot 4.0.8, en especial springdoc, el plugin Kotlin y Testcontainers; fijar versiones compatibles en el POM.
- [ ] Definir nombre/descripcion Maven, perfiles local/test y configuracion PostgreSQL reproducible; no usar credenciales de desarrollo como configuracion de despliegue.
- [ ] Hacer que las pruebas arranquen con PostgreSQL aislado (Testcontainers) y documentar el requisito de Docker; separar cualquier prueba que realmente exija una instancia manual.
- [ ] Ejecutar `mvnw.cmd test` y `mvnw.cmd package` en Java 21; agregar CI por cambios en `Backend/`.


**Salida:** build reproducible, versiones compatibles y base de datos de prueba automatizada.

## Fase 1 — Modelo relacional y persistencia

- [ ] Crear `V1__init.sql` con `installations`, `user_config`, `emergency_contacts`, `incidents` e `incident_traces`, restricciones, claves foraneas e indices de `docs/04-data-model.md`.
- [ ] Implementar entidades JPA Kotlin como clases (no `data class`), repositorios y conversiones DTO; mantener `ddl-auto=validate` y Flyway como unico gestor de esquema.
- [ ] Definir enums/longitudes/rangos y reglas de borrado en cascada; asegurar que `incidents` nunca almacene telefonos.
- [ ] Agregar pruebas de migracion sobre PostgreSQL real y validar esquema vacio -> V1 -> Hibernate validate.

**Salida:** esquema trazable que valida en PostgreSQL desde cero.

## Fase 2 — Instalacion, autenticacion y borrado

- [ ] Implementar `POST /api/v1/installations`: UUID de instalacion, token aleatorio de 256 bits, respuesta con token una sola vez y persistencia exclusiva del SHA-256.
- [ ] Implementar limite de 5 registros por hora/IP para el MVP, con proxy headers tratados solo desde el proxy confiable.
- [ ] Crear `InstallationTokenFilter`, `SecurityFilterChain` y resolucion de instalacion actual; dejar publico solo registro, health y documentacion definida. CORS deshabilitado.
- [ ] Implementar `DELETE /api/v1/me` transaccional con borrado en cascada y revocacion inmediata del token.
- [ ] Probar token ausente, invalido, revocado, aislamiento entre instalaciones, rate limit y borrado completo.

**Salida:** ningun dato personal se expone sin token y cada instalacion solo accede a sus propios datos.

## Fase 3 — Configuracion y contactos

- [ ] Implementar DTOs y `GET/PUT /api/v1/me/config`; validar rangos de `docs/04-data-model.md`, guardar revision optimista y `updatedAt` UTC.
- [ ] Implementar `GET/PUT /api/v1/me/contacts`; reemplazo completo de hasta dos contactos, como maximo un PRIMARY, SECONDARY requiere PRIMARY, E.164 valido y numeros distintos.
- [ ] Aplicar Bean Validation y reglas de dominio; no exponer entidades JPA como contrato HTTP.
- [ ] Probar limites min/max, listas vacias permitidas, combinaciones invalidas y aislamiento por instalacion.

**Salida:** config y contactos soportan el contrato del cliente con errores 400 consistentes.

## Fase 4 — Incidentes idempotentes

- [ ] Implementar `PUT/GET /api/v1/me/incidents/{id}` y listado paginado ordenado por `detectedAt` descendente.
- [ ] Tratar UUID del cliente como clave idempotente: crear devuelve 201; reintento o actualizacion mutable devuelve 200.
- [ ] Hacer inmutables `type`, `triggerType`, `detectedAt` y `deviceEventKey`; cambios incompatibles responden 409 sin modificar datos.
- [ ] Restringir toda consulta a la instalacion autenticada; validar estados, enums, ubicacion, timestamps e idempotencia bajo concurrencia.
- [ ] Agregar pruebas de creacion, repeticion, actualizacion permitida, conflicto, paginacion y acceso cruzado.

**Salida:** sincronizacion con reintentos no duplica ni corrompe incidentes.

## Fase 5 — Trazas y errores API

- [ ] Implementar `PUT/GET /api/v1/me/incidents/{id}/trace`; exigir incidente propio existente y validar `samplesBase64` decodificado = `totalSamples * 12` bytes.
- [ ] Limitar el cuerpo a 64 KB, guardar metadatos/un bytes identicos y responder 413/404 segun el contrato.
- [ ] Crear `@RestControllerAdvice` con RFC 9457 Problem Details y codigos de validacion estables; no filtrar SQL, tokens ni datos personales.
- [ ] Publicar OpenAPI en `/v3/api-docs` y Swagger UI en `/swagger-ui`; describir Bearer auth, ejemplos y errores.
- [ ] Probar cuerpo malformado, limite de tamano, round-trip de bytes y documentacion generada.

**Salida:** contrato documentado y trazas seguras, acotadas e integras.

## Fase 6 — Operacion y despliegue


- [ ] Confirmar que `/actuator/health` y probes funcionen sin exponer otros endpoints Actuator (verificado manualmente: health 200, resto 403). Prueba automatizada escrita en `BackendApplicationTests` (MockMvc + Spring Security); falta verla en verde con Docker/CI.
- [x] Crear Dockerfile multi-stage no-root con JVM ajustada al plan de Render; health check via `healthCheckPath` de Render.
- [x] Crear `render.yaml` y `docs/DEPLOY_RENDER.md`; la URL JDBC se arma en `application-prod.yaml` con `DB_*` de `fromDatabase` (sin convertir la URL `postgres://`).
- [ ] Ejecutar el primer deploy real en Render el 13-oct-2026 (no antes: la base Free vence a los 30 dias) y registrar la URL publica; cronograma en `docs/DEPLOY_RENDER.md` §9.
=======
- [ ] Confirmar que `/actuator/health` y probes funcionen sin exponer otros endpoints Actuator; probarlo a traves de Spring Security.
- [ ] Crear Dockerfile multi-stage no-root con JVM ajustada al plan de Render; incluir health check si la plataforma lo requiere.
- [ ] Crear `render.yaml`/instrucciones de despliegue y variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`; convertir correctamente la URL PostgreSQL de Render a JDBC.

- [ ] Configurar HTTPS/proxy headers, HSTS, limites de conexion, timeouts y pool pequeno; confirmar retencion de datos y region informada en consentimiento.
- [ ] Ejecutar suite completa en CI, escaneo de dependencias y ensayo de despliegue; verificar Swagger y sincronizacion de un incidente de demo.

**Salida MVP:** servicio desplegado y observable, esquema migrado y contrato de API disponible.

## Gates de aceptacion

- [ ] Ningun endpoint del backend forma parte de la ruta de countdown/SMS/llamada; app opera con backend apagado.
- [ ] Pruebas de contrato cubren `POST /installations`, `/me/config`, `/me/contacts`, incidentes, trazas, borrado y Problem Details.
- [ ] La suite de integracion corre contra PostgreSQL real con Testcontainers.
- [ ] Logs no incluyen token, telefonos ni coordenadas; solo se expone health de Actuator.

## Referencias

- `../docs/10-backend-architecture.md`, `../docs/04-data-model.md`, `../docs/07-error-handling.md`
- `../docs/08-security-privacy.md`, `../docs/09-testing-strategy.md`
- `../docs/decisions/ADR-004-local-first.md`, `../docs/decisions/ADR-005-backend-stack.md`, `../docs/decisions/ADR-006-api-authentication.md`
- `../specs/features/data-synchronization.md`, `../specs/features/emergency-contacts.md`, `../specs/features/black-box-trace.md`
