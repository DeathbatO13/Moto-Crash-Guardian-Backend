# ADR-005a — Mantener Maven como build del backend

- **Estado:** Aceptada (29-sep-2026)
- **Modifica:** ADR-005 (`../docs/decisions/ADR-005-backend-stack.md`), solo en la herramienta de build. El resto del stack (Kotlin, Spring Boot, PostgreSQL, Flyway) no cambia.

## Contexto

ADR-005 y `docs/10-backend-architecture.md` prescriben Gradle Kotlin DSL, pero el proyecto se genero y ya opera con Maven: `pom.xml`, Maven Wrapper 3.9.16, `Dockerfile` multi-stage, `render.yaml` y el CI de GitHub Actions dependen de `mvnw`. La entrega es el 19-oct-2026 y el primer deploy en Render el 13-oct.

## Opciones

| Criterio | Mantener Maven | Migrar a Gradle Kotlin DSL |
|---|---|---|
| Trabajo inmediato | Ninguno | Reescribir build (plugins Kotlin allopen/noarg/jpa, Spring Boot, BOM), Dockerfile, CI y docs |
| Riesgo para la entrega | Bajo: build, imagen y CI ya verificados | Medio: nueva superficie de fallo a 3 semanas de la entrega |
| Valor funcional | — | Ninguno para el MVP; ventajas (build cache, scripts Kotlin) poco relevantes en un modulo unico |
| Coherencia con la app | Distinta herramienta que Android | Misma herramienta que Android |

## Decision

Mantener **Maven** (via Maven Wrapper) como unico build del backend. La coherencia con Gradle de la app no justifica el costo ni el riesgo antes de la entrega; los dos proyectos son independientes y no comparten modulos.

## Consecuencias

- Comandos oficiales: `mvnw.cmd test`, `mvnw.cmd package`, `mvnw.cmd spring-boot:run`; CI ejecuta `./mvnw -B verify`.
- Las versiones de dependencias se toman del BOM de Spring Boot; solo se fijan en el POM las que el BOM no gestiona (springdoc).
- Al actualizar el repositorio de documentacion (`../docs/`), ADR-005 y `10-backend-architecture.md` deben referenciar este ADR en lugar de Gradle.
- Una migracion futura a Gradle queda fuera del MVP y requeriria un ADR nuevo.
