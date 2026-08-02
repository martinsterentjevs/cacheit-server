package com.martinsterentjevs.cacheit.integration.controllers

import com.martinsterentjevs.cacheit.dtos.session.AccountSessionDto
import com.martinsterentjevs.cacheit.testdata.AccountTestDataFactory
import com.martinsterentjevs.cacheit.testdata.AccountTestDataFactory.validRegistrationRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class AuthControllerIntegrationTest : BaseControllerTest() {
    val registrationPath = "/auth/register"
    val loginPath = "/auth/login"
    val logoutPath = "/auth/logout"
    val refreshPath = "/auth/refresh"

    @Test
    fun `register returns 200 and issues access and refresh tokens`() {
        val response =
            post(registrationPath, validRegistrationRequest())
        if (response.status != HttpStatus.OK) throw Exception("Registration failed, status: ${response.status}")
        val session = response.body<AccountSessionDto>()
        assertThat(session.accessToken).isNotBlank()
        assertThat(session.refreshToken).isNotBlank()
    }

    @Test
    fun `register rejects malformed MEK envelope`() {
        post(registrationPath, AccountTestDataFactory.malformedMekRegistrationRequest())
            .expectStatus(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `register rejects duplicate email`() {
        val r1Response = post(registrationPath, validRegistrationRequest())
        assertThat(r1Response.status == HttpStatus.OK)
        val r2Response = post(registrationPath, validRegistrationRequest())
        assertThat(r2Response.status == HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `login succeeds with both email or username as identifier`() { // note: if both work
        val testAccount = validRegistrationRequest()
        post(registrationPath, testAccount).expectStatus(HttpStatus.OK)
        post(logoutPath)
        val emailLogin = AccountTestDataFactory.validLoginRequest(identifier = testAccount.email!!)
        val emailResponse = post(loginPath, emailLogin)
        assertThat(emailResponse.status == HttpStatus.OK)
        assertThat(emailResponse.body<AccountSessionDto>().accessToken).isNotBlank()
        assertThat(emailResponse.body<AccountSessionDto>().refreshToken).isNotBlank()
        post(logoutPath)
        val usernameLogin = AccountTestDataFactory.validLoginRequest(identifier = testAccount.username!!)
        val usernameResponse = post(loginPath, usernameLogin)
        assertThat(usernameResponse.status == HttpStatus.OK)
        assertThat(usernameResponse.body<AccountSessionDto>().accessToken).isNotBlank()
        assertThat(usernameResponse.body<AccountSessionDto>().refreshToken).isNotBlank()
    }

    @Test
    fun `login rejects invalid credentials`() {
        val testAccount = validRegistrationRequest()
        val session = post(registrationPath, testAccount).expectStatus(HttpStatus.OK)
        post(logoutPath, headers = bearer(session.body<AccountSessionDto>().accessToken))

        val loginWrongPassword = AccountTestDataFactory.badPasswordLoginRequest(identifier = testAccount.email!!)
        post(loginPath, loginWrongPassword).expectStatus(HttpStatus.UNAUTHORIZED)

        val loginBadEmail = AccountTestDataFactory.badIdentifierLoginRequest()
        post(loginPath, loginBadEmail).expectStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `login rejects no identifier`() {
        post(loginPath, AccountTestDataFactory.validLoginRequest(identifier = "")).expectStatus(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `refresh accepts correct token`() {
        val registration = validRegistrationRequest()
        val registerResponse = post(registrationPath, registration).body<AccountSessionDto>()

        val refreshToken = AccountTestDataFactory.refreshDto(registerResponse.refreshToken, registration.deviceId)

        val response = post(refreshPath, refreshToken)
        assertThat(response.status == HttpStatus.OK)
        assertThat(response.body<AccountSessionDto>().accessToken).isNotBlank()
        assertThat(response.body<AccountSessionDto>().refreshToken).isNotBlank()
    }

    @Test
    fun `refresh rejects invalid token`() {
        val registration = validRegistrationRequest()
        post(registrationPath, registration).body<AccountSessionDto>()

        val refreshToken = AccountTestDataFactory.badTokenRefreshDto(registration.deviceId)

        val response = post(refreshPath, refreshToken)
        assertThat(response.status == HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `refresh rejects replayed token`() {
        val registration = validRegistrationRequest()
        val registerResponse = post(registrationPath, registration).body<AccountSessionDto>()

        val refreshToken = AccountTestDataFactory.refreshDto(registerResponse.refreshToken, registration.deviceId)

        post(refreshPath, refreshToken).expectStatus(HttpStatus.OK)
        post(refreshPath, refreshToken).expectStatus(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `logout removes session`() {
        val registration = validRegistrationRequest()
        val session = post(registrationPath, registration).expectStatus(HttpStatus.OK).body<AccountSessionDto>()

        val logout = post(logoutPath, headers = bearer(session.accessToken))
        assertThat(logout.status == HttpStatus.OK)
        val refresh = post(refreshPath, AccountTestDataFactory.refreshDto(session.refreshToken, registration.deviceId))
        assert(refresh.status == HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `logout rejects invalid token`() {
        post(logoutPath, headers = bearer("not-a-token")).expectStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `logout rejects unauthorized use`() {
        post(logoutPath).expectStatus(HttpStatus.UNAUTHORIZED)
    }
}