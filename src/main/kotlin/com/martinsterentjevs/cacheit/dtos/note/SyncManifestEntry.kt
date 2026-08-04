package com.martinsterentjevs.cacheit.dtos.note

import java.time.Instant
import java.util.UUID

data class SyncManifestEntry(
    val noteId: UUID,
    val lastModifiedAt: Instant
)