package com.martinsterentjevs.cacheit.dtos.note

import java.time.Instant

data class SyncRequestDto(
    val lastSyncedAt: Instant,
    val localNotes: List<SyncManifestEntry>
)