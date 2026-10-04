package com.martinsterentjevs.cacheit.integration.controllers.notes

import com.martinsterentjevs.cacheit.dtos.note.NoteDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID

class NoteCrudIntegrationTest : NoteControllerTestBase() {
    private val notesPath = "/notes"

    @Test
    fun `add note returns 201 and the created note`() {
        val (_, session) = registerAndAuthenticate()

        val response =
            post(notesPath, noteDtoFor(encTitle = "My first note"), headers = bearer(session.accessToken))
                .expectStatus(HttpStatus.CREATED)
                .body<NoteDto>()

        assertThat(response.encTitle).isEqualTo("My first note")
        assertThat(response.isDeleted).isFalse()
    }

    @Test
    fun `add note rejects a missing access token`() {
        post(notesPath, noteDtoFor()).expectStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `add note returns 400 on missing noteId`() {
        val (_, session) = registerAndAuthenticate()

        val rawBody =
            """
            {
              "userId": "${UUID.randomUUID()}",
              "lastModifiedAt": "${Instant.now()}",
              "isDeleted": false,
              "encTitle": "some-title",
              "encBody": null,
              "encDrawing": null,
              "lockedByDeviceId": null,
              "lockedAt": null
            }
            """.trimIndent()

        post(notesPath, rawBody, headers = bearer(session.accessToken))
            .expectStatus(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `add note returns 400 on malformed noteId`() {
        val (_, session) = registerAndAuthenticate()

        val rawBody =
            """
            {
              "noteId": "not-a-uuid",
              "userId": "${UUID.randomUUID()}",
              "lastModifiedAt": "${Instant.now()}",
              "isDeleted": false,
              "encTitle": "some-title",
              "encBody": null,
              "encDrawing": null,
              "lockedByDeviceId": null,
              "lockedAt": null
            }
            """.trimIndent()

        post(notesPath, rawBody, headers = bearer(session.accessToken)).expectStatus(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `add note returns 400 on explicit null noteId`() {
        val (_, session) = registerAndAuthenticate()

        val rawBody =
            """
            {
              "noteId": null,
              "userId": "${UUID.randomUUID()}",
              "lastModifiedAt": "${Instant.now()}",
              "isDeleted": false,
              "hasHistory": false,
              "encTitle": "some-title",
              "encBody": null,
              "encDrawing": null,
              "lockedByDeviceId": null,
              "lockedAt": null
            }
            """.trimIndent()

        post(notesPath, rawBody, headers = bearer(session.accessToken)).expectStatus(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `add note ignores a client-supplied userId and uses the authenticated account`() {
        val (_, session) = registerAndAuthenticate()
        val spoofedUserId = UUID.randomUUID() // belongs to nobody

        val response =
            post(
                notesPath,
                noteDtoFor(userId = spoofedUserId, encTitle = "test"),
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.CREATED)
                .body<NoteDto>()

        assertThat(response.userId).isNotEqualTo(spoofedUserId)
    }

    @Test
    fun `get notes returns an empty list for a fresh account`() {
        val (_, session) = registerAndAuthenticate()

        val notes =
            get(
                notesPath,
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.OK).body<List<NoteDto>>()

        assertThat(notes).isEmpty()
    }

    @Test
    fun `get notes returns notes created by the account`() {
        val (_, session) = registerAndAuthenticate()
        createNote(session.accessToken, encTitle = "First")
        createNote(session.accessToken, encTitle = "Second")

        val notes =
            get(
                notesPath,
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.OK).body<List<NoteDto>>()

        assertThat(notes).hasSize(2)
    }

    @Test
    fun `get notes rejects a missing access token`() {
        get(notesPath).expectStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `update note returns 200 and the updated fields`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken, encTitle = "Original")

        val updated =
            put(
                "$notesPath/${note.noteId}",
                noteDtoFor(noteId = note.noteId!!, encTitle = "Updated"),
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.OK).body<NoteDto>()

        assertThat(updated.encTitle).isEqualTo("Updated")
    }

    @Test
    fun `update note rejects a caller who does not own the note`() {
        val (_, ownerSession) = registerAndAuthenticate()
        val note = createNote(ownerSession.accessToken)
        val (_, otherSession) = registerAndAuthenticate()

        put(
            "$notesPath/${note.noteId}",
            noteDtoFor(noteId = note.noteId!!, encTitle = "Hijacked"),
            headers = bearer(otherSession.accessToken)
        ).expectStatus(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `update note returns 404 for an unknown noteId`() {
        val (_, session) = registerAndAuthenticate()

        put(
            "$notesPath/${UUID.randomUUID()}",
            noteDtoFor(),
            headers = bearer(session.accessToken)
        ).expectStatus(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `update note uses the body's noteId, not the path noteId`() {
        val (_, session) = registerAndAuthenticate()
        val noteA = createNote(session.accessToken, encTitle = "A")
        val noteB = createNote(session.accessToken, encTitle = "B")

        // PUT to noteA's URL, but the body claims to be noteB.
        val response =
            put(
                "$notesPath/${noteA.noteId}",
                noteDtoFor(noteId = noteB.noteId!!, encTitle = "Overwritten"),
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.OK).body<NoteDto>()

        assertThat(response.noteId).isEqualTo(noteB.noteId) // documents current behavior
    }

    @Test
    fun `update note rejects a missing access token`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken)

        put("$notesPath/${note.noteId}", noteDtoFor(noteId = note.noteId!!, encTitle = "Edited"))
            .expectStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `delete note returns 204 and the note no longer appears in listing`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken)

        delete("$notesPath/${note.noteId}", headers = bearer(session.accessToken)).expectStatus(HttpStatus.NO_CONTENT)

        val notes =
            get(
                notesPath,
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.OK).body<List<NoteDto>>()
        assertThat(notes).isEmpty()
    }

    @Test
    fun `delete note rejects a caller who does not own the note`() {
        val (_, ownerSession) = registerAndAuthenticate()
        val note = createNote(ownerSession.accessToken)
        val (_, otherSession) = registerAndAuthenticate()

        delete(
            "$notesPath/${note.noteId}",
            headers = bearer(otherSession.accessToken)
        ).expectStatus(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `delete note returns 404 for an unknown noteId`() {
        val (_, session) = registerAndAuthenticate()

        delete(
            "$notesPath/${UUID.randomUUID()}",
            headers = bearer(session.accessToken)
        ).expectStatus(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `getNote returns a single note owned by the caller`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken, encTitle = "Mine")

        val fetched =
            get(
                "$notesPath/${note.noteId}",
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.OK).body<NoteDto>()

        assertThat(fetched.noteId).isEqualTo(note.noteId)
    }

    @Test
    fun `getNote rejects a caller who does not own the note`() {
        val (_, ownerSession) = registerAndAuthenticate()
        val note = createNote(ownerSession.accessToken)
        val (_, otherSession) = registerAndAuthenticate()

        get(
            "$notesPath/${note.noteId}",
            headers = bearer(otherSession.accessToken)
        ).expectStatus(HttpStatus.FORBIDDEN)
    }
}