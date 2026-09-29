package com.martinsterentjevs.cacheit.exceptions

import com.martinsterentjevs.cacheit.dtos.error.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ControllerAdvice {
    private val log = LoggerFactory.getLogger(ControllerAdvice::class.java)

    @ExceptionHandler(InvalidCredentialsException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleInvalidCredentials(ex: InvalidCredentialsException): ErrorResponse =
        ErrorResponse("INVALID_CREDENTIALS", ex.message ?: "Invalid credentials provided.")

    @ExceptionHandler(InvalidTokenException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleInvalidToken(ex: InvalidTokenException): ErrorResponse =
        ErrorResponse("INVALID_TOKEN", ex.message ?: "The provided token is invalid or expired.")

    @ExceptionHandler(InvalidSessionException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidSession(ex: InvalidSessionException): ErrorResponse =
        ErrorResponse("INVALID_SESSION", ex.message ?: "The session could not be validated.")

    @ExceptionHandler(InvalidRegistrationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleInvalidRegistration(ex: InvalidRegistrationException): ErrorResponse =
        ErrorResponse("INVALID_REGISTRATION_REQUEST", ex.message ?: "The registration request is invalid.")

    @ExceptionHandler(AccountAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleAccountAlreadyExists(ex: AccountAlreadyExistsException): ErrorResponse =
        ErrorResponse("ACCOUNT_ALREADY_EXISTS", ex.message ?: "Username or email is already in use.")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ErrorResponse {
        val errors = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ErrorResponse("VALIDATION_ERROR", "Request validation failed: $errors")
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMalformedJson(ex: HttpMessageNotReadableException): ErrorResponse =
        ErrorResponse("MALFORMED_REQUEST_BODY", "The request body is not valid JSON.")

    @ExceptionHandler(MissingRequestHeaderException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleMissingAuthHeader(ex: MissingRequestHeaderException): ErrorResponse =
        ErrorResponse("UNAUTHORIZED", "Authentication is required to access this resource.")

    @ExceptionHandler(NoteOwnershipValidationException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun handleOwnershipMismatch(ex: NoteOwnershipValidationException): ErrorResponse =
        ErrorResponse("FORBIDDEN", ex.message ?: "Note requested does not belong to the requestor")

    @ExceptionHandler(NoteNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNoteNotFound(ex: NoteNotFoundException): ErrorResponse =
        ErrorResponse("NOTE_NOT_FOUND", ex.message ?: "Note requested does not exist.")

    @ExceptionHandler(NoteLockedException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleNoteLocked(ex: NoteLockedException): ErrorResponse =
        ErrorResponse("NOTE_LOCKED", ex.message ?: "This note is currently locked by another device.")

    // Was missing — new exception added alongside version history/restore endpoints.
    @ExceptionHandler(NoteVersionNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNoteVersionNotFound(ex: NoteVersionNotFoundException): ErrorResponse =
        ErrorResponse("NOTE_VERSION_NOT_FOUND", ex.message ?: "Note version requested does not exist.")

    @ExceptionHandler(DeviceNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleDeviceNotFound(ex: DeviceNotFoundException): ErrorResponse =
        ErrorResponse("DEVICE_NOT_FOUND", ex.message ?: "Couldn't find device by ID ")

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleUnexpected(ex: Exception): ErrorResponse {
        log.error("Unhandled exception reached ControllerAdvice", ex)
        return ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred.")
    }
}