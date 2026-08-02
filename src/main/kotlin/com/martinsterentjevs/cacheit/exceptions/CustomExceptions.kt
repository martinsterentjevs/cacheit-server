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