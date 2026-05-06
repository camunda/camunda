# ADR-0007: Physical Tenants — Identity Support

## Status

Proposed

## Context

Camunda 8.9 multi-tenancy is logical: all tenants share one engine and one authorization scope, so a user's role in any logical tenant applies cluster-wide. **Physical Tenants (PT)** introduce a new boundary for the 8.10 release where each tenant is an isolated execution unit within a single Orchestration Cluster — separate data, independent backup/restore, no runtime interference. The umbrella epic is [camunda/product-hub#3430 — *Strong Tenant Isolation in Camunda 8 OC (Self-Managed)*](https://github.com/camunda/product-hub/issues/3430).

This ADR covers the **identity (authN/authZ) slice** of that epic — the line item *"Per-Physical-Tenant authentication and authorization (incl. IDP)"* under *Scope – Milestone 1 → Security and observability*. The slice covers per-PT REST authentication and authorization, browser login/logout, and gRPC `Camunda-Physical-Tenant` header handling. Sibling slices — per-PT primary/secondary storage isolation (Strong Isolation), Connectors multi-PT, observability — are **not** delivered here. The cluster-admin filter-chain matcher this slice provides is the auth contract those downstream controllers consume.

Per the epic body, **canonical naming** is fixed: API and config use `physicalTenantId` / `physical-tenants`; Hub UX surfaces this concept as **Environment**; the existing logical-tenant `tenantId` is unchanged. This ADR uses `physicalTenantId` and `physical-tenants` throughout.

The 8.10 identity contract is:

- One or more cluster-level IdPs (already supported via `ProvidersConfiguration`).
- A new top-level URL space `/v2/physical-tenants/{physicalTenantId}/...` carries every existing per-tenant API call, scoped to one PT. The path variable name `physicalTenantId` is deliberately distinct from the existing logical-tenant `{tenantId}` so the two concepts cannot be confused at any layer (URL, code, logs).
- A new `/v2/cluster/...` space carries cluster-wide operations (topology, backup, restore) protected by a **claim-based** cluster-admin role — no persisted role bindings, no new identity service.
- Existing `/v2/...` paths without a PT prefix continue to work, routing to an implicit `default` PT (backwards compatibility for existing 8.9 clients).
- Each PT owns its own roles, mapping rules, groups, and authorizations. Per-PT auth method or IdP overrides are explicitly out of scope.

Two design pressures bound the solution:

1. **Reuse first.** The existing `io.camunda.security.configuration.InitializationConfiguration` already models users, mapping rules, default roles, authorizations, tenants, groups, and roles. Re-using it verbatim per PT avoids parallel domain types and keeps Spring binding free.
2. **Forward compatible without overreach.** Strong Tenant Isolation will later swap the in-process `TopologyServices` for per-PT broker clients; the design must accept that swap without restructuring. The slice must not pre-build per-PT `BrokerClient` lifecycle or per-PT login chains, both of which carry significant unrelated risk.

A separate planning document with code skeletons, test plan, and milestone breakdown lives alongside this ADR at [`physical-tenants-identity-plan.md`](../physical-tenants-identity-plan.md). This ADR records only the architectural decisions.

## Decision

### Configuration shape

Per-PT configuration is a **top-level tree** under `camunda.physical-tenants.{physicalTenantId}` — a `Map<String, PhysicalTenantConfiguration>`. Each PT entry carries a `security` sub-block (this slice) and may, in sibling slices, also carry `secondary-storage`, `backup`, `broker-client`, etc. sub-blocks.

