package com.mtg.backend.incidents

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class IncidentTraceBodySizeFilterTest {

    private val filter = IncidentTraceBodySizeFilter()
    private val tracePath = "/api/v1/me/incidents/6f1e7c2e-0000-0000-0000-000000000001/trace"

    private fun request(body: ByteArray, declareLength: Boolean): MockHttpServletRequest =
        MockHttpServletRequest("PUT", tracePath).apply {
            setContent(body)
            // Una peticion chunked no declara Content-Length.
            if (declareLength) addHeader("Content-Length", body.size)
        }

    @Test
    fun `rechaza con 413 un cuerpo grande que declara Content-Length`() {
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request(ByteArray(70 * 1024), declareLength = true), response, chain)

        assertEquals(413, response.status)
        assertNull(chain.request)
    }

    @Test
    fun `rechaza con 413 un cuerpo chunked sin Content-Length`() {
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request(ByteArray(70 * 1024), declareLength = false), response, chain)

        assertEquals(413, response.status)
        assertNull(chain.request)
        assertTrue(response.contentAsString.contains("https://motocrashguardian.app/problems/payload-too-large"))
    }

    @Test
    fun `deja pasar un cuerpo dentro del limite y lo entrega intacto`() {
        val body = """{"sampleRateHz":200}""".toByteArray()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request(body, declareLength = false), response, chain)

        assertEquals(200, response.status)
        val forwarded = chain.request
        assertNotNull(forwarded)
        assertEquals(String(body), String(forwarded!!.inputStream.readAllBytes()))
    }

    @Test
    fun `acepta exactamente el limite`() {
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(
            request(ByteArray(IncidentTraceBodySizeFilter.MAX_TRACE_BODY_BYTES), declareLength = false),
            response,
            chain
        )

        assertNotNull(chain.request)
        assertEquals(200, response.status)
    }

    @Test
    fun `no interviene en otras rutas ni metodos`() {
        val other = MockHttpServletRequest("PUT", "/api/v1/me/config").apply { setContent(ByteArray(70 * 1024)) }
        val get = MockHttpServletRequest("GET", tracePath)

        listOf(other, get).forEach { request ->
            val chain = MockFilterChain()
            filter.doFilter(request, MockHttpServletResponse(), chain)
            assertNotNull(chain.request)
        }
    }
}
