package com.martinsterentjevs.cacheit.unit.test.services

import com.martinsterentjevs.cacheit.dtos.note.NoteDto
import com.martinsterentjevs.cacheit.dtos.note.SyncManifestEntry
import com.martinsterentjevs.cacheit.dtos.note.SyncRequestDto
import com.martinsterentjevs.cacheit.exceptions.InvalidSessionException
import com.martinsterentjevs.cacheit.exceptions.NoteLockedException
import com.martinsterentjevs.cacheit.exceptions.NoteNotFoundException
import com.martinsterentjevs.cacheit.exceptions.NoteOwnershipValidationException
import com.martinsterentjevs.cacheit.exceptions.NoteVersionNotFoundException
import com.martinsterentjevs.cacheit.models.Account
import com.martinsterentjevs.cacheit.models.DeviceSession
import com.martinsterentjevs.cacheit.models.DeviceSessionRepository
import com.martinsterentjevs.cacheit.models.Note
import com.martinsterentjevs.cacheit.models.NoteRepository
import com.martinsterentjevs.cacheit.models.NoteVersion
import com.martinsterentjevs.cacheit.models.NoteVersionRepository
import com.martinsterentjevs.cacheit.models.auth.RequestIdentity
import com.martinsterentjevs.cacheit.services.NoteService
import com.martinsterentjevs.cacheit.services.UserService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class NoteServiceTest {
    private val noteRepository = mockk<NoteRepository>()
    private val userService = mockk<UserService>()
    private val noteVersionRepository = mockk<NoteVersionRepository>()
    private val deviceSessionRepository = mockk<DeviceSessionRepository>()

    private val noteService =
        NoteService(
            noteRepository,
            userService,
            noteVersionRepository,
            deviceSessionRepository
        )

    private val ownerId = UUID.randomUUID()
    private val deviceId = UUID.randomUUID()

    private fun testAccount(userId: UUID = ownerId) =
        Account(
            userId = userId,
            accountHolder = "Test User",
            username = "tester-$userId",
            email = "test-$userId@example.com",
            passwordHash = "hash",
            mekEnvelope = "mek-envelope",
            kdfSalt = "test-kdf-salt"
        )

    private fun testIdentity(
        userId: UUID = ownerId,
        deviceId: UUID = this.deviceId
    ) = RequestIdentity(account = testAccount(userId), deviceId = deviceId)

    private fun testDeviceSession(id: UUID = deviceId) =
        DeviceSession(
            deviceId = id,
            account = testAccount(),
            deviceName = "Test Device",
            refreshToken = "refresh-token"
        )

    private fun testNote(
        noteId: UUID = UUID.randomUUID(),
        account: Account = testAccount(),
        encTitle: String = "Title",
        encBody: String? = null,
        encDrawing: String? = null,
        isDeleted: Boolean = false,
        lastModifiedAt: Instant = Instant.now(),
        lockedByDevice: DeviceSession? = null,
        lockedAt: Instant? = null
    ) = Note(
        noteId = noteId,
        account = account,
        encTitle = encTitle,
        encBody = encBody,
        encDrawing = encDrawing,
        isDeleted = isDeleted,
        lastModifiedAt = lastModifiedAt,
        lockedByDevice = lockedByDevice,
        lockedAt = lockedAt
    )

    private fun testNoteDto(
        noteId: UUID,
        encTitle: String = "Title",
        encBody: String? = null,
        encDrawing: String? = null,
        hasHistory: Boolean = false
    ) = NoteDto(
        noteId = noteId,
        userId = ownerId,
        lastModifiedAt = Instant.now(),
        isDeleted = false,
        hasHistory = hasHistory,
        encTitle = encTitle,
        encBody = encBody,
        encDrawing = encDrawing,
        lockedByDeviceId = null,
        lockedAt = null
    )

    /** Stubs the common "load note, resolve identity" path shared by every ownership-checked call. */
    private fun stubOwnedNote(
        note: Note,
        identity: RequestIdentity = testIdentity()
    ) {
        every { noteRepository.findByNoteId(note.noteId) } returns note
        every { userService.getRequestIdentity(any()) } returns identity
    }

    @BeforeEach
    fun setUp() {
        every {
            noteVersionRepository.findAllByNoteNoteIdOrderByCreatedAtDesc(any())
        } returns emptyList()
    }
    // -- getNotes --

    @Test
    fun `getNotes returns all non-deleted notes for the requesting account`() {
        every { userService.getRequestIdentity(any()) } returns testIdentity()
        every {
            noteRepository.findAllByAccountUserIdAndIsDeletedFalseOrderByLastModifiedAtDesc(ownerId)
        } returns listOf(testNote())

        assertEquals(1, noteService.getNotes("bearer").size)
    }

    // -- addNote --

    @Test
    fun `addNote persists the note and its initial version, then returns the created note`() {
        every { userService.getRequestIdentity(any()) } returns testIdentity()
        every { deviceSessionRepository.findByDeviceId(deviceId) } returns testDeviceSession()
        every { noteRepository.save(any()) } answers { firstArg() }
        every { noteVersionRepository.save(any()) } answers { firstArg() }

        val result = noteService.addNote(testNoteDto(UUID.randomUUID(), encTitle = "First note"), "bearer")

        verify(exactly = 1) { noteRepository.save(any()) }
        verify(exactly = 1) { noteVersionRepository.save(any()) }
        assertEquals("First note", result.encTitle)
    }

    // -- updateNote --

    @Test
    fun `updateNote rejects a caller who does not own the note`() {
        val note = testNote(account = testAccount(userId = UUID.randomUUID()))
        stubOwnedNote(note)

        assertThrows(NoteOwnershipValidationException::class.java) {
            noteService.updateNote(testNoteDto(note.noteId, encTitle = "Updated"), "bearer")
        }
    }

    @Test
    fun `updateNote retires the previous current version and creates a new one`() {
        val note = testNote()
        val previousVersion =
            NoteVersion(note = note, encTitle = "Old title", encBody = null, encDrawing = null, isCurrent = true)

        stubOwnedNote(note)
        every { deviceSessionRepository.findByDeviceId(deviceId) } returns testDeviceSession()
        every { noteVersionRepository.findByNoteAndIsCurrentTrue(note) } returns previousVersion
        every { noteVersionRepository.save(any()) } answers { firstArg() }
        every { noteRepository.save(any()) } answers { firstArg() }

        val result =
            noteService.updateNote(
                testNoteDto(note.noteId, encTitle = "New title", encBody = "New body"),
                "bearer"
            )

        assertEquals(false, previousVersion.isCurrent)
        verify(exactly = 2) { noteVersionRepository.save(any()) } // retire old + save new current
        assertEquals("New title", result.encTitle)
    }

    // -- deleteNote --

    @Test
    fun `deleteNote soft-deletes and snapshots a final version`() {
        val note = testNote()
        stubOwnedNote(note)
        every { deviceSessionRepository.findByDeviceId(deviceId) } returns testDeviceSession()
        every { noteVersionRepository.findByNoteAndIsCurrentTrue(note) } returns null
        every { noteVersionRepository.save(any()) } answers { firstArg() }
        every { noteRepository.save(any()) } answers { firstArg() }

        noteService.deleteNote(note.noteId, "bearer")

        assertTrue(note.isDeleted)
        verify(exactly = 1) { noteRepository.save(note) }
    }

    @Test
    fun `deleteNote rejects a caller who does not own the note`() {
        val note = testNote(account = testAccount(userId = UUID.randomUUID()))
        stubOwnedNote(note)

        assertThrows(NoteOwnershipValidationException::class.java) {
            noteService.deleteNote(note.noteId, "bearer")
        }
    }

    // -- getSyncDelta --

    @Test
    fun `getSyncDelta returns notes newer than the client's manifest`() {
        val staleNote = testNote(lastModifiedAt = Instant.now())
        every { userService.getRequestIdentity(any()) } returns testIdentity()
        every { noteRepository.findAllByAccountUserId(ownerId) } returns listOf(staleNote)

        val request =
            SyncRequestDto(
                lastSyncedAt = Instant.now().minusSeconds(3600),
                localNotes = listOf(SyncManifestEntry(staleNote.noteId, Instant.now().minusSeconds(600)))
            )

        val result = noteService.getSyncDelta(request, "bearer")

        assertEquals(1, result.updatedNotes.size)
        assertEquals(0, result.deletedIds.size)
    }

    @Test
    fun `getSyncDelta reports deleted notes the client still has in its manifest`() {
        val deletedNote = testNote(isDeleted = true)
        every { userService.getRequestIdentity(any()) } returns testIdentity()
        every { noteRepository.findAllByAccountUserId(ownerId) } returns listOf(deletedNote)

        val request =
            SyncRequestDto(
                lastSyncedAt = Instant.now().minusSeconds(3600),
                localNotes = listOf(SyncManifestEntry(deletedNote.noteId, Instant.now().minusSeconds(600)))
            )

        val result = noteService.getSyncDelta(request, "bearer")

        assertEquals(0, result.updatedNotes.size)
        assertEquals(listOf(deletedNote.noteId), result.deletedIds)
    }

    // -- acquireDrawingLock / releaseDrawingLock --

    @Test
    fun `acquireDrawingLock succeeds when the note is unlocked`() {
        val note = testNote()
        stubOwnedNote(note)
        every { deviceSessionRepository.findByDeviceId(deviceId) } returns testDeviceSession()
        every { noteRepository.save(any()) } answers { firstArg() }

        assertEquals(deviceId, noteService.acquireDrawingLock(note.noteId, "bearer").lockedByDeviceId)
    }

    @Test
    fun `acquireDrawingLock throws when locked by a different, non-stale device`() {
        val otherDevice = testDeviceSession(id = UUID.randomUUID())
        val note = testNote(lockedByDevice = otherDevice, lockedAt = Instant.now())
        stubOwnedNote(note)
        every { deviceSessionRepository.findByDeviceId(deviceId) } returns testDeviceSession()

        assertThrows(NoteLockedException::class.java) {
            noteService.acquireDrawingLock(note.noteId, "bearer")
        }
    }

    @Test
    fun `acquireDrawingLock allows takeover when the existing lock is stale`() {
        val otherDevice = testDeviceSession(id = UUID.randomUUID())
        val staleLockTime = Instant.now().minusSeconds(NoteService.DRAWING_LOCK_TTL_SECONDS + 60)
        val note = testNote(lockedByDevice = otherDevice, lockedAt = staleLockTime)
        stubOwnedNote(note)
        every { deviceSessionRepository.findByDeviceId(deviceId) } returns testDeviceSession()
        every { noteRepository.save(any()) } answers { firstArg() }

        assertEquals(deviceId, noteService.acquireDrawingLock(note.noteId, "bearer").lockedByDeviceId)
    }

    @Test
    fun `acquireDrawingLock throws when the token resolves to no active device session`() {
        val note = testNote()
        stubOwnedNote(note)
        every { deviceSessionRepository.findByDeviceId(deviceId) } returns null

        assertThrows(InvalidSessionException::class.java) {
            noteService.acquireDrawingLock(note.noteId, "bearer")
        }
    }

    @Test
    fun `acquireDrawingLock throws when the note does not exist`() {
        val missingNoteId = UUID.randomUUID()
        every { noteRepository.findByNoteId(missingNoteId) } throws NoteNotFoundException()

        assertThrows(NoteNotFoundException::class.java) {
            noteService.acquireDrawingLock(missingNoteId, "bearer")
        }
    }

    @Test
    fun `releaseDrawingLock clears the lock unconditionally`() {
        val lockingDevice = testDeviceSession()
        val note = testNote(lockedByDevice = lockingDevice, lockedAt = Instant.now())
        stubOwnedNote(note)
        every { noteRepository.save(any()) } answers { firstArg() }

        noteService.releaseDrawingLock(note.noteId, "bearer")

        assertNull(note.lockedByDevice)
        assertNull(note.lockedAt)
    }

    // -- getVersionHistory / getVersion / restoreVersion --

    @Test
    fun `getVersionHistory returns lightweight metadata for every version`() {
        val note = testNote()
        stubOwnedNote(note)
        every { noteVersionRepository.findAllByNoteNoteIdOrderByCreatedAtDesc(note.noteId) } returns
            listOf(
                NoteVersion(note = note, encTitle = "V1", encBody = null, encDrawing = null, isCurrent = false),
                NoteVersion(note = note, encTitle = "V2", encBody = null, encDrawing = null, isCurrent = true)
            )

        assertEquals(2, noteService.getVersionHistory(note.noteId, "bearer").size)
    }

    @Test
    fun `getVersion rejects a versionId that does not belong to the requested note`() {
        val note = testNote()
        val otherNote = testNote()
        val foreignVersion = NoteVersion(note = otherNote, encTitle = "Other", encBody = null, encDrawing = null)

        stubOwnedNote(note)
        every { noteVersionRepository.findById(any()) } returns Optional.of(foreignVersion)

        assertThrows(NoteVersionNotFoundException::class.java) {
            noteService.getVersion(note.noteId, UUID.randomUUID(), "bearer")
        }
    }

    @Test
    fun `restoreVersion applies the target version's fields, including drawing, as a new current version`() {
        val note = testNote(encTitle = "Current", encDrawing = "current-drawing")
        val target =
            NoteVersion(
                note = note,
                encTitle = "Restored title",
                encBody = "Restored body",
                encDrawing = "restored-drawing",
                isCurrent = false
            )

        stubOwnedNote(note)
        every { deviceSessionRepository.findByDeviceId(deviceId) } returns testDeviceSession()
        every { noteVersionRepository.findById(any()) } returns Optional.of(target)
        every { noteVersionRepository.findByNoteAndIsCurrentTrue(note) } returns null
        every { noteVersionRepository.save(any()) } answers { firstArg() }
        every { noteRepository.save(any()) } answers { firstArg() }

        val result = noteService.restoreVersion(note.noteId, UUID.randomUUID(), "bearer")

        assertEquals("Restored title", result.encTitle)
        assertEquals("restored-drawing", result.encDrawing)
    }
}