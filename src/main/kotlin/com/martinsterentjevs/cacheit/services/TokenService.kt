package com.martinsterentjevs.cacheit.services

import com.martinsterentjevs.cacheit.exceptions.InvalidTokenException
import com.martinsterentjevs.cacheit.models.auth.TokenPair
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Instant
import java.util.*
import javax.crypto.spec.SecretKeySpec

@Service
class TokenService(
    private val secretsManager: SecretsManager,
    secret: String = secretsManager.getJwtSecret()
) {
    init {
        require(secret.length >= 32) { "JWT_SECRET must be at least 32 characters long" }
    }

    private val signingKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")

    private val ttl = 3600L

    /** Generates a pair of session tokens (Access Token and Refresh Token). */
    fun generateSessionTokens(
        userId: UUID,
        deviceId: UUID
    ): TokenPair {
        val accessToken = generateJwt(userId = userId, deviceId = deviceId)
        return TokenPair(accessToken = accessToken, refreshToken = generateOpaqueToken())
    }

    /** Generates a JWT Access Token containing user and device identifiers. */
    private fun generateJwt(
        userId: UUID,
        deviceId: UUID
    ): String =
        Jwts
            .builder()
            .subject(userId.toString())
            .issuer("CacheIt")
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(ttl)))
            .claim("deviceId", deviceId.toString())
            .signWith(signingKey)
            .compact()

    /** Generates a cryptographically secure, opaque refresh token. */
    private fun generateOpaqueToken(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Retrieves the Claims object from a given token string, performing JWT validation (signature and expiration).
     * Throws TokenValidationException if the token is invalid or expired.
     */
    private fun getClaims(token: String): Claims {
        val cleanToken = if (token.startsWith("Bearer ")) token.substring(7) else token
        return try {
            Jwts
                .parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(cleanToken)
                .payload
        } catch (_: Exception) {
            throw InvalidTokenException("Invalid or expired JWT token") // Centralized failure point
        }
    }

    /**
     * Extracts a specific claim value and converts it to UUID.
     * Throws IllegalStateException if the required claim is missing or cannot be parsed as UUID.
     */
    private fun getClaimAsUUID(
        token: String,
        claimKey: String
    ): UUID {
        val claims = getClaims(token) // This validates signature and expiration first
        val uuidString =
            claims[claimKey]?.toString()
                ?: throw IllegalStateException("Required claim '$claimKey' missing in token.")

        return try {
            UUID.fromString(uuidString)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Claim '$claimKey' value '$uuidString' is not a valid UUID.", e)
        }
    }

    /**
     * Public function to validate the token and extract the User ID.
     * Throws TokenValidationException if the token is invalid or missing required claims.
     */
    fun extractUserIdFromToken(auth: String): UUID {
        try {
            return getClaimAsUUID(auth, "sub")
        } catch (e: IllegalStateException) {
            throw InvalidTokenException("Invalid token structure or missing User ID: ${e.message}")
        } catch (e: IllegalArgumentException) {
            // This catches the exception thrown by getClaims if JWT is invalid/expired
            throw InvalidTokenException("Token validation failed: ${e.message}")
        }
    }

    /** Extracts the Device ID from a token string, returning null if extraction fails due to missing claim or parsing error (though throwing is generally preferred). */
    fun extractDeviceIdFromToken(token: String): UUID? =
        try {
            getClaimAsUUID(token, "deviceId")
        } catch (e: InvalidTokenException) {
            // If validation fails, treat it as extraction failure for this helper
            throw e
        } catch (_: IllegalStateException) {
            // Specific case of missing claim/parsing error -> return null
            null
        }
}