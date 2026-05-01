# ADR-0007: Physical Tenants — Identity Support

## Status

Proposed

## Context

Camunda 8.9 multi-tenancy is logical: all tenants share one engine and one authorization scope, so a user's role in any logical tenant applies cluster-wide. **Physical Tenants (PT)** introduce a new boundary for the 8.10 release where each tenant is an isolated execution unit within a single Orchestration Cluster — separate data, independent backup/restore, no runtime interference. The umbrella epic is [camunda/product-hub#3430 — *Strong Tenant Isolation in Camunda 8 OC (Self-Managed)*](https://github.com/camunda/product-hub/issues/3430).

This ADR is scoped narrowly to the **identity (authN/authZ) slice** of that epic — the line item *"Per-Physical-Tenant authentication and authorization (incl. IDP)"* under *Scope – Milestone 1 → Security and observability*. Sibling slices of the same epic — primary/secondary storage isolation (Raft groups per PT), per-PT backup/restore, webapp PT routing (login picker, `sso-callback/{physicalTenantId}`, per-PT session cookie), gRPC PT header parity, Connectors multi-PT, observability — are referenced where their seams matter but are **not** delivered here.

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

Per-PT configuration nests under the existing security tree as `camunda.security.physical-tenants.{physicalTenantId}` — a `Map<String, PhysicalTenantConfiguration>`. The map key **is** the physical tenant id; no `id` field appears inside the bound type. IdP assignments live inside each PT entry as `idps: [...]` rather than in a separate top-level `engine-idp-assignments` mapping.

> **Coordination note.** Other slices of epic #3430 will need their own per-PT configuration trees for non-security concerns: per-PT secondary storage (search/secondary-storage slice — see the placeholder javadoc reference to `camunda.physical-tenants` in `search/search-client-connect/src/main/java/io/camunda/search/connect/tenant/TenantConnectConfigResolver.java`), per-PT backup repositories, per-PT BrokerClient, etc. Those are not security configuration and intentionally do not nest under `camunda.security`. The shared concept across slices is the **set of configured PT ids**; this ADR does not claim ownership of that set, only of its security-scoped attributes. A future cross-slice agreement on the canonical top-level location for non-security PT properties is an open question — see Consequences.

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
    physical-tenants:
      default:
        name: Default
        idps: [default]
        initialization: { roles: [...], groups: [...], authorizations: [...] }
      risk-production:
        name: Risk Team Production
        idps: [default, provider-a]
        initialization: { ... }
