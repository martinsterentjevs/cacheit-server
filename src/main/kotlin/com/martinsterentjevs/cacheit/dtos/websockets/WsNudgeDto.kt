package com.martinsterentjevs.cacheit.dtos.websockets

import java.time.Instant
import java.util.UUID

data class WsNudgeDto(
    val type: WsNudgeType,
    val noteId: UUID,
    val lastModifiedAt: Instant? = null,
    val lockedByDeviceId: UUID? = null
)

enum class WsNudgeType {
    NOTE_UPDATED,
    NOTE_DELETED,
    NOTE_RESTORED,
    NOTE_LOCK_ACQUIRED,
    NOTE_LOCK_RELEASED
}