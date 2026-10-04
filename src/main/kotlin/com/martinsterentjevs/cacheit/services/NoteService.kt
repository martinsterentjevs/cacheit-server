package com.martinsterentjevs.cacheit.services

import com.martinsterentjevs.cacheit.dtos.note.NoteDto
import com.martinsterentjevs.cacheit.dtos.note.SyncRequestDto
import com.martinsterentjevs.cacheit.dtos.note.SyncResponseDto
import com.martinsterentjevs.cacheit.dtos.note.version.NoteVersionDto
import com.martinsterentjevs.cacheit.dtos.note.version.NoteVersionMeta
import com.martinsterentjevs.cacheit.events.NoteDeletedEvent
import com.martinsterentjevs.cacheit.events.NoteLockAcquiredEvent
import com.martinsterentjevs.cacheit.events.NoteLockReleasedEvent
import com.martinsterentjevs.cacheit.events.NoteRestoredEvent
import com.martinsterentjevs.cacheit.events.NoteUpdatedEvent
import com.martinsterentjevs.cacheit.exceptions.InvalidSessionException
import com.martinsterentjevs.cacheit.exceptions.NoteLockedException
import com.martinsterentjevs.cacheit.exceptions.NoteNotFoundException
import com.martinsterentjevs.cacheit.exceptions.NoteOwnershipValidationException
import com.martinsterentjevs.cacheit.exceptions.NoteVersionNotFoundException
import com.martinsterentjevs.cacheit.models.DeviceSession
import com.martinsterentjevs.cacheit.models.DeviceSessionRepository
import com.martinsterentjevs.cacheit.models.Note
import com.martinsterentjevs.cacheit.models.NoteRepository
import com.martinsterentjevs.cacheit.models.NoteVersion
import com.martinsterentjevs.cacheit.models.NoteVersionRepository
import com.martinsterentjevs.cacheit.models.auth.RequestIdentity
import jakarta.transaction.Transactional
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class NoteService(
    private val noteRepository: NoteRepository,
    private val userService: UserService,
    private val noteVersionRepository: NoteVersionRepository,
    private val deviceSessionRepository: DeviceSessionRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    companion object {
        // Deliberately independent of the access token TTL, not shared/aliased with it — see
        // note-reference.md's "Drawing lock — Acquire" section. Anchored strictly to
        // Note.lockedAt; never extended by the holding device refreshing its session mid-edit.
        const val DRAWING_LOCK_TTL_SECONDS = 3600L
    }

    fun getNotes(bearer: String): List<NoteDto> {
        val identity = userService.getRequestIdentity(bearer)
        return convertToDto(
            noteRepository.findAllByAccountUserIdAndIsDeletedFalseOrderByLastModifiedAtDesc(identity.account.userId)
        )
    }

    fun addNote(
        noteDto: NoteDto,
        bearer: String
    ): NoteDto {
        val identity = userService.getRequestIdentity(bearer)
        val device = deviceSessionRepository.findByDeviceId(identity.deviceId)

        val note =
            Note(
                noteId = noteDto.noteId,
                account = identity.account,
                encTitle = noteDto.encTitle,
                encBody = noteDto.encBody,
                encDrawing = noteDto.encDrawing,
                isDeleted = false,
                lastModifiedAt = Instant.now(),
                createdAt = Instant.now()
            )
        noteRepository.save(note)
        noteVersionRepository.save(buildNoteVersion(note, device))
        return note.toDto()
    }

    fun getNote(
        noteId: UUID,
        bearer: String
    ): NoteDto {
        val (note, _) = requireOwnedNote(noteId, bearer)
        return note.toDto()
    }

    @Transactional
    fun updateNote(
        incoming: NoteDto,
        bearer: String
    ): NoteDto {
        val (note, identity) = requireOwnedNote(incoming.noteId, bearer)
        val device = deviceSessionRepository.findByDeviceId(identity.deviceId)

        note.encTitle = incoming.encTitle
        note.encBody = incoming.encBody
        note.encDrawing = incoming.encDrawing
        note.lastModifiedAt = Instant.now()
        note.lockedByDevice = null
        note.lockedAt = null
        retireCurrentVersionAndSnapshot(note, device)

        val saved = noteRepository.save(note)
        eventPublisher.publishEvent(
            NoteUpdatedEvent(userId = identity.account.userId, noteId = saved.noteId, lastModifiedAt = Instant.now())
        )
        return saved.toDto()
    }

    @Transactional
    fun deleteNote(
        noteId: UUID,
        bearer: String
    ) {
        val (note, identity) = requireOwnedNote(noteId, bearer)
        val device = deviceSessionRepository.findByDeviceId(identity.deviceId)

        note.isDeleted = true
        note.lastModifiedAt = Instant.now()
        retireCurrentVersionAndSnapshot(note, device)
        noteRepository.save(note)

        eventPublisher.publishEvent(
            NoteDeletedEvent(userId = identity.account.userId, noteId = note.noteId, lastModifiedAt = Instant.now())
        )
    }

    fun getSyncDelta(
        request: SyncRequestDto,
        bearer: String
    ): SyncResponseDto {
        val identity = userService.getRequestIdentity(bearer)
        val serverNotes = noteRepository.findAllByAccountUserId(identity.account.userId)
        val clientManifest = request.localNotes.associateBy { it.noteId }

        val updatedNotes =
            serverNotes
                .filter { note ->
                    !note.isDeleted &&
                        (clientManifest[note.noteId]?.let { note.lastModifiedAt.isAfter(it.lastModifiedAt) } ?: true)
                }.map { it.toDto() }

        val deletedIds =
            serverNotes
                .filter { it.isDeleted && clientManifest.containsKey(it.noteId) }
                .map { it.noteId }

        return SyncResponseDto(
            updatedNotes = updatedNotes,
            deletedIds = deletedIds,
            serverTime = Instant.now()
        )
    }

    @Transactional
    fun acquireDrawingLock(
        noteId: UUID,
        bearer: String
    ): NoteDto {
        val (note, identity) = requireOwnedNote(noteId, bearer)
        val device =
            deviceSessionRepository.findByDeviceId(identity.deviceId)
                ?: throw InvalidSessionException("No active device session for this token.")

        val heldByOtherDevice = note.lockedByDevice != null && note.lockedByDevice?.deviceId != device.deviceId
        if (heldByOtherDevice && !isLockStale(note)) {
            throw NoteLockedException("This note is currently being edited on another device.")
        }

        note.lockedByDevice = device
        note.lockedAt = Instant.now()

        val newNote = noteRepository.save(note)
        eventPublisher.publishEvent(
            NoteLockAcquiredEvent(
                userId = identity.account.userId,
                noteId = note.noteId,
                lockedByDeviceId = identity.deviceId
            )
        )
        return newNote.toDto()
    }

    @Transactional
    fun releaseDrawingLock(
        noteId: UUID,
        bearer: String
    ) {
        val (note, identity) = requireOwnedNote(noteId, bearer)
        note.lockedByDevice = null
        note.lockedAt = null

        eventPublisher.publishEvent(NoteLockReleasedEvent(userId = identity.account.userId, noteId = note.noteId))

        noteRepository.save(note)
    }

    fun getVersionHistory(
        noteId: UUID,
        bearer: String
    ): List<NoteVersionMeta> {
        requireOwnedNote(noteId, bearer)
        return noteVersionRepository.findAllByNoteNoteIdOrderByCreatedAtDesc(noteId).map { it.toMeta() }
    }

    fun getVersion(
        noteId: UUID,
        versionId: UUID,
        bearer: String
    ): NoteVersionDto {
        requireOwnedNote(noteId, bearer)
        return findOwnedVersion(noteId, versionId).toDto()
    }

    @Transactional
    fun restoreVersion(
        noteId: UUID,
        versionId: UUID,
        bearer: String
    ): NoteDto {
        val (note, identity) = requireOwnedNote(noteId, bearer)
        val version = findOwnedVersion(noteId, versionId)
        val device = deviceSessionRepository.findByDeviceId(identity.deviceId)

        note.encTitle = version.encTitle
        note.encBody = version.encBody
        note.encDrawing = version.encDrawing
        note.lastModifiedAt = Instant.now()
        retireCurrentVersionAndSnapshot(note, device)

        eventPublisher.publishEvent(
            NoteRestoredEvent(
                userId = identity.account.userId,
                noteId = note.noteId,
                lastModifiedAt = note.lastModifiedAt
            )
        )
        return noteRepository.save(note).toDto()
    }

    /** Loads the note and verifies ownership in one pass — one identity resolution per request. */
    @Throws(NoteNotFoundException::class)
    private fun requireOwnedNote(
        noteId: UUID?,
        bearer: String
    ): Pair<Note, RequestIdentity> {
        if (noteId == null) throw NoteNotFoundException()
        val note =
            noteRepository.findByNoteId(noteId)
                ?: throw NoteNotFoundException()
        val identity = userService.getRequestIdentity(bearer)
        if (note.account.userId != identity.account.userId) {
            throw NoteOwnershipValidationException("The user trying to access this note is not its owner.")
        }
        return note to identity
    }

    private fun findOwnedVersion(
        noteId: UUID,
        versionId: UUID
    ): NoteVersion {
        val version =
            noteVersionRepository
                .findById(versionId)
                .orElseThrow { NoteVersionNotFoundException() }
        if (version.note.noteId != noteId) {
            throw NoteVersionNotFoundException("Version does not belong to the specified note.")
        }
        return version
    }

    private fun isLockStale(note: Note): Boolean {
        val lockedAt = note.lockedAt ?: return true
        return Instant.now().isAfter(lockedAt.plusSeconds(DRAWING_LOCK_TTL_SECONDS))
    }

    private fun buildNoteVersion(
        note: Note,
        device: DeviceSession? = null
    ): NoteVersion =
        NoteVersion(
            note = note,
            encTitle = note.encTitle,
            encBody = note.encBody,
            encDrawing = note.encDrawing,
            device = device,
            isCurrent = true
        )

    /**
     * Retires the note's existing current version (if any) and snapshots its new state as the
     * fresh current version. Shared by updateNote, deleteNote, and restoreVersion — all three are
     * "change the note's state, then snapshot it," differing only in what changed.
     */
    private fun retireCurrentVersionAndSnapshot(
        note: Note,
        device: DeviceSession?
    ) {
        noteVersionRepository.findByNoteAndIsCurrentTrue(note)?.let { previous ->
            previous.isCurrent = false
            noteVersionRepository.save(previous)
        }
        noteVersionRepository.save(buildNoteVersion(note, device))
    }

    private fun Note.toDto(): NoteDto =
        NoteDto(
            noteId = noteId,
            userId = account.userId,
            lastModifiedAt = lastModifiedAt,
            isDeleted = isDeleted,
            hasHistory = noteVersionRepository.findAllByNoteNoteIdOrderByCreatedAtDesc(noteId).count() > 1,
            encTitle = encTitle,
            encBody = encBody,
            encDrawing = encDrawing,
            lockedByDeviceId = lockedByDevice?.deviceId,
            lockedAt = lockedAt
        )

    private fun convertToDto(notes: List<Note>): List<NoteDto> = notes.map { it.toDto() }

    private fun NoteVersion.toMeta(): NoteVersionMeta =
        NoteVersionMeta(
            versionId = versionId,
            noteId = note.noteId,
            createdAt = createdAt,
            deviceId = device?.deviceId,
            isCurrent = isCurrent
        )

    private fun NoteVersion.toDto(): NoteVersionDto =
        NoteVersionDto(
            versionId = versionId,
            encTitle = encTitle,
            encBody = encBody,
            encDrawing = encDrawing
        )
}