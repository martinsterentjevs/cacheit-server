package com.martinsterentjevs.cacheit.unit.test.services

import com.martinsterentjevs.cacheit.dtos.session.LoginDto
import com.martinsterentjevs.cacheit.dtos.session.RegisterDto
import com.martinsterentjevs.cacheit.exceptions.AccountAlreadyExistsException
import com.martinsterentjevs.cacheit.exceptions.InvalidCredentialsException
import com.martinsterentjevs.cacheit.exceptions.InvalidRegistrationException
import com.martinsterentjevs.cacheit.models.Account
import com.martinsterentjevs.cacheit.models.AccountRepository
import com.martinsterentjevs.cacheit.models.DeviceSession
import com.martinsterentjevs.cacheit.models.auth.SessionResult
import com.martinsterentjevs.cacheit.models.auth.TokenPair
import com.martinsterentjevs.cacheit.services.SessionService
import com.martinsterentjevs.cacheit.services.UserService
import com.martinsterentjevs.cacheit.testdata.AccountTestDataFactory
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID

class UserServiceTest {
    private val accountRepository = mockk<AccountRepository>()
    private val sessionService = mockk<SessionService>()
    private val passwordEncoder = mockk<PasswordEncoder>()

    private val userService = UserService(accountRepository, sessionService, passwordEncoder)

    // -- registerUser --

    @Test
    fun `registerUser hashes the password before persisting the account`() {
        val registration = AccountTestDataFactory.validRegistrationRequest()
        val savedAccount = stubSuccessfulRegistration(registration)

        userService.registerUser(registration)

        verify(exactly = 1) { passwordEncoder.encode(registration.password) }
        assertThat(savedAccount.captured.passwordHash).isEqualTo(PASSWORD_HASH)
        assertThat(savedAccount.captured.passwordHash).isNotEqualTo(registration.password)
    }

    @Test
    fun `registerUser stores encMekEnvelope opaquely without re-encoding it`() {
        // guards against the earlier passwordEncoder-on-MEK mistake reappearing
        val registration = AccountTestDataFactory.validRegistrationRequest()
        val savedAccount = stubSuccessfulRegistration(registration)

        userService.registerUser(registration)

        assertThat(savedAccount.captured.mekEnvelope).isEqualTo(registration.encMekEnvelope)
        verify(exactly = 0) { passwordEncoder.encode(registration.encMekEnvelope) }
    }

    @Test
    fun `registerUser rejects when neither username nor email is provided`() {
        val registration = AccountTestDataFactory.validRegistrationRequest(username = null, email = null)

        assertThrows<InvalidRegistrationException> { userService.registerUser(registration) }

        verify(exactly = 0) { accountRepository.save(any()) }
        verify(exactly = 0) { sessionService.initiateNewSession(any(), any(), any()) }
    }

    @Test
    fun `registerUser rejects a duplicate username or email`() {
        val registration = AccountTestDataFactory.validRegistrationRequest()
        every { accountRepository.findByUsernameOrEmail(registration.username, registration.email) } returns
            generateTestAccount()

        assertThrows<AccountAlreadyExistsException> { userService.registerUser(registration) }

        verify(exactly = 0) { accountRepository.save(any()) }
        verify(exactly = 0) { sessionService.initiateNewSession(any(), any(), any()) }
    }

    @Test
    fun `registerUser delegates session creation to SessionService with the new account`() {
        val registration = AccountTestDataFactory.validRegistrationRequest()
        val savedAccount = stubSuccessfulRegistration(registration)

        val result = userService.registerUser(registration)

        verify(exactly = 1) {
            sessionService.initiateNewSession(
                savedAccount.captured,
                registration.deviceId,
                registration.deviceName
            )
        }
        assertThat(result.userId).isEqualTo(savedAccount.captured.userId)
        assertThat(result.deviceId).isEqualTo(registration.deviceId)
    }

    // -- authenticateUser --

    @Test
    fun `authenticateUser succeeds when identifier matches username`() {
        val account = generateTestAccount(username = "test-user", email = null)
        val login = AccountTestDataFactory.validLoginRequest(account.username!!)
        stubSuccessfulAuthentication(login, account)

        val result = userService.authenticateUser(login)

        assertSessionDtoMatches(result, account, login.deviceId)
    }

    @Test
    fun `authenticateUser succeeds when identifier matches email`() {
        val account = generateTestAccount(username = null, email = "test@example.com")
        val login = AccountTestDataFactory.validLoginRequest(account.email!!)
        stubSuccessfulAuthentication(login, account)

        val result = userService.authenticateUser(login)

        assertSessionDtoMatches(result, account, login.deviceId)
    }

    @Test
    fun `authenticateUser rejects an unknown identifier`() {
        val login = AccountTestDataFactory.badIdentifierLoginRequest()
        every { accountRepository.findByUsernameOrEmail(login.identifier, login.identifier) } returns null

        assertThrows<InvalidCredentialsException> { userService.authenticateUser(login) }

        verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
        verify(exactly = 0) { sessionService.initiateNewSession(any(), any(), any()) }
    }

