# ADR-0001: Extract Authentication Into a Hexagonal Library

**Date:** 2026-03-11
**Status:** Accepted

## Context

Authentication and authorization logic in the Camunda orchestration cluster was deeply embedded in
`camunda-authentication` and `security-core` — two modules tightly coupled to Spring Security,
Jakarta Servlet, and Jackson. This made it impossible for non-Spring components (e.g., Zeebe broker
internals, gRPC interceptors) to use the same identity model, and created a barrier for new
components (e.g., Hub) that need different security configurations.

We needed to provide a shared authentication contract that:
- Works for any JVM component, not just Spring Boot applications
- Allows consuming teams to implement their own authentication plumbing
- Does not drag in framework dependencies transitively

## Decision

Create a new `gatekeeper` library with two modules following hexagonal architecture:

**`gatekeeper-domain`** — Pure Java module with zero framework dependencies. Contains:
- Model records (`CamundaAuthentication`, `CamundaUserInfo`, `SecurityContext`, etc.)
- SPI interfaces that consuming applications implement (`MembershipResolver`, `CamundaUserProvider`, `SecurityPathProvider`, etc.)
- Auth utilities and configuration records

**`gatekeeper-spring-boot-starter`** — Spring Boot auto-configuration module. Contains:
- Authentication converters, holders, and provider
- Security filter chain configuration
- OIDC integration (client registration, JWT decoding, token refresh)
- Basic auth integration
- Session management, CSRF protection, handlers

### Key design rules (enforced by ArchUnit):

- Domain must not depend on Spring, Jakarta Servlet, or Jackson runtime
- Model classes must be records, enums, or sealed interfaces
- SPIs must be interfaces
- Models must not depend on SPIs
- All production classes must be `final` unless they are intentional extension points
- Every auto-configured bean uses `@ConditionalOnMissingBean` so consumers can override

## Consequences

- Consuming teams depend on a stable SPI contract and implement only what they need
- Non-Spring modules can depend on `gatekeeper-domain` alone for the type system
- Spring Boot applications get full auto-configuration by adding the starter
- The existing `camunda-authentication` module becomes a thin adapter layer implementing gatekeeper SPIs (see ADR-0002)
- ArchUnit tests enforce the architectural boundaries at build time

