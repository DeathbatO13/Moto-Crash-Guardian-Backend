package com.mtg.backend.config

import com.mtg.backend.common.security.InstallationTokenFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import tools.jackson.databind.ObjectMapper
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import java.net.URI

@Configuration
class SecurityConfig(
    private val installationTokenFilter: InstallationTokenFilter,
    private val objectMapper: ObjectMapper
) {

    @Bean
    fun authenticationEntryPoint(): AuthenticationEntryPoint =
        AuthenticationEntryPoint { request: HttpServletRequest, response: HttpServletResponse, _: AuthenticationException ->
            val protectedApiPath =
                request.requestURI == "/api/v1/me" || request.requestURI.startsWith("/api/v1/me/")
            val status = if (protectedApiPath) HttpStatus.UNAUTHORIZED else HttpStatus.FORBIDDEN
            response.status = status.value()
            response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            val problem = ProblemDetail.forStatusAndDetail(
                status,
                if (protectedApiPath) {
                    "Token de instalación requerido o inválido"
                } else {
                    "La ruta solicitada no está disponible"
                }
            ).apply {
                title = if (protectedApiPath) "Unauthorized" else "Forbidden"
                type = URI.create(
                    if (protectedApiPath) {
                        "https://motocrashguardian.app/problems/unauthorized"
                    } else {
                        "https://motocrashguardian.app/problems/forbidden"
                    }
                )
                instance = URI.create(request.requestURI)
            }
            objectMapper.writeValue(response.outputStream, problem)
        }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            csrf { disable() }
            cors { disable() }
            httpBasic { disable() }
            formLogin { disable() }
            logout { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            exceptionHandling {
                authenticationEntryPoint = authenticationEntryPoint()
            }
            authorizeHttpRequests {
                authorize(HttpMethod.POST, "/api/v1/installations", permitAll)
                authorize("/v3/api-docs", permitAll)
                authorize("/v3/api-docs/**", permitAll)
                authorize("/swagger-ui.html", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/api/v1/me", authenticated)
                authorize("/api/v1/me/**", authenticated)
                authorize("/actuator/health", permitAll)
                authorize("/actuator/health/**", permitAll)
                authorize("/error", permitAll)
                authorize(anyRequest, denyAll)
            }
        }

        http.addFilterBefore(installationTokenFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
