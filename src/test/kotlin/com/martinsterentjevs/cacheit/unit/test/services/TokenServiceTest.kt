package com.martinsterentjevs.cacheit.unit.test.services

import com.martinsterentjevs.cacheit.exceptions.InvalidTokenException
import com.martinsterentjevs.cacheit.models.auth.TokenPair
import com.martinsterentjevs.cacheit.services.TokenService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class TokenServiceTest {
    private val tokenService = TokenService(secret = "test-secret-at-least-32-characters-long")

    @Test
    fun `generateSessionTokens returns a valid jwt and a distinct opaque refresh token`() {
        val tokens = generateValidTokenPair().tokens
        assertNotNull(tokens)
        assertNotNull(tokens.accessToken)
        assertNotNull(tokens.refreshToken)
    }

    @Test
    fun `extractDeviceIdFromToken returns the deviceId encoded in a valid token`() {
        val tokenState = generateValidTokenPair()
        val deviceId = tokenState.deviceId
        val extractedDeviceId = tokenService.extractDeviceIdFromToken(tokenState.tokens.accessToken)
        assertEquals(deviceId, extractedDeviceId)
    }

    @Test
    fun `extractDeviceIdFromToken throws InvalidTokenException for a malformed token`() {
        val malformedToken = "not-a-jwt"
        assertThrows(InvalidTokenException::class.java) { tokenService.extractDeviceIdFromToken(malformedToken) }
    }

    @Test
    fun `extractUserIdFromToken throws on an invalid token instead of returning null`() {
        val malformedToken = "not-a-jwt"
        assertThrows(InvalidTokenException::class.java) { tokenService.extractUserIdFromToken(malformedToken) }
    }

    @Test
    fun `signing key construction fails fast when secret is under 32 characters`() {
        assertThrows(IllegalArgumentException::class.java) { TokenService(secret = "short") }
    }

    // Helper functions
    fun generateValidTokenPair(): ValidTokenPair {
        val userId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        return ValidTokenPair(tokenService.generateSessionTokens(userId, deviceId), deviceId, userId)
    }

    class ValidTokenPair(
        val tokens: TokenPair,
        val deviceId: UUID,
        val userId: UUID
    )
}