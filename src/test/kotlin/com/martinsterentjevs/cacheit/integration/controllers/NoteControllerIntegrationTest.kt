package com.martinsterentjevs.cacheit.integration.controllers

import com.martinsterentjevs.cacheit.dtos.note.NoteDto
import com.martinsterentjevs.cacheit.dtos.note.SyncRequestDto
import com.martinsterentjevs.cacheit.dtos.note.SyncResponseDto
import com.martinsterentjevs.cacheit.dtos.note.version.NoteVersionDto
import com.martinsterentjevs.cacheit.dtos.note.version.NoteVersionMeta
import com.martinsterentjevs.cacheit.dtos.session.AccountSessionDto
import com.martinsterentjevs.cacheit.testdata.AccountTestDataFactory
import com.martinsterentjevs.cacheit.testdata.NoteTestDataFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

class NoteControllerIntegrationTest : BaseControllerTest() {
    private val registrationPath = "/auth/register"
    private val notesPath = "/notes"

    private lateinit var accessToken: String

    @BeforeEach
    fun authenticate() {
        val registration = AccountTestDataFactory.validRegistrationRequest()
        val session =
            post(registrationPath, registration)
                .expectStatus(HttpStatus.OK)
                .body<AccountSessionDto>()

        accessToken = session.accessToken
    }

    @Test
    fun `get notes returns 200 and a list of notes`() {
        val response =
            get(
                notesPath,
                headers = bearer(accessToken)
            ).expectStatus(HttpStatus.OK)

        response.body<List<NoteDto>>()
    }

    @Test
    fun `add note returns 201 and created note`() {
        val note = NoteTestDataFactory.validNote()

        val response =
            post(
                notesPath,
                note,
                headers = bearer(accessToken)
            ).expectStatus(HttpStatus.CREATED)

        assertThat(response.body<NoteDto>().noteId)
            .isEqualTo(note.noteId)
    }

    @Test
    fun `update note returns updated note`() {
        val note = NoteTestDataFactory.validNote()

        val response =
            put(
                "$notesPath/${note.noteId}",
                note,
                headers = bearer(accessToken)
            ).expectStatus(HttpStatus.OK)

        assertThat(response.body<NoteDto>().noteId)
            .isEqualTo(note.noteId)
    }

    @Test
    fun `delete note returns 204`() {
        val noteId = UUID.randomUUID()

        delete(
            "$notesPath/$noteId",
            headers = bearer(accessToken)
        ).expectStatus(HttpStatus.NO_CONTENT)
    }

    @Test
    fun `sync returns 200`() {
        val request =
            SyncRequestDto(
                lastSyncedAt = Instant.now().minusSeconds(3600),
                localNotes = emptyList()
            )

        post(
            "$notesPath/sync",
            request,
            headers = bearer(accessToken)
        ).expectStatus(HttpStatus.OK)
            .body<SyncResponseDto>()
    }

    @Test
    fun `acquire drawing lock returns locked note`() {
        val noteId = UUID.randomUUID()

        val response =
            post(
                "$notesPath/$noteId/lock",
                headers = bearer(accessToken)
            ).expectStatus(HttpStatus.OK)

        assertThat(response.body<NoteDto>().lockedByDeviceId)
            .isNotNull()
    }

    @Test
    fun `release drawing lock returns 204`() {
        val noteId = UUID.randomUUID()

        delete(
            "$notesPath/$noteId/lock",
            headers = bearer(accessToken)
        ).expectStatus(HttpStatus.NO_CONTENT)
    }

    @Test
    fun `version history returns 200`() {
        val noteId = UUID.randomUUID()

        get(
            "$notesPath/$noteId/history",
            headers = bearer(accessToken)
        ).expectStatus(HttpStatus.OK)
            .body<List<NoteVersionMeta>>()
    }

    @Test
    fun `get version returns 200`() {
        val noteId = UUID.randomUUID()
        val versionId = UUID.randomUUID()

        get(
            "$notesPath/$noteId/history/$versionId",
            headers = bearer(accessToken)
        ).expectStatus(HttpStatus.OK)
            .body<NoteVersionDto>()
    }

    @Test
    fun `restore version returns restored note`() {
        val noteId = UUID.randomUUID()
        val versionId = UUID.randomUUID()

        post(
            "$notesPath/$noteId/restore/$versionId",
            headers = bearer(accessToken)
        ).expectStatus(HttpStatus.OK)
            .body<NoteDto>()
    }

    @Test
    fun `note endpoints reject unauthorized requests`() {
        get(notesPath).expectStatus(HttpStatus.UNAUTHORIZED)

        post(notesPath, NoteTestDataFactory.validNote())
            .expectStatus(HttpStatus.UNAUTHORIZED)

        put(
            "$notesPath/${UUID.randomUUID()}",
            NoteTestDataFactory.validNote()
        ).expectStatus(HttpStatus.UNAUTHORIZED)

        delete("$notesPath/${UUID.randomUUID()}")
            .expectStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `note endpoints reject invalid bearer token`() {
        val headers = bearer("not-a-token")

        get(notesPath, headers)
            .expectStatus(HttpStatus.UNAUTHORIZED)

        post(
            notesPath,
            NoteTestDataFactory.validNote(),
            headers
        ).expectStatus(HttpStatus.UNAUTHORIZED)
    }
}