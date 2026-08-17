package com.martinsterentjevs.cacheit.services

import com.martinsterentjevs.cacheit.dtos.session.AccountSessionDto
import com.martinsterentjevs.cacheit.dtos.session.LoginDto
import com.martinsterentjevs.cacheit.dtos.session.RefreshRequestDto
import com.martinsterentjevs.cacheit.dtos.session.RegisterDto
import com.martinsterentjevs.cacheit.dtos.session.SaltLookupDto
import com.martinsterentjevs.cacheit.dtos.session.SaltResponseDto
import com.martinsterentjevs.cacheit.exceptions.AccountAlreadyExistsException
import com.martinsterentjevs.cacheit.exceptions.InvalidCredentialsException
import com.martinsterentjevs.cacheit.exceptions.InvalidRegistrationException
import com.martinsterentjevs.cacheit.exceptions.InvalidTokenException
import com.martinsterentjevs.cacheit.models.Account
import com.martinsterentjevs.cacheit.models.AccountRepository
import com.martinsterentjevs.cacheit.models.auth.RequestIdentity
import com.martinsterentjevs.cacheit.models.auth.SessionResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class UserService(
    private val accountRepository: AccountRepository,
    private val sessionService: SessionService,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${HMAC_SECRET}")
    private val hmacKey: String,
    @Value("\${IS_SINGLE_USER:false}")
    private val isSingleUser: Boolean
) {
    fun authenticateUser(login: LoginDto): AccountSessionDto {
        val account =
            accountRepository.findByUsernameOrEmail(login.identifier, login.identifier)
                ?: throw InvalidCredentialsException("Invalid credentials")
        if (!passwordEncoder.matches(
                login.password,
                account.passwordHash
            )
        ) {
            throw InvalidCredentialsException("Invalid credentials")
        }
        val newSession = sessionService.initiateNewSession(account, login.deviceId, login.deviceName)

        val session = assembleAccountDto(account, newSession)
        return session
    }

    fun registerUser(registration: RegisterDto): AccountSessionDto {
        if (!identifierProvided(
                registration.email,
                registration.username
            )
        ) {
            throw InvalidRegistrationException("Either username or email must be provided")
        }
        if (!isIdentifierProvided(
                registration.email,
                registration.username
            )
        ) {
            throw AccountAlreadyExistsException("Username or email is already in use")
        }
        val account =
            Account(
                accountHolder = registration.accountHolder,
                username = registration.username,
                email = registration.email,
                passwordHash = hashPassword(registration.authHash),
                mekEnvelope = registration.encMekEnvelope,
                kdfSalt = registration.kdfSalt,
                isSingleUser = isSingleUser
            )
        val savedAccount = accountRepository.save(account)
        val session = sessionService.initiateNewSession(savedAccount, registration.deviceId, registration.deviceName)
        return assembleAccountDto(savedAccount, session)
    }

    fun refreshSession(refreshRequest: RefreshRequestDto): AccountSessionDto {
        val newSession = sessionService.refreshSession(refreshRequest)
        val account = newSession.deviceSession.account
        return assembleAccountDto(account, newSession)
    }

    fun logout(authorization: String) {
        sessionService.logout(authorization)
    }

    fun getRequestIdentity(auth: String): RequestIdentity {
        val userId = sessionService.extractUserIdFromToken(auth)
        val account =
            accountRepository
                .findById(userId)
                .orElseThrow { InvalidTokenException("No account matches this token.") }
        val deviceId = sessionService.extractDeviceIdFromToken(auth) ?: throw InvalidTokenException()
        return RequestIdentity(account, deviceId)
    }

    fun getSaltById(saltRequest: SaltLookupDto): SaltResponseDto {
        val account = accountRepository.findByUsernameOrEmail(saltRequest.identifier, saltRequest.identifier)
        if (account == null) {
            return SaltResponseDto(
                kdfSalt = getFakeSalt(saltRequest.identifier)
            )
        } else {
            return SaltResponseDto(
                kdfSalt = account.kdfSalt
            )
        }
    }

    private fun getFakeSalt(identifier: String): String {
        val algorithm = "HmacSHA256"
        val mac = Mac.getInstance(algorithm)
        val key = SecretKeySpec(hmacKey.toByteArray(Charsets.UTF_8), algorithm)

        mac.init(key)

        return Base64.getEncoder().encodeToString(
            mac.doFinal(identifier.toByteArray(Charsets.UTF_8))
        )
    }

    private fun identifierProvided(
        email: String?,
        username: String?
    ): Boolean = username != null || email != null

    private fun isIdentifierProvided(
        email: String?,
        username: String?
    ): Boolean = accountRepository.findByUsernameOrEmail(username, email) == null

    private fun assembleAccountDto(
        account: Account,
        session: SessionResult
    ): AccountSessionDto =
        AccountSessionDto(
            deviceId = session.deviceSession.deviceId,
            userId = account.userId,
            accessToken = session.tokens.accessToken,
            refreshToken = session.tokens.refreshToken,
            encMekEnvelope = account.mekEnvelope
        )

    private fun hashPassword(password: String): String = passwordEncoder.encode(password)
}