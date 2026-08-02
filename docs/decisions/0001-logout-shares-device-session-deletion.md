# 0001 — Logout shares device-session deletion with self-revocation

**Status:** Accepted
**Date:** 10/07/2026

## Context
`device_sessions.refresh_token` is non-nullable, so "invalidate current device
refresh token" (the /auth/logout endpoint's stated behavior) cannot mean
nulling the token in place — the only schema-legal operation is deleting
the row entirely, which is the same operation `DELETE /account/devices/{deviceId}`
already performs.

## Decision
SessionService's logout path converges on the same DeviceSessionRepository
delete operation as self-service device revocation. The two differ only in
how the target deviceId is resolved: an explicit path param for device
management (any of the account's devices), versus the deviceId claim on the
current JWT for logout (always the calling device).

## Consequences
No separate "logout" persistence path to maintain — one deletion operation,
two callers. note_versions.device_id going nullable on deletion was already
anticipated in database.md, so no schema change follows from this.