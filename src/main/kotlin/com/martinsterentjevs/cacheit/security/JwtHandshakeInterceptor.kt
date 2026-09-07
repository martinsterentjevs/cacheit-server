package com.martinsterentjevs.cacheit.security

import com.martinsterentjevs.cacheit.exceptions.InvalidTokenException
import com.martinsterentjevs.cacheit.services.TokenService
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

class JwtHandshakeInterceptor(
    private val tokenService: TokenService
) : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val authHeader =
            request.headers.getFirst("Authorization")
                ?: return reject(response)

        return try {
            val userId = tokenService.extractUserIdFromToken(authHeader)
            attributes["userId"] = userId.toString()
            true
        } catch (e: InvalidTokenException) {
            reject(response)
        }
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) { /* no-op */ }

    private fun reject(response: ServerHttpResponse): Boolean {
        response.setStatusCode(HttpStatus.UNAUTHORIZED)
        return false
    }
}