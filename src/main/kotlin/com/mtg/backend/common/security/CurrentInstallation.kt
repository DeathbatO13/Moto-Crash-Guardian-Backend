package com.mtg.backend.common.security

import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentInstallation

@Component
class CurrentInstallationArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentInstallation::class.java) &&
            parameter.parameterType == UUID::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): UUID {
        val auth = SecurityContextHolder.getContext().authentication
        val principal = auth?.principal
        if (principal is UUID) {
            return principal
        }
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Instalación no autenticada")
    }
}