```yaml
camunda:
  security:
    authentication:
      method: oidc
      oidc:
        providers:
          default:    { ... }
          provider-a: { ... }
    cluster-admin:
      providers: [default]
      claim: groups
      value: cluster-admin
    initialization:                          # IN-MEMORY ONLY in PT mode
      users:
        - { username: cluster-admin, password: ${...} }
      roles:
        - { roleId: cluster-admin, users: [cluster-admin] }
  physical-tenants:
    default:
      name: Default
      security:                              # PT-scoped security
        idps: [default]
        initialization:                      # SEEDED into PT primary + secondary storage
          roles: [...]
          groups: [...]
          tenants: [...]                     # logical tenants inside this physical tenant
          authorizations: [...]
          mappingRules: [...]
          # NOTE: no `users` block — BASIC is cluster-level only.
    risk-production:
      name: Risk Team Production
      security:
        idps: [default, provider-a]
        initialization: { ... }
```

Four substantive choices in this shape:

1. **Top-level `camunda.physical-tenants`** with per-PT sub-blocks. Per the requirements clarification, PT data is shared across slices; one top-level tree with per-slice sub-blocks (`security`, `secondary-storage`, `backup`, …) keeps a single set of PT ids and per-slice ownership of sub-trees. Earlier draft nested under `camunda.security` — rejected because it forced sibling slices to either piggyback on the security tree (semantically wrong) or duplicate the PT id list (drift risk).
2. **`Map`, not `List`.** A list with an `id` field allows duplicate ids, requires a separate uniqueness check, and bloats the YAML with `- id:` prefixes. A map keyed by id makes duplicates structurally impossible, lets cross-references use direct lookup instead of stream-and-filter, and reads more like a registry than a sequence — which is the actual semantic.
3. **`InitializationConfiguration` per PT, not `SecurityConfiguration` per PT.** Authentication method (BASIC vs OIDC) is cluster-level. **BASIC is cluster-level only** per the requirements clarification — PTs cannot declare a `users` block, and startup validation rejects a non-empty PT user list. Each PT exposes only `name` and a `security` sub-block (with `idps` + `initialization`).
4. **Cross-tree validation eliminated.** No separate `engine-idp-assignments` map. IdP assignments live inline as `security.idps` per PT.

### Registry as the single source of truth

A new `PhysicalTenantRegistry` bean (interface in `security/security-core/.../tenants/`, default implementation immutable after construction) is the single lookup point every other PT-aware code path consults. It exposes forward (`findById`) and reverse (`tenantsForIdp`) lookups, the reserved-ID predicate, and a `legacyMode` flag.

When `camunda.physical-tenants` is empty, the registry synthesizes one `default` entry whose `initialization` **references** (not copies) the cluster-level `camunda.security.initialization`. This is the backwards-compat path: existing 8.9 configs see no behavioral change, and there is **one code path** through the system rather than `if (legacyMode) ... else ...` branching in business logic. The mode collapses into the registry abstraction.

Validation runs in `@PostConstruct` and fails the application context on any violation (id format, reserved-ID collision, unknown IdP reference, OIDC mode with empty `idps`). Duplicate-id validation is structurally impossible thanks to the map shape; cross-tree mismatch validation is unnecessary because there is no second tree.

### Filter chain topology

Two new `SecurityFilterChain` beans land in `WebSecurityConfig`, ordered **before** the existing `API_PATHS` chain:

| Order | Chain | `securityMatcher` | Purpose |
|---|---|---|---|
| 10 | `physicalTenantApiSecurityFilterChain` | `/v2/physical-tenants/{physicalTenantId}/**` | per-PT authN/authZ |
| 15 | `clusterAdminSecurityFilterChain` | `/v2/cluster/**` | claim-based cluster admin |
| 20 | (existing legacy `API_PATHS`) | `/v2/**`, `/v1/**`, … | default-PT, unchanged |

The PT chain disables `formLogin`, `oauth2Login`, `AdminUserCheckFilter`, and `WebComponentAuthorizationCheckFilter` — these belong to webapp / login concerns that are out of scope for the API surface. CSRF, security headers, and the request firewall **remain** active. The "skip everything else" instruction in the requirements is interpreted strictly as *skip global authentication mechanisms*, not *skip cross-cutting hardening*. Removing CSRF would defeat defense-in-depth on session-bearing webapp callers; removing the firewall would weaken request-shape validation. Both stay.

