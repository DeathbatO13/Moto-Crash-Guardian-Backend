package com.mtg.backend.common.security

import org.springframework.stereotype.Service
import java.time.Clock
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

        // Sin esto el mapa solo crece (una entrada por IP que alguna vez se registro).
        private const val PURGE_THRESHOLD = 1_000
        private val PURGE_INTERVAL = Duration.ofMinutes(1)
    }

    /** Reloj reemplazable en pruebas. */
    internal var clock: Clock = Clock.systemUTC()

    // Mapa IP -> lista de marcas de tiempo en la ventana actual
    private val requestHistory = ConcurrentHashMap<String, MutableList<Instant>>()
    @Volatile private var lastPurge: Instant = Instant.EPOCH

    /** IPs con registros dentro de la ventana; util para pruebas y diagnostico. */
    internal fun trackedAddresses(): Int = requestHistory.size

    /**
     * Valida si la IP puede realizar una nueva petición de registro.
     * Si supera el límite de 5 por hora, arroja RateLimitExceededException.
     */
    fun checkRegistrationAllowed(ipAddress: String) {
        val now = clock.instant()
        val windowStart = now.minus(WINDOW_DURATION)
        purgeExpiredIfNeeded(now, windowStart)

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

    private fun purgeExpiredIfNeeded(now: Instant, windowStart: Instant) {
        if (requestHistory.size < PURGE_THRESHOLD) return
        if (Duration.between(lastPurge, now) < PURGE_INTERVAL) return
        lastPurge = now
        requestHistory.entries.removeIf { (_, timestamps) -> timestamps.none { it.isAfter(windowStart) } }
    }
}
