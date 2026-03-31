# Noridoc: Auth & Routes

Path: @/src/main/kotlin/io/github/nomisrev/auth

### Overview
- Provides a centralized DSL for route authorization (`publicGet`, `authGet`, `authPost`, etc.).
- Enforces the Admin vs Guest access rules at the edge layer.

### How it fits into the larger codebase
- Acts as a middleware layer built on top of Ktor's `RoutingContext` and the custom JWT middleware (`jwtAuth`, `optionalJwtAuth`).
- Guarantees that the web adapter enforces access rules before requests ever reach the service/domain layer.
- Relies on `JwtService` to validate and extract user contexts.

### Core Implementation
- Extends Ktor's `Route` with inline reified functions (`authGet`, `publicGet`, `authPost`, `authPut`, `authDelete`).
- Endpoints requiring authentication extract a non-null `JwtContext` and return `401 Unauthorized` if invalid.
- Endpoints allowing public read extract a nullable `JwtContext?` and return `200 OK` regardless of auth presence.

### Things to Know
- Writing data (POST, PUT, DELETE) is strictly forbidden for guests (requires `auth*` guards).
- The `get` endpoints that read private data (like feed or user profile) must use `authGet`.
- Missing or invalid JWTs on protected routes return HTTP `401 Unauthorized` (instead of `422 Unprocessable Entity`), aligning with REST standards.

Created and maintained by Nori.