The cluster chain runs alongside HTTP BASIC for break-glass access using `camunda.security.initialization.users` — no new identity store is introduced.

### Per-PT authentication: shared decoder, per-tenant converter

A `PerPhysicalTenantAuthenticationManagerResolver` extracts `physicalTenantId` from the path and looks up (or lazily builds) one `JwtAuthenticationProvider` per PT. Each provider holds:

- The **shared** existing issuer-aware `JwtDecoder`. JWT signature, `exp`, and `aud` validation are cluster-level concerns that don't change per PT, and the existing `IssuerAwareJWSKeySelector` already covers all configured IdPs. Per-PT decoders would multiply key-set fetches, JWKS caches, and discovery calls by the number of PTs for no security benefit.
- A **per-PT** `JwtAuthenticationConverter` produced by `PhysicalTenantJwtAuthenticationConverterFactory.forTenant(physicalTenantId)`. This converter wraps the existing `OidcTokenAuthenticationConverter` / `TokenClaimsConverter` pipeline, seeded with a `SecurityConfiguration` projection where `initialization` is replaced by the PT's `initialization`. No converter logic is duplicated.

Cross-PT token replay is rejected inside the per-PT converter: before delegating to the existing pipeline, the converter asserts `jwt.iss ∈ pt.idps()` and throws `BadJwtException` otherwise. A token issued for `default` (`idps: [default]`) presented to `risk-production` (`idps: [default, provider-a]`) succeeds; the reverse — a token from `provider-a` presented to `default` — fails. This is the highest-blast-radius security boundary in the slice and is asserted at the unit, slice, and integration test layers.

### Storage model: cluster level in-memory, PT level seeded into per-PT primary + secondary storage

Per the requirements clarification, the storage strategy is **inverted between cluster and PT**:

- **Cluster level (`/v2/cluster/**`, cluster-admin BASIC fallback) is in-memory only.** `camunda.security.initialization.users`, `camunda.security.cluster-admin.{providers, claim, value}` are read on each request from YAML. No DB lookup, no seeder, no secondary storage involvement at the cluster level. This is correct because cluster-scoped resources are few and their authorization is binary (`ROLE_CLUSTER_ADMIN` or not).
- **PT level (`/v2/physical-tenants/{id}/**`, webapp routes under `/{id}/...`, gRPC with PT header) uses primary + secondary storage.** Each PT's `security.initialization` is **seeded once at startup** (existing 8.9 seeder logic, executed per PT against the per-PT storage that the Strong Isolation epic provides). Runtime authority resolution goes through the existing `MembershipService` and `ResourceAccessProvider` against that PT's secondary storage.

The per-PT JWT converter pipeline therefore selects **per-PT `MembershipService` and `ResourceAccessProvider` beans** from a registry-backed map, keyed on the URL's PT id — Strong Isolation's per-PT storage primitives are the dependency. The earlier draft's "in-memory only per PT" model is replaced by this storage-backed model.

**Backwards compatibility (no PTs configured).** The registry synthesizes a single `default` PT whose `initialization` references the cluster-level `camunda.security.initialization`. The 8.9 single-storage seeder runs unchanged against the cluster's secondary storage. Cluster-level YAML is **both** the in-memory cluster-admin source and the seed source for the synthetic default PT. Runtime behaviour is byte-identical to 8.9.

**No cross-tenant authorization rule.** `ROLE_CLUSTER_ADMIN` grants access only to `/v2/cluster/**` — it does **not** authorise any operation on PT-scoped resources. The topology aggregator's per-PT fan-out runs under a marker authority (`ROLE_INTERNAL_TOPOLOGY_READ`) that PT-side `ResourceAccessProvider` whitelists **only** for `TopologyServices.getTopology()` and rejects everywhere else. There is no cross-PT data path even with cluster-admin privilege. The same rule applies to `/v2/cluster/backup` (backup-only marker authority).

### Cluster-admin: claim-based, stateless

