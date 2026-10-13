package com.mtg.backend.common.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class ClientIpResolverTest {

    private val resolver = ClientIpResolver()

    private fun request(remoteAddr: String = "10.1.2.3") =
        MockHttpServletRequest().apply { this.remoteAddr = remoteAddr }

    @Test
    fun `usa CF-Connecting-IP cuando existe`() {
        val request = request().apply { addHeader("CF-Connecting-IP", "203.0.113.7") }

        assertEquals("203.0.113.7", resolver.resolve(request))
    }

    @Test
    fun `ignora X-Forwarded-For porque el cliente puede falsificarlo`() {
        val request = request("10.1.2.3").apply { addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8") }

        assertEquals("10.1.2.3", resolver.resolve(request))
    }

    @Test
    fun `sin headers de proxy usa la direccion del socket`() {
        assertEquals("192.168.0.9", resolver.resolve(request("192.168.0.9")))
    }

    @Test
    fun `descarta un CF-Connecting-IP vacio o desmesurado`() {
        val blank = request("10.1.2.3").apply { addHeader("CF-Connecting-IP", "   ") }
        val huge = request("10.1.2.3").apply { addHeader("CF-Connecting-IP", "9".repeat(500)) }

        assertEquals("10.1.2.3", resolver.resolve(blank))
        assertEquals("10.1.2.3", resolver.resolve(huge))
    }
}
