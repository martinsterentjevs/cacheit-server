package com.martinsterentjevs.cacheit.dtos.note

import java.time.Instant
import java.util.UUID

data class SyncResponseDto(
    val updatedNotes: List<NoteDto>,
    val deletedIds: List<UUID>,
    val serverTime: Instant
)