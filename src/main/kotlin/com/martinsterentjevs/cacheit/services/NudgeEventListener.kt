package com.martinsterentjevs.cacheit.services

import com.martinsterentjevs.cacheit.dtos.websockets.WsNudgeDto
import com.martinsterentjevs.cacheit.dtos.websockets.WsNudgeType
import com.martinsterentjevs.cacheit.events.NoteDeletedEvent
import com.martinsterentjevs.cacheit.events.NoteLockAcquiredEvent
import com.martinsterentjevs.cacheit.events.NoteLockReleasedEvent
import com.martinsterentjevs.cacheit.events.NoteRestoredEvent
import com.martinsterentjevs.cacheit.events.NoteUpdatedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class NudgeEventListener(
    private val nudgeService: NudgeService
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onNoteUpdated(event: NoteUpdatedEvent) {
        nudgeService.send(
            event.userId.toString(),
            WsNudgeDto(
                type = WsNudgeType.NOTE_UPDATED,
                noteId = event.noteId,
                lastModifiedAt = event.lastModifiedAt
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onNoteDeleted(event: NoteDeletedEvent) {
        nudgeService.send(
            event.userId.toString(),
            WsNudgeDto(
                type = WsNudgeType.NOTE_DELETED,
                noteId = event.noteId,
                lastModifiedAt = event.lastModifiedAt
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onNoteRestored(event: NoteRestoredEvent) {
        nudgeService.send(
            event.userId.toString(),
            WsNudgeDto(
                type = WsNudgeType.NOTE_RESTORED,
                noteId = event.noteId,
                lastModifiedAt = event.lastModifiedAt
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onNoteLockAcquired(event: NoteLockAcquiredEvent) {
        nudgeService.send(
            event.userId.toString(),
            WsNudgeDto(
                type = WsNudgeType.NOTE_LOCK_ACQUIRED,
                noteId = event.noteId,
                lockedByDeviceId = event.lockedByDeviceId
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onNoteLockReleased(event: NoteLockReleasedEvent) {
        nudgeService.send(
            event.userId.toString(),
            WsNudgeDto(
                type = WsNudgeType.NOTE_LOCK_RELEASED,
                noteId = event.noteId
            )
        )
    }
}