package com.mtg.backend

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * PostgreSQL aislado para las pruebas de integracion (requiere Docker).
 *
 * Sin Docker se puede apuntar a una base manual con `MCG_TEST_EXTERNAL_DB=true` y
 * `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` (ver README). CI siempre usa el contenedor.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	@ConditionalOnProperty(name = ["mcg.test.external-db"], havingValue = "false", matchIfMissing = true)
	fun postgresContainer(): PostgreSQLContainer =
		PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
}
