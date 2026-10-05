package com.mtg.backend.common.security

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RateLimiterServiceTest {

    private val rateLimiter = RateLimiterService()

    @Test
    fun `permite hasta 5 peticiones por hora por IP`() {
        val ip = "192.168.1.100"
        for (i in 1..5) {
            assertDoesNotThrow {
                rateLimiter.checkRegistrationAllowed(ip)
            }
        }
    }

    @Test
    fun `bloquea la sexta peticion con RateLimitExceededException`() {
        val ip = "192.168.1.101"
        for (i in 1..5) {
            rateLimiter.checkRegistrationAllowed(ip)
        }

        val exception = assertThrows<RateLimitExceededException> {
            rateLimiter.checkRegistrationAllowed(ip)
        }

        assertTrue(exception.retryAfterSeconds > 0)
    }

    @Test
    fun `diferentes IPs tienen buckets independientes`() {
        val ip1 = "10.0.0.1"
        val ip2 = "10.0.0.2"

        for (i in 1..5) {
            rateLimiter.checkRegistrationAllowed(ip1)
        }

        assertDoesNotThrow {
            rateLimiter.checkRegistrationAllowed(ip2)
        }
    }
}
