# syntax=docker/dockerfile:1

# ---- Build: compila con el Maven Wrapper y separa el jar en capas ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencias primero para aprovechar la cache de capas de Docker.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Las pruebas corren en CI (necesitan PostgreSQL); aqui solo se empaqueta.
RUN ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/backend-*.jar extract --layers --launcher --destination target/extracted

# ---- Runtime: JRE minimo y usuario sin privilegios ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

COPY --from=build --chown=app:app /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/application/ ./

USER app

# Ajustado al plan Free/Starter de Render (512 MB). Se puede sobrescribir desde render.yaml.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod \
    PORT=8080

EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
