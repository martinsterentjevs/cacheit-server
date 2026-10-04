package com.martinsterentjevs.cacheit.integration.controllers.notes

import com.martinsterentjevs.cacheit.dtos.note.NoteDto
import com.martinsterentjevs.cacheit.dtos.session.AccountSessionDto
import com.martinsterentjevs.cacheit.dtos.session.RegisterDto
import com.martinsterentjevs.cacheit.integration.controllers.BaseControllerTest
import com.martinsterentjevs.cacheit.testdata.AccountTestDataFactory
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

abstract class NoteControllerTestBase : BaseControllerTest() {
    protected data class NoteTestSession(
        val registration: RegisterDto,
        val session: AccountSessionDto
    )

    protected fun registerAndAuthenticate(): NoteTestSession {
        val registration = AccountTestDataFactory.validRegistrationRequest()
        val session = post("/auth/register", registration).expectStatus(HttpStatus.OK).body<AccountSessionDto>()
        return NoteTestSession(registration, session)
    }

    /** Logs the same account in from a second, distinct device — needed for lock-conflict tests. */
    protected fun loginSecondDevice(
        registration: RegisterDto,
        deviceId: UUID = UUID.randomUUID()
    ): AccountSessionDto {
        val email =
            registration.email
                ?: throw IllegalArgumentException("Test registration must have an email")

        val salt =
            post(
                "/auth/salt",
                AccountTestDataFactory.newSaltLookupRequest(email)
            ).expectStatus(HttpStatus.OK)
                .body<com.martinsterentjevs.cacheit.dtos.session.SaltResponseDto>()
                .kdfSalt

        val loginRequest =
            AccountTestDataFactory.validLoginRequest(
                identifier = email,
                salt = salt,
                deviceId = deviceId
            )
        return post("/auth/login", loginRequest).expectStatus(HttpStatus.OK).body()
    }

    protected fun createNote(
        accessToken: String,
        encTitle: String = "Test note",
        encBody: String? = null,
        encDrawing: String? = null
    ): NoteDto =
        post(
            "/notes",
            noteDtoFor(encTitle = encTitle, encBody = encBody, encDrawing = encDrawing),
            headers = bearer(accessToken)
        ).expectStatus(HttpStatus.CREATED).body()

    protected fun noteDtoFor(
        noteId: UUID = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID(),
        encTitle: String = "Test note",
        encBody: String? = null,
        encDrawing: String? = null,
        hasHistory: Boolean = false
    ): NoteDto =
        NoteDto(
            noteId = noteId,
            userId = userId, // discarded server-side — server derives the real owner from the token
            lastModifiedAt = Instant.now(),
            isDeleted = false,
            hasHistory = hasHistory,
            encTitle = encTitle,
            encBody = encBody,
            encDrawing = encDrawing,
            lockedByDeviceId = null,
            lockedAt = null
        )
}