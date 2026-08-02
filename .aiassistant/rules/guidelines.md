---
apply: always
---

# CacheIt — Guidelines for Local AI Assistant (JetBrains AI Assistant, Gemma model)

Purpose of this file: give a small local model an unambiguous rulebook. Gemma is fast but
will guess, drift, or "improve" things you didn't ask for if given loose instructions. This
file exists so you can point it at hard constraints instead of re-explaining them every time.

Paste this file into the assistant's context (or keep it open in the editor) for any
CacheIt session. When prompting, reference sections by number, e.g. "follow §3 and §5."

---

## 1. Project snapshot (read first)

CacheIt is a cross-platform, zero-trust end-to-end encrypted note-taking app. Solo
portfolio project. **Hard deadline: August 2026 MVP.** Every decision below is already
made — the model's job is implementation, not architecture debate.

Repos (per-repo, not monorepo):
- `cacheit-server` — Spring Boot 3 / Kotlin / Gradle Kotlin DSL / PostgreSQL / GraalVM native
- `cacheit-android` — Kotlin / Jetpack Compose
- `cacheit-desktop` — Avalonia / .NET 10
- `cacheit-spec` — docs, ADRs, Eleventy landing page

Package namespace: `com.martinsterentjevs.cacheit`. Repos are public, MIT-licensed.

---

## 2. Rule zero for the model

**Never propose changing anything in §3. Never invent new architecture.**
If a task seems to require deviating from §3, the model must stop and say so explicitly
instead of quietly working around it. Small local models tend to "helpfully" reinterpret
constraints — treat that as a bug, not a feature.

---

## 3. Locked architecture decisions (do not question, do not change)

- Zero-trust: server **never** holds plaintext or decryption keys. Per-field AES-256-GCM
  encryption happens client-side only.
- MEK (AES-256-GCM) generated client-side at registration. Wrapped by MUK, which is
  derived via Argon2id from password + client-generated KDF salt. Stored server-side as
  an opaque `encMekEnvelope`, created atomically with registration — never a separate step.
- `deviceId` is a client-generated UUID, created at first app launch, persisted locally,
  sent on every login/register request alongside `deviceName`.
- New-device login **fetches and locally unwraps the existing MEK envelope** — it never
  generates a new MEK.
- Delta sync via typed WebSocket nudge signals that trigger REST pulls — not full payload
  push over the socket.
- Conflict resolution: last-write-wins. 30-day version history retained.
- Refresh tokens: opaque, DB-stored, one per device. `ACCOUNT_TERMINATED` signal fires on
  account deletion.
- Soft delete via nullable `deletedAt`. UUID primary keys. `java.time.Instant` maps to
  PostgreSQL `TIMESTAMPTZ`.
- `enc_drawing` is a nullable `TEXT` column on notes, present in schema now, feature
  deferred to v1.2. Do not build drawing logic yet.
- Voice notes are post-MVP (v1.1), separate table, need `BlobStorageService` abstraction
  over an S3-compatible backend (Garage or SeaweedFS — **not MinIO, archived April 2026**).
  Do not build this now.
- Multi/single-user mode is set at first boot and is immutable afterward.
- No web client, ever — a web client would require server-side decryption, which breaks
  the zero-trust model. Don't suggest one, even for admin/debug tooling.

---

## 4. Tech stack per repo

| Repo | Stack |
|---|---|
| `cacheit-server` | Spring Boot 3, Kotlin, Gradle Kotlin DSL, PostgreSQL, Spring Data JPA, Spring Security, GraalVM native |
| `cacheit-android` | Kotlin, Jetpack Compose |
| `cacheit-desktop` | Avalonia, .NET 10 |
| `cacheit-spec` | Eleventy, Bootstrap dark theme, GitHub Pages via Actions |

Background: the developer's strong languages are **C# and Java**. When the model
explains a Kotlin or Spring-specific behavior, it should frame it as *"this is different
from C#/Java because…"* rather than generic documentation — that framing is what actually
gets retained under time pressure.

---

## 5. Known gotchas — treat these as fixed rules, not suggestions

- `@JoinColumn` names must be **explicit snake_case**. Hibernate's naming strategy does
  not apply once a name is explicitly set.
- Any encrypted field needs `@Column(columnDefinition = "TEXT")`, or it silently
  truncates to VARCHAR(255).
- **Never use `data class` for a JPA entity.** Use a plain `class` with manual ID-based
  `equals`/`hashCode`. `data class` breaks on Hibernate proxies.
- Kotlin constructor properties need the `@field:` prefix for Bean Validation annotations
  to actually apply (e.g. `@field:NotBlank`), or Spring silently ignores them.