```

Three substantive choices in this shape:

1. **Single tree under `camunda.security`** for the identity slice. Two earlier proposals were rejected: a top-level `camunda.physical-tenants` (collides with the pre-existing javadoc placeholder in `TenantConnectConfigResolver` and would require coordinating with non-security slices before any of them ship), and a sibling `camunda.identity.engine-idp-assignments` mapping (cross-tree, easy to drift). Nesting under `camunda.security` keeps the existing `CamundaSecurityProperties extends SecurityConfiguration` binder authoritative — no new `@EnableConfigurationProperties` registration — and collapses the two security-related trees that were previously cross-referencing (PT definitions and IdP assignments) into one self-contained one, removing a class of "PT defined here, not assigned over there" misconfigurations.
2. **`Map`, not `List`.** A list with an `id` field allows duplicate ids, requires a separate uniqueness check, and bloats the YAML with `- id:` prefixes. A map keyed by id makes duplicates structurally impossible, lets cross-references use direct lookup instead of stream-and-filter, and reads more like a registry than a sequence — which is the actual semantic.
3. **`InitializationConfiguration` per PT, not `SecurityConfiguration` per PT.** IdPs and authentication method are cluster-level by requirement; a per-PT `authentication` block would either be silently ignored or open a footgun (PT declares OIDC while cluster runs BASIC). Each PT entry exposes only `name`, `idps`, and `initialization`. Operators get full control of *who* and *what* per PT while *how to authenticate* stays cluster-uniform. The existing `ConfiguredUser` / `ConfiguredRole` / `ConfiguredAuthorization` / `ConfiguredGroup` / `ConfiguredTenant` / `ConfiguredMappingRule` types are reused unchanged.

### Registry as the single source of truth

A new `PhysicalTenantRegistry` bean (interface in `security/security-core/.../tenants/`, default implementation immutable after construction) is the single lookup point every other PT-aware code path consults. It exposes forward (`findById`) and reverse (`tenantsForIdp`) lookups, the reserved-ID predicate, and a `legacyMode` flag.

When `camunda.security.physical-tenants` is empty, the registry synthesizes one `default` entry whose `initialization` **references** (not copies) the cluster-level `camunda.security.initialization`. This is the backwards-compat path: existing 8.9 configs see no behavioral change, and there is **one code path** through the system rather than `if (legacyMode) ... else ...` branching in business logic. The mode collapses into the registry abstraction.

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

### Per-PT authorization: in-memory, read-on-request

The PT's `InitializationConfiguration.authorizations` block is read on each request and translated to `GrantedAuthority`s by the existing converter pipeline. We do **not** seed it into a per-PT secondary-storage authorization store.

This decision resolves a contradiction in the source documents. The Strong Isolation epic describes per-PT secondary storage; the Identity epic states *"in-memory only — no users, identities come from the IdP at login."* Seeding `InitializationConfiguration` per PT into secondary storage would create a runtime/config divergence: every IdP claim change could drift from the persisted snapshot, and auditors would see two sources of truth. Reading on request keeps the YAML authoritative and avoids the divergence. The existing cluster-level seeder (`camunda.security.initialization`) keeps running unchanged — it owns the cluster-admin BASIC fallback users, which are the only users actually persisted.

### Cluster-admin: claim-based, stateless

`ClusterAdminClaimAuthorizer` is a `Converter<Jwt, AbstractAuthenticationToken>` that grants `ROLE_CLUSTER_ADMIN` when the JWT issuer is in the configured `clusterAdmin.providers` list **and** the JWT carries the configured claim with the configured value. There is no persisted grant table, no audit trail, no token revocation primitive — those were explicitly out of scope per the issue and are documented as known gaps (see Consequences below). BASIC fallback covers break-glass via the cluster-level `initialization.users`.

### Cluster-scoped controllers — uniform pattern

All cluster-wide endpoints follow the **same shape** behind the cluster-admin chain: a thin controller that fans out per PT (using `PhysicalTenantRegistry.allTenantIds()`), aggregates the per-PT result into a `{ physicalTenantId → ResultOrError }` map, and surfaces per-PT failure as data rather than as a 5xx. The chain matcher (`/v2/cluster/**`) covers every such endpoint uniformly — no per-endpoint security wiring is needed.

**`/v2/cluster/topology`.** The existing `TopologyController` (`zeebe/gateway-rest/.../controller/TopologyController.java`) is **not modified** — it continues to serve `/v1/topology` and `/v2/topology` exactly as today, satisfying both 8.9 callers and the default-PT case. A new `ClusterTopologyController` mounts `/v2/cluster/topology` behind the cluster-admin chain. Response shape is `{ physicalTenantId → TopologyOrError }`. In legacy mode (no PTs configured) the response is `{ "default": <topology> }`, so the shape is **stable across modes** and tooling does not branch on whether PTs are configured.

**`/v2/cluster/backup`.** Follows the identical pattern. The cluster-admin filter chain (`securityMatcher: /v2/cluster/**`) already covers it — no additional security wiring is needed in this slice. The backup controller (`ClusterBackupController` or equivalent name to be agreed with the backup/restore sibling slice) is delivered by that sibling slice along with the actual fan-out logic to per-PT backup repositories. The auth contract this ADR establishes — claim-based cluster admin, in-process aggregation, per-PT failure as data — is what the backup slice will inherit unchanged. The same applies to any future `/v2/cluster/*` endpoint (`/restore`, `/health`, `/list-physical-tenants` if added later).

For 8.10, every PT shares the in-process `TopologyServices` (Strong Isolation hasn't shipped). When per-PT broker clients arrive, only the topology controller's per-PT delegation line changes — the response shape, the chain it sits behind, and the authorization model are forward-compatible. This is the only place in the identity slice where Strong Isolation will require a follow-up change, by design.

### Login and logout: deliberately unchanged

This slice **does not modify** the browser login or logout flow. `/login`, the OIDC picker, `/sso-callback`, RP-initiated logout, and the global `camunda-session` cookie all continue to behave exactly as they do in 8.9. The PT chain (`physicalTenantApiSecurityFilterChain`) is configured with `oauth2ResourceServer().jwt()` and **no** `oauth2Login`, **no** `formLogin`, **no** `httpBasic` — it is bearer-token only.

The result is that two surfaces coexist without overlap:

- **Bearer-token clients** (Java client, Connectors runtime, M2M services, gRPC once parity ships) hit `/v2/physical-tenants/{physicalTenantId}/...` directly with an IdP-issued access token. No login flow involved; the PT chain authenticates them via the per-PT converter and rejects tokens whose `iss` is not in `pt.idps`.
- **Browser users** continue to log in through the unchanged cluster-uniform `/login` page, receive a session cookie, and consume **webapps + legacy `/v2/...` endpoints**. They cannot directly call PT-scoped endpoints from a session — those calls return 401 because no `Authorization: Bearer …` header is present. This is intentional: webapps in 8.10 remain cluster-uniform and have no UX path to PT-scoped APIs yet.
- **Cluster admin** is stateless on both legs (claim-based JWT or HTTP BASIC against `camunda.security.initialization.users`). The browser login flow is not on this path either.

The cost is a UX gap: the cluster-level login picker shows every IdP, even those not assigned to the PT a user works in. This is **not** a security gap — the PT chain rejects mismatched issuers at request time (see Decision: per-PT authentication). Closing the UX gap is the *Webapps PT routing* sibling slice's job: tenant-aware login picker at `/login/{physicalTenantId}`, `/sso-callback/{physicalTenantId}` (with the documented Microsoft Entra wildcard-redirect-URI constraint), per-PT session cookie scoping, per-PT logout, and a chosen approach for session→bearer bridging on PT API calls (webapp-side token forwarding, or an `oauth2Login` extension on the PT chain).

The wiring this slice ships — `PhysicalTenantRegistry`, `PhysicalTenantJwtAuthenticationConverterFactory`, `PhysicalTenantContext` — is **forward-reused** by the webapps slice. Nothing here needs to be unwound.

### What is deliberately not in this slice

- **Per-PT login chains**, tenant-aware OIDC login picker, `/sso-callback/{physicalTenantId}`, per-PT `OAuth2AuthorizedClientRepository`, per-PT session cookie scoping. The PoC referenced in the issue covers these for a future Webapps PT routing slice. Subclassing Spring's `DefaultLoginPageGeneratingFilter` (an internal that has changed shape across 5.x → 6.x) is a fragility we accept paying for once, in that follow-up, after the API-side semantics are locked.
- **gRPC `Camunda-Physical-Tenant` header handling.** REST resolves PT from the path; gRPC resolves from this canonical metadata header (fixed by the epic body). Both will share `PhysicalTenantRegistry` and `PhysicalTenantJwtAuthenticationConverterFactory` so the two protocols converge on a single source of truth — but the gRPC interceptor itself is owned by a sibling slice of the same epic.
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
- **gRPC and REST will temporarily diverge.** REST gets per-PT handling in this slice; gRPC keeps the cluster-uniform behavior until the parity slice lands. Java client users who mix REST and gRPC against the same cluster will see different authorization semantics across protocols during that window. Both slices share `PhysicalTenantRegistry` to bound the divergence to dispatch logic only.
- **The "skip everything else" instruction was scoped narrowly.** CSRF, security headers, and the request firewall remain active on the PT chain. An interpretation that strips them would simplify the chain definition by ~10 lines but introduce a defense-in-depth regression that this ADR rejects.
- **Per-PT topology fan-out shares the same `TopologyServices` in 8.10.** Today, every PT in `/v2/cluster/topology` returns the same topology snapshot. The shape is forward-compatible, but the values are not yet per-PT. This is the price of decoupling identity work from Strong Isolation; it goes away when the broker-client epic lands.
- **Two PT-related config trees may coexist short-term.** This slice owns `camunda.security.physical-tenants.{physicalTenantId}` for identity attributes. Sibling slices need to attach non-security per-PT properties (secondary storage, backup repository, broker client) and will likely choose a different top-level location — the placeholder javadoc in `TenantConnectConfigResolver` already references `camunda.physical-tenants` for that purpose. A future cross-slice agreement on a unified top-level tree (e.g. `camunda.physical-tenants` for non-security and a sibling `camunda.security.physical-tenants` for security, or a single tree with `security:` and `secondary-storage:` sub-blocks) is **not** in this ADR's scope. The shared concept across slices is the **set of configured PT ids**, and the registry abstracts that set.

### Out of scope for this ADR

- Tenant-aware OIDC login picker, `sso-callback/{physicalTenantId}`, per-PT session cookie scoping (Webapps PT routing).
- gRPC tenant header (`Camunda-Physical-Tenant`) handling.
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
