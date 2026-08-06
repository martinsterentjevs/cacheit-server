package com.martinsterentjevs.cacheit.testdata

import com.martinsterentjevs.cacheit.dtos.note.NoteDto
import java.time.Instant
import java.util.UUID

object NoteTestDataFactory {
    private const val TEST_ENC_TITLE = "DummyTitle"
    private const val TEST_ENC_BODY = "DummyBody"
    private const val TEST_ENC_DRAWING = "DummyDrawing"

    private fun randomNoteId() = UUID.randomUUID()

    private fun randomUserId() = UUID.randomUUID()

    fun validNote(
        noteId: UUID? = null,
        userId: UUID = randomUserId(),
        lastModifiedAt: Instant = Instant.now(),
        isDeleted: Boolean = false,
        encTitle: String = TEST_ENC_TITLE,
        encBody: String? = TEST_ENC_BODY,
        encDrawing: String? = TEST_ENC_DRAWING,
        lockedByDeviceId: UUID? = null,
        lockedAt: Instant? = null
    ) = NoteDto(
        noteId = noteId,
        userId = userId,
        lastModifiedAt = lastModifiedAt,
        isDeleted = isDeleted,
        encTitle = encTitle,
        encBody = encBody,
        encDrawing = encDrawing,
        lockedByDeviceId = lockedByDeviceId,
        lockedAt = lockedAt
    )

    fun deletedNote(
        noteId: UUID,
        userId: UUID = randomUserId()
    ) = validNote(
        noteId = noteId,
        userId = userId,
        isDeleted = true
    )

    fun lockedNote(
        noteId: UUID,
        userId: UUID = randomUserId(),
        lockedByDeviceId: UUID = UUID.randomUUID(),
        lockedAt: Instant = Instant.now()
    ) = validNote(
        noteId = noteId,
        userId = userId,
        lockedByDeviceId = lockedByDeviceId,
        lockedAt = lockedAt
    )
}