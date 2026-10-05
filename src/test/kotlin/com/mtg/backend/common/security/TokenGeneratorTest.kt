package com.mtg.backend.common.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenGeneratorTest {

    private val generator = TokenGenerator()

    @Test
    fun `genera token con prefijo mcg_ y longitud adecuada`() {
        val token = generator.generateToken()
        assertTrue(token.startsWith("mcg_"), "El token debe iniciar con mcg_")
        // mcg_ (4) + 32 bytes base64url sin padding (43 chars) = 47 chars
        assertEquals(47, token.length)
    }

    @Test
    fun `genera tokens unicos`() {
        val token1 = generator.generateToken()
        val token2 = generator.generateToken()
        assertNotEquals(token1, token2)
    }

    @Test
    fun `hashToken produce exactamente 32 bytes sha-256`() {
        val token = generator.generateToken()
        val hash = generator.hashToken(token)
        assertEquals(32, hash.size, "SHA-256 debe generar exactamente 32 bytes")

        val hashAgain = generator.hashToken(token)
        assertTrue(hash.contentEquals(hashAgain), "El hash del mismo token debe ser determinista")
    }
}
