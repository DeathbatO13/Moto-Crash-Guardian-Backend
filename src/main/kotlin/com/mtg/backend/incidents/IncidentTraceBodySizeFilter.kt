package com.mtg.backend.incidents

import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

/**
 * Limita a 64 KB el cuerpo de `PUT /incidents/{id}/trace` ANTES de que Jackson lo cargue.
 *
 * `Content-Length` por si solo no basta: una peticion chunked no lo trae. Por eso el cuerpo se lee
 * con un tope de MAX + 1 bytes; si lo supera se responde 413 sin procesarlo, y si no, se entrega
 * a la cadena ya en memoria (acotado a 64 KB).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class IncidentTraceBodySizeFilter : OncePerRequestFilter() {

    companion object {
        const val MAX_TRACE_BODY_BYTES = 64 * 1024
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
            rejectTooLarge(response)
            return
        }

        val body = request.inputStream.readNBytes(MAX_TRACE_BODY_BYTES + 1)
        if (body.size > MAX_TRACE_BODY_BYTES) {
            rejectTooLarge(response)
            return
        }
        filterChain.doFilter(CachedBodyRequest(request, body), response)
    }

    private fun rejectTooLarge(response: HttpServletResponse) {
        response.status = HttpStatus.PAYLOAD_TOO_LARGE.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(
            """{"type":"https://motocrashguardian.app/problems/payload-too-large","title":"Payload Too Large","status":413,"detail":"La traza supera el límite de 64 KB"}"""
        )
    }

    private class CachedBodyRequest(
        request: HttpServletRequest,
        private val body: ByteArray
    ) : HttpServletRequestWrapper(request) {

        override fun getInputStream(): ServletInputStream {
            val source = ByteArrayInputStream(body)
            return object : ServletInputStream() {
                override fun read(): Int = source.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int = source.read(b, off, len)
                override fun isFinished(): Boolean = source.available() == 0
                override fun isReady(): Boolean = true
                override fun setReadListener(listener: ReadListener?) {
                    throw UnsupportedOperationException("El cuerpo ya esta en memoria")
                }
            }
        }

        override fun getReader(): BufferedReader {
            val charset = characterEncoding?.let { java.nio.charset.Charset.forName(it) } ?: Charsets.UTF_8
            return BufferedReader(InputStreamReader(getInputStream(), charset))
        }

        override fun getContentLength(): Int = body.size
        override fun getContentLengthLong(): Long = body.size.toLong()
    }
}
