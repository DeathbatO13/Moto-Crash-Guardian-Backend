package com.mtg.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
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
	fun `otros endpoints de actuator se deniegan`() {
		listOf("/actuator/env", "/actuator/info").forEach { path ->
			mockMvc.get(path).andExpect {
				status { isForbidden() }
				content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
				jsonPath("$.type") { value("https://motocrashguardian.app/problems/forbidden") }
				jsonPath("$.status") { value(403) }
				jsonPath("$.instance") { value(path) }
			}
		}
	}

	@Test
	fun `ruta protegida sin token responde unauthorized con Problem Details`() {
		mockMvc.get("/api/v1/me").andExpect {
			status { isUnauthorized() }
			content { contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON) }
			jsonPath("$.type") { value("https://motocrashguardian.app/problems/unauthorized") }
			jsonPath("$.status") { value(401) }
			jsonPath("$.instance") { value("/api/v1/me") }
		}
	}
}
