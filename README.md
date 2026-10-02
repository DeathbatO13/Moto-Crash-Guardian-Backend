# Backend — Moto Crash Guardian

API REST para replicar configuracion y contactos y conservar historial/trazas de incidentes. El backend no participa en la deteccion ni en el despacho de emergencia: la app Android debe seguir funcionando aunque este servicio o internet no esten disponibles.

> **Prototipo academico.** No es un sistema eCall certificado ni reemplaza a los servicios de emergencia.

## Estado actual


Base Spring Boot 4.0.8 + Kotlin 2.2.21 + Java 21 con Maven Wrapper (Fase 0 completada). La aplicacion contiene la clase de arranque, perfiles `local`/`prod`, Flyway configurado (aun sin migraciones), una cadena de seguridad minima (solo `/actuator/health/**` publico) y pruebas de integracion sobre PostgreSQL con Testcontainers; faltan migraciones, entidades, autenticacion por instalacion y endpoints de producto.

El despliegue en Render ya esta configurado (`Dockerfile`, `render.yaml`, CI). Guia completa: [`docs/DEPLOY_RENDER.md`](docs/DEPLOY_RENDER.md).
=======
Base Spring Boot 4.0.8 + Kotlin 2.2.21 + Java 21 con Maven Wrapper. El POM ya incluye MVC, JPA, Validation, Security, Actuator, Flyway/PostgreSQL, springdoc y Testcontainers. La aplicacion aun solo contiene la clase de arranque, configuracion general y una prueba de carga de contexto; faltan migraciones, entidades, autenticacion por instalacion y endpoints de producto.


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
=======
- Spring Boot 4.0.8: MVC, Data JPA, Validation, Security y Actuator.
- PostgreSQL + Flyway; Testcontainers para pruebas de integracion.
- Maven es el build configurado actualmente (`pom.xml`, `mvnw.cmd`); ADR-005 prescribe Gradle Kotlin DSL. Resolver esa discrepancia en la Fase 0 del roadmap antes de ampliar el servicio.
- Docker/Render como destino previsto; no hay despliegue configurado aun.

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
=======
- PostgreSQL local para la prueba `contextLoads` actual. Docker Desktop sera necesario cuando las pruebas de integracion migren a Testcontainers (tarea pendiente del roadmap).
- Para ejecucion local, PostgreSQL disponible y variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`. Los valores de fallback actuales de `application.yaml` son solo para desarrollo local, nunca para despliegue.


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

El servidor usa `PORT` o 8080. `/actuator/health`, `/actuator/health/liveness` y `/actuator/health/readiness` son publicos; cualquier otra ruta responde 403 hasta la Fase 2 (cubierto por `BackendApplicationTests`).
=======
.\mvnw.cmd test
.\mvnw.cmd package
.\mvnw.cmd spring-boot:run
```

El servidor usa `PORT` o 8080. La prueba de contexto actual necesita que PostgreSQL este disponible en la URL configurada. Actuator tiene configurado `/actuator/health`; hasta implementar la cadena de seguridad, confirmar que el endpoint quede publico y que no se expongan otros endpoints.


## Estructura actual

```text

src/main/kotlin/com/mtg/backend/          # Aplicacion Spring Boot
src/main/kotlin/com/mtg/backend/config/   # SecurityConfig
src/main/resources/application*.yaml      # Configuracion base, local y prod
src/main/resources/db/migration/          # Migraciones Flyway (V1 en Fase 1)
src/test/kotlin/com/mtg/backend/          # Pruebas + TestcontainersConfiguration
Dockerfile, .dockerignore, render.yaml    # Despliegue
.github/workflows/backend-ci.yml          # CI: pruebas + imagen Docker
docs/DEPLOY_RENDER.md                     # Guia de despliegue
docs/ADR-005a-build-maven.md              # Decision de build
=======
src/main/kotlin/com/mtg/backend/       # Aplicacion Spring Boot
src/main/resources/application.yaml    # Configuracion
src/test/kotlin/com/mtg/backend/       # Prueba de contexto inicial

```

La arquitectura objetivo por feature, recursos y contrato API estan en [`../docs/10-backend-architecture.md`](../docs/10-backend-architecture.md). El modelo PostgreSQL esta en [`../docs/04-data-model.md`](../docs/04-data-model.md).

## Roadmap


Ver [`ROADMAP.md`](ROADMAP.md) para fases, tareas y criterios verificables.
=======
Ver [`ROADMAP.md`](ROADMAP.md) para fases, tareas y criterios verificables. El build operativo es Maven, mientras que la decision arquitectonica vigente indica Gradle; el equipo debe ratificar uno y alinear el proyecto antes de implementar las features.


## API prevista

Base `/api/v1`; registro publico de instalacion y rutas `/me/**` protegidas con token Bearer opaco. Incluye configuracion, contactos, incidentes idempotentes, trazas, borrado de datos y OpenAPI. El contrato completo esta en [`../docs/10-backend-architecture.md`](../docs/10-backend-architecture.md).

## Seguridad

El servidor debe guardar solo SHA-256 del token de instalacion. No registrar tokens, telefonos ni coordenadas. Usar secretos unicamente en variables de entorno del despliegue. El esquema y la autenticacion deben respetar [`../docs/08-security-privacy.md`](../docs/08-security-privacy.md) y ADR-006.
