package com.martinsterentjevs.cacheit.unit.test.services

import com.martinsterentjevs.cacheit.dtos.session.RefreshRequestDto
import com.martinsterentjevs.cacheit.exceptions.InvalidSessionException
import com.martinsterentjevs.cacheit.exceptions.InvalidTokenException
import com.martinsterentjevs.cacheit.models.Account
import com.martinsterentjevs.cacheit.models.DeviceSession
import com.martinsterentjevs.cacheit.models.DeviceSessionRepository
import com.martinsterentjevs.cacheit.services.SecretsManager
import com.martinsterentjevs.cacheit.services.SessionService
import com.martinsterentjevs.cacheit.services.TokenService
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class SessionServiceTest {
    private val deviceSessionRepo = mockk<DeviceSessionRepository>()
    private val secretsManager = mockk<SecretsManager>()
    private val tokenService = TokenService(secretsManager,secret = "test-secret-at-least-32-characters-long")
    private val sessionService = SessionService(deviceSessionRepo, tokenService)

    @Test
    fun `initiateNewSession saves exactly one session and returns matching tokens`() {
        val userId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        val account = generateTestAccount(userId)
        val savedSession = captureSavedSession()

        val result = sessionService.initiateNewSession(account, deviceId, "Test device")

        verify(exactly = 1) { deviceSessionRepo.save(any<DeviceSession>()) }
        assertThat(savedSession.captured).isSameAs(result.deviceSession)
        assertThat(savedSession.captured.deviceId).isEqualTo(deviceId)
        assertThat(savedSession.captured.account).isSameAs(account)
        assertThat(savedSession.captured.refreshToken).isEqualTo(result.tokens.refreshToken)
    }

    @Test
    fun `refreshSession rejects an unknown refresh token`() {
        val badRefreshRequestDto = RefreshRequestDto(UUID.randomUUID(), "bad-device-id")
        every { deviceSessionRepo.findByRefreshToken(badRefreshRequestDto.refreshToken) } returns null

        assertThrows(InvalidSessionException::class.java) { sessionService.refreshSession(badRefreshRequestDto) }
    }

    @Test
    fun `refreshSession deletes the old session and issues a new one`() {
        val oldSession = generateTestSession()
        val refreshRequest = RefreshRequestDto(oldSession.deviceId, oldSession.refreshToken)
        val savedSession = captureSavedSession()
        every { deviceSessionRepo.findByRefreshToken(oldSession.refreshToken) } returns oldSession

        val result = sessionService.refreshSession(refreshRequest)

        verify(exactly = 1) { deviceSessionRepo.save(any<DeviceSession>()) }
        assertThat(savedSession.captured.deviceId).isEqualTo(oldSession.deviceId)
        assertThat(savedSession.captured.refreshToken).isNotEqualTo(oldSession.refreshToken)
        assertThat(result.tokens.refreshToken).isEqualTo(savedSession.captured.refreshToken)
    }

    @Test
    fun `refreshSession rejects a device id mismatch`() {
        val oldSession = generateTestSession()
        val refreshRequest = RefreshRequestDto(UUID.randomUUID(), oldSession.refreshToken)
        every { deviceSessionRepo.findByRefreshToken(oldSession.refreshToken) } returns oldSession

        assertThrows(InvalidSessionException::class.java) { sessionService.refreshSession(refreshRequest) }

        verify(exactly = 0) { deviceSessionRepo.save(any<DeviceSession>()) }
    }

    @Test
    fun `logout deletes the session matching the token's device and user`() {
        val session = generateTestSession()
        val authorization = generateAuthorization(session.account.userId, session.deviceId)
        every { deviceSessionRepo.findByDeviceId(session.deviceId) } returns session
        every { deviceSessionRepo.delete(session) } returns Unit

        sessionService.logout(authorization)

        verify(exactly = 1) { deviceSessionRepo.delete(session) }
    }

    @Test
    fun `logout rejects a token for a device with no session`() {
        val deviceId = UUID.randomUUID()
        val authorization = generateAuthorization(UUID.randomUUID(), deviceId)
        every { deviceSessionRepo.findByDeviceId(deviceId) } returns null

        assertThrows(InvalidSessionException::class.java) { sessionService.logout(authorization) }

        verify(exactly = 0) { deviceSessionRepo.delete(any<DeviceSession>()) }
    }

    @Test
    fun `logout rejects a token whose userId does not own the session's device`() {
        val session = generateTestSession()
        val authorization = generateAuthorization(UUID.randomUUID(), session.deviceId)
        every { deviceSessionRepo.findByDeviceId(session.deviceId) } returns session

        assertThrows(InvalidSessionException::class.java) { sessionService.logout(authorization) }

        verify(exactly = 0) { deviceSessionRepo.delete(any<DeviceSession>()) }
    }

    @Test
    fun `logout rejects an invalid token without throwing an unhandled exception`() {
        assertThrows(InvalidTokenException::class.java) { sessionService.logout("not-a-token") }

        verify(exactly = 0) { deviceSessionRepo.findByDeviceId(any()) }
        verify(exactly = 0) { deviceSessionRepo.delete(any<DeviceSession>()) }
    }

    // helper methods

    private fun generateTestAccount(userId: UUID = UUID.randomUUID()): Account =
        Account(
            userId = userId,
            accountHolder = "Test User",
            username = "test-user",
            email = null,
            passwordHash = "password-hash",
            mekEnvelope = "mek-envelope",
            kdfSalt = "test-kdf-salt"
        )

    private fun generateTestSession(
        account: Account = generateTestAccount(),
        deviceId: UUID = UUID.randomUUID(),
        refreshToken: String = "old-refresh-token"
    ): DeviceSession =
        DeviceSession(
            deviceId = deviceId,
            account = account,
            deviceName = "Test device",
            refreshToken = refreshToken
        )

    private fun generateAuthorization(
        userId: UUID,
        deviceId: UUID
    ): String = tokenService.generateSessionTokens(userId, deviceId).accessToken

    private fun captureSavedSession(): CapturingSlot<DeviceSession> =
        slot<DeviceSession>().also { savedSession ->
            every { deviceSessionRepo.save(capture(savedSession)) } answers { savedSession.captured }
        }
}