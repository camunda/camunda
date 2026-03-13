# ADR-0002: Camunda-Authentication as Thin Adapter Layer

**Date:** 2026-03-12
**Status:** Accepted

## Context

With gatekeeper providing the canonical authentication infrastructure (ADR-0001), the existing
`camunda-authentication` module has significant overlap. It contains its own Spring Security
configuration, OIDC plumbing, converters, holders, handlers, filters, session management, and
services — all of which gatekeeper now provides via auto-configuration.

We needed to decide how to transition consuming modules (`zeebe/gateway-rest`,
`gateways/gateway-mapping-http`, `dist`, `operate/common`, `tasklist/webapp`) from
`camunda-authentication` to gatekeeper without a big-bang migration.

## Decision

Make `camunda-authentication` a thin adapter layer that implements gatekeeper SPIs. The module
keeps its `adapter/` package and its conditional annotations, but delegates all authentication
infrastructure to gatekeeper.

### Adapter implementations:

- `DefaultMembershipResolverAdapter` — bridges to the existing secondary storage for groups, roles, tenants
- `NoDbMembershipResolverAdapter` — for deployments without a secondary user store
- `BasicCamundaUserProviderAdapter` / `OidcCamundaUserProviderAdapter` — assemble `CamundaUserInfo` from existing services
- `SessionPersistenceAdapter` — delegates to existing session store
- `WebComponentAccessAdapter` — bridges to existing component authorization
- `AdminUserCheckAdapter` — delegates to existing admin user check
- `OidcConfigurationAdapter` — converts existing OIDC config properties to gatekeeper format

### What gets deleted from camunda-authentication:

- All Spring Security configuration classes
- OIDC classes (token refresh, JWK, user service)
- Converters, holders, handlers, filters
- CSRF configuration
- Session management
- Services, DTOs, exceptions, utilities
- Controllers
- Root-level conditional annotations (except `ConditionalOnCamundaGroupsEnabled` and `ConditionalOnInternalUserManagement`)

### What consuming modules change:

- Import types from `io.camunda.gatekeeper.*` instead of `io.camunda.authentication.*`
- Use `CamundaUserProvider` instead of `CamundaUserService`
- Use `CamundaUserInfo` instead of `CamundaUserDTO`

### Dual type coexistence:

During transition, two `CamundaAuthentication` types coexist — `security-core`'s and gatekeeper's.
Adapters bridge between them. This is temporary until security-core consumers are migrated.

## Consequences

- No big-bang migration — consuming modules can transition incrementally
- `camunda-authentication` becomes a much smaller module (~10 adapter files vs. ~58 files)
- gatekeeper-spring-boot-starter becomes the single source of auth infrastructure
- Runtime behavior does not change — the adapters preserve existing semantics
- Risk: adapter bugs could cause subtle behavioral differences during transition (mitigated by integration tests)

