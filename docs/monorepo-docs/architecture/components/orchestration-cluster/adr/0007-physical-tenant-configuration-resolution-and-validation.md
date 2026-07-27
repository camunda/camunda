# Physical-Tenant Configuration: Shared-Config-with-Override Resolution and the Per-PT / Cross-PT Validation Split

**DRI**: Houssain Barouni ([@houssain-barouni](https://github.com/houssain-barouni))

**Status**: Accepted (8.10)

**Purpose**: Defines how a fully-resolved per-physical-tenant `Camunda` configuration is produced from the root config plus a per-tenant overlay, and how it is validated at boot.

**Audience**: Engineers working on physical-tenant configuration resolution, secondary-storage and document-store isolation, and the Orchestration Cluster `configuration/` layer.

## Context

Physical Tenants (epic [#52027](https://github.com/camunda/camunda/issues/52027), issue [#52680](https://github.com/camunda/camunda/issues/52680)) introduces per-physical-tenant (PT) configuration for the Orchestration Cluster. Operators declare global defaults once under `camunda.*` and override selected properties per tenant under `camunda.physical-tenants.<id>.*`. Each tenant must resolve to a *fully-resolved* `Camunda` configuration that consumers — secondary-storage clients, document services, per-tenant security chains — read exactly as they read the root configuration today.

The model is unsafe by default in specific ways: two tenants can silently write into the same secondary storage or document store; a tenant can override a property that must be cluster-uniform; a tenant can inherit the root's identity seed and so accept foreign tokens. These must fail fast at boot. This ADR covers how a per-tenant `Camunda` is *produced and validated* in the `configuration/` layer; its runtime and security *consumers* are covered by the sibling ADRs [ADR-0003](0003-physical-tenant-request-scoping-via-pre-security-filter.md), [ADR-0004](0004-per-physical-tenant-provider-selection-via-assigned.md), [ADR-0005](0005-physical-tenant-routing-of-authorization-reads.md), and [ADR-0006](0006-physical-tenant-scoped-grpc-authentication.md).

Two constraints bound the design. All validation runs inside `configuration/`, where the resolved `Camunda` objects are already available. And storage-collision detection can only ever be best-effort and static: the config layer compares declared coordinates, not live connections, so DNS aliases and distinct URLs resolving to the same cluster are out of reach.

## Decision

**D1. Shared config plus per-tenant override.** Each tenant's `Camunda` is produced by binding the root `camunda.*` into a fresh instance, then binding the tenant's `camunda.physical-tenants.<id>.*` overlay on top. This "shared defaults, override per tenant" rule applies to every property; standard binder overlay semantics govern the merge for scalars and nested beans. Per-tenant config is computed once in a single resolver, and consumers only ever read an already-resolved `Camunda` — they never re-merge.

**D2. The `default` tenant is synthesized from the root config.** When no `default` tenant is declared, the resolver synthesizes one whose value *is* the root `Camunda`, so the root is always addressable as a tenant and participates in cross-PT validation like any other. An explicit `default` declaration is honored as-is.

**D3. Tenant ids are lowercase-alphanumeric, at most 64 characters.** Ids are discovered by key inspection and constrained to `[a-z0-9]+` (no dashes). This avoids a mismatch where a YAML key containing punctuation and its environment-variable form (which typically drops punctuation) would resolve to different ids.

**D4. Partial overrides of a typed map entry inherit the untouched root fields.** Spring's map binding replaces a touched entry with a fresh value built only from the tenant's sub-keys, silently dropping root fields the tenant did not restate (this affects named OIDC providers and document stores). The resolver instead re-applies each touched entry's overlay onto the original root entry, so omitted fields inherit. A registry of the maps that need this behavior is the single source of truth, so adding a per-tenant typed map is a registry entry, not new merge code.

**D5. Raw `Map<String,Object>` exporter args are excluded from the typed overlay.** Exporter `args` have no enumerable field type, so they cannot participate in the field-level inheritance of D4. Per-tenant exporter handling is deferred to [#55155](https://github.com/camunda/camunda/issues/55155) and decided in [ADR-0008](0008-physical-tenant-exporter-assignment-and-args-merge.md); until then a partial override replaces the whole `args` map and no per-tenant exporter consumer exists.

**D6. Validation is split by what it reads: per-PT versus cross-PT.** Per-PT validations operate on a single tenant's declared keys (override policy, required per-tenant identity seed, OIDC provider selection, document-store selection, id format) and fail fast independently of full resolution. Cross-PT validations operate on the resolved set of all tenants (the synthesized `default` included) and express relational uniqueness rules through a single collection-signature SPI — so a colliding group is reported in one message naming its tenants. Cross-PT isolation has **no opt-out**: secondary-storage isolation (tenants grouped by a minimal, location-only storage identity), secondary-storage type homogeneity, and document-store isolation (provider-dependent, location-only coordinates) all hard-fail at boot.

**D7. Non-overridable properties are a deny-list, not an allow-list.** The model is "override anything except cluster identity and uniform security policy", so an allow-list would be enormous and perpetually stale. A deny-list, enforced by key inspection, covers the cluster-topology/identity subtrees, process-wide system settings, the single-per-installation license key, and the identity-security policy that must apply uniformly — while leaving provider *content* and the per-tenant identity-seed block overridable (the latter is in fact required per tenant, per D6).

## Alternatives considered

- **Per-field declarative validation (annotate a field `@CrossTenantUnique` and walk the tree).** Rejected: cross-PT rules are relational — they constrain a *set* of tenants together ("these N storage locations must all be distinct"), which a per-field annotation cannot express, and the framework is a large cost for a handful of rules. Hand-written validators over the resolved set express the relational rules directly.
- **Allow-list of overridable properties.** Rejected as the inverse of D7 — enormous and perpetually stale against a model whose default is "override almost anything".

## Consequences

- The two headline isolation risks close at boot: two tenants sharing a secondary storage or a document store fail fast with a message naming the tenants and the shared identity.
- Every non-`default` tenant must spell out its storage namespace, its assigned document stores and OIDC providers (where applicable), and its own identity-seed block. This raises the configuration bar for operators adopting physical tenants — intended, and to be documented.
- Collision detection is best-effort static: DNS aliases, distinct URLs resolving to one cluster, partially-overlapping URL lists, and same-container-different-account are not flagged. Accepted limitations, not bugs.
- Adding a per-tenant typed map is one registry entry; adding a cross-PT rule is one validator — the SPI does not grow.
- Per-tenant exporter args are resolved but effectively unconsumed and still replace-on-override until [#55155](https://github.com/camunda/camunda/issues/55155); [ADR-0008](0008-physical-tenant-exporter-assignment-and-args-merge.md) decides the per-tenant exporter assignment and args-merge model.
- The merge itself never deletes: a tenant can override or add entries, but not remove one the root declares. The exception is maps that carry an `assigned` selector (document stores, OIDC providers): a tenant narrows its effective set to the assigned keys, which drops the unassigned root entries from that tenant's resolved configuration. Outside those selector-backed maps, per-tenant disable of an inherited entry is not possible and would be a follow-up if it becomes a requirement.

## Source

- [Strong Isolation via Physical Tenants: Implementation planning — PT configuration resolution](https://docs.google.com/document/d/1hLdPXbKNZvijRxwFn-N1QUW8pAMXX4vZtrrnpAr9TGM/edit?tab=t.jread4etvuh3#heading=h.l89qqep6cx5i) (internal) — design doc.
- [Strong Isolation via Physical Tenants: Implementation planning — per-PT override rules for multi-provider maps (document store, OIDC, secret store)](https://docs.google.com/document/d/1hLdPXbKNZvijRxwFn-N1QUW8pAMXX4vZtrrnpAr9TGM/edit?tab=t.tl54tsmcckua#heading=h.h42i1w443l5o) (internal) — design doc.
- [Cross-tenant config validation #52680](https://github.com/camunda/camunda/issues/52680) (internal) — primary issue.
- [Physical Tenants epic #52027](https://github.com/camunda/camunda/issues/52027) (internal).
- Related sub-issues: [#54366](https://github.com/camunda/camunda/issues/54366) (document-store isolation), [#54730](https://github.com/camunda/camunda/issues/54730) (OIDC `assigned`), [#55155](https://github.com/camunda/camunda/issues/55155) (per-tenant exporters).
- Sibling ADRs: [ADR-0003](0003-physical-tenant-request-scoping-via-pre-security-filter.md), [ADR-0004](0004-per-physical-tenant-provider-selection-via-assigned.md), [ADR-0005](0005-physical-tenant-routing-of-authorization-reads.md), [ADR-0006](0006-physical-tenant-scoped-grpc-authentication.md), [ADR-0008](0008-physical-tenant-exporter-assignment-and-args-merge.md) (per-tenant exporter assignment and args merge).
