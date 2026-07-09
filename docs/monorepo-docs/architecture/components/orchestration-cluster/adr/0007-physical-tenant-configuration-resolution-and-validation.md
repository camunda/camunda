# ADR-0007: Physical-Tenant Configuration Resolution — Shared-Config-with-Override Model and the Per-PT / Cross-PT Validation Split

## Status

Accepted

## Deciders

- Proposed by: Houssain Barouni ([@houssain-barouni](https://github.com/houssain-barouni))
- Deepthi Akkoorath ([@deepthidevaki](https://github.com/deepthidevaki))

_Additional deciders may be recorded as more of the team reviews._

## Context

Issue [#52680](https://github.com/camunda/camunda/issues/52680) (epic
[#52027](https://github.com/camunda/camunda/issues/52027), Physical Tenants) introduces
per-physical-tenant (PT) configuration for the Orchestration Cluster. Operators declare global
defaults once under `camunda.*` and override selected properties per tenant under
`camunda.physical-tenants.<id>.*`. Each tenant must resolve to a *fully-resolved* `Camunda`
configuration that consumers (secondary-storage clients, document services, per-tenant security
chains) can read exactly as they read the root configuration today.

This ADR records the decisions taken for the **configuration-resolution layer** — everything that
lives in the `configuration/` module around `PhysicalTenantResolver`. It is the counterpart of the
runtime/security decisions already captured in the sibling ADRs in this folder:
[ADR-0003](0003-physical-tenant-request-scoping-via-pre-security-filter.md) (request scoping),
[ADR-0004](0004-per-physical-tenant-provider-selection-via-assigned.md) (provider selection),
[ADR-0005](0005-physical-tenant-routing-of-authorization-reads.md) (authorization-read routing) and
ADR-0006 (gRPC authentication). Those consume the resolved per-tenant `Camunda`; this ADR is about
how that `Camunda` is produced and validated.

The problem breaks into three parts:

1. **A resolution model** — how a per-tenant `Camunda` is built from the root config plus the tenant
   overlay, and what "override" means (which properties inherit, which replace, which deep-merge).
2. **A validation architecture** — the physical-tenant model is unsafe by default in specific ways
   (two tenants silently writing into the same secondary storage or document store; a tenant
   overriding a cluster-wide property that must be uniform; a tenant silently inheriting the root's
   identity seed and so accepting foreign tokens). These must fail fast at boot.
3. **A pitfall in Spring Boot's `MapBinder`** — for a `Map`-typed property, a partial per-tenant
   override of a map *entry* silently drops the root entry's untouched fields.

Two facts about the runtime shape the decisions. First, all validation runs *inside* `configuration/`
where the resolved `Camunda` objects and all their getters are already available. Second, detection
of storage collisions can only ever be **best-effort and static**: the config layer compares declared
coordinates, not live connections, so DNS aliases and distinct URLs resolving to the same cluster are
out of reach.

Related work: PR [#52529](https://github.com/camunda/camunda/pull/52529) (resolver),
PR [#54700](https://github.com/camunda/camunda/pull/54700) (cross-tenant validation),
PR [#55132](https://github.com/camunda/camunda/pull/55132) (tenant-id length); sub-issues
[#54366](https://github.com/camunda/camunda/issues/54366) (document-store isolation),
[#54730](https://github.com/camunda/camunda/issues/54730) (OIDC `assigned`),
[#55155](https://github.com/camunda/camunda/issues/55155) (per-tenant exporters).

## Decision

### 1. Resolution model: shared config + per-tenant override via a two-bind

`PhysicalTenantResolver.of(Environment, Camunda)` eagerly resolves one `Camunda` per discovered
physical tenant. Each tenant `Camunda` is produced by:

1. creating a fresh `Camunda` instance;
2. binding `camunda.*` into it (so every non-overridden value matches the root resolution, and the
   legacy-property fallback in the getters — via `UnifiedConfigurationHelper` — keeps working
   unchanged);
3. binding `camunda.physical-tenants.<id>.*` on top (so tenant-specific keys override the seeded
   values).

This "shared defaults, override per tenant" model is the general rule for **every** property. Spring
Binder's overlay semantics govern the merge for scalars and nested non-map beans (e.g. `cluster`,
whose sibling fields survive a partial override). The resolver is the single place per-tenant config
is computed; consumers only ever read an already-resolved `Camunda` through
`PhysicalTenantResolver.mapValues(...)` / `forPhysicalTenant(...)` and never re-merge.

**Default-tenant synthesis.** If no `default` tenant is declared under `camunda.physical-tenants.*`,
the resolver synthesizes one whose value **is the root `Camunda`** — so consumers can always address
the root configuration as a tenant, and the synthesized `default` participates in cross-PT validation
like any other tenant. An explicit `default` declaration is honored as-is.

**Tenant-id constraints.** Ids are discovered by pure key inspection and must be lowercase
alphanumeric (`[a-z0-9]+`, no dashes) and at most 64 characters. Forbidding dashes keeps YAML
(`tenant-a`) and environment-variable (`CAMUNDA_PHYSICALTENANTS_TENANTA_*`) forms from resolving to
two different ids.

### 2. The `MapBinder` defect and the generic overlay engine (typed-POJO maps)

Spring Boot's `MapBinder` merges `Map`-typed properties at the **entry level** (disjoint keys
survive), but for a key the tenant overlay *touches*, it constructs a **fresh** value POJO from only
the tenant's sub-keys instead of seeding from the existing root entry. A tenant overriding one field
of a shared map entry therefore silently loses every root field it did not restate.

This bites named OIDC providers (`Map<String, OidcConfiguration>`) and document stores
(`Map<String, AwsStore>` and its sibling provider maps). It is repaired by **one generic,
registry-driven overlay engine** (`PhysicalTenantMapOverlay` + `MapOverlaySpec` +
`PhysicalTenantMapOverlays`) that runs after the two-bind and recomputes each registered map with a
**snapshot-then-rebind** algorithm: snapshot the original root entries, then for every id present on
both root and tenant, re-bind the tenant overlay onto the original root POJO so omitted fields
inherit root. Adding a per-tenant typed-POJO map means adding one `MapOverlaySpec` to the registry —
not new merge code. The registry (currently: document stores, OIDC providers) is also the single
source of truth the override golden test reads to guard the per-field surface.

### 3. Exporter args (`Map<String, Object>`) — deferred to #55155

Exporter `args` at `camunda.data.exporters.<id>.args` is a raw `Map<String, Object>` whose value
type cannot be field-enumerated, so it stays **out** of the typed-POJO overlay engine. Per-tenant
exporter handling is **deferred to [#55155](https://github.com/camunda/camunda/issues/55155) and not
sorted out yet**. Current state: a partial tenant override **replaces** the whole `args` map (root
`className` and sibling args are dropped), and no per-tenant exporter consumer exists, so per-tenant
resolved exporter args are effectively not consumed.

### 4. Validation architecture: per-PT vs cross-PT

Validations are split by **what they read**, and run in a fixed order inside
`PhysicalTenantResolver.of()`: first the per-PT key-inspection checks (before/independent of
binding), then the per-tenant two-bind + overlay, then the cross-PT checks over the resolved map.

**Per-PT validations** — operate on a single tenant's *declared* keys (pure key inspection over the
`Environment`, with a targeted `Binder` read only where a value genuinely decides applicability).
They fail fast before or independently of the full resolution:

- **Override policy** (`PhysicalTenantOverridePolicyValidation`) — a **deny-list** of cluster-wide
  and identity-security properties that may **not** be overridden per tenant (see §5).
- **Required override** (`PhysicalTenantRequiredOverrideValidation`) — every explicitly-declared
  non-`default` tenant **must** declare its own `security.initialization.*` block (unless
  authorization is disabled for it). The identity seed must not be inherited from the root, or every
  tenant would get the same admin user/authorizations, defeating isolation.
- **OIDC provider selection** (`PhysicalTenantAssignedProvidersValidation`) — under the OIDC
  method, every non-`default` tenant must declare a non-empty `security.authentication.providers.assigned`
  of known provider ids; the `default` tenant may omit it (implicit full set). Configuration-layer
  counterpart of the `authentication`-module narrowing (see
  [ADR-0004](0004-per-physical-tenant-provider-selection-via-assigned.md), #54730).
- **Document store selection** (`PhysicalTenantDocumentAssignedValidation`) — every non-`default`
  tenant with a non-empty store catalog must declare a non-empty `document.assigned` of known store
  ids; and `camunda.document.assigned` is rejected at the **root** level (it is meaningful only per
  tenant).
- **Tenant-id format/length** — as in §1.

**Cross-PT validations** — operate on the *resolved* `Map<String, Camunda>` (the synthesized
`default` included) after all tenants are bound. They express relational, uniqueness/group-by rules
via a single SPI, `CrossTenantValidation.validate(Map<String, Camunda>)` (a collection signature,
not pairwise, so it can group collisions into one message and name the tenants). There is
**no opt-out toggle** — hard isolation is the point. The registered rules:

- **Secondary-storage isolation** (`SecondaryStorageIsolationValidation`) — group tenants by
  `StorageIdentity` `(type, normalized-connection, namespace)` and reject any group with more than
  one tenant. This closes the headline risk: two tenants left at the shared default storage
  (both with the default empty index prefix / null table prefix) resolve to the same identity and
  fail at boot. The identity is deliberately **minimal and location-only** — adding cluster name or
  credentials would let an incidental difference mask a real collision. Same URL + different
  index/table prefix is the explicitly-allowed "shared cluster, distinct namespace" setup.
- **Secondary-storage type homogeneity** (`SecondaryStorageTypeHomogeneityValidation`) — all tenants
  must resolve to the same *category* of secondary storage. Elasticsearch and OpenSearch are one
  category and may be mixed with each other; `rdbms` is its own category and `none` (no secondary
  storage) is its own — neither may be combined with any other type. So a deployment cannot have one
  tenant on Elasticsearch and another on RDBMS, or one tenant with `none` and another with a
  configured store.
- **Document-store isolation** (`DocumentStoreIsolationValidation`) — reject two tenants resolving to
  the same `DocumentStoreLocation` (provider-dependent, location-only coordinates: AWS
  `bucketName + bucketPath`; GCP `bucketName + prefix`; Azure `containerName + containerPath +
  endpoint`; `local` `path`; in-memory excluded — ephemeral, cannot collide). Same philosophy as
  `StorageIdentity`; per #54366, hard-fail with no opt-out.

### 5. Override policy: deny-list, not allow-list

The physical-tenant model is "override anything except cluster identity and uniform security policy",
so an allow-list would be enormous and perpetually out of date. The policy is therefore a **deny-list**
of non-overridable properties, enforced by pure key inspection (ancestor matching, no value
comparison). It covers the cluster-topology/identity subtrees (`cluster.*` minus the carve-outs
`partition-count` / `replication-factor`), process-wide `system.*` (minus `clock-controlled`), the
single-per-installation `license.key`, and the **identity-security** subtrees of `security.*` that
must apply uniformly (authentication method, unprotected-api, CSRF, HTTP headers, session,
multi-tenancy, cluster TLS, and the forward-declared `cluster-admin` from #54898). Notably,
identity-security *policy* is denied, but provider *content* (`security.authentication.oidc` /
`providers.oidc`) and the per-tenant `security.initialization` block remain overridable — indeed the
latter is *required* per tenant (§4).

## Considered alternatives

- **Generic declarative validation engine** — express cross-PT rules by annotating individual config
  fields (e.g. `@CrossTenantUnique` on a field) and have a framework walk the config tree
  reflectively to enforce them. Rejected: cross-PT rules are *relational* — they constrain a set of
  tenants together ("these N tenants' storage locations must all be distinct"), not one field in
  isolation. A per-field annotation cannot express "distinct across the whole set", and building the
  framework is a large upfront cost for the handful of rules that exist. Hand-written validators over
  the resolved map express the relational rules directly.
- **Pairwise cross-PT SPI** — a validator signature `validate(Camunda a, Camunda b)` invoked on every
  pair of tenants. Rejected: the rules are uniqueness / group-by, so a pairwise callback produces
  O(n²) redundant collision reports (every pair in a colliding group reported separately) and cannot
  name the whole colliding group in one message. The chosen collection signature
  `validate(Map<String, Camunda>)` can still express pairwise checks internally, so it is the strict
  superset.
- **Storage identity that is connection-only, or broad (with credentials).** The storage identity
  decides when two tenants "point at the same place". A connection-*only* identity (URL, ignoring the
  index/table prefix) would reject the legitimate "shared cluster, distinct prefix per tenant" setup.
  A *broad* identity (adding cluster name or credentials) makes real collisions *harder* to detect,
  because an incidental difference in a non-location field would hide two tenants that actually write
  to the same place. The chosen minimal location-only tuple `(type, connection, namespace)` is the
  balance between the two.
- **Allow-list of overridable properties.** Rejected — enormous and perpetually stale versus the
  deny-list.
- **Pluggable `MapBinder` / generic `BindHandler` to fix the map-override behavior.** The custom
  `MapBinder` is impossible (package-private in Spring Boot 4.0.7). A `BindHandler` was spiked and
  proven correct via `onStart`-replace, but rejected on cost/risk: ~20% more code, reliance on
  undocumented per-entry bind semantics, and version-pinned characterization tests.
  Snapshot-then-rebind is a verbatim extraction of shipping code using only the public `Binder` API.
- **Raw `deepMerge` for typed-POJO maps as well** — convert each typed config (OIDC provider,
  document store) to a `Map<String,Object>`, deep-merge root and tenant, then rebind via Spring
  `Binder`. Rejected: the round-trip silently discards Spring's type-safe binding and
  `@ConfigurationProperties` validation. Exporter args use raw `deepMerge` without this penalty
  because their value type is *already* `Map<String,Object>` — there is no typed POJO to lose.

## Consequences

- The two headline isolation risks are closed at boot: two tenants sharing a secondary storage or a
  document store fail fast with a message naming the tenants and the shared identity.
- Every non-`default` tenant must **spell out** its storage namespace, its `document.assigned` (when a
  catalog exists), its OIDC `providers.assigned` (under OIDC), and its own `security.initialization`
  block. This raises the configuration bar for operators adopting physical tenants — intended, and to
  be documented.
- Collision detection is **best-effort static**: DNS aliases, distinct URLs resolving to one cluster,
  partially-overlapping multi-URL lists, and (for Azure) same-container-different-account are **not**
  flagged. Accepted limitations, not bugs.
- Adding a per-tenant typed-POJO map is one registry entry; adding a cross-PT rule is one
  `CrossTenantValidation` class — the SPI does not grow. Adding a document provider forces a
  `DocumentStoreLocation` factory (a compile-time obligation).
- Per-tenant **exporter** args are resolved but effectively unconsumed and still use native REPLACE;
  the deep-merge and a per-tenant exporter consumer are deferred to
  [#55155](https://github.com/camunda/camunda/issues/55155).
- The merge itself never deletes: a tenant can override or add entries, not remove one the root
  declares (the `assigned` lists narrow the *visible* set, but do not delete from the merge). Whether
  per-tenant disable of an inherited entry is needed is **not decided yet**; it would be a separate
  follow-up if it becomes a requirement.
