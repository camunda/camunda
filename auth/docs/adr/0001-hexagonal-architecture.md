# ADR-0001: Hexagonal Architecture for the Auth Library

**Date:** 2025-02
**Status:** Accepted

## Context

The Camunda monorepo has three overlapping modules handling authentication and security:

1. **`authentication/`** — A monolith (54 Java files) containing Spring Security filter chains,
   OIDC/Basic auth, user services, membership resolution, session management, and conditional
   annotations. Tightly coupled to `service/`, `security-core/`, `search/`, and `spring-utils/`.

2. **`security/`** — Four submodules (`security-protocol`, `security-core`, `security-services`,
   `security-validation`) containing authorization models, configuration POJOs, validators, and
   `ResourceAccessProvider`. 617 consumer files across the codebase.

3. Various persistence code scattered across `search/`, `db/rdbms/`, `dist/`, and `webapps-schema/`
   handling session storage, user/role/tenant/group persistence, and authorization records.

This tight coupling prevents reuse outside the monorepo and makes it impossible for other products
(e.g., Camunda Hub) to use Camunda's authentication without importing the entire monorepo.

## Decision

Adopt hexagonal architecture (ports and adapters) for the auth library. The module structure is:

```
auth/
├── auth-domain/                  # Pure core — zero framework dependencies
│   ├── model/                    # Records, enums, sealed interfaces (immutable)
│   ├── port/inbound/             # Use-case interfaces (what the app does)
│   ├── port/outbound/            # Infrastructure interfaces (what the app needs)
│   ├── spi/                      # Extension points for consumers
│   ├── exception/                # Unchecked domain exceptions
│   └── support/                  # Pure-Java utilities (delegating holders, converters)
│
├── auth-spring/                  # Spring Security adapters (implements ports/SPIs)
│
├── auth-spring-boot-starter/     # Auto-configuration for Spring Boot consumers
│
├── auth-persist-elasticsearch/   # Elasticsearch persistence adapters
│
├── auth-persist-rdbms/           # RDBMS (MyBatis) persistence adapters
│
├── auth-bom/                     # Bill of Materials for version alignment
│
└── auth-integration-tests/       # End-to-end verification
```

### Architectural rules (enforced by ArchUnit)

1. **`auth-domain` must not depend on Spring, Jakarta Servlet, or Jackson runtime** — only
   jackson-annotations (pure metadata) are allowed.
2. **Models must be records, enums, or sealed interfaces** — no mutable classes in the model package.
3. **Ports and SPIs must be interfaces** — they define contracts, not implementations.
4. **Models must not depend on ports or SPIs** — models are pure data.
5. **Domain exceptions must extend RuntimeException** — unchecked only.

These rules are verified on every build via `DomainArchTest`.

### Dependency direction

```
auth-domain  ←  auth-spring  ←  auth-spring-boot-starter
     ↑               ↑
     │               └── auth-persist-elasticsearch
     └────────────────── auth-persist-rdbms
```

All arrows point inward toward the domain. The domain never depends on adapters.

## Consequences

- **Positive:** The domain module is a plain Java library with no framework dependencies.
  Non-Spring consumers can depend on `auth-domain` directly.
- **Positive:** Each adapter module can be replaced independently. Swapping persistence from
  Elasticsearch to RDBMS is a classpath change + one property.
- **Positive:** ArchUnit tests prevent accidental architectural erosion.
- **Negative:** More modules to maintain than a monolith approach.
- **Negative:** Some indirection — adapters translate between domain models and
  framework-specific types.

