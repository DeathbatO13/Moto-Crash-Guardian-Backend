# Backend — Moto Crash Guardian

API REST para replicar configuracion y contactos y conservar historial/trazas de incidentes. El backend no participa en la deteccion ni en el despacho de emergencia: la app Android debe seguir funcionando aunque este servicio o internet no esten disponibles.

> **Prototipo academico.** No es un sistema eCall certificado ni reemplaza a los servicios de emergencia.

## Estado actual

Base Spring Boot 4.0.8 + Kotlin 2.2.21 + Java 21 con Maven Wrapper. El POM ya incluye MVC, JPA, Validation, Security, Actuator, Flyway/PostgreSQL, springdoc y Testcontainers. La aplicacion aun solo contiene la clase de arranque, configuracion general y una prueba de carga de contexto; faltan migraciones, entidades, autenticacion por instalacion y endpoints de producto.

## Stack y build

- Kotlin 2.2.21 sobre Java 21.
- Spring Boot 4.0.8: MVC, Data JPA, Validation, Security y Actuator.
- PostgreSQL + Flyway; Testcontainers para pruebas de integracion.
- Maven es el build configurado actualmente (`pom.xml`, `mvnw.cmd`); ADR-005 prescribe Gradle Kotlin DSL. Resolver esa discrepancia en la Fase 0 del roadmap antes de ampliar el servicio.
- Docker/Render como destino previsto; no hay despliegue configurado aun.

## Requisitos locales

- JDK 21.
- PostgreSQL local para la prueba `contextLoads` actual. Docker Desktop sera necesario cuando las pruebas de integracion migren a Testcontainers (tarea pendiente del roadmap).
- Para ejecucion local, PostgreSQL disponible y variables `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`. Los valores de fallback actuales de `application.yaml` son solo para desarrollo local, nunca para despliegue.

## Comandos (PowerShell)

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
.\mvnw.cmd spring-boot:run
```

El servidor usa `PORT` o 8080. La prueba de contexto actual necesita que PostgreSQL este disponible en la URL configurada. Actuator tiene configurado `/actuator/health`; hasta implementar la cadena de seguridad, confirmar que el endpoint quede publico y que no se expongan otros endpoints.

## Estructura actual

```text
src/main/kotlin/com/mtg/backend/       # Aplicacion Spring Boot
src/main/resources/application.yaml    # Configuracion
src/test/kotlin/com/mtg/backend/       # Prueba de contexto inicial
```

La arquitectura objetivo por feature, recursos y contrato API estan en [`../docs/10-backend-architecture.md`](../docs/10-backend-architecture.md). El modelo PostgreSQL esta en [`../docs/04-data-model.md`](../docs/04-data-model.md).

## Roadmap

Ver [`ROADMAP.md`](ROADMAP.md) para fases, tareas y criterios verificables. El build operativo es Maven, mientras que la decision arquitectonica vigente indica Gradle; el equipo debe ratificar uno y alinear el proyecto antes de implementar las features.

## API prevista

Base `/api/v1`; registro publico de instalacion y rutas `/me/**` protegidas con token Bearer opaco. Incluye configuracion, contactos, incidentes idempotentes, trazas, borrado de datos y OpenAPI. El contrato completo esta en [`../docs/10-backend-architecture.md`](../docs/10-backend-architecture.md).

## Seguridad

El servidor debe guardar solo SHA-256 del token de instalacion. No registrar tokens, telefonos ni coordenadas. Usar secretos unicamente en variables de entorno del despliegue. El esquema y la autenticacion deben respetar [`../docs/08-security-privacy.md`](../docs/08-security-privacy.md) y ADR-006.
