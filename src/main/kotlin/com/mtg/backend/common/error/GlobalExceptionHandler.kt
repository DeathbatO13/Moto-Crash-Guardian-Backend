package com.mtg.backend.common.error

import com.mtg.backend.common.security.RateLimitExceededException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.net.URI

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimitExceeded(
        ex: RateLimitExceededException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val problem = createProblem(
            status = HttpStatus.TOO_MANY_REQUESTS,
            suffix = "rate-limited",
            title = "Too Many Requests",
            detail = ex.message ?: "Límite de peticiones alcanzado",
            instance = request.requestURI
        ).apply {
            setProperty("retryAfterSeconds", ex.retryAfterSeconds)
        }

        val headers = HttpHeaders().apply {
            set(HttpHeaders.RETRY_AFTER, ex.retryAfterSeconds.toString())
        }

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers).body(problem)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(
        ex: ResponseStatusException,
        request: HttpServletRequest
    ): ResponseEntity<ProblemDetail> {
        val status = ex.statusCode
        val (suffix, title, detail) = when (status.value()) {
            400 -> Triple("validation", "Validation failed", ex.reason ?: "La petición no es válida")
            401 -> Triple("unauthorized", "Unauthorized", ex.reason ?: "Token de instalación requerido o inválido")
            404 -> Triple("not-found", "Not Found", ex.reason ?: "El recurso solicitado no existe")
            409 -> Triple("conflict", "Conflict", ex.reason ?: "El recurso contiene un conflicto")
            413 -> Triple("payload-too-large", "Payload Too Large", ex.reason ?: "El payload supera el límite permitido")
            else -> Triple("request-failed", "Request failed", ex.reason ?: "La petición no pudo completarse")
        }
        val problem = createProblem(status, suffix, title, detail, request.requestURI)
        if (status.value() == 400) {
            problem.setProperty("errors", emptyList<Map<String, String>>())
        }
        return ResponseEntity.status(status).body(problem)
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        val errors = ex.bindingResult.fieldErrors
            .map { fieldError ->
                mapOf(
                    "field" to fieldError.field,
                    "code" to (fieldError.code ?: "INVALID")
                )
            }
            .sortedWith(compareBy({ it.getValue("field") }, { it.getValue("code") }))
            .distinct()

        val problem = createProblem(
            status = HttpStatus.BAD_REQUEST,
            suffix = "validation",
            title = "Validation failed",
            detail = "La petición contiene uno o más campos inválidos",
            instance = requestPath(request)
        ).apply {
            setProperty("errors", errors)
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(problem)
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        val problem = when (body) {
            is ProblemDetail -> body
            else -> ProblemDetail.forStatusAndDetail(statusCode, "La petición no pudo procesarse")
        }.apply {
            if (type == null || type == URI.create("about:blank")) {
                type = URI.create("https://motocrashguardian.app/problems/${problemSuffix(statusCode)}")
            }
            if (title.isNullOrBlank()) {
                title = statusCode.toString()
            }
            if (instance == null) {
                requestPath(request)?.let { instance = URI.create(it) }
            }
        }
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request)
    }

    private fun createProblem(
        status: HttpStatusCode,
        suffix: String,
        title: String,
        detail: String,
        instance: String?
    ) = ProblemDetail.forStatusAndDetail(status, detail).apply {
        type = URI.create("https://motocrashguardian.app/problems/$suffix")
        this.title = title
        instance?.let { this.instance = URI.create(it) }
    }

    private fun problemSuffix(status: HttpStatusCode): String = when (status.value()) {
        400 -> "validation"
        401 -> "unauthorized"
        404 -> "not-found"
        409 -> "conflict"
        413 -> "payload-too-large"
        429 -> "rate-limited"
        else -> "request-failed"
    }

    private fun requestPath(request: WebRequest): String? =
        (request as? ServletWebRequest)?.request?.requestURI
}
