package com.martinsterentjevs.cacheit.models

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
@Table(name = "note_versions")
class NoteVersion (
    @Id
    var versionId: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    var note : Note,
    @Column(columnDefinition = "TEXT")
    var encTitle: String,
    @Column(columnDefinition = "TEXT")
    var encBody: String? = null,

    var createdAt: Instant = Instant.now(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = true)
    var device: DeviceSession? = null,
    var isCurrent: Boolean = true,
    ){
    override fun equals(other: Any?): Boolean =
        this === other || (other is NoteVersion && versionId == other.versionId)

    override fun hashCode(): Int = versionId.hashCode()
}
interface NoteVersionRepository : JpaRepository<NoteVersion, UUID> {}