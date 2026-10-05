package com.mtg.backend.common.security

import com.mtg.backend.installation.InstallationRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InstallationAuthentication(
    val installationId: UUID
) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_INSTALLATION"))) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null
    override fun getPrincipal(): UUID = installationId
}

@Component
class InstallationTokenFilter(
    private val tokenGenerator: TokenGenerator,
    private val installationRepository: InstallationRepository
) : OncePerRequestFilter() {

    companion object {
        private const val BEARER_PREFIX = "Bearer "
        private val LAST_SEEN_UPDATE_INTERVAL = Duration.ofHours(1)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            val token = authHeader.removePrefix(BEARER_PREFIX).trim()
            if (token.isNotEmpty()) {
                val tokenHash = tokenGenerator.hashToken(token)
                val installation = installationRepository.findByTokenHash(tokenHash)

                if (installation != null) {
                    val now = Instant.now()
                    if (Duration.between(installation.lastSeenAt, now) > LAST_SEEN_UPDATE_INTERVAL) {
                        installation.lastSeenAt = now
                        installationRepository.save(installation)
                    }

                    val authentication = InstallationAuthentication(installation.id)
                    SecurityContextHolder.getContext().authentication = authentication
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}
