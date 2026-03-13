# ADR-0005: Scope Gatekeeper to Authentication

**Date:** 2026-03-13
**Status:** Accepted

## Context

Gatekeeper-domain initially included both authentication types (CamundaAuthentication, session
management, OIDC utilities) and authorization types (Authorization, AuthorizationCondition,
ResourceAccessController, ResourceAccessProvider, TenantAccessProvider, and related models).

Attempting to consolidate security-core's authorization types into gatekeeper revealed a fundamental
incompatibility: security-core's `Authorization` record uses zeebe-protocol enums
(`AuthorizationResourceType`, `PermissionType`) while gatekeeper's uses `String`. This difference
cascades through `AuthorizationCondition`, `SecurityContext`, `ResourceAccessProvider`, and
`ResourceAccessController` — making a mechanical import swap impossible.

More importantly, authentication and authorization are distinct concerns with different consumers
and lifecycles. Mixing them in one module creates unnecessary coupling.

## Decision

Scope gatekeeper to **authentication only**:

- **Keep:** Identity model (`CamundaAuthentication`, `CamundaUserInfo`, `SecurityContext`), session
  management, OIDC integration, authentication SPIs (`MembershipResolver`, `CamundaUserProvider`,
  `SecurityPathProvider`), security filter chains
- **Remove:** All authorization types (`Authorization`, `AuthorizationCondition` hierarchy,
  `ResourceAccess`, `TenantAccess`, etc.), authorization SPIs (`ResourceAccessController`,
  `ResourceAccessProvider`, `TenantAccessProvider`, `AdminUserCheckProvider`,
  `WebComponentAccessProvider`), authorization filters, authorization/multi-tenancy config
- **Add:** `WebappFilterChainCustomizer` extension point so consuming applications can inject their
  own filters (including authorization filters) into gatekeeper's webapp security chains

Authorization will be addressed in a future dedicated effort, likely as interfaces in a separate
module that components implement to answer "can this identity do X?"

## Consequences

- Gatekeeper has a focused, coherent scope: establishing and managing identity
- The Authorization enum-vs-String incompatibility is no longer a blocker
- Authorization types remain in security-core for now; consumers continue using them as-is
- Future authorization work can design the right abstraction without being constrained by
  gatekeeper's domain model
- `SecurityContext` is simplified to carry only `CamundaAuthentication`
- Consuming applications use `WebappFilterChainCustomizer` to add authorization filters
