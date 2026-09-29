package com.martinsterentjevs.cacheit.models

import com.martinsterentjevs.cacheit.exceptions.NoteNotFoundException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notes")
class Note(
    @Id
    var noteId: UUID,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var account: Account,
    @Column(columnDefinition = "TEXT")
    var encTitle: String,
    @Column(columnDefinition = "TEXT")
    var encBody: String? = null,
    @Column(columnDefinition = "TEXT")
    var encDrawing: String? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locked_by_device_id", nullable = true)
    var lockedByDevice: DeviceSession? = null,
    var lockedAt: Instant? = null,
    var isDeleted: Boolean = false,
    var lastModifiedAt: Instant = Instant.now(),
    var createdAt: Instant = Instant.now()
) {
    override fun equals(other: Any?): Boolean = this === other || (other is Note && noteId == other.noteId)

    override fun hashCode(): Int = noteId.hashCode()
}

interface NoteRepository : JpaRepository<Note, UUID> {
    fun save(note: Note): Note

    // For normal listing (GET /notes) — filtered and ordered for display.
    fun findAllByAccountUserIdAndIsDeletedFalseOrderByLastModifiedAtDesc(userId: UUID): List<Note>

    // For sync (POST /notes/sync) — unfiltered, since the delta needs to see soft-deleted notes
    // too (to populate SyncResponseDto.deletedIds), not just the ones a normal listing would show.
    fun findAllByAccountUserId(userId: UUID): List<Note>

    fun findByNoteId(noteId: UUID): Note? = findByNoteIdAndIsDeletedFalse(noteId) ?: throw NoteNotFoundException()

    fun findByNoteIdAndIsDeletedFalse(noteId: UUID): Note?

    fun deleteAllByAccountUserId(userId: UUID)

    fun findAllByLockedByDeviceDeviceId(deviceId: UUID): List<Note>

    fun findAllByLockedAtBefore(cutoff: Instant): List<Note>
}