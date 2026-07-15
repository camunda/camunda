# Physical-Tenant Exporter Configuration: Explicit Assignment and Opt-In Type-Aware Args Merge

**DRI**: Houssain Barouni ([@houssain-barouni](https://github.com/houssain-barouni))

**Status**: Accepted (8.10)

**Purpose**: Defines which root-declared exporters apply to a physical tenant, and how a tenant's partial exporter configuration combines with the root entry.

**Audience**: Engineers working on physical-tenant configuration resolution, exporter configuration, and secondary-storage isolation in the Orchestration Cluster.

## Context

[ADR-0007](0007-physical-tenant-configuration-resolution-and-validation.md) (D5) deferred per-physical-tenant (PT) exporter configuration to [#55155](https://github.com/camunda/camunda/issues/55155) because an exporter's `args` is a raw `Map<String,Object>` with no enumerable field type, so it cannot join the typed-POJO overlay engine that gives every other property field-level inheritance. This ADR closes that deferral, answering two questions for `camunda.data.exporters` under the PT overlay: **which** root-declared exporters apply to a tenant (inheritance), and **how** a tenant's partial entry combines with the root entry (merge).

Since [#57202](https://github.com/camunda/camunda/pull/57202), per-tenant resolved exporters are live behavior — each tenant's partition group runs exporters from a per-tenant `BrokerCfg` and `ExporterRepository` — so these are not paper semantics. Two rough edges exist today: Spring's native map binding forces a tenant to restate a *complete* entry to change one arg (and can leave `className: null`), and every root exporter runs on every tenant's partitions with root args unless fully redeclared.

Two properties make the design non-trivial. The merge cannot be done generically: a key-agnostic deep-merge must canonicalize keys, but for custom exporters we do not own the config model, and `args` may hold nested maps whose keys are *user data* that must not be rewritten — distinguishing a property sub-map from a user-data sub-map requires the exporter's config `Class`, which the configuration layer does not have. And inheritance is a safety question: a generic exporter's target (an ES URL and index prefix in its `args`) is opaque to secondary-storage isolation validation, so silently inheriting every root exporter would export every tenant's records to one shared target — a breach no existing check catches.

## Decision

**D1. A tenant runs a generic exporter only if it is listed in `camunda.physical-tenants.<id>.data.exporters-assigned`.** Root-declared generic exporters form a catalog; the per-tenant `exporters-assigned` list is the complete manifest of the generic exporters that run for a tenant, spanning both catalog entries it inherits and exporters it declares itself. The key is mandatory-explicit whenever a generic exporter could apply (an empty list is valid and means "no generic exporters"): an absent key has two bad silent readings — inherit-all breaches isolation, inherit-none loses export data — so the operator must state which they mean. This mirrors the document-store and OIDC `assigned` surfaces ([ADR-0007](0007-physical-tenant-configuration-resolution-and-validation.md) D6, [ADR-0004](0004-per-physical-tenant-provider-selection-via-assigned.md)), making a tenant's export surface readable off one key. `exporters-assigned` is the single source of truth for what runs: configuring an id without assigning it, and assigning an id while giving it a `className`/`jarPath` that diverges from the catalog, are both boot errors — a config edit is not a deactivation mechanism (runtime disable is the job of the management endpoint, per D6).

**D2. The autoconfigured `camundaexporter` and `rdbms` instances are exempt by id, not by class.** They are present for every tenant whose corresponding secondary storage is configured — `camundaexporter` when ES/OS is set, `rdbms` when a relational store is — are never listed in `exporters-assigned`, and derive their config from the tenant's already-per-tenant-resolved `secondary-storage` properties. The exemption attaches to the autoconfigured *instance*: a second, explicitly declared CamundaExporter (the multi-region duplication setup) is an ordinary catalog entry, because its target args are exactly as isolation-sensitive as any ES exporter's.

**D3. Arg merging is opt-in and type-aware, via the `ExporterConfigMerger` SPI.** Where the config `Class` is reachable — next to each exporter's config class, not in the configuration layer — an exporter may register a merger that deep-merges root and tenant args (tenant wins per key). An assigned id whose class has no merger gets the tenant's args wholesale (no partial inheritance for models we cannot introspect); a custom `jarPath` exporter is not on the boot classpath at resolution time, so it falls out of merge scope automatically. Merging is thus performed only where it is provably safe, and never rewrites keys of a config model we do not own — the failure mode that sank the generic approach.

**D4. Key normalization has a single owner.** The type-aware machinery that decides which keys are properties (normalize, merge) versus user data (leave untouched) lives in one shared helper that the broker delegates to and every merger reuses, rather than being re-implemented per exporter. This prevents the broker and the mergers from drifting apart on relaxed-spelling collapse (`index-prefix` / `indexPrefix`) and Map-field key preservation.

**D5. Resolution runs as a dedicated step in the PT resolver, not in the typed-POJO overlay engine.** A single resolver step recomputes each tenant's `data.exporters` from the authoritative root catalog and the tenant's declared entries, and fails fast on the validation rules above — consistent with the one-helper-per-overlay-concern shape of the other per-PT resolutions. `data.exporters` is deliberately kept out of the ADR-0007 overlay engine: the engine's seed-from-root semantics would grant partial inheritance to custom exporters, and Spring's rebind cannot correctly deep-merge the nested `args` map.

## Alternatives considered

- **Generic recursive deep-merge in `configuration/` ([#55157](https://github.com/camunda/camunda/pull/55157)).** The original approach, abandoned unmerged — and the reason this ADR exists: without the config `Class`, key canonicalization cannot tell property keys from user-data keys in custom-exporter args, so it silently rewrites data it must not touch. Type-awareness is reachable next to each config class, hence the SPI.
- **Inherit-all, merge as the only feature.** Rejected: a root generic exporter would silently export every tenant's records to one shared target, with no validation covering generic-exporter args. The document/OIDC precedent already makes explicit selection the model.
- **Optional `assigned`, absent = inherit none.** Safer than inherit-all but an operator who forgets the key gets silent data loss instead of a silent breach. Mandatory-explicit makes both failure modes impossible for one required key.
- **Exemption by className instead of by autoconfigured id.** Rejected: a second, explicitly declared CamundaExporter (multi-region) would bypass assignment and silently duplicate every tenant into one shared second-region target — exactly what `assigned` exists to prevent.
- **Registering `data.exporters` in the ADR-0007 typed-POJO overlay engine.** Rejected: seed-from-root would grant partial inheritance to custom exporters (contradicting whole-entry ownership), and the nested raw `args` map still cannot be deep-merged by Spring's binder — a different problem than the engine solves.

## Consequences

- The generic-exporter isolation gap closes at the same place as document stores and OIDC providers; the three `assigned` surfaces are now consistent, and a tenant's export surface is readable off one key.
- The headline use case works: `exporters-assigned: [elasticsearch]` plus a one-line `index.prefix` override — share the tuning, override the target.
- The configuration bar rises again: with a non-empty catalog, every tenant must declare `exporters-assigned`, on top of ADR-0007's required per-tenant keys. Intended, documented together with those.
- `exporter-api` gains one public interface (revapi-guarded). Normalization has a single owner, so broker and merge cannot drift.
- Merging is exactly as safe as it can be — only where the config class is known; the `className: null` binder pitfall is structurally impossible for assigned ids. Classes without a merger require operators to restate full `args` per tenant.
- Assignment does **not by itself** guarantee target isolation: a tenant that assigns the root ES exporter without overriding its target still shares that target. Extending cross-tenant validation to generic-exporter targets (a sibling target-locator SPI) is a possible follow-up, not covered here.
- Assignment is gated on [#56652](https://github.com/camunda/camunda/issues/56652): until then the inherit-all interim stands, and PT deployments configured before it lands will fail loudly at boot on the newly-mandatory key — a deliberate migration for an isolation decision that was previously implicit.

## Source

- [#55155](https://github.com/camunda/camunda/issues/55155) (internal) — per-tenant exporter config overlay; primary issue. Parent [#52680](https://github.com/camunda/camunda/issues/52680), epic [#52027](https://github.com/camunda/camunda/issues/52027).
- [PR #57339](https://github.com/camunda/camunda/pull/57339) (internal) — prototype (merge step). [PR #55157](https://github.com/camunda/camunda/pull/55157) (internal) — abandoned generic deep-merge, superseded by this ADR.
- [PR #57202](https://github.com/camunda/camunda/pull/57202) (internal) — per-tenant `BrokerCfg` + `ExporterRepository` wiring; makes these semantics live and adds `PhysicalTenantExporterConfigIT`.
- [ADR-0007](0007-physical-tenant-configuration-resolution-and-validation.md) — resolution model, overlay engine, validation split (D5 deferred exporters here). [ADR-0004](0004-per-physical-tenant-provider-selection-via-assigned.md) — the `assigned` precedent.
- [#56652](https://github.com/camunda/camunda/issues/56652) (internal) — per-tenant exporter enable state; gates the assignment step (D6).
