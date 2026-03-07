# ADR-0002: Standalone Library Extraction from the Monorepo

**Date:** 2025-02
**Status:** Accepted

## Context

The Camunda platform has two distinct consumers that need authentication and authorization:

1. **Orchestration Cluster (OC)** — The existing Camunda 8 platform. Uses Zeebe as the source of
   truth for identity data, with Elasticsearch or RDBMS as secondary storage populated by Zeebe
   exporters. Authentication is handled by the `authentication/` monolith.

2. **Camunda Hub** — A new product that needs the same authentication capabilities (OIDC, Basic
   Auth, session management, authorization) but does not use Zeebe. It needs a self-contained
   library that owns its own persistence.

The `authentication/` module cannot be extracted because it depends on `service/`, `search/`,
`security-core/`, and other monorepo-internal modules. A clean separation is needed.

## Decision

Extract authentication into a standalone library (`auth/`) that:

1. **Lives in the monorepo** during the transition period but has **zero compile-time dependencies
   on monorepo-internal modules** (except `auth-domain` which other modules may depend on).

2. **Has its own Maven wrapper and BOM** so it can be built independently of the monorepo.

3. **Defines SPIs in `auth-domain`** that consumers implement to integrate with their specific
   infrastructure. The library never imports consumer code.

4. **Provides default implementations** in `auth-spring`, `auth-persist-elasticsearch`, and
   `auth-persist-rdbms` that cover the common cases.

5. **Uses Spring Boot auto-configuration** in `auth-spring-boot-starter` so consumers get
   sensible defaults by adding the starter to their classpath.

### Migration strategy

The migration from `authentication/` + `security/` to `auth/` is phased:

- **Phase 1:** Port configuration classes to `auth-domain` (eliminate duplication with
  `security-core`).
- **Phase 2:** Port membership resolution to `auth/` (extend `MembershipResolver` SPI).
- **Phase 3:** Port user service to `auth/` (`CamundaUserProvider` SPI).
- **Phase 4:** Port session management to `auth/` (`SessionPersistencePort` SPI).
- **Phase 5:** Replace `WebSecurityConfig` monolith with auth library auto-configuration.
- **Phase 6:** Delete `authentication/` module.
- **Phase 7:** Clean up `security-core` — remove authentication concerns, keep authorization.
- **Phase 8:** Audit custom Spring Security usage and document.

### The monorepo as a consumer

During and after migration, the monorepo's `dist/` module consumes the auth library like any other
consumer. It provides SPI implementations that bridge to the monorepo's existing infrastructure
(e.g., `SessionPersistenceAdapter` bridging `SessionPersistencePort` to `PersistentWebSessionClient`).

Over time, these bridges are replaced by the library's own implementations (see ADR-0003).

## Consequences

- **Positive:** Hub can depend on the auth library without importing the monorepo.
- **Positive:** Clear ownership boundary — authentication lives in `auth/`, authorization
  in `security/`.
- **Positive:** The library can be versioned and released independently.
- **Negative:** Migration requires updating 600+ import statements across the monorepo.
- **Negative:** Temporary duplication during the transition period (bridges in `dist/`).

