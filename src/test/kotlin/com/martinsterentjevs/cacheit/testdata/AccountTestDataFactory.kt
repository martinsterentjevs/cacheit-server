package com.martinsterentjevs.cacheit.testdata

import com.martinsterentjevs.cacheit.dtos.session.LoginDto
import com.martinsterentjevs.cacheit.dtos.session.RefreshRequestDto
import com.martinsterentjevs.cacheit.dtos.session.RegisterDto
import java.util.UUID

object AccountTestDataFactory {
    // static, fine as val — no uniqueness needed
    private const val TEST_ACCOUNT_HOLDER = "Test User"
    private const val TEST_PASSWORD = "Password123!"
    private const val TEST_DEVICE_NAME = "Test Device"
    private const val TEST_ENC_MEK_ENVELOPE = "ZHVtbXktbWVrLWVudmVsb3Bl"

    // needs freshness per call — fun, not val
    private fun randomUsername() = "tester-${UUID.randomUUID()}".substring(0, 16)

    private fun randomEmail() = "test-${UUID.randomUUID()}@test.com"

    private fun randomDeviceId() = UUID.randomUUID()

    fun validRegistrationRequest(
        accountHolder: String = TEST_ACCOUNT_HOLDER,
        username: String? = randomUsername(),
        email: String? = randomEmail(),
        password: String = TEST_PASSWORD,
        deviceId: UUID = randomDeviceId(),
        deviceName: String = TEST_DEVICE_NAME,
        encMekEnvelope: String = TEST_ENC_MEK_ENVELOPE
    ) = RegisterDto(accountHolder, username, email, password, deviceId, deviceName, encMekEnvelope)

    fun validLoginRequest(
        identifier: String,
        password: String = TEST_PASSWORD,
        deviceId: UUID = randomDeviceId(),
        deviceName: String = TEST_DEVICE_NAME
    ) = LoginDto(identifier, password, deviceId, deviceName)

    fun malformedMekRegistrationRequest(): RegisterDto = validRegistrationRequest(encMekEnvelope = "not-base64#%TPOJ@%")

    fun badIdentifierLoginRequest(): LoginDto = validLoginRequest(identifier = "not-an-email-or-username")

    fun badPasswordLoginRequest(identifier: String): LoginDto =
        validLoginRequest(identifier = identifier, password = "wrong-password")

    fun refreshDto(
        refreshToken: String,
        deviceId: UUID
    ) = RefreshRequestDto(deviceId, refreshToken)

    fun badTokenRefreshDto(deviceId: UUID) = refreshDto("not-a-refresh-token", deviceId)
}