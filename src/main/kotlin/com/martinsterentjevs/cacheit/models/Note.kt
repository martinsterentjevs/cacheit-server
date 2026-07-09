package com.martinsterentjevs.cacheit.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

// 1️⃣ Note entity definition
import java.time.Instant

@Entity
@Table(name = "notes")
class Note(
    @Id
    var noteId: UUID = UUID.randomUUID(),

    @ManyToOne( fetch = FetchType.LAZY )
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

    var createdAt: Instant = Instant.now(),
){
    override fun equals(other: Any?): Boolean =
        this === other || (other is Note && noteId == other.noteId)

    override fun hashCode(): Int = noteId.hashCode()
}
interface NoteRepository : JpaRepository<Note, UUID> {

}