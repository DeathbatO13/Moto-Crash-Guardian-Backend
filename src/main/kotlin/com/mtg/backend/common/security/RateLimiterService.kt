package com.mtg.backend.common.security

import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class RateLimitExceededException(
    val retryAfterSeconds: Long,
    message: String = "Límite de registros alcanzado. Reintente en $retryAfterSeconds segundos."
) : RuntimeException(message)

@Service
class RateLimiterService {

    companion object {
        private const val MAX_REQUESTS_PER_HOUR = 5
        private val WINDOW_DURATION = Duration.ofHours(1)
    }

    // Mapa IP -> lista de marcas de tiempo en la ventana actual
    private val requestHistory = ConcurrentHashMap<String, MutableList<Instant>>()

    /**
     * Valida si la IP puede realizar una nueva petición de registro.
     * Si supera el límite de 5 por hora, arroja RateLimitExceededException.
     */
    fun checkRegistrationAllowed(ipAddress: String) {
        val now = Instant.now()
        val windowStart = now.minus(WINDOW_DURATION)

        requestHistory.compute(ipAddress) { _, timestamps ->
            val validTimestamps = (timestamps ?: mutableListOf())
                .filter { it.isAfter(windowStart) }
                .toMutableList()

            if (validTimestamps.size >= MAX_REQUESTS_PER_HOUR) {
                val oldestInWindow = validTimestamps.first()
                val retryAfter = Duration.between(now, oldestInWindow.plus(WINDOW_DURATION)).seconds.coerceAtLeast(1)
                throw RateLimitExceededException(retryAfter)
            }

            validTimestamps.apply { add(now) }
        }
    }
}