- `Account.username` is nullable by design (register-by-email flow is valid).
- `NoteVersion` excludes `encDrawing` per the schema doc — don't add it back.
- Auth/identity error codes stay deliberately generic (e.g. `INVALID_CREDENTIALS`) to
  avoid leaking account existence. Domain-specific codes (notes, sync) can be verbose.
  This is intentional inconsistency — don't "fix" it.

---

## 6. Current focus — update this section as work progresses

**Issue 3: authentication layer** on `cacheit-server` —
`UserService`, `SessionService`, `AuthController`, `TokenService`.

- `UserService` is the orchestrator for all four auth flows (register, login, new-device
  login, logout).
- `TokenService` = token generation/validation only.
- `SessionService` = session lifecycle only.
- Logout **deletes** the `DeviceSession` row. There's no null-out path because
  `refresh_token` is non-nullable — logout and device self-revocation are the same
  operation. Don't add a "revoked" flag instead of deleting; that was already decided
  against.
- Issue 2 (JPA entities) is done. Don't re-litigate entity design — just consume it.

*(Replace this section's contents each time you move to a new issue. Keep it to what
the model needs for the current task, not the whole backlog.)*

---

## 7. Git / commit conventions (self-contained, no external spec)

Branch from `dev`:
```
feature/<issue-id>-<slug>
bugfix/<issue-id>-<slug>
chore/<slug>
docs/<slug>
```
Chore/hygiene commits can go directly to `dev`, no issue or branch required.

Commit format:
```
<type>(<scope>): <short summary>
```
Types: `feat`, `fix`, `refactor`, `docs`, `chore`, `test`, `ci`, `perf`, `style`.
Imperative mood, ≤72 chars, no trailing period, lowercase after the colon.

Portfolio-readable commit sequence for a feature: `feat: scaffold` → `test: unit` →
`feat: implement` → `feat: integrate`.

PR to `dev`, `Closes #N` in the message. `cacheit-spec` doc updates land as in-progress
commits during the same feature work — the server PR's docs checkbox is the
cross-repo acknowledgment, not a separate itemized task.

ADRs: only written when a decision **deviates from or extends** the spec. Compliance
with an existing decision is not an ADR trigger. Don't ask the model to write an ADR for
every change — only flag it when a genuinely new or reversed decision is made.

---

## 8. How the model should behave, given it's a fast/small local model

- **Default to short answers and working code over exploration.** Don't restate the
  problem back, don't offer three architectural alternatives for something already
  decided in §3.
- **If a request conflicts with §3, say so first, in one sentence, before doing anything
  else.** Don't silently comply and don't silently refuse — name the conflict.
- **Don't introduce new dependencies, libraries, or patterns** without being told to.
  If something seems to need one, say what and why, then stop and ask.
- **Don't guess at unstated requirements.** If a method signature, field name, or flow
  isn't specified, ask one direct question rather than producing plausible-looking code
  that has to be reverse-checked later — that costs more time than it saves right now.
- **Match existing code style in the file being edited**, not a generic Kotlin/Spring
  style guide. Consistency within `cacheit-server` matters more than idiomatic purity.
- When explaining *why* something works a certain way in Kotlin/Spring, keep the
  explanation anchored to the C#/Java gap (§4) — that's the fastest path to retention,
  not a full conceptual writeup.
- Prefer flagging risk over fixing it silently, specifically around `crypto/` and
  `sync/` — a subtle bug there is expensive. Say "this touches key handling, flagging
  it" rather than quietly refactoring around it.

---

## 9. Definition of done for a task (use as a checklist prompt)

Before considering an auth/CRUD/sync task complete, confirm:
- [ ] Matches the locked decisions in §3 — no silent deviation
- [ ] Follows the gotchas in §5 (explicit `@JoinColumn`, `TEXT` columns, no `data class`
  entities, `@field:` on validated Kotlin properties)
- [ ] Commit message follows §7 format, correct type and scope
- [ ] If it touches `crypto/` or `sync/`, flagged explicitly in the PR description
- [ ] `cacheit-spec` doc updated in the same PR if architecture/API behavior changed
- [ ] No new library, pattern, or endpoint added beyond what was asked

---

## 10. Explicit "don't" list

- Don't build voice notes, drawing, or web client features — all deferred, see §3.
- Don't add MinIO anywhere — it's archived; Garage/SeaweedFS only, and only when the
  voice-notes phase actually starts.
- Don't make auth error codes verbose or specific — genericity is intentional (§5).
- Don't switch `NoteVersion`/entity design back to `data class`.
- Don't restate the whole architecture back before doing a small task — costs tokens
  and time for no benefit with a fast local model.

---

*Update §6 as issues progress. Everything else in this file should stay stable for the
life of the MVP push — that stability is what makes a small model reliable to point at.*