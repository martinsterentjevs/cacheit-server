package com.martinsterentjevs.cacheit.dtos.note.version

import java.time.Instant
import java.util.UUID

data class NoteVersionMeta(
    val versionId: UUID,
    val noteId: UUID,
    val createdAt: Instant,
    // Was: UUID (non-nullable) — mismatched NoteVersion.device's real nullability (a device may
    // since be deleted; schemas.md itself documents this field as nullable for that reason).
    val deviceId: UUID?,
    val isCurrent: Boolean
)