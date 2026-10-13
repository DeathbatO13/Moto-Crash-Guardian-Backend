package com.mtg.backend.common.security

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    fun `purga las IPs vencidas para no crecer sin limite`() {
        var now = java.time.Instant.parse("2026-10-13T10:00:00Z")
        rateLimiter.clock = object : java.time.Clock() {
            override fun getZone() = java.time.ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant(): java.time.Instant = now
        }
        for (i in 1..1_200) rateLimiter.checkRegistrationAllowed("10.0.${i / 250}.${i % 250}")
        assertEquals(1_200, rateLimiter.trackedAddresses())

        now = now.plus(java.time.Duration.ofHours(2))
        rateLimiter.checkRegistrationAllowed("172.16.0.1")

        assertEquals(1, rateLimiter.trackedAddresses())
    }

    @Test
    fun `tras la ventana la IP puede registrarse otra vez`() {
        var now = java.time.Instant.parse("2026-10-13T10:00:00Z")
        rateLimiter.clock = object : java.time.Clock() {
            override fun getZone() = java.time.ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant(): java.time.Instant = now
        }
        repeat(5) { rateLimiter.checkRegistrationAllowed("10.9.9.9") }
        assertThrows<RateLimitExceededException> { rateLimiter.checkRegistrationAllowed("10.9.9.9") }

        now = now.plus(java.time.Duration.ofMinutes(61))

        assertDoesNotThrow { rateLimiter.checkRegistrationAllowed("10.9.9.9") }
    }
}
