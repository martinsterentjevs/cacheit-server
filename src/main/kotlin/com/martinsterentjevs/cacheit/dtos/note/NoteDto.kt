package com.martinsterentjevs.cacheit.dtos.note

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID


data class NoteDto(
    //client supplied
    val noteId: UUID,
    val userId: UUID,
    val lastModifiedAt: Instant,
    val isDeleted: Boolean,
    val hasHistory: Boolean,
    @field:NotBlank
    val encTitle: String,
    val encBody: String?,
    val encDrawing: String?,
    // clear both on drawing save
    val lockedByDeviceId: UUID?,
    val lockedAt: Instant?
)
