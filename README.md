# Backend — Moto Crash Guardian

API REST para replicar configuracion y contactos y conservar historial/trazas de incidentes. El backend no participa en la deteccion ni en el despacho de emergencia: la app Android debe seguir funcionando aunque este servicio o internet no esten disponibles.

> **Prototipo academico.** No es un sistema eCall certificado ni reemplaza a los servicios de emergencia.

## Estado actual

Base Spring Boot 4.0.8 + Kotlin 2.2.21 + Java 21 con Maven Wrapper. Las fases 0 a 5 del roadmap estan implementadas; las pruebas con PostgreSQL 17/Testcontainers validan la migracion Flyway, Hibernate `ddl-auto: validate`, el round-trip de trazas, borrado en cascada y upserts concurrentes. La suite completa paso con 50 pruebas. Persisten tareas operativas/de despliegue y sincronizacion Android.

El despliegue en Render ya esta configurado (`Dockerfile`, `render.yaml`, CI). Guia completa: [`docs/DEPLOY_RENDER.md`](docs/DEPLOY_RENDER.md).

## Stack y build

- Kotlin 2.2.21 sobre Java 21.
- Spring Boot 4.0.8: MVC, Data JPA, Validation, Security, Actuator y Flyway (`spring-boot-starter-flyway`, obligatorio en Boot 4 para que las migraciones corran).
- PostgreSQL 17 + Flyway; Testcontainers 2.x (version del BOM de Boot) para pruebas de integracion.
- **Maven** es el build oficial: ver [`docs/ADR-005a-build-maven.md`](docs/ADR-005a-build-maven.md). Las versiones salen del BOM de Spring Boot; solo springdoc se fija en el POM.
- springdoc-openapi 3.x (la rama 2.x no arranca con Spring Boot 4).
- Docker + Render (Blueprint `render.yaml`, region `virginia`).

## Perfiles y configuracion

| Perfil | Archivo | Uso | Base de datos |
|---|---|---|---|
| (base) | `application.yaml` | Comun; sin credenciales | `SPRING_DATASOURCE_*` o falla el arranque |
| `local` | `application-local.yaml` | `spring-boot:run` (activo por defecto en el plugin) | `localhost:5432/mcg`, usuario `mcg`/`mcg` |
| `prod` | `application-prod.yaml` | Render (`SPRING_PROFILES_ACTIVE=prod`) | `DB_*` desde `fromDatabase` |
| pruebas | `TestcontainersConfiguration` | `mvnw test/verify` | Contenedor `postgres:17-alpine` |

Las credenciales de `local` son solo de desarrollo; el jar y la imagen Docker no activan `local` nunca.

## Requisitos locales

- JDK 21.
- **Docker Desktop** en ejecucion para `mvnw.cmd test` / `verify` (Testcontainers).
- PostgreSQL local para `spring-boot:run` con el perfil `local`. Crear el rol y la base una vez (como superusuario `postgres`):

```sql
CREATE ROLE mcg LOGIN PASSWORD 'mcg';
CREATE DATABASE mcg OWNER mcg;
CREATE DATABASE mcg_test OWNER mcg;  -- solo si se usan pruebas sin Docker
```

### Pruebas sin Docker (excepcion manual)

Si Docker no esta disponible, las pruebas pueden apuntar a una base manual **dedicada** (Flyway la migra y las pruebas pueden escribir en ella):

```powershell
$env:MCG_TEST_EXTERNAL_DB="true"; $env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/mcg_test"; $env:SPRING_DATASOURCE_USERNAME="mcg"; $env:SPRING_DATASOURCE_PASSWORD="mcg"
.\mvnw.cmd test
```

CI siempre usa Testcontainers.

## Comandos (PowerShell)

```powershell
.\mvnw.cmd test              # requiere Docker
.\mvnw.cmd package           # incluye pruebas; -DskipTests para solo empaquetar
.\mvnw.cmd spring-boot:run   # perfil local
```

Para simular produccion localmente:

```powershell
$env:SPRING_PROFILES_ACTIVE="prod"; $env:DB_HOST="localhost"; $env:DB_NAME="mcg"; $env:DB_USER="mcg"; $env:DB_PASSWORD="<clave>"
java -jar target\backend-0.0.1-SNAPSHOT.jar
```

El servidor usa `PORT` o 8080. `/actuator/health` y sus probes son publicos; el registro de instalacion es publico y `/api/v1/me/**` requiere token. Las demas rutas se deniegan. OpenAPI esta disponible en `/v3/api-docs` y Swagger UI en `/swagger-ui/index.html`; sus rutas son publicas y la API describe los errores RFC 9457.

## Estructura actual

```text
src/main/kotlin/com/mtg/backend/          # Features: config, contacts, incidents, installation
src/main/resources/application*.yaml      # Configuracion base, local y prod
src/main/resources/db/migration/          # Migraciones Flyway
src/test/kotlin/com/mtg/backend/          # Pruebas MVC/servicio + TestcontainersConfiguration
Dockerfile, .dockerignore, render.yaml    # Despliegue
.github/workflows/backend-ci.yml          # CI: pruebas + imagen Docker
docs/DEPLOY_RENDER.md                     # Guia de despliegue
docs/ADR-005a-build-maven.md              # Decision de build
```

La arquitectura objetivo por feature, recursos y contrato API estan en [`../docs/10-backend-architecture.md`](../docs/10-backend-architecture.md). El modelo PostgreSQL esta en [`../docs/04-data-model.md`](../docs/04-data-model.md).

## Roadmap

Ver [`ROADMAP.md`](ROADMAP.md) para fases, tareas y criterios verificables.

## API implementada

Base `/api/v1`: registro de instalacion, config/contactos, sincronizacion idempotente y listado paginado de incidentes, trazas y borrado de datos. Los errores siguen RFC 9457 (`application/problem+json`) y el contrato OpenAPI se publica en `/v3/api-docs` (Swagger UI: `/swagger-ui/index.html`). El contrato funcional esta en [`../docs/10-backend-architecture.md`](../docs/10-backend-architecture.md).

## Seguridad

El servidor debe guardar solo SHA-256 del token de instalacion. No registrar tokens, telefonos ni coordenadas. Usar secretos unicamente en variables de entorno del despliegue. El esquema y la autenticacion deben respetar [`../docs/08-security-privacy.md`](../docs/08-security-privacy.md) y ADR-006.
