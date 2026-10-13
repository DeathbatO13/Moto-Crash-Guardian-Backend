package com.mtg.backend.common.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * Obtiene la IP del cliente para el rate limit de registros.
 *
 * `X-Forwarded-For` no sirve: el proxy de Render solo AGREGA al final y no descarta lo que envia
 * el cliente, asi que el primer valor es falsificable (y cada IP falsa seria un bucket nuevo).
 * Delante de Render esta Cloudflare, que escribe `CF-Connecting-IP` sobrescribiendo cualquier
 * valor del cliente. Sin ese header (pruebas, ejecucion local) se usa la direccion del socket.
 */
@Component
class ClientIpResolver {

    fun resolve(request: HttpServletRequest): String {
        val cloudflare = request.getHeader(CLOUDFLARE_HEADER)?.trim()
        if (!cloudflare.isNullOrEmpty() && cloudflare.length <= MAX_IP_LENGTH) return cloudflare
        return request.remoteAddr ?: UNKNOWN
    }

    private companion object {
        const val CLOUDFLARE_HEADER = "CF-Connecting-IP"
        const val MAX_IP_LENGTH = 45 // IPv6 en texto
        const val UNKNOWN = "unknown"
    }
}
