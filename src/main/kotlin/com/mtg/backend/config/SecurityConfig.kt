package com.mtg.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Cadena minima para despliegue: solo health es publico y el resto se deniega.
 * Fase 2 del roadmap agrega el registro de instalacion y el filtro de token Bearer.
 */
@Configuration
class SecurityConfig {

	@Bean
	fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
		http {
			csrf { disable() }
			cors { disable() }
			httpBasic { disable() }
			formLogin { disable() }
			logout { disable() }
			sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
			authorizeHttpRequests {
				authorize("/actuator/health", permitAll)
				authorize("/actuator/health/**", permitAll)
				authorize("/error", permitAll)
				authorize(anyRequest, denyAll)
			}
		}
		return http.build()
	}
}
