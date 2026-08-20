# Tenant-access provider: CSL ownership and a uniform provider seam

**DRI**: Identity / Core Features team

**Status**: Proposed (8.10)

**Purpose**: Establish CSL `core` as the owner of the concrete tenant-access provider and the
`TenantOwnedEntity` contract, and unify the read (search) and write (engine) paths on the shared
`TenantAccess` type and `TenantAccessProvider` interface — the concrete provider shared on the read
path, the engine keeping its own resolver — without changing tenant-authorization behavior on either.

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
`no username & no client-id → allowed([])`, else `claims-resolved tenants`. Every input except
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

The relocation is behavior-preserving and a **clean move**: `TenantOwnedEntity` is deleted from
`io.camunda.search.entities` and each of its 20 implementers switches to
`import io.camunda.security.core.authz.TenantOwnedEntity`. This makes the compiler enforce
completeness — an implementer left behind references a symbol no longer in scope and fails to
compile, rather than silently losing its tenant check. (`hasTenantAccess(T)` does return `allowed`
for resources that are not `TenantOwnedEntity`, but that is a standing property about entities never
marked tenant-owned; this move neither introduces nor removes it, so no runtime `instanceof` guard
is load-bearing here.)

**2. The engine keeps its own resolver on the shared `TenantAccess` type (write path stays Option
C).** The substance of the unification is the shared **type**: the engine produces `TenantAccess`
from `CslTenantCheck.resolveAuthorizedTenants(Map)`, and the `TenantAccessProvider` interface lives
in core. Having the engine also *consume* the core `TenantAccessProvider` was evaluated and
**rejected** on two independent grounds:

- **Signature.** `TenantAccessProvider.resolveTenantAccess(CamundaAuthentication)` cannot carry the
  engine's inputs. The engine resolves from `(raw claims Map, EngineSecurityConfig)`, and its two
  decisive inputs — the `AUTHORIZED_ANONYMOUS_USER` marker and `isMultiTenancyChecksEnabled` — are
  not on `CamundaAuthentication`.
- **Semantics.** The engine resolves empty tenants to `allowed(tenantIds)` and anonymous to
  `wildcard`; the core provider resolves empty to `denied(null)` and is anonymous-agnostic. Engine
  processors (`JobBatchActivateProcessor`, `ResourceDeletionDeleteProcessor`, `ResourceFetchProcessor`)
  read `.tenantIds()` off the result, so the empty→`null` flip is caller-visible (an NPE risk), not
  cosmetic.

So the engine-specific policy (anonymous → wildcard, multi-tenancy off → default tenant, no principal
→ allowed([])) stays in the engine, and the shared provider stays **anonymous-agnostic**. The concrete
provider is shared only on the read path (decision 1); the write path shares the `TenantAccess` type
and the `TenantAccessProvider` interface, not the implementation.

## Alternatives considered

- **Engine adopts the core `TenantAccessProvider` (a decorator over `DefaultTenantAccessProvider`).**
  The original plan (this was Option D). Rejected — see decision 2: the interface signature cannot
  carry the engine's `(claims Map, config)` inputs, and the empty/anonymous semantics diverge in
  caller-visible ways, so the decorator would have to override exactly the diverging cases and
  delegate almost nothing — pure ceremony with behaviour risk.
- **Push all policy into `CamundaAuthentication` construction so one dumb provider serves both
  paths.** Cleanest theoretical end state, but changes how both paths build authentication and must
  model mt-off/wildcard on the authentication — highest risk for modest gain. The pre-existing
  `anonymousUser` field is a convenience, not a reason to adopt this.

## Consequences

- CSL `core` gains a security-owned entity contract (`TenantOwnedEntity`) and the canonical provider;
  its public API now carries both.
- The work lands on the **existing** PRs, not new ones. Relocation (decision 1) extends
  camunda-security-library#584 alongside the membership predicates, so that PR's alpha ships the
  relocated provider and delivers camunda-security-library#582 AC1. The monorepo relocation cutover
  and the engine's migration to the shared `TenantAccess` type are camunda/camunda#59107, consuming
  that alpha.
- Read and write paths share the `TenantAccess` type and the `TenantAccessProvider` interface; they
  share the concrete provider only on the read path. Because the engine's tenant policy stays
  engine-side, a future change to it does not touch CSL or the search read path.

**Revisited for camunda-security-library#587.** That issue asked whether the read and write paths'
differing anonymous-detection mechanisms (a `CamundaAuthentication` predicate on the read path vs. a
raw claims-map key on the write path) could be standardized. They can't: the write path's only
entry point is the command record's deserialized claims map (`TypedRecord.getAuthorizations()`),
and the shared converter to `CamundaAuthentication` (`TokenClaimsAuthenticationResolver.resolve()`)
throws when no username/client-id claim is present — which is exactly what an anonymous claims map
looks like. The raw-key read must happen before a `CamundaAuthentication` can safely be built, not
after, so the "one dumb provider" alternative above stays rejected for this reason too. The
monorepo addressed the issue's narrower "or via one shared short-circuit" framing by collapsing the
three duplicate raw-key checks inside the engine (`CslTenantCheck`, `CslAuthorizationCheck`) into
one helper, `CslTenantCheck.isAnonymousCommand`.

