package com.martinsterentjevs.cacheit.integration.controllers

import com.martinsterentjevs.cacheit.dtos.session.AccountSessionDto
import com.martinsterentjevs.cacheit.dtos.session.SaltResponseDto
import com.martinsterentjevs.cacheit.testdata.AccountTestDataFactory
import com.martinsterentjevs.cacheit.testdata.AccountTestDataFactory.validRegistrationRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class AuthControllerIntegrationTest : BaseControllerTest() {
    val registrationPath = "/auth/register"
    val saltPath = "/auth/salt"
    val loginPath = "/auth/login"
    val logoutPath = "/auth/logout"
    val refreshPath = "/auth/refresh"

    @Test
    fun `register returns 200 and issues access and refresh tokens`() {
        val response = post(registrationPath, validRegistrationRequest())
        if (response.status != HttpStatus.OK) {
            throw Exception("Registration failed, status: ${response.status}")
        }

        val session = response.body<AccountSessionDto>()

        assertThat(session.accessToken).isNotBlank()
        assertThat(session.refreshToken).isNotBlank()
    }

    @Test
    fun `register rejects malformed MEK envelope`() {
        post(
            registrationPath,
            AccountTestDataFactory.malformedMekRegistrationRequest()
        ).expectStatus(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `register rejects duplicate email`() {
        val registration = validRegistrationRequest()
        val r1Response = post(registrationPath, registration)
        assertThat(r1Response.status).isEqualTo(HttpStatus.OK)

        val r2Response = post(registrationPath, validRegistrationRequest(email = registration.email))
        assertThat(r2Response.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `salt lookup returns stored salt for existing email`() {
        val registration = validRegistrationRequest()

        post(registrationPath, registration)
            .expectStatus(HttpStatus.OK)

        val response =
            post(
                saltPath,
                AccountTestDataFactory.newSaltLookupRequest(registration.email!!)
            ).expectStatus(HttpStatus.OK)
                .body<SaltResponseDto>()

        assertThat(response.kdfSalt)
            .isEqualTo(registration.kdfSalt)
    }

    @Test
    fun `salt lookup returns stored salt for existing username`() {
        val registration = validRegistrationRequest()

        post(registrationPath, registration)
            .expectStatus(HttpStatus.OK)

        val response =
            post(
                saltPath,
                AccountTestDataFactory.newSaltLookupRequest(registration.username!!)
            ).expectStatus(HttpStatus.OK)
                .body<SaltResponseDto>()

        assertThat(response.kdfSalt)
            .isEqualTo(registration.kdfSalt)
    }

    @Test
    fun `salt lookup returns deterministic fake salt for unknown identifier`() {
        val identifier = "unknown@test.com"
        val request = AccountTestDataFactory.newSaltLookupRequest(identifier)

        val first =
            post(saltPath, request)
                .expectStatus(HttpStatus.OK)
                .body<SaltResponseDto>()

        val second =
            post(saltPath, request)
                .expectStatus(HttpStatus.OK)
                .body<SaltResponseDto>()

        assertThat(first.kdfSalt)
            .isNotBlank()
            .isEqualTo(second.kdfSalt)
    }

    @Test
    fun `salt lookup returns different fake salts for different unknown identifiers`() {
        val first =
            post(
                saltPath,
                AccountTestDataFactory.newSaltLookupRequest("unknown-one@test.com")
            ).expectStatus(HttpStatus.OK)
                .body<SaltResponseDto>()

        val second =
            post(
                saltPath,
                AccountTestDataFactory.newSaltLookupRequest("unknown-two@test.com")
            ).expectStatus(HttpStatus.OK)
                .body<SaltResponseDto>()

        assertThat(first.kdfSalt)
            .isNotBlank()
            .isNotEqualTo(second.kdfSalt)
    }

    @Test
    fun `login succeeds with email after salt lookup`() {
        val registration = validRegistrationRequest()

        post(registrationPath, registration)
            .expectStatus(HttpStatus.OK)

        post(logoutPath)

        val salt = lookupSalt(registration.email!!)

        val response =
            post(
                loginPath,
                AccountTestDataFactory.validLoginRequest(
                    identifier = registration.email!!,
                    salt = salt
                )
            ).expectStatus(HttpStatus.OK)
                .body<AccountSessionDto>()

        assertThat(response.accessToken).isNotBlank()
        assertThat(response.refreshToken).isNotBlank()
    }

    @Test
    fun `login succeeds with username after salt lookup`() {
        val registration = validRegistrationRequest()

        post(registrationPath, registration)
            .expectStatus(HttpStatus.OK)

        post(logoutPath)

        val salt = lookupSalt(registration.username!!)

        val response =
            post(
                loginPath,
                AccountTestDataFactory.validLoginRequest(
                    identifier = registration.username!!,
                    salt = salt
                )
            ).expectStatus(HttpStatus.OK)
                .body<AccountSessionDto>()

        assertThat(response.accessToken).isNotBlank()
        assertThat(response.refreshToken).isNotBlank()
    }

    @Test
    fun `login rejects wrong password after salt lookup`() {
        val registration = validRegistrationRequest()
        val session =
            post(registrationPath, registration)
                .expectStatus(HttpStatus.OK)
                .body<AccountSessionDto>()

        post(logoutPath, headers = bearer(session.accessToken))

        val salt = lookupSalt(registration.email!!)

        post(
            loginPath,
            AccountTestDataFactory.badPasswordLoginRequest(
                identifier = registration.email!!,
                salt = salt
            )
        ).expectStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `login rejects unknown identifier after fake salt lookup`() {
        val identifier = "not-an-email-or-username"

        val fakeSalt = lookupSalt(identifier)

        post(
            loginPath,
            AccountTestDataFactory.badIdentifierLoginRequest(
                identifier = identifier,
                salt = fakeSalt
            )
        ).expectStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `login rejects no identifier`() {
        post(
            saltPath,
            AccountTestDataFactory.newSaltLookupRequest("")
        ).expectStatus(HttpStatus.OK)

        post(
            loginPath,
            AccountTestDataFactory.validLoginRequest(
                identifier = "",
                salt = "test-salt"
            )
        ).expectStatus(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `refresh accepts correct token`() {
        val registration = validRegistrationRequest()
        val registerResponse =
            post(registrationPath, registration)
                .body<AccountSessionDto>()

        val refreshToken =
            AccountTestDataFactory.refreshDto(
                registerResponse.refreshToken,
                registration.deviceId
            )

        val response = post(refreshPath, refreshToken)

        assertThat(response.status).isEqualTo(HttpStatus.OK)
        assertThat(response.body<AccountSessionDto>().accessToken).isNotBlank()
        assertThat(response.body<AccountSessionDto>().refreshToken).isNotBlank()
    }

    @Test
    fun `refresh rejects invalid token`() {
        val registration = validRegistrationRequest()

        post(registrationPath, registration)
            .body<AccountSessionDto>()

        val refreshToken =
            AccountTestDataFactory.badTokenRefreshDto(registration.deviceId)

        val response = post(refreshPath, refreshToken)

        assertThat(response.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `refresh rejects replayed token`() {
        val registration = validRegistrationRequest()
        val registerResponse =
            post(registrationPath, registration)
                .body<AccountSessionDto>()

        val refreshToken =
            AccountTestDataFactory.refreshDto(
                registerResponse.refreshToken,
                registration.deviceId
            )

        post(refreshPath, refreshToken)
            .expectStatus(HttpStatus.OK)

        post(refreshPath, refreshToken)
            .expectStatus(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `logout removes session`() {
        val registration = validRegistrationRequest()
        val session =
            post(registrationPath, registration)
                .expectStatus(HttpStatus.OK)
                .body<AccountSessionDto>()

        val logout =
            post(
                logoutPath,
                headers = bearer(session.accessToken)
            )

        assertThat(logout.status).isEqualTo(HttpStatus.OK)

        val refresh =
            post(
                refreshPath,
                AccountTestDataFactory.refreshDto(
                    session.refreshToken,
                    registration.deviceId
                )
            )

        assertThat(refresh.status).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `logout rejects invalid token`() {
        post(
            logoutPath,
            headers = bearer("not-a-token")
        ).expectStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `logout rejects unauthorized use`() {
        post(logoutPath)
            .expectStatus(HttpStatus.UNAUTHORIZED)
    }

    private fun lookupSalt(identifier: String): String =
        post(
            saltPath,
            AccountTestDataFactory.newSaltLookupRequest(identifier)
        ).expectStatus(HttpStatus.OK)
            .body<SaltResponseDto>()
            .kdfSalt
    @Test
    fun `salt lookup has roughly similar timing for known and unknown identifiers`() {
        val registration = validRegistrationRequest()

        post(registrationPath, registration)
            .expectStatus(HttpStatus.OK)

        val knownIdentifier = registration.email!!
        val unknownIdentifier = "timing-unknown@test.com"

        // Warm up both paths so we're not measuring initial JVM/application effects.
        repeat(5) {
            lookupSalt(knownIdentifier)
            lookupSalt(unknownIdentifier)
        }

        val knownTimings = measureSaltLookup(knownIdentifier)
        val unknownTimings = measureSaltLookup(unknownIdentifier)

        val knownMedian = median(knownTimings)
        val unknownMedian = median(unknownTimings)

        val slower = maxOf(knownMedian, unknownMedian)
        val faster = minOf(knownMedian, unknownMedian)

        // This is intentionally a rough parity check rather than a strict
        // constant-time guarantee. CI timing is inherently noisy.
        assertThat(slower.toDouble() / faster.toDouble())
            .withFailMessage(
                "Salt lookup timing differs too much: " +
                        "known median=${knownMedian / 1_000_000.0}ms, " +
                        "unknown median=${unknownMedian / 1_000_000.0}ms"
            )
            .isLessThan(5.0)
    }
    private fun measureSaltLookup(
        identifier: String,
        samples: Int = 25
    ): List<Long> =
        (1..samples).map {
            val start = System.nanoTime()

            post(
                saltPath,
                AccountTestDataFactory.newSaltLookupRequest(identifier)
            ).expectStatus(HttpStatus.OK)

            System.nanoTime() - start
        }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()

        return if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        } else {
            sorted[sorted.size / 2]
        }
    }
}