package com.martinsterentjevs.cacheit.testdata

import com.martinsterentjevs.cacheit.dtos.session.LoginDto
import com.martinsterentjevs.cacheit.dtos.session.RefreshRequestDto
import com.martinsterentjevs.cacheit.dtos.session.RegisterDto
import com.martinsterentjevs.cacheit.dtos.session.SaltLookupDto
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

object AccountTestDataFactory {
    private const val TEST_ACCOUNT_HOLDER = "Test User"
    private const val TEST_PASSWORD = "Password123!"
    private const val TEST_DEVICE_NAME = "Test Device"
    private const val TEST_ENC_MEK_ENVELOPE = "ZHVtbXktbWVrLWVudmVsb3Bl"

    // Represents the client-side KDF salt stored as part of registration test data.
    private val TEST_KDF_SALT = getSalt()

    private fun getSalt(): String {
        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    // The current server tests intentionally use salt + password as a lightweight
    // stand-in for the client-side Argon2 derivation.
    private val TEST_AUTH_HASH = TEST_KDF_SALT + TEST_PASSWORD

    // Needs freshness per call.
    private fun randomUsername() = "tester-${UUID.randomUUID()}".substring(0, 16)

    private fun randomEmail() = "test-${UUID.randomUUID()}@test.com"

    private fun randomDeviceId() = UUID.randomUUID()

    fun validRegistrationRequest(
        accountHolder: String = TEST_ACCOUNT_HOLDER,
        username: String? = randomUsername(),
        email: String? = randomEmail(),
        authHash: String = TEST_AUTH_HASH,
        deviceId: UUID = randomDeviceId(),
        deviceName: String = TEST_DEVICE_NAME,
        encMekEnvelope: String = TEST_ENC_MEK_ENVELOPE,
        kdfSalt: String = TEST_KDF_SALT
    ) = RegisterDto(
        accountHolder,
        username,
        email,
        authHash,
        deviceId,
        deviceName,
        encMekEnvelope,
        kdfSalt
    )

    fun newSaltLookupRequest(identifier: String) = SaltLookupDto(identifier)

    fun validLoginRequest(
        identifier: String,
        salt: String,
        password: String = TEST_PASSWORD,
        deviceId: UUID = randomDeviceId(),
        deviceName: String = TEST_DEVICE_NAME
    ) = LoginDto(
        identifier,
        salt + password,
        deviceId,
        deviceName
    )

    fun malformedMekRegistrationRequest(): RegisterDto = validRegistrationRequest(encMekEnvelope = "not-base64#%TPOJ@%")

    fun badIdentifierLoginRequest(
        identifier: String,
        salt: String
    ): LoginDto =
        validLoginRequest(
            identifier = identifier,
            salt = salt
        )

    fun badPasswordLoginRequest(
        identifier: String,
        salt: String
    ): LoginDto =
        validLoginRequest(
            identifier = identifier,
            salt = salt,
            password = "wrong-password"
        )

    fun refreshDto(
        refreshToken: String,
        deviceId: UUID
    ) = RefreshRequestDto(deviceId, refreshToken)

    fun badTokenRefreshDto(deviceId: UUID) = refreshDto("not-a-refresh-token", deviceId)
}