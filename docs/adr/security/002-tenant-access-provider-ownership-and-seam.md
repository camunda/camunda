# Tenant-access provider: CSL ownership and a uniform provider seam

**DRI**: Identity / Core Features team

**Status**: Proposed (8.10)

**Purpose**: Establish CSL `core` as the owner of the concrete tenant-access provider and the
`TenantOwnedEntity` contract, and adopt a single `TenantAccessProvider` seam across both the read
(search) and write (engine) paths — without changing tenant-authorization behavior on either.

**Audience**: Engineers and AI coding agents working on the Camunda Security Library, the Zeebe
engine write-path authorization, and the search/query authorization stack.

Relates to: camunda-security-library#582 (Inc 5), camunda-security-library#584,
camunda/camunda#59107.

## Context

The `TenantAccessProvider` interface and the `TenantAccess` verdict type already live in CSL `core`,
but the only concrete implementation — `DefaultTenantAccessProvider` — lives in the monorepo, on the
read path (`search/search-client-query-transformer/.../auth/`). Its resource-aware
`hasTenantAccess(T)` extracts a tenant from a document via the `TenantOwnedEntity` marker
(`search/search-domain/.../entities/`), implemented by 21 search read-model entities.

Two forces meet here:

1. **camunda-security-library#582 AC1** asks for a concrete claims-based provider in CSL `core`. An
   interim stub (`ClaimsBasedTenantAccessProvider`, PR camunda-security-library#584) satisfies it
   only partially:
   `hasTenantAccess(T)` throws, because the tenant-from-resource extraction needs `TenantOwnedEntity`,
   which is not on the CSL classpath.
2. The Inc 5 goal is for the **engine write-path** to consume `TenantAccessProvider` and retire its
   bespoke `AuthorizedTenants*` stack, rather than copy the default provider into the engine.

The engine's tenant policy is genuinely richer than plain claims-reading
(`AuthorizedTenantsResolver`): `anonymous → wildcard`, `multi-tenancy off → default tenant`,
`no username & no client-id → denied`, else `claims-resolved tenants`. Every input except
`EngineSecurityConfig.isMultiTenancyChecksEnabled()` is derivable from a `CamundaAuthentication`,
which the engine can obtain at the call site (`claimsConverter.resolve(...)`).

On the read path, anonymous authentication is routed to a dedicated
`AnonymousResourceAccessController` that short-circuits to `ResourceAccessChecks.disabled()`; the
controller that calls `hasTenantAccess` excludes anonymous. So `DefaultTenantAccessProvider` is never
invoked with an anonymous authentication and correctly ignores the `anonymousUser` flag.

## Decision

**1. Tenant-ownership is a security contract, not a search read-model detail.** Relocate
`TenantOwnedEntity` and the `DefaultTenantAccessProvider` logic into CSL `core` as the canonical
claims-based provider. This is why CSL — not `search` — is the right owner: the marker names a
security property of an entity (which tenant owns it), and the provider is the authorization
component that reads it. `search-domain` already depends on CSL `core`, so no dependency cycle is
introduced. The `ClaimsBasedTenantAccessProvider` stub is superseded and removed.

The relocation is behavior-preserving. The monorepo cutover is **atomic** (delete the search
`TenantOwnedEntity` + `DefaultTenantAccessProvider`, repoint all 21 entities and the read-path
wiring in one commit) and guarded by a test asserting every tenant-scoped entity is `instanceof` the
core `TenantOwnedEntity`. This guard is required because `hasTenantAccess(T)` **fails open** —
returns `allowed` for any resource that is not `instanceof TenantOwnedEntity` — so a missed repoint
would be a silent authorization bypass, not a compile error.

**2. A uniform tenant-access provider seam across read and write paths.** The engine consumes the
shared `TenantAccessProvider` through a thin engine-side decorator over the core claims provider:
`anonymousUser → wildcard`, `mt-off → default tenant`, else delegate. This replaces
`AuthorizedTenantsResolver`.

The claim of this decision is precisely the **seam**, not elimination of engine code. The
anonymous / mt-off policy is irreducibly engine-specific (it depends on engine config and on
signals the read path handles by controller selection) and remains in a named engine class. The
shared provider stays **anonymous-agnostic**, matching the read path, so no read-path behavior
changes. Honoring `anonymousUser` inside the shared provider was rejected: it would be dead code on
the read path and an unnecessary behavior-surface change.

## Alternatives considered

- **Share only the interface + `TenantAccess` type; each side keeps its own impl** — the current
  shape of engine PR camunda/camunda#59107. Lowest risk, but no shared seam: the engine stays on a
  bespoke resolver.
- **Push all policy into `CamundaAuthentication` construction so one dumb provider serves both
  paths.** Cleanest theoretical end state, but changes how both paths build authentication and must
  model mt-off/wildcard on the authentication — highest risk for modest gain. The pre-existing
  `anonymousUser` field is a convenience, not a reason to adopt this.

## Consequences

- CSL `core` gains a security-owned entity contract (`TenantOwnedEntity`) and the canonical provider;
  its public API now carries both.
- The two moves land on the **existing** PRs, not new ones. Relocation (decision 1) extends
  camunda-security-library#584 alongside the membership predicates, so that PR's alpha ships the
  relocated provider and delivers camunda-security-library#582 AC1. The monorepo cutover and engine
  adoption (decision 2) extend camunda/camunda#59107 alongside the `TenantAccess` migration already
  there, consuming that alpha.
- The two decisions stay logically distinct and separately reviewable, but decision 2 depends on the
  alpha produced by decision 1. Engine adoption reworks the resolver on camunda/camunda#59107 as a
  deliberate extension of that PR, not a prerequisite for the relocation.

