package com.martinsterentjevs.cacheit.events

import java.time.Instant
import java.util.UUID

sealed class NoteDomainEvent {
    abstract val userId: UUID
    abstract val noteId: UUID
}

data class NoteUpdatedEvent(
    override val noteId: UUID,
    override val userId: UUID,
    val lastModifiedAt: Instant
) : NoteDomainEvent()

data class NoteDeletedEvent(
    override val noteId: UUID,
    override val userId: UUID,
    val lastModifiedAt: Instant
) : NoteDomainEvent()

data class NoteRestoredEvent(
    override val noteId: UUID,
    override val userId: UUID,
    val lastModifiedAt: Instant
) : NoteDomainEvent()

data class NoteLockAcquiredEvent(
    override val noteId: UUID,
    override val userId: UUID,
    val lockedByDeviceId: UUID
) : NoteDomainEvent()

data class NoteLockReleasedEvent(
    override val noteId: UUID,
    override val userId: UUID
) : NoteDomainEvent()