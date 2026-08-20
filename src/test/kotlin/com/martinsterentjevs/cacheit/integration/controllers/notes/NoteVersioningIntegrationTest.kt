package com.martinsterentjevs.cacheit.integration.controllers.notes

import com.martinsterentjevs.cacheit.dtos.note.NoteDto
import com.martinsterentjevs.cacheit.dtos.note.version.NoteVersionDto
import com.martinsterentjevs.cacheit.dtos.note.version.NoteVersionMeta
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class NoteVersioningIntegrationTest : NoteControllerTestBase() {
    @Test
    fun `history lists a version per save, newest first`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken, encTitle = "First")
        put(
            "/notes/${note.noteId}",
            noteDtoFor(noteId = note.noteId!!, encTitle = "Second"),
            headers = bearer(session.accessToken)
        ).expectStatus(HttpStatus.OK)

        val history =
            get("/notes/${note.noteId}/history", headers = bearer(session.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<List<NoteVersionMeta>>()

        assertThat(history).hasSize(2)
        assertThat(history.first().isCurrent).isTrue()
    }

    @Test
    fun `history rejects a caller who does not own the note`() {
        val (_, ownerSession) = registerAndAuthenticate()
        val note = createNote(ownerSession.accessToken)
        val (_, otherSession) = registerAndAuthenticate()

        get("/notes/${note.noteId}/history", headers = bearer(otherSession.accessToken))
            .expectStatus(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `history returns 404 for an unknown noteId`() {
        val (_, session) = registerAndAuthenticate()

        get("/notes/${UUID.randomUUID()}/history", headers = bearer(session.accessToken))
            .expectStatus(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `fetching a version returns its full encrypted content including drawing`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken, encTitle = "Original", encDrawing = "original-drawing")

        val history =
            get("/notes/${note.noteId}/history", headers = bearer(session.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<List<NoteVersionMeta>>()
        val versionId = history.first().versionId

        val version =
            get("/notes/${note.noteId}/history/$versionId", headers = bearer(session.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<NoteVersionDto>()

        assertThat(version.encTitle).isEqualTo("Original")
        assertThat(version.encDrawing).isEqualTo("original-drawing")
    }

    @Test
    fun `fetching a version rejects a versionId that belongs to a different note`() {
        val (_, session) = registerAndAuthenticate()
        val noteA = createNote(session.accessToken, encTitle = "Note A")
        val noteB = createNote(session.accessToken, encTitle = "Note B")

        val noteBHistory =
            get("/notes/${noteB.noteId}/history", headers = bearer(session.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<List<NoteVersionMeta>>()
        val noteBVersionId = noteBHistory.first().versionId

        get("/notes/${noteA.noteId}/history/$noteBVersionId", headers = bearer(session.accessToken))
            .expectStatus(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `restoring a version brings back its title, body, and drawing together`() {
        val (_, session) = registerAndAuthenticate()
        val note =
            createNote(
                session.accessToken,
                encTitle = "Original",
                encBody = "Original body",
                encDrawing = "original-drawing"
            )

        put(
            "/notes/${note.noteId}",
            noteDtoFor(
                noteId = note.noteId!!,
                encTitle = "Changed",
                encBody = "Changed body",
                encDrawing = "changed-drawing"
            ),
            headers = bearer(session.accessToken)
        ).expectStatus(HttpStatus.OK)

        val history =
            get("/notes/${note.noteId}/history", headers = bearer(session.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<List<NoteVersionMeta>>()
        val originalVersionId = history.last().versionId // oldest entry = the original creation snapshot

        val restored =
            post(
                "/notes/${note.noteId}/restore/$originalVersionId",
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.OK).body<NoteDto>()

        assertThat(restored.encTitle).isEqualTo("Original")
        assertThat(restored.encBody).isEqualTo("Original body")
        assertThat(restored.encDrawing).isEqualTo("original-drawing")
    }

    @Test
    fun `restoring a version is append-only - history grows rather than rewinding`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken, encTitle = "Original")
        put(
            "/notes/${note.noteId}",
            noteDtoFor(noteId = note.noteId!!, encTitle = "Changed"),
            headers = bearer(session.accessToken)
        ).expectStatus(HttpStatus.OK)

        val historyBefore =
            get("/notes/${note.noteId}/history", headers = bearer(session.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<List<NoteVersionMeta>>()
        val originalVersionId = historyBefore.last().versionId

        post("/notes/${note.noteId}/restore/$originalVersionId", headers = bearer(session.accessToken))
            .expectStatus(HttpStatus.OK)

        val historyAfter =
            get("/notes/${note.noteId}/history", headers = bearer(session.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<List<NoteVersionMeta>>()

        assertThat(historyAfter.size).isEqualTo(historyBefore.size + 1)
    }

    @Test
    fun `restore rejects a caller who does not own the note`() {
        val (_, ownerSession) = registerAndAuthenticate()
        val note = createNote(ownerSession.accessToken)
        val history =
            get("/notes/${note.noteId}/history", headers = bearer(ownerSession.accessToken))
                .expectStatus(HttpStatus.OK)
                .body<List<NoteVersionMeta>>()
        val (_, otherSession) = registerAndAuthenticate()

        post(
            "/notes/${note.noteId}/restore/${history.first().versionId}",
            headers = bearer(otherSession.accessToken)
        ).expectStatus(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `restore returns 404 for an unknown versionId`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken)

        post("/notes/${note.noteId}/restore/${UUID.randomUUID()}", headers = bearer(session.accessToken))
            .expectStatus(HttpStatus.NOT_FOUND)
    }
    @Test
    fun `update note sets hasHistory to true after a second save`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken, encTitle = "Original") // 1st save -> 1 version

        val updated =
            put(
                "/notes/${note.noteId}",
                noteDtoFor(noteId = note.noteId!!, encTitle = "Updated"), // 2nd save -> 2 versions
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.OK).body<NoteDto>()

        assertThat(updated.hasHistory).isTrue()
    }

    @Test
    fun `a note with only its initial save reports hasHistory as false`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken) // 1 version - the create itself

        assertThat(note.hasHistory).isFalse()
    }
}