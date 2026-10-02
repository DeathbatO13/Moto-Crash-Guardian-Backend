# Despliegue en Render — Backend Moto Crash Guardian

Guia para publicar el backend (Spring Boot 4 + Kotlin, Java 21) en [Render](https://render.com) con PostgreSQL administrado. Render no tiene runtime Java nativo, por eso el servicio usa **Docker**.

> Recordatorio de arquitectura: el backend solo replica configuracion, contactos, incidentes y trazas. La app debe funcionar con el backend apagado o dormido (plan Free), asi que un arranque en frio nunca afecta la ruta de emergencia.

## 1. Archivos de despliegue

| Archivo | Proposito |
|---|---|
| `Dockerfile` | Build multi-stage: compila con `mvnw`, extrae el jar en capas y corre sobre `eclipse-temurin:21-jre-alpine` con usuario no-root. |
| `.dockerignore` | Excluye `target/`, `.maven-cache/`, IDE y documentacion del contexto de build. |
| `render.yaml` | Blueprint: base PostgreSQL `mcg-db` + web service `mcg-backend`, health check y variables. |
| `src/main/resources/application-prod.yaml` | Perfil `prod`: arma la URL JDBC con `DB_*`, pool pequeno, sin lazy init, apagado graceful, health sin detalles. |
| `src/main/kotlin/.../config/SecurityConfig.kt` | Cadena minima: `/actuator/health/**` publico, todo lo demas `denyAll` hasta la Fase 2. |
| `.github/workflows/backend-ci.yml` | `mvnw verify` (PostgreSQL 17 via Testcontainers) + `docker build`. Render espera a que pase (`autoDeployTrigger: checksPass`). |

## 2. Requisitos previos

1. Repositorio en GitHub (o GitLab/Bitbucket) con este contenido en la rama `main`.
2. Cuenta en Render conectada al proveedor Git.
3. Decidir plan (ver seccion 6). El Blueprint usa `free` por defecto.

### Monorepo vs. repositorio independiente

- **Repositorio solo del backend** (este directorio es la raiz): no cambies nada.
- **Monorepo** (`Backend/`, `App Android/`, `docs/` en la misma raiz): mueve `render.yaml` a la raiz, descomenta `rootDir: Backend` y mueve `.github/workflows/backend-ci.yml` a la raiz agregando `paths: ["Backend/**"]` y `defaults.run.working-directory: Backend`.

## 3. Primer despliegue (Blueprint)

1. Render Dashboard → **New** → **Blueprint** → selecciona el repositorio.
2. Render lee `render.yaml` y muestra dos recursos: `mcg-db` y `mcg-backend`. Confirma con **Apply**.
3. Render crea la base, inyecta `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` (host interno, sin exponer la base a internet) y construye la imagen.
4. Al arrancar, Flyway aplica las migraciones de `src/main/resources/db/migration` y Hibernate valida el esquema (`ddl-auto: validate`).
5. Render marca el deploy como exitoso cuando `GET /actuator/health/readiness` responde `200` (incluye el chequeo de la base).

### Verificacion

```bash
curl -i https://mcg-backend.onrender.com/actuator/health
```

Esperado: `200` con `{"status":"UP"}` y cabecera `Strict-Transport-Security`. Cualquier otra ruta debe responder `403` hasta que se implemente la Fase 2 del roadmap.

La URL real depende del nombre disponible; copiala desde el Dashboard y usala como `mcg.apiBaseUrl` en la app Android.

## 4. Variables de entorno

| Variable | Origen | Descripcion |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `render.yaml` | `prod`. |
| `PORT` | Render | Puerto asignado; `server.port` ya lo lee. |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | `fromDatabase` | Conexion interna a `mcg-db`. |
| `DB_POOL_SIZE` | `render.yaml` | Tamano maximo del pool Hikari (5). |
| `JAVA_TOOL_OPTIONS` | `render.yaml` | Heap al 70 % del contenedor, SerialGC, salida ante OOM. |
| `SPRING_DATASOURCE_URL` (opcional) | Manual | Si se define, tiene prioridad sobre `DB_*` (p. ej. base externa). Debe ser JDBC: `jdbc:postgresql://host:5432/db?sslmode=require`. |

**Nunca** uses la "External Database URL" de Render (`postgres://usuario:clave@host/db`) directamente: no es una URL JDBC y expone la base fuera de la red privada.

Los secretos de fases futuras (p. ej. pepper de tokens) se agregan en el Dashboard como variables `sync: false` en `render.yaml`, nunca en el repositorio.

## 5. Probar la imagen localmente (opcional)

Requiere Docker Desktop y un PostgreSQL accesible:

```bash
docker build -t mcg-backend .
docker run --rm -p 8080:8080 -e DB_HOST=host.docker.internal -e DB_NAME=mcg -e DB_USER=mcg -e DB_PASSWORD=mcg mcg-backend
```

## 6. Planes y limites

| Recurso | Free | Alternativa paga |
|---|---|---|
| Web service | 512 MB, se duerme tras 15 min sin trafico; primer request tarda ~30-60 s. | `starter` (siempre activo). |
| PostgreSQL | 1 GB, **expira a los 30 dias** de creada; solo una base Free por workspace. | `basic-256mb` con backups. |

**Decision para la sustentacion:** plan Free en ambos recursos, creados el **13 de octubre de 2026**. La base expira hacia el **12 de noviembre**, despues de la entrega del 19 de octubre, asi que no hace falta plan pago. El arranque en frio se cubre despertando el servicio antes de la demo (seccion 9); la app lo tolera porque la sincronizacion va por WorkManager con reintentos y la ruta de emergencia no usa el backend.

Si se quiere eliminar el riesgo del arranque en frio durante la demo, se puede subir solo el web service a `starter` la semana de la entrega (Render cobra de forma prorrateada) y volver a `free` despues.

Si la evaluacion o las correcciones se extienden mas alla del 12 de noviembre, exporta la base con `pg_dump` y sube a `basic-256mb` antes de esa fecha: Render borra la base Free al vencer.

## 7. Operacion

- **Logs**: Dashboard → `mcg-backend` → Logs. Los logs no deben contener tokens, telefonos ni coordenadas (ver gates del roadmap).
- **Rollback**: Dashboard → Events → seleccionar un deploy anterior → *Rollback*. Las migraciones Flyway no se revierten solas: disenalas compatibles hacia atras.
- **Backups**: solo en planes pagos de PostgreSQL. En Free, exporta con `pg_dump` usando la conexion externa (requiere agregar tu IP a `ipAllowList` temporalmente).
- **Region**: `virginia`. Si cambia, actualiza el texto de consentimiento y la politica de privacidad de la app.

## 8. Hasta el deploy

Mientras no exista el servicio en Render, el backend se desarrolla y prueba en local y en CI (PostgreSQL 17 en GitHub Actions + `docker build`). La app debug apunta a `http://10.0.2.2:8080/` y no depende de Render.

No crees la base Free antes del 13 de octubre: su plazo de 30 dias empieza al crearla.

## 9. Cronograma hacia la sustentacion (lunes 19-oct-2026)

| Fecha | Tarea |
|---|---|
| Hasta el vie 9 oct | CI en verde (`mvnw verify` + `docker build`). Si hay Docker local, probar la imagen con la seccion 5. |
| Lun 12 oct | Congelar migraciones Flyway para la entrega. Revisar que el repo tenga `render.yaml` en la raiz correcta. |
| **Mar 13 oct** | Aplicar el Blueprint (seccion 3). Verificar `/actuator/health`, que Flyway corrio y que las rutas no publicas responden 401/403. Anotar la URL publica. |
| Mie 14 oct | Enviar la URL al equipo Android para `mcg.apiBaseUrl` y el APK candidato. Crear una instalacion de prueba y sincronizar un incidente de demo. |
| Jue 15 – vie 16 oct | Prueba de extremo a extremo con los telefonos de prueba. Revisar que los logs de Render no contengan tokens, telefonos ni coordenadas. |
| Dom 18 oct | Desactivar el auto-deploy (Settings → Auto-Deploy: *Off*) para que ningun push cambie el servicio antes de la demo. |
| **Lun 19 oct** | 5-10 minutos antes: `curl https://<servicio>.onrender.com/actuator/health` hasta obtener `200` y mantener la pestana abierta. Tener a mano el Dashboard para mostrar logs si lo piden. |

Despues de la entrega: reactivar el auto-deploy si se sigue trabajando, o suspender el servicio para no depender de la base Free cuando venza.

## 10. Problemas frecuentes

| Sintoma | Causa probable | Solucion |
|---|---|---|
| Deploy queda en "health check failed" | Base no disponible o credenciales `DB_*` ausentes | Revisa que el servicio tenga las variables `fromDatabase` y que `mcg-db` este `Available`. |
| `NoClassDefFoundError ... WebMvcProperties` | springdoc 2.x con Spring Boot 4 | Usar springdoc 3.x (ya fijado en `springdoc.version`). |
| `Schema-validation: missing table` | Entidad sin migracion Flyway | Crear `V<n>__*.sql`; nunca cambiar `ddl-auto`. |
| Contenedor reiniciado por memoria | Heap demasiado grande para 512 MB | Bajar `MaxRAMPercentage` o subir de plan. |
| Todas las rutas devuelven 403 | Comportamiento esperado antes de la Fase 2 | Implementar registro de instalacion y filtro Bearer. |
