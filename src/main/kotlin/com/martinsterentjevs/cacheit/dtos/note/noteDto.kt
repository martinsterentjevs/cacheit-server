package com.martinsterentjevs.cacheit.dtos.note

import java.time.Instant
import java.util.UUID

data class NoteDto(
    val noteId: UUID?,
    val userId: UUID,
    val lastModifiedAt: Instant,
    val isDeleted: Boolean,
    val encTitle: String,
    val encBody: String?,
    val encDrawing: String?,
    // clear on drawing save
    val lockedByDeviceId: UUID?,
    // clear on drawing save
    val lockedAt: Instant?
)