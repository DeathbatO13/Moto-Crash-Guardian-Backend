package com.mtg.backend.common.security

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Component
class TokenGenerator {

    private val secureRandom = SecureRandom()
    private val base64Encoder = Base64.getUrlEncoder().withoutPadding()

    companion object {
        private const val TOKEN_PREFIX = "mcg_"
        private const val ENTROPY_BYTES = 32 // 256 bits
    }

    /**
     * Genera un token opaco de 256 bits codificado en Base64URL sin padding,
     * con prefijo "mcg_".
     */
    fun generateToken(): String {
        val randomBytes = ByteArray(ENTROPY_BYTES)
        secureRandom.nextBytes(randomBytes)
        return "$TOKEN_PREFIX${base64Encoder.encodeToString(randomBytes)}"
    }

    /**
     * Calcula el digest SHA-256 del token plano en formato UTF-8 para
     * su almacenamiento persistente en la columna token_hash (BYTEA).
     */
    fun hashToken(token: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(token.toByteArray(Charsets.UTF_8))
    }
}
