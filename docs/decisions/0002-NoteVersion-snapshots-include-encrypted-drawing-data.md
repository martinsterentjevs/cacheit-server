# 0002 — NoteVersion snapshots include encrypted drawing data

**Status:** Accepted
**Date:** 2026-08-02

## Context

`Note.encDrawing` (nullable `TEXT`, client-side encrypted) was added to scope as an Android MVP
feature. During Issue 2's entity design, before drawing was confirmed in-scope for MVP,
`database.md` noted that `NoteVersion` would exclude `encDrawing` — versioning would cover
`title`/`content` only.

The system's versioning model is whole-note snapshotting at save-point granularity — a
`NoteVersion` row is a full point-in-time copy of the note, created per save, not per keystroke or
per stroke. This is the same cadence last-write-wins conflict resolution already assumes elsewhere.
Under that model, excluding one field from the snapshot while including the others isn't a
different versioning *strategy* for that field — it's an arbitrary carve-out, and the only
justification available for it is raw storage size, not any difference in how or when the data
changes.

Drawing payloads (vector/raster path data, base64-encoded, then encrypted) are meaningfully larger
than typical `title`/`content` text, so the size difference is real. But size alone doesn't
distinguish drawing from text under a bounded, fixed-retention (30-day) snapshot model — both grow
at the same rate (snapshot count × field size), just with a larger constant for drawing.

## Decision

`NoteVersion` will include `encDrawing` (nullable `TEXT`, `@Column(columnDefinition = "TEXT")`,
matching `Note.encDrawing`), captured at the same snapshot points as `title` and `content`.
Restoring a version restores the full note state, including drawing — "restore" is not a partial
operation. If no drawing existed at that snapshot, the field is simply `null`, same as it would be
on `Note` itself.

This supersedes the exclusion noted in `database.md` during Issue 2 entity design. That note was
never itself a formal ADR — this is the first formal decision on the question.

## Consequences

**Easier:**
- Restore semantics are coherent and complete — a version is genuinely a full point-in-time note
  state, matching the snapshot/last-write-wins model already in use rather than introducing a
  silent exception to it.
- No separate restore code path or partial-restore UX needed on either client (Avalonia desktop or
  Jetpack Compose Android) — one restore operation, one result.

**Harder / accepted cost:**
- Per-version storage is higher for any note that uses drawing, proportional to drawing payload
  size. Accepted because it's *bounded* growth (fixed retention window, fixed snapshot cadence),
  not unbounded — the same shape of cost the system already accepts for text, just a larger
  constant.
- No deduplication exists for an unchanged drawing across consecutive snapshots — each version
  stores an independent encrypted copy, same as `title`/`content` already do. Client-side
  encryption with per-encryption nonces/IVs means ciphertext isn't stable even when the underlying
  plaintext is unchanged, so straightforward content-hash dedup isn't available without an explicit
  client-side "unchanged" signal at the API layer.
- Real storage growth rate under drawing-heavy usage (expected to concentrate on Android, the
  primary drawing-capable client) is unmeasured. Flagged as a v1.1+ monitoring concern, not a
  blocker for Issue 4.

## Alternatives considered

- **Keep the original exclusion (drawing never versioned).** Rejected: creates an unjustified
  asymmetry against `title`/`content` under an otherwise-uniform snapshot model. The only
  supporting argument was storage cost, which scales the same way as already-accepted text
  versioning under the bounded retention window — just with a larger per-row constant, not a
  different growth shape.
- **Single-row-per-note drawing storage, versioned later post-MVP.** Rejected for now: adds a
  second schema shape and a second restore code path to save storage that hasn't yet been shown to
  be a real problem at current scale. Revisit if real usage data shows drawing-driven storage
  growth is actually significant.
- **Content-hash-based deduplication of unchanged drawings across snapshots.** Rejected for MVP:
  requires the client to explicitly signal "drawing unchanged, don't resend" at the API level,
  since ciphertext alone can't be compared for equality across encryptions. Real design and
  protocol work for a cost that isn't yet observed — not worth the time against the MVP deadline.

## Related
- Supersedes the `NoteVersion` exclusion note in `database.md` (Issue 2) — update that document
  directly to reflect current schema; this ADR is the record of *why* it changed.
- No direct dependency on ADR 0002 (device identity origin) or ADR 0003 (MEK origin and lifecycle).