    @Test
    fun `authenticateUser rejects a wrong password`() {
        val account = generateTestAccount()
        val login = AccountTestDataFactory.badPasswordLoginRequest(account.username!!)
        every { accountRepository.findByUsernameOrEmail(login.identifier, login.identifier) } returns account
        every { passwordEncoder.matches(login.password, account.passwordHash) } returns false

        assertThrows<InvalidCredentialsException> { userService.authenticateUser(login) }

        verify(exactly = 0) { sessionService.initiateNewSession(any(), any(), any()) }
    }

    @Test
    fun `unknown identifier and wrong password produce the same exception type`() {
        // the generic-error-code guarantee — assert type equality, not just "both fail"
        val account = generateTestAccount()
        val unknownLogin = AccountTestDataFactory.badIdentifierLoginRequest()
        val wrongPasswordLogin = AccountTestDataFactory.badPasswordLoginRequest(account.username!!)
        every {
            accountRepository.findByUsernameOrEmail(unknownLogin.identifier, unknownLogin.identifier)
        } returns null
        every {
            accountRepository.findByUsernameOrEmail(wrongPasswordLogin.identifier, wrongPasswordLogin.identifier)
        } returns account
        every { passwordEncoder.matches(wrongPasswordLogin.password, account.passwordHash) } returns false

        val unknownIdentifierFailure =
            assertThrows<InvalidCredentialsException> { userService.authenticateUser(unknownLogin) }
        val wrongPasswordFailure =
            assertThrows<InvalidCredentialsException> { userService.authenticateUser(wrongPasswordLogin) }

        assertThat(unknownIdentifierFailure::class).isEqualTo(wrongPasswordFailure::class)
    }

    // -- refreshSession / logout --

    @Test
    fun `refreshSession delegates to SessionService and reassembles the DTO from the new session`() {
        val account = generateTestAccount()
        val refreshRequest = AccountTestDataFactory.refreshDto(REFRESH_TOKEN, UUID.randomUUID())
        val refreshedSession = generateSessionResult(account, refreshRequest.deviceId)
        every { sessionService.refreshSession(refreshRequest) } returns refreshedSession

        val result = userService.refreshSession(refreshRequest)

        verify(exactly = 1) { sessionService.refreshSession(refreshRequest) }
        assertSessionDtoMatches(result, account, refreshRequest.deviceId)
    }

    @Test
    fun `logout delegates directly to SessionService without additional checks`() {
        every { sessionService.logout(AUTHORIZATION) } returns Unit

        userService.logout(AUTHORIZATION)

        verify(exactly = 1) { sessionService.logout(AUTHORIZATION) }
    }

    // helper methods

    private fun stubSuccessfulRegistration(registration: RegisterDto): CapturingSlot<Account> {
        val savedAccount = slot<Account>()
        every { accountRepository.findByUsernameOrEmail(registration.username, registration.email) } returns null
        every { passwordEncoder.encode(registration.password) } returns PASSWORD_HASH
        every { accountRepository.save(capture(savedAccount)) } answers { savedAccount.captured }
        every {
            sessionService.initiateNewSession(any(), registration.deviceId, registration.deviceName)
        } answers { generateSessionResult(firstArg(), registration.deviceId, registration.deviceName) }
        return savedAccount
    }

    private fun stubSuccessfulAuthentication(
        login: LoginDto,
        account: Account
    ) {
        every { accountRepository.findByUsernameOrEmail(login.identifier, login.identifier) } returns account
        every { passwordEncoder.matches(login.password, account.passwordHash) } returns true
        every {
            sessionService.initiateNewSession(account, login.deviceId, login.deviceName)
        } returns generateSessionResult(account, login.deviceId, login.deviceName)
    }

    private fun generateTestAccount(
        username: String? = "test-user",
        email: String? = "test@example.com"
    ): Account =
        Account(
            accountHolder = "Test User",
            username = username,
            email = email,
            passwordHash = PASSWORD_HASH,
            mekEnvelope = MEK_ENVELOPE
        )

    private fun generateSessionResult(
        account: Account,
        deviceId: UUID,
        deviceName: String = "Test device"
    ): SessionResult =
        SessionResult(
            tokens = TokenPair(ACCESS_TOKEN, REFRESH_TOKEN),
            deviceSession =
                DeviceSession(
                    deviceId = deviceId,
                    account = account,
                    deviceName = deviceName,
                    refreshToken = REFRESH_TOKEN
                )
        )

    private fun assertSessionDtoMatches(
        result: com.martinsterentjevs.cacheit.dtos.session.AccountSessionDto,
        account: Account,
        deviceId: UUID
    ) {
        assertThat(result.userId).isEqualTo(account.userId)
        assertThat(result.deviceId).isEqualTo(deviceId)
        assertThat(result.accessToken).isEqualTo(ACCESS_TOKEN)
        assertThat(result.refreshToken).isEqualTo(REFRESH_TOKEN)
        assertThat(result.encMekEnvelope).isEqualTo(account.mekEnvelope)
    }

    companion object {
        private const val PASSWORD_HASH = "encoded-password"
        private const val MEK_ENVELOPE = "encrypted-mek-envelope"
        private const val ACCESS_TOKEN = "access-token"
        private const val REFRESH_TOKEN = "refresh-token"
        private const val AUTHORIZATION = "Bearer access-token"
    }
}