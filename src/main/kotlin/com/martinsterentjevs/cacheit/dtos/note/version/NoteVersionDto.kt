package com.martinsterentjevs.cacheit.dtos.note.version

import java.util.UUID

data class NoteVersionDto(
    val versionId: UUID,
    val encTitle: String,
    val encBody: String?,
    val encDrawing: String?
)