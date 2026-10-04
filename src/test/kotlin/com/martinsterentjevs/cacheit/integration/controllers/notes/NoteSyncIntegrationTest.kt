package com.martinsterentjevs.cacheit.integration.controllers.notes

import com.martinsterentjevs.cacheit.dtos.note.NoteDto
import com.martinsterentjevs.cacheit.dtos.note.SyncManifestEntry
import com.martinsterentjevs.cacheit.dtos.note.SyncRequestDto
import com.martinsterentjevs.cacheit.dtos.note.SyncResponseDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant

class NoteSyncIntegrationTest : NoteControllerTestBase() {
    private val syncPath = "/notes/sync"

    @Test
    fun `sync returns a newly created note when the client manifest is empty`() {
        val (_, session) = registerAndAuthenticate()
        createNote(session.accessToken, encTitle = "New note")

        val response =
            post(
                syncPath,
                SyncRequestDto(lastSyncedAt = Instant.now().minusSeconds(60), localNotes = emptyList()),
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.OK).body<SyncResponseDto>()

        assertThat(response.updatedNotes).hasSize(1)
        assertThat(response.deletedIds).isEmpty()
    }

    @Test
    fun `sync omits a note the client's manifest already has current`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken)

        val response =
            post(
                syncPath,
                SyncRequestDto(
                    lastSyncedAt = Instant.now(),
                    localNotes = listOf(SyncManifestEntry(note.noteId!!, note.lastModifiedAt.plusSeconds(1)))
                ),
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.OK).body<SyncResponseDto>()

        assertThat(response.updatedNotes).isEmpty()
    }

    @Test
    fun `sync includes a note the client's manifest has a stale timestamp for`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken)
        val updated =
            put(
                "/notes/${note.noteId}",
                noteDtoFor(noteId = note.noteId!!, encTitle = "Edited"),
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.OK).body<NoteDto>()

        val response =
            post(
                syncPath,
                SyncRequestDto(
                    lastSyncedAt = Instant.now().minusSeconds(60),
                    // non-null marking used to avoid smart cast error
                    localNotes = listOf(SyncManifestEntry(noteId = note.noteId!!, note.lastModifiedAt))
                ),
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.OK).body<SyncResponseDto>()

        assertThat(response.updatedNotes.map { it.noteId }).contains(updated.noteId)
    }

    @Test
    fun `sync reports a deleted note the client's manifest still lists`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken)
        delete("/notes/${note.noteId}", headers = bearer(session.accessToken)).expectStatus(HttpStatus.NO_CONTENT)

        val response =
            post(
                syncPath,
                SyncRequestDto(
                    lastSyncedAt = Instant.now().minusSeconds(60),
                    localNotes = listOf(SyncManifestEntry(note.noteId!!, note.lastModifiedAt))
                ),
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.OK).body<SyncResponseDto>()

        assertThat(response.deletedIds).containsExactly(note.noteId)
    }

    @Test
    fun `sync does not report a deleted note the client's manifest never had`() {
        val (_, session) = registerAndAuthenticate()
        val note = createNote(session.accessToken)
        delete("/notes/${note.noteId}", headers = bearer(session.accessToken)).expectStatus(HttpStatus.NO_CONTENT)

        val response =
            post(
                syncPath,
                SyncRequestDto(lastSyncedAt = Instant.now().minusSeconds(60), localNotes = emptyList()),
                headers = bearer(session.accessToken)
            ).expectStatus(HttpStatus.OK).body<SyncResponseDto>()

        assertThat(response.deletedIds).isEmpty()
    }

    @Test
    fun `sync rejects a missing access token`() {
        post(syncPath, SyncRequestDto(lastSyncedAt = Instant.now(), localNotes = emptyList()))
            .expectStatus(HttpStatus.UNAUTHORIZED)
    }
}