`ClusterAdminClaimAuthorizer` is a `Converter<Jwt, AbstractAuthenticationToken>` that grants `ROLE_CLUSTER_ADMIN` when the JWT issuer is in the configured `clusterAdmin.providers` list **and** the JWT carries the configured claim with the configured value. There is no persisted grant table, no audit trail, no token revocation primitive — those were explicitly out of scope per the issue and are documented as known gaps (see Consequences below). BASIC fallback covers break-glass via the cluster-level `initialization.users`.

### Cluster-admin chain matcher — contract for downstream controllers

This slice ships the `clusterAdminSecurityFilterChain` with `securityMatcher("/v2/cluster/**")`. It does **not** ship any cluster-scoped controllers (`/v2/cluster/topology`, `/v2/cluster/backup`, `/v2/cluster/restore`) — those are owned by their respective slices (gateway/topology team, backup/restore team).

What this slice provides as a contract to downstream controllers:

- Any controller mounted under `/v2/cluster/**` automatically receives:
  - JWT authentication against `camunda.security.cluster-admin.providers`.
  - Claim-based authorisation via the configured `(claim, value)` triple.
  - HTTP BASIC fallback against `camunda.security.initialization.users` (in-memory, break-glass).
  - `ROLE_CLUSTER_ADMIN` granted authority on success.
- The "no cross-tenant authorization" rule (next subsection) constrains how those controllers can fan out to per-PT operations. Each per-PT call must use a single-purpose marker authority that PT-side `ResourceAccessProvider` whitelists only for that specific code path.

The existing `TopologyController` (`/v1/topology`, `/v2/topology`) is untouched. Whether a future `/v2/cluster/topology` controller replaces or augments it is a decision for the gateway team — not this slice.

### Login and logout — 🚨 DECISION PENDING (CTA) 🚨

> **Login/logout is mandatory in 8.10** per the requirements clarification — Option C ("defer login") was removed. The remaining decision is **A vs B**.

PoC PR [camunda/camunda#51959](https://github.com/camunda/camunda/pull/51959) demonstrates a tenant-aware OIDC login picker that **binds the HTTP session to a chosen PT** via `?tenant=` query parameter on `/oauth2/authorization/{idpId}`. A four-perspective review (Spring Security, Java/architecture, devil's advocate, TDD) found three security defects structural to the choice of HTTP session as the binding store:

1. **CSRF on the binding GET.** `<img src="/oauth2/authorization/idp-x?tenant=victim-tenant">` from a third-party page pins a victim's session pre-authentication. `CsrfFilter` ignores GETs by design; the PoC adds a session side-effect to a GET.
2. **Bearer-token bypass on webapp paths.** `oidcWebappSecurity` enables both `oauth2Login` and `oauth2ResourceServer.jwt(...)`. The PoC's enforcement filter passes `JwtAuthenticationToken` through. Anyone with a JWT can hit `/{anyPt}/operate/...` unbound.
3. **Multi-tab race not actually defended.** The success-handler check is an authorization check (is this IdP allowed on this PT?), not a race detector. Tabs picking PTs whose IdP sets overlap leave the user authenticated to one PT but session-bound to the other.

The team converged on **two options** (Option C "defer" was removed when login became mandatory). **Decision deadline: before M2c starts.**

**Option A — Refined PoC: bind PT in OIDC `state`, not HTTP session.** Keep the PoC's shape (per-PT picker, single `/sso-callback`) but replace the session attribute with PT id round-tripped through Spring's existing `state` parameter (HMAC-bound, per-flow). Custom enforcement filter replaced by `authorizeHttpRequests` keyed on `PT_<id>` authority. Reject Bearer on webapp paths. Closes all three defects. Does NOT close the foundational UX limit (logout-to-switch) or the API/webapp BFF gap. Effort: ~5-7 days from PoC.

**Option B — Cluster-wide session + per-request RBAC.** Drop session-tenant binding entirely. User logs in once cluster-wide; OIDC token claims carry PT memberships (`pt-memberships: [risk-production, default]`). Every request is gated by a one-line `authorizeHttpRequests` against the URL's PT and the principal's authorities. Closes all three defects PLUS multi-PT switching UX PLUS the API/webapp coexistence problem (one access token works both surfaces). Requires a customer-side IdP claim contract (PT memberships emitted as a token claim) — supported by all major enterprise IdPs but a deployment-time requirement to document. Effort: ~10-14 days; bigger upfront but kills more downstream work.

| Dimension | A — refined PoC | B — claim-based RBAC |
|---|---|---|
| Resolves CSRF / Bearer bypass / race | ✅ | ✅ |
| Multi-PT UX without logout | ❌ | ✅ |
| Webapp → PT API works | ⚠ needs BFF token relay | ✅ one token, both surfaces |
| IdP claim requirement | none | **PT memberships claim required** |
| Effort delta from PoC | ~5-7 days | ~10-14 days |
| Risk | low | medium |

**Recommendation.** Spring Security specialist recommends **A** as a minimum bar. Devil's advocate and TDD specialist (and this ADR's author) prefer **B** because the session-binding primitive in **A** is structurally what produces the defects, and **B** is the only option that closes the API/webapp coexistence gap *inside* this identity slice rather than punting to an unbuilt webapp BFF.

The decision turns on whether requiring customer IdPs to emit PT membership claims is acceptable in the 8.10 timeframe. If yes → **B**. If no but UX-correctness matters → **A**.

The wiring this slice ships — `PhysicalTenantRegistry`, `PhysicalTenantConfiguration`, the `camunda.physical-tenants.{id}.security.*` property tree, the API-side filter chains, the gRPC interceptor — is forward-compatible with both options. The decision is **purely about what runs on the OIDC webapp chain**.

Once the team chooses, this section is rewritten to record the chosen design as the documented behaviour of that option.

### What is deliberately not in this slice

- **Per-PT session cookie scoping** (so two browser tabs can hold sessions for different PTs simultaneously) — only relevant under Option A; explicitly excluded even from A's scope.
- **Bulk migration tooling** from 8.9 logical-tenant authorizations to 8.10 PT authorizations.
- **Persisted cluster-admin grant store, audit, dynamic management APIs, Hub policy distribution.**

## Consequences

### Positive

- **No new top-level configuration tree.** The existing `CamundaSecurityProperties extends SecurityConfiguration` binder picks up PT configuration without code changes in `dist/`. Spring Boot natively binds `Map<String, PhysicalTenantConfiguration>` from the YAML shape.
- **Backwards compatibility is structural, not branching.** A vanilla 8.9 configuration starts on 8.10 with no behavior change because the registry synthesizes a `default` PT that references the cluster-level `initialization`. There is one code path through every PT-aware service, regardless of mode.
- **Bean-count stays bounded.** One shared `JwtDecoder`, one converter per accessed PT (cached). Not one filter chain per PT, not one decoder per PT. Registering N filter chains for N PTs would explode startup time and memory; the chosen design scales linearly only in cached converters, lazily built.
- **Forward-compatible response shape for `/v2/cluster/topology`.** Clients written against the 8.10 endpoint continue to work unchanged when Strong Isolation lands per-PT broker clients in a later release. Only the controller's per-PT delegation changes.
- **Cross-PT token replay is rejected at the converter level**, asserted at every test layer. This is the highest-blast-radius regression the slice could ship and is structurally guarded.
- **No per-PT seeder against secondary storage** means there is no persisted-vs-in-memory divergence to debug. The YAML is the single source of truth for PT authorization.
- **The existing `TopologyController` is untouched**, eliminating a regression vector for the most-called REST endpoint in operational tooling.

### Negative

