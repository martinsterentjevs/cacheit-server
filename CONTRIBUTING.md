# Contributing to CacheIt Server

Thanks for your interest in CacheIt. This document covers the conventions
used in this repository — branching, commits, and the PR process.

## Before you start

- Setup instructions: see [README.md](README.md)
- Architecture overview: see [docs/architecture.md](docs/architecture.md)
- Full project documentation: [cacheit-spec](https://github.com/martinsterentjevs/cacheit-spec)

## Branching

`main` and `dev` are the two permanent branches:

- **`main`** — production-ready code only. Protected, requires a PR and review to merge.
- **`dev`** — integration branch. All work merges here first. Protected, requires CI to pass.

All work happens on short-lived branches created from `dev`:

```
feature/<issue-id>-<slug>     e.g. feature/1-add-note-entity
bugfix/<issue-id>-<slug>      e.g. bugfix/12-fix-token-expiry
chore/<slug>                  e.g. chore/bump-spring-boot
docs/<slug>                   e.g. docs/update-setup-guide
```

`hotfix/` branches are the exception — they branch from `main` and merge
to both `main` and `dev`, used only for urgent production fixes.

Releases are cut on `release/v<MAJOR>_<MINOR>` branches from `dev`,
tagged on `main` once merged (e.g. `release/v1_2` → tag `v1.2.0`).

## Commit messages

Commits follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short summary>

[optional body]

[optional footer]
```

**Types:**

| Type | Use for |
|---|---|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Restructure with no behaviour change |
| `docs` | Documentation only |
| `chore` | Build, deps, config |
| `test` | Adding or updating tests |
| `ci` | CI/CD pipeline changes |
| `perf` | Performance improvement |
| `style` | Formatting only |

**Scope** — use the affected layer in lowercase-kebab:

```
feat(auth): add refresh token rotation
fix(notes): correct soft delete flag on sync response
chore(deps): bump spring-boot to 3.5.16
test(sync): add delta sync edge case coverage
```

Common scopes for this repo: `auth`, `notes`, `sync`, `ws`, `crypto`,
`account`, `version-history`, `config`.

**Rules:**
- Imperative mood ("add", not "added")
- 72 characters max on the summary line
- One logical change per commit
- Squash fixup commits before opening a PR
- Breaking changes get a `BREAKING CHANGE:` footer, never buried in the body

## Pull requests

Fill out the PR template in full. Every PR must include:

- **Summary** — what changed and why, one paragraph
- **Type of change** — feat / fix / refactor / chore / breaking
- **Testing** — what was tested, how
- **Related issues** — `Closes #N` if applicable

**Review requirements:**

| Target branch | Review | CI |
|---|---|---|
| `main` | 1 approval required | Full suite must pass |
| `dev` | Self-merge allowed, review welcomed | Build + tests must pass |
| `release/*` | 1 approval required | Full suite must pass |

## Code style

Enforced via `.editorconfig` — 4-space indent, LF line endings.
Run `./gradlew ktlintCheck` before opening a PR.

## Testing

```bash
./gradlew test
```

Add or update tests for any new logic. PRs touching `service/` or
`security/` must include unit tests — these layers contain the auth
and encryption boundary logic where a subtle bug has outsized consequences.
No PR that weakens test coverage in these areas will be merged without
explicit justification in the PR description.

## Changelog

Add an entry under `[Unreleased]` in [`CHANGELOG.md`](CHANGELOG.md)
for any user-facing change, as part of the same PR — not as a follow-up.

## Documentation

If your change affects architecture, the API contract, the sync protocol,
or setup steps, update the relevant file in `docs/` in the same PR.

## A note on the security model

CacheIt uses a zero-trust encryption design — the server never holds a
decryption key or plaintext note content. The MEK/DMUK key envelope is
returned to the client and decrypted locally. If your change touches auth,
token handling, or anything adjacent to the encryption boundary, flag it
explicitly in the PR description even if it looks minor. These are the
areas where a subtle bug has outsized consequences.