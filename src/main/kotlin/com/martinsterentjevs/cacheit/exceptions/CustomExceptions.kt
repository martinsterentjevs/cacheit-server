package com.martinsterentjevs.cacheit.exceptions

// Auth/identity — codes stay deliberately generic, never distinguish *why* credentials failed
class InvalidCredentialsException(
    message: String = "Invalid credentials provided."
) : RuntimeException(message)

// JWT structurally invalid: malformed, unsigned, expired signature — access token layer
class InvalidTokenException(
    message: String = "The provided token is invalid or expired."
) : RuntimeException(message)

// Refresh/logout layer: unknown refresh token, replayed token, device/user mismatch, unknown device id.
// Deliberately one type for all of these — same reasoning as InvalidCredentialsException: don't let
// the response shape tell a caller *which* of "doesn't exist" vs "doesn't match" was true.
class InvalidSessionException(
    message: String = "The session could not be validated."
) : RuntimeException(message)

// Registration — request itself is malformed (missing identifier, bad MEK envelope shape, etc.)
class InvalidRegistrationException(
    message: String
) : RuntimeException(message)

// Registration — identifier collision. Existence disclosure is unavoidable and expected here,
// unlike login: the caller just supplied the identifier themselves.
class AccountAlreadyExistsException(
    message: String = "Username or email is already in use."
) : RuntimeException(message)

// Notes - Ownership mismatch. Fires when a NoteId is requested by a different user as per JWT claim.
class NoteOwnershipValidationException(
    message: String = "Note does not belong to user requesting it."
) : RuntimeException(message)

// Notes - not found.
class NoteNotFoundException(
    message: String = "Note requested does not exist."
) : RuntimeException(message)

// Notes - Drawing lock.
class NoteLockedException(
    message: String = "This note is currently locked by another device."
) : RuntimeException(message)

// Notes - Non-existent version. Thrown on calling a NoteVersion id that wasnt found
class NoteVersionNotFoundException(
    message: String = "This note version does not exist."
) : RuntimeException(message)

// Secrets exceptions
// Secret - Not found
class SecretNotFoundException(
    message: String = "Secret not found."
) : RuntimeException(message + "Check your environment variables")

// Secret - Not valid
class SecretValidationException(
    message: String = "Secret validation failed."
) : RuntimeException(message)

// Device - not found
class DeviceNotFoundException(
    message: String = "Failed to find device by id requested"
) : RuntimeException(message)