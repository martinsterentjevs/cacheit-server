package com.martinsterentjevs.cacheit.integration.controllers.notes

import com.martinsterentjevs.cacheit.dtos.note.NoteDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class NoteLockingIntegrationTest : NoteControllerTestBase() {
    @Test
    fun `acquiring a lock on an unlocked note succeeds`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken)

        val locked =
            post("/notes/${note.noteId}/lock", headers = bearer(session.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<NoteDto>()

        assertThat(locked.lockedByDeviceId).isNotNull()
    }

    @Test
    fun `re-acquiring a lock from the same device succeeds`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken)
        post("/notes/${note.noteId}/lock", headers = bearer(session.accessToken)).expectStatus(HttpStatus.OK)

        post("/notes/${note.noteId}/lock", headers = bearer(session.accessToken)).expectStatus(HttpStatus.OK)
    }

    @Test
    fun `re-acquiring a lock from the same device refreshes lockedAt`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken)

        val firstLock =
            post("/notes/${note.noteId}/lock", headers = bearer(session.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<NoteDto>()

        Thread.sleep(50) // force a strictly later Instant on the second acquire

        val secondLock =
            post("/notes/${note.noteId}/lock", headers = bearer(session.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<NoteDto>()

        assertThat(secondLock.lockedAt).isAfter(firstLock.lockedAt)
    }

    @Test
    fun `acquiring a lock held by a different device is rejected`() {
        val (registration, firstDeviceSession) = registerAndAuthenticate()
        val note = createNote(firstDeviceSession.accessToken)
        post("/notes/${note.noteId}/lock", headers = bearer(firstDeviceSession.accessToken)).expectStatus(HttpStatus.OK)

        val secondDeviceSession = loginSecondDevice(registration)

        post("/notes/${note.noteId}/lock", headers = bearer(secondDeviceSession.accessToken))
            .expectStatus(HttpStatus.CONFLICT)
    }

    @Test
    fun `releasing a lock allows a different device to acquire it`() {
        val (registration, firstDeviceSession) = registerAndAuthenticate()
        val note = createNote(firstDeviceSession.accessToken)
        post("/notes/${note.noteId}/lock", headers = bearer(firstDeviceSession.accessToken)).expectStatus(HttpStatus.OK)

        delete("/notes/${note.noteId}/lock", headers = bearer(firstDeviceSession.accessToken))
            .expectStatus(HttpStatus.NO_CONTENT)

        val secondDeviceSession = loginSecondDevice(registration)
        post("/notes/${note.noteId}/lock", headers = bearer(secondDeviceSession.accessToken))
            .expectStatus(HttpStatus.OK)
    }

    @Test
    fun `updating a note is not blocked by another device's active lock`() {
        // Documents current, intentional behavior per note-reference.md's open item under
        // "Note update": the lock check only applies inside acquireDrawingLock, not updateNote —
        // a locked note's title/body can still be edited by another device today.
        val (registration, firstDeviceSession) = registerAndAuthenticate()
        val note = createNote(firstDeviceSession.accessToken)
        post("/notes/${note.noteId}/lock", headers = bearer(firstDeviceSession.accessToken)).expectStatus(HttpStatus.OK)

        val secondDeviceSession = loginSecondDevice(registration)

        put(
            "/notes/${note.noteId}",
            noteDtoFor(noteId = note.noteId!!, encTitle = "Edited despite lock"),
            headers = bearer(secondDeviceSession.accessToken)
        ).expectStatus(HttpStatus.OK)
    }

    @Test
    fun `acquiring a lock on an unknown note returns 404`() {
        val (_, session) = registerAndAuthenticate()

        post("/notes/${UUID.randomUUID()}/lock", headers = bearer(session.accessToken))
            .expectStatus(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `acquiring a lock rejects a caller who does not own the note`() {
        val (_, ownerSession) = registerAndAuthenticate()
        val note = createNote(ownerSession.accessToken)
        val (_, otherSession) = registerAndAuthenticate()

        post("/notes/${note.noteId}/lock", headers = bearer(otherSession.accessToken))
            .expectStatus(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `releasing a lock rejects a caller who does not own the note`() {
        val (_, ownerSession) = registerAndAuthenticate()
        val note = createNote(ownerSession.accessToken)
        val (_, otherSession) = registerAndAuthenticate()

        delete("/notes/${note.noteId}/lock", headers = bearer(otherSession.accessToken))
            .expectStatus(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `acquiring a lock rejects a missing access token`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken)

        post("/notes/${note.noteId}/lock").expectStatus(HttpStatus.UNAUTHORIZED)
    }

    // Stale-lock takeover (a lock expiring after DRAWING_LOCK_TTL_SECONDS) is covered at the unit
    // level in NoteServiceTest. Exercising it here would mean sleeping for real wall-clock time
    // (the TTL is 3600s) or injecting a Clock — neither fits a black-box HTTP integration test.
}