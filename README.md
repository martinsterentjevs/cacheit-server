# CacheIt Server

Server for a zero-trust, end-to-end encrypted note-taking application.

## Overview

This repo contains the Spring Boot backend for the CacheIt project. The server
handles user authentication, note storage, and real-time sync signals via
WebSocket. All user-readable note content arrives and is stored as ciphertext —
the server never holds a decryption key or plaintext note data.

For a full project overview: [CacheIt](https://martinsterentjevs.github.io/cacheit-spec/)

## Stack

- Kotlin
- Spring Boot 3 (Gradle Kotlin DSL)
- Spring Data JPA
- Spring Security
- PostgreSQL

## Project Structure

- `src/` — application source
- `tests/` — test suite
- `docs/` — architecture decisions and runbooks

## Quick Start

```bash
git clone https://github.com/martinsterentjevs/cacheit-server.git
cd cacheit-server
cp .env.example .env
# configure .env with your PostgreSQL credentials
./gradlew bootRun
```

## Links

- [Contributing](CONTRIBUTING.md)
- [Full project documentation](https://github.com/martinsterentjevs/cacheit-spec)

## License

MIT — see [LICENSE](LICENSE)