package com.mtg.backend.incidents

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class IncidentTraceBodySizeFilter : OncePerRequestFilter() {

    companion object {
        private const val MAX_TRACE_BODY_BYTES = 64 * 1024L
        private val TRACE_PATH = Regex("/api/v1/me/incidents/[^/]+/trace")
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method != "PUT" || !TRACE_PATH.matches(request.requestURI)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.contentLengthLong > MAX_TRACE_BODY_BYTES) {
            response.status = HttpStatus.PAYLOAD_TOO_LARGE.value()
            response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
            response.writer.write(
                """{"type":"https://motocrashguardian.com/errors/payload-too-large","title":"Payload Too Large","status":413,"detail":"La traza supera el límite de 64 KB"}"""
            )
            return
        }

        filterChain.doFilter(request, response)
    }
}