- **Configuration size grows with PT count.** With 50 PTs (the documented upper end), the YAML re-declares roles / groups / mapping rules / authorizations 50 times. There is no inheritance or templating. This is an accepted cost — a baseline-and-overrides mechanism would be a meaningful design effort and was excluded to keep the slice deliverable. Operators with many PTs are expected to manage YAML through Helm / templating tools or wait for the future Hub policy distribution capability.
- **Cluster-admin has no revocation, no audit, and no rotation primitive in 8.10.** A fired employee retains cluster-admin access until the token expires; a typo in the configured claim value bricks ops. Operators are expected to monitor the token lifetime of their cluster-admin IdP and to keep the BASIC break-glass user on a rotated password. A minimal grant-on-first-use record is a documented follow-up.
- **Reserved-ID list is a maintenance landmine.** Every future top-level path (`/v2/billing`, `/v2/console`, …) must be added to the reserved set, or a customer will eventually configure a colliding PT id and silently break. The list is a static constant in the registry and carries a unit test, but a build-time scanner over `@RequestMapping` annotations is a documented follow-up.
- **Login flow remains cluster-uniform.** Webapp users land on the cluster-level login page that lists every IdP, not just the IdPs assigned to their target PT. Tenant-aware login is owned by the Webapps PT routing slice and is a customer-visible gap in 8.10.
- **The "skip everything else" instruction was scoped narrowly.** CSRF, security headers, and the request firewall remain active on the PT chain. An interpretation that strips them would simplify the chain definition by ~10 lines but introduce a defense-in-depth regression that this ADR rejects.
- **Storage model depends on Strong Isolation.** Per-PT primary + secondary storage is a hard prerequisite (per requirements clarification). Identity slice consumes per-PT `MembershipService` / `ResourceAccessProvider` beans from a registry-backed map; Strong Isolation provides those beans. If Strong Isolation slips, identity falls back to legacy single-storage mode (one synthetic `default` PT, 8.9-byte-identical behaviour).
- **Two PT-related config trees may coexist short-term.** This slice owns `camunda.physical-tenants.{physicalTenantId}` for identity attributes. Sibling slices need to attach non-security per-PT properties (secondary storage, backup repository, broker client) and will likely choose a different top-level location — the placeholder javadoc in `TenantConnectConfigResolver` already references `camunda.physical-tenants` for that purpose. A future cross-slice agreement on a unified top-level tree (e.g. `camunda.physical-tenants` for non-security and a sibling `camunda.physical-tenants` for security, or a single tree with `security:` and `secondary-storage:` sub-blocks) is **not** in this ADR's scope. The shared concept across slices is the **set of configured PT ids**, and the registry abstracts that set.

### Out of scope for this ADR

- Tenant-aware OIDC login picker, `sso-callback/{physicalTenantId}`, per-PT session cookie scoping (Webapps PT routing).
- Per-PT `BrokerClient` and `TopologyServices` instances (Strong Isolation).
- Migration tooling from 8.9 logical-tenant authorizations to 8.10 PT authorizations.
- Persisted cluster-admin grant store, audit, dynamic PT/IdP management APIs, Hub policy distribution.
- A `tenantAuthorizer.canRead(actor, physicalTenantId)` granularity inside `/v2/cluster/topology` and `/v2/cluster/backup` (cluster-admin currently sees all PTs by definition).
- Multi-value `clusterAdmin.value` (`value: [...]`); single-value only in 8.10.

## References

- Implementation plan with code skeletons, test plan, and milestones: [`physical-tenants-identity-plan.md`](../physical-tenants-identity-plan.md)
- Umbrella epic: [camunda/product-hub#3430 — Strong Tenant Isolation in Camunda 8 OC (Self-Managed)](https://github.com/camunda/product-hub/issues/3430)
- Existing security configuration model: `security/security-core/src/main/java/io/camunda/security/configuration/`
- Existing main security wiring: `authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java`
- Existing topology controller (untouched): `zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/controller/TopologyController.java`
- Related ADRs: ADR-0001 (cluster-embedded identity), ADR-0002 (OIDC default authentication), ADR-0003 (resource-based authorization), ADR-0004 (multi-JWKS endpoints per issuer), ADR-0006 (UserInfo claim augmentation for bearer tokens).
