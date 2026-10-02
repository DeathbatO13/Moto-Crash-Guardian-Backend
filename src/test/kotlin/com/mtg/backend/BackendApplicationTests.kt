package com.mtg.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class BackendApplicationTests {

	@Autowired
	lateinit var mockMvc: MockMvc

	@Test
	fun contextLoads() {
	}

	@Test
	fun `health y probes son publicos`() {
		listOf("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").forEach { path ->
			mockMvc.get(path).andExpect { status { isOk() } }
		}
	}

	@Test
	fun `otros endpoints de actuator y rutas desconocidas se deniegan`() {
		listOf("/actuator/env", "/actuator/info", "/api/v1/me").forEach { path ->
			mockMvc.get(path).andExpect { status { isForbidden() } }
		}
	}
}
