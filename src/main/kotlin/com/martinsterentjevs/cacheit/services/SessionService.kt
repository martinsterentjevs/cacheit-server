package com.martinsterentjevs.cacheit.services

import com.martinsterentjevs.cacheit.dtos.session.RefreshRequestDto
import com.martinsterentjevs.cacheit.exceptions.InvalidSessionException
import com.martinsterentjevs.cacheit.models.Account
import com.martinsterentjevs.cacheit.models.DeviceSession
import com.martinsterentjevs.cacheit.models.DeviceSessionRepository
import com.martinsterentjevs.cacheit.models.auth.SessionResult
import com.martinsterentjevs.cacheit.models.auth.TokenPair
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SessionService(
    private val deviceSessionRepo: DeviceSessionRepository,
    private val tokenService: TokenService
) {
    fun initiateNewSession(
        account: Account,
        deviceId: UUID,
        deviceName: String?
    ): SessionResult {
        val tokens: TokenPair = tokenService.generateSessionTokens(account.userId, deviceId)
        val session =
            DeviceSession(
                deviceId = deviceId,
                account = account,
                deviceName = deviceName ?: "Unknown device",
                refreshToken = tokens.refreshToken
            )
        deviceSessionRepo.save(session)
        return SessionResult(deviceSession = session, tokens = tokens)
    }

    fun refreshSession(refreshRequest: RefreshRequestDto): SessionResult {
        val session =
            deviceSessionRepo.findByRefreshToken(refreshRequest.refreshToken)
                ?: throw InvalidSessionException("Invalid refresh token")
        val account = session.account
        if (session.deviceId != refreshRequest.deviceId) throw InvalidSessionException("Invalid device id")
        return initiateNewSession(account, deviceId = refreshRequest.deviceId, session.deviceName)
    }

    fun logout(auth: String) {
        val userId = tokenService.extractUserIdFromToken(auth)
        val deviceId = tokenService.extractDeviceIdFromToken(auth) ?: throw InvalidSessionException("Invalid token")
        val session = deviceSessionRepo.findByDeviceId(deviceId) ?: throw InvalidSessionException("Invalid device id")
        if (session.account.userId != userId) throw InvalidSessionException("Invalid user id")
        deviceSessionRepo.delete(session)
    }

    fun extractUserIdFromToken(auth: String): UUID = tokenService.extractUserIdFromToken(auth)

    fun extractDeviceIdFromToken(auth: String): UUID? = tokenService.extractDeviceIdFromToken(auth)
}