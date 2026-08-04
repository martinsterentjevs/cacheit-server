package com.martinsterentjevs.cacheit.controllers

import com.martinsterentjevs.cacheit.dtos.note.NoteDto
import com.martinsterentjevs.cacheit.dtos.note.SyncRequestDto
import com.martinsterentjevs.cacheit.dtos.note.SyncResponseDto
import com.martinsterentjevs.cacheit.dtos.note.version.NoteVersionDto
import com.martinsterentjevs.cacheit.dtos.note.version.NoteVersionMeta
import com.martinsterentjevs.cacheit.services.NoteService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/notes")
class NoteController(
    private val noteService: NoteService
) {
    @GetMapping
    fun getNotes(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ): List<NoteDto> = noteService.getNotes(authorization)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addNote(
        @RequestBody note: NoteDto,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ): NoteDto = noteService.addNote(note, authorization)

    // noteId path variable is present for correct REST routing; NoteService currently resolves
    // the target note from the body's own noteId field, not this parameter.
    @PutMapping("/{noteId}")
    fun updateNote(
        @PathVariable noteId: UUID,
        @RequestBody note: NoteDto,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ): NoteDto = noteService.updateNote(note, authorization)

    @DeleteMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteNote(
        @PathVariable noteId: UUID,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ) = noteService.deleteNote(noteId, authorization)

    // Deviates from sync-protocol.md's literal GET + query-param description — see
    // note-reference.md's "Spec follow-ups" for why.
    @PostMapping("/sync")
    fun getSyncDelta(
        @RequestBody request: SyncRequestDto,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ): SyncResponseDto = noteService.getSyncDelta(request, authorization)

    @PostMapping("/{noteId}/lock")
    fun acquireDrawingLock(
        @PathVariable noteId: UUID,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ): NoteDto = noteService.acquireDrawingLock(noteId, authorization)

    @DeleteMapping("/{noteId}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun releaseDrawingLock(
        @PathVariable noteId: UUID,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ) = noteService.releaseDrawingLock(noteId, authorization)

    @GetMapping("/{noteId}/history")
    fun getVersionHistory(
        @PathVariable noteId: UUID,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ): List<NoteVersionMeta> = noteService.getVersionHistory(noteId, authorization)

    @GetMapping("/{noteId}/history/{versionId}")
    fun getVersion(
        @PathVariable noteId: UUID,
        @PathVariable versionId: UUID,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ): NoteVersionDto = noteService.getVersion(noteId, versionId, authorization)

    @PostMapping("/{noteId}/restore/{versionId}")
    fun restoreVersion(
        @PathVariable noteId: UUID,
        @PathVariable versionId: UUID,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String
    ): NoteDto = noteService.restoreVersion(noteId, versionId, authorization)
}