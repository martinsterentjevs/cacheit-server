package com.martinsterentjevs.cacheit.testdata

import com.martinsterentjevs.cacheit.dtos.session.AccountSessionDto
import com.martinsterentjevs.cacheit.dtos.session.SaltResponseDto
import com.martinsterentjevs.cacheit.services.TokenService
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.postForEntity
import java.util.UUID

data class AuthedTestUser(
    val userId: UUID,
    val deviceId: UUID,
    val accessToken: String
)

object AuthTestHelper {
    fun registerAndLogin(
        restTemplate: TestRestTemplate,
        tokenService: TokenService
    ): AuthedTestUser {
        val registerReq = AccountTestDataFactory.validRegistrationRequest()
        val identifier =
            registerReq.username ?: registerReq.email
                ?: error("factory produced neither username nor email")

        val registerResponse =
            restTemplate.postForEntity<AccountSessionDto>(
                "/auth/register",
                registerReq
            )
        check(registerResponse.statusCode.is2xxSuccessful) {
            "register failed: ${registerResponse.statusCode} ${registerResponse.body}"
        }

        val saltReq = AccountTestDataFactory.newSaltLookupRequest(identifier)
        val saltResponse =
            restTemplate.postForEntity<SaltResponseDto>(
                "/auth/salt",
                saltReq
            )
        check(saltResponse.statusCode.is2xxSuccessful) {
            "salt lookup failed: ${saltResponse.statusCode}"
        }
        val salt = saltResponse.body!!.kdfSalt

        val loginReq =
            AccountTestDataFactory.validLoginRequest(
                identifier = identifier,
                salt = salt,
                deviceId = registerReq.deviceId
            )
        val loginResponse =
            restTemplate.postForEntity<AccountSessionDto>(
                "/auth/login",
                loginReq
            )
        check(loginResponse.statusCode.is2xxSuccessful) {
            "login failed: ${loginResponse.statusCode}"
        }

        val accessToken = loginResponse.body!!.accessToken
        val userId = tokenService.extractUserIdFromToken(accessToken)

        return AuthedTestUser(userId, registerReq.deviceId, accessToken)
    }
}