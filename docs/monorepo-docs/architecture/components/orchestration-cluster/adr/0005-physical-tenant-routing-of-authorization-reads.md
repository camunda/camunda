# ADR-0005: Physical-Tenant Routing of the Authorization Layer

## Status

Accepted

## Deciders

- Sebastian Bathke ([@megglos](https://github.com/megglos)) — proposer
- Ben Sheppard ([@Ben-Sheppard](https://github.com/Ben-Sheppard))
- Houssain Barouni ([@houssain-barouni](https://github.com/houssain-barouni))

## Context

[ADR-0003](0003-physical-tenant-request-scoping-via-pre-security-filter.md) established that the
physical-tenant (PT) id is stamped on the request **before** Spring Security's filter chain runs and
is available throughout the request via `PhysicalTenantContext.current()` and the `@PhysicalTenantId`
argument resolver. Non-prefixed cluster paths (`/v2/...`) fall back to `default`.

A per-PT **read** infrastructure already exists on `main`: the `ServiceRegistry` exposes per-PT
services (`authorizationServices(pt)`, `groupServices(pt)`, `roleServices(pt)`, `tenantServices(pt)`,
`mappingRuleServices(pt)`, `userServices(pt)`, …); the per-PT reader bundles (`SearchClientReaders` /
`RdbmsTenantReaders`, ES/OS + RDBMS via #54474) each carry a per-PT `AuthorizationReader`; and
`CamundaSearchClients` routes **data** reads to the in-context PT via `withPhysicalTenant(id)`. The
**authorization query API** is already PT-correct end to end
(`AuthorizationController.searchAuthorizations(@PhysicalTenantId …)` →
`ServiceRegistry.authorizationServices(pt)`), and the basic-auth path already follows the target
shape — `BasicAuthUserDetailsAdapter` resolves `PhysicalTenantContext.current()` and selects
`serviceRegistry.userServices(physicalTenantId)`.

The gap is the rest of the **authorization layer** — the reads that decide access. **All** are
currently pinned to the `default` PT, so in a multi-PT deployment every non-default PT is authorized
against the wrong tenant's data:

1. **Principal membership resolution.** At authentication time, `DefaultMembershipService` resolves
   the principal's groups, roles, tenants, and mapping-rules via
   `serviceRegistry.{group,role,tenant,mappingRule}Services(...)` to build the `CamundaAuthentication`,
   but currently passes `DEFAULT_PHYSICAL_TENANT_ID` (four `// TODO replace with contextual
   physicalTenantId`). These memberships are the **owner ids** the authorization check resolves grants
   for and the **tenant ids** the tenant check enforces — so if they come from the wrong PT, every
   downstream check is wrong regardless of how the grant lookup is routed.

2. **Authorization grant lookup (the _decision_ read).** The Camunda Security Library (CSL) consults
   the `AuthorizationReader` to decide whether a principal may access a resource. It is served by a
   **singleton bean hardwired to `default`** (#55252) — ES/OS
   `SearchClientReaderConfiguration.authorizationReader()` via `requireDefault(...)`; RDBMS
   `RdbmsConfiguration` equivalently — on two call paths:
   - **Control-plane** (Spring Security permission checks): `hasPermission()` → CSL
     `ResourcePermissionService` → `AuthorizationRepositoryAdapter` → `AuthorizationReader.search()`.
   - **Data-plane** (result authorization inside `CamundaSearchClients`): `doSearch`/`doGet` →
     `ResourceAccessDelegatingController` → `AuthorizationChecker` → `AuthorizationScopeRepositoryPort`
     (`SearchAuthorizationScopeRepository`) → `AuthorizationReader`.

This decision is constrained by the Slice 1 (#54728) principle that **CSL never learns about physical
tenants** (all PT resolution lives OC-side), and by the **engine isolation** principle agreed by the
squad: a per-PT engine incarnation must read only its own PT's secondary storage.

**Execution context determines how each read resolves the PT:**

- **Request-scoped reads** run on the request thread, where ADR-0003's pre-security filter has stamped
  the PT, so `PhysicalTenantContext.current()` is available. This covers **membership resolution** and
  the **control-plane** permission check. Memberships are resolved once at authentication time and
  **persisted into `CamundaAuthentication`**, so they travel with the principal — including into the
  engine for batch operations, which never re-resolve them.
- The **data-plane** decision read runs inside `CamundaSearchClients`, invoked from the request path
  **and from the engine** — batch operations materialise their item set via
  `ProcessInstanceItemProvider` → `searchClientsProxy.withSecurityContext(...).search…(…)`, off the
  request thread, where no `PhysicalTenantContext` is bound. So this read must resolve the PT
  **instance-bound** — from the PT the executing `CamundaSearchClients` is scoped to (#55401 already
  scopes the engine's data reads per partition PT) — not from a thread-local.

## Decision

Route the **entire authorization layer** to the in-context physical tenant, keeping CSL
physical-tenant-agnostic. The PT-resolution mode is chosen by where each read executes; every read
uses the existing per-PT `ServiceRegistry` services / per-PT reader bundles — no `default`-pinned
singleton.

### A. Request-scoped reads → `PhysicalTenantContext.current()`

These run on the request thread (PT stamped by ADR-0003's pre-security filter), mirroring
`BasicAuthUserDetailsAdapter`:

1. **Identity & membership resolution.** Every request-time read that builds or gates on the
   `CamundaAuthentication` resolves the in-context PT via `PhysicalTenantContext.current()` instead of
   `DEFAULT_PHYSICAL_TENANT_ID`. These are call sites in the authentication / Spring Security services
   that today call `serviceRegistry.*Services("default")` — inventoried in #54327
   ("Authentication & Spring Security services"): `DefaultMembershipService` (groups, roles, tenants,
   mapping-rules), `BasicCamundaUserService` and `OidcCamundaUserService` (user + tenants),
   `CamundaUserDetailsService` (user details), and `AdminUserPresenceAdapter` (admin-role check). The
   resolved memberships are persisted in `CamundaAuthentication` and reused everywhere downstream
   (including the engine), so this single request-time resolution is sufficient.
2. **Control-plane authorization decision.** `AuthorizationRepositoryAdapter` (OC's
   `AuthorizationRepositoryPort` impl) resolves `PhysicalTenantContext.current()` → that PT's
   `AuthorizationReader`. It reads with `ResourceAccessChecks.disabled()` (it *is* the
   permission-computation data source), so it routes at the `AuthorizationReader` layer, not through a
   `ResourceAccessController`.

### B. Instance-bound read → the search client's PT

3. **Data-plane authorization decision.** `CamundaSearchClients` holds a `Map<String,
   ResourceAccessController>`; `withPhysicalTenant(pt)` selects **both** the PT's data `readers` **and**
   its `ResourceAccessController` (each the existing `DocumentBasedResourceAccessController` /
   `RdbmsResourceAccessController` → `DefaultResourceAccessProvider` → CSL `AuthorizationChecker` →
   `SearchAuthorizationScopeRepository` chain, built over that PT's `AuthorizationReader`). Because the
   controller is bound to the search client's PT, the access check follows the data read in every
   caller — the request path and the engine (#55401) — with no thread-local.

   **No default-pinned search client.** `CamundaSearchClients` does **not** hold a `default`-scoped
   `readers`/`ResourceAccessController`; the base bean is an unscoped router and `withPhysicalTenant`
   is mandatory before any read. An unscoped read **fails fast** rather than silently resolving to
   `default` — so a caller that forgets to scope (e.g. the engine before #55401) errors loudly
   instead of reading the wrong tenant. This makes the PT always explicit (single-PT deployments scope
   to `default` explicitly via the per-PT services). The gateway/query path scopes through the per-PT
   `ServiceRegistry` services; the engine scopes per partition via **#55401** — both must land for the
   engine path to be runtime-correct under fail-fast.

CSL is untouched; all PT resolution lives in OC's adapters and bean wiring. **Net contract:** for any
caller carrying PT `X` (the request's PT, or a partition's PT in the engine), the principal's
memberships, the control-plane permission check, and the data-plane result-authorization check all
read PT `X`'s data.

## Consequences

### Positive

- The **whole authorization layer** — membership resolution, control-plane permission checks, and
  data-plane result authorization — is evaluated against the correct PT, on both backends and in both
  execution contexts (request and engine). Closes #55252 (widened to the layer) including the
  batch-operation path.
- Upholds the engine **isolation** principle: a per-PT engine incarnation reads only its own PT's
  authorization data; no usual execution path reaches another PT's secondary storage.
- No implicit `default` routing — the PT is always explicit (request context or the search client's
  scope), in line with the agreed direction to set the PT explicitly and never silently fall back to
  `default`.
- CSL stays physical-tenant-agnostic (Slice 1); reuses the per-PT `ServiceRegistry` services and
  reader bundles already on `main` (ES/OS, and RDBMS via #54474).

### Negative / trade-offs

- **Two resolution modes** (request-scoped vs instance-bound), justified by genuinely different
  execution contexts: membership resolution and the control-plane check run request-only, while the
  data-plane check also runs in the engine off the request thread.
- The data-plane change requires **per-PT instances of the resource-access chain**
  (`ResourceAccessController` → `ResourceAccessProvider` → `AuthorizationChecker` →
  `AuthorizationScopeRepository`), constructed alongside the per-PT readers and held in
  `CamundaSearchClients`. The components are thin wrappers, so the cost is wiring, not logic.
- The request-scoped reads rely on the thread-bound `PhysicalTenantContext`. This is safe because they
  run only on the request thread; the memberships they produce are then persisted, so the engine never
  needs to re-resolve them off-thread.

### Neutral

- No change to the authorization **query** API (already PT-routed) and none to CSL.
- `TenantAccessProvider` is stateless (it derives from the authentication's tenant ids), so it needs
  no per-PT instance — its correctness comes from membership resolution (A.1) populating the right
  tenant ids.
- Single/default-PT deployments are unchanged (everything resolves to the one `default` PT).

## Alternatives Considered

1. **Single thread-local routing `AuthorizationReader` for all reads** — re-point the one
   `authorizationReader` bean to a reader that resolves `PhysicalTenantContext.current()` and rely on
   the thread-local everywhere. **Superseded.** This was initially adopted after concluding the
   data-plane read always runs on the request thread — but that examined only the request path. Batch
   operations run authorization-checked secondary-storage searches **in the engine**, off the request
   thread, where `PhysicalTenantContext.current()` falls back to `default`. Thread-local is correct
   for the request-scoped reads (membership + control-plane) but unsafe for the data-plane, which is
   why the data-plane is resolved instance-bound instead. (It also implicitly routes to `default`,
   which the squad agreed to avoid.)

2. **Embed the routing inline in the host-side adapter(s)** (à la `BasicAuthUserDetailsAdapter`).
   This is in fact *how the request-scoped reads are resolved* in the decision above — the membership
   service and the control-plane adapter select the per-PT services/reader from `PhysicalTenantContext`.
   It does **not** help the data-plane decision, whose check runs in the engine with no request
   context; hence the separate instance-bound mechanism there.

3. **Carry the PT inside CSL's `SecurityContext`** so it could be threaded explicitly through CSL.
   Rejected: it makes CSL's context object physical-tenant-aware, violating the Slice 1 principle that
   CSL stays PT-agnostic.

4. **Do nothing / defer past alpha3.** Rejected: multi-PT authorization is incorrect without this,
   and it is also an engine isolation concern. Final alpha3 scoping is confirmed on #55252 / #54728.

## References

- [ADR-0003](0003-physical-tenant-request-scoping-via-pre-security-filter.md) — pre-security filter
  that stamps the PT id on the request (the request-scoped reads' PT source).
- #55252 — physical-tenant routing of the authorization layer (the defect this resolves; widened from
  the original `AuthorizationReader`-only scope to membership resolution + both decision-read planes).
- #54728 — Physical Tenants Identity, Slice 1 (parent).
- #54474 — per-physical-tenant RDBMS readers (`RdbmsTenantReaders`), merged.
- #54327 — inventory of hardcoded-`default` `serviceRegistry.*Services(...)` call sites in the
  authentication / Spring Security services (the request-scoped reads routed under A.1) and the wider
  per-tenant service-registry integration.
- #55401 — engine uses the partition's PT-scoped `SearchClient` for batch operations (scopes the
  *data* read; this ADR extends the same scoping to the data-plane *authorization* read via the per-PT
  `ResourceAccessController`).
- `DefaultMembershipService` (`authentication/.../service/DefaultMembershipService.java`) — resolves
  the principal's group/role/tenant/mapping-rule memberships; routed per PT under A.1.
- `BasicAuthUserDetailsAdapter` (`authentication/.../config/spi/BasicAuthUserDetailsAdapter.java`) —
  the request-scoped per-PT resolution pattern this ADR generalises across the authorization layer.
