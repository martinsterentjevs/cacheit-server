# Exceptions Reference Document
Created: 02/08/2026 Last updated: 02/08/2026

## Overview
This document contains all exceptions that are thrown by the library, and the framework-level
exceptions the application explicitly maps, along with their HTTP status and stable error code.
This is the single source of truth for that mapping — `ControllerAdvice` implements this table.

## Owns
- Exception definitions and HTTP error codes
- Exception domains

## Does not own
- Error handling mechanics (see `ControllerAdvice` for the actual `@ExceptionHandler` implementations)

## Related documentation
- `auth-reference.md` — each endpoint's "Expected failures" section should match the rows below

## Exception domains
Exception domains group exceptions by the layer of the system where the error originates. The
domains are:
- **Auth domain** — invalid session/credential/token markers during authentication and session
  lifecycle (login, register, refresh, logout).
- **NoteSync domain** — reserved, not yet populated (Issue 4+).
- **Note domain** — reserved, not yet populated (Issue 4+).
- **RTC domain** — reserved, not yet populated (future issue).

## Exception definitions
Any unmapped exception falls through to the generic handler and is returned as `500
INTERNAL_ERROR`, logged server-side with full detail, and never leaks internals to the client.

### Custom exceptions

| Exception | Domain | HTTP Status | Error Code | Thrown when |
|---|---|---|---|---|
| `InvalidCredentialsException` | Auth | 401 | `INVALID_CREDENTIALS` | Login: identifier doesn't match any account, or password hash doesn't verify. Deliberately the same exception for both cases — never discloses which one failed. |
| `InvalidTokenException` | Auth | 401 | `INVALID_TOKEN` | An access token (JWT) is structurally invalid — malformed, unsigned, bad signature, or expired. |
| `InvalidSessionException` | Auth | 400 | `INVALID_SESSION` | Refresh/logout: refresh token doesn't match any session, refresh token's device doesn't match the request, session's device isn't found, or the session belongs to a different account than the token claims. One type for all of these, for the same reason as `InvalidCredentialsException` — the response never discloses *which* check failed. |
| `InvalidRegistrationException` | Auth | 400 | `INVALID_REGISTRATION_REQUEST` | Registration request is semantically invalid in a way Bean Validation can't express — currently: neither `username` nor `email` provided. |
| `AccountAlreadyExistsException` | Auth | 400 | `ACCOUNT_ALREADY_EXISTS` | Registration: the provided `username` or `email` is already in use. Existence disclosure here is expected and unavoidable — the caller supplied the identifier themselves, unlike login. |

### Framework-originated mappings

These aren't custom exceptions, but `ControllerAdvice` explicitly maps them, so they're part of the
same contract and belong in this table.

| Exception | HTTP Status | Error Code | Thrown when |
|---|---|---|---|
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` | A `@Valid`-annotated request body fails a Bean Validation constraint (`@field:NotBlank`, `@field:Pattern`, etc.). Message includes the failing field(s). |
| `HttpMessageNotReadableException` | 400 | `MALFORMED_REQUEST_BODY` | The request body isn't valid JSON, or a field can't be deserialized into its target type (e.g. a non-UUID string where a `UUID` is expected) — this fires *before* Bean Validation runs, since Jackson fails during deserialization. |
| `MissingRequestHeaderException` | 401 | `UNAUTHORIZED` | A required `@RequestHeader` (currently: `Authorization` on `/auth/logout`) is absent from the request entirely. |
| `Exception` (catch-all) | 500 | `INTERNAL_ERROR` | Anything not explicitly mapped above. Logged with full stack trace server-side; the client only ever sees the generic message. |

## Known gaps
- No exception currently distinguishes a client-side bug (malformed request) from a genuine server
  fault outside the auth domain — acceptable for Issue 3's scope (auth only), revisit once note/sync
  domains are populated.