# ADR-0008: Physical-Tenant Exporter Configuration — Explicit Assignment and Opt-In Type-Aware Args Merge

## Status

Proposed

## Deciders

- Proposed by: Houssain Barouni ([@houssain-barouni](https://github.com/houssain-barouni))

_Additional deciders may be recorded as more of the team reviews._

## Context

[ADR-0007](0007-physical-tenant-configuration-resolution-and-validation.md) §3 deferred per-tenant
exporter configuration to [#55155](https://github.com/camunda/camunda/issues/55155). This ADR closes
that deferral. It decides, for `camunda.data.exporters` under the physical-tenant (PT) overlay:

1. **Which** root-declared exporters apply to a tenant at all (the inheritance question).
2. **How** a tenant's partial exporter config combines with the root entry (the merge question).

Current state on `main`: since PR [#57202](https://github.com/camunda/camunda/pull/57202)
(fixing [#56517](https://github.com/camunda/camunda/issues/56517)), per-tenant resolved exporters
are **consumed by the broker**: `BrokerBasedPropertiesOverride.convert(Camunda)` produces a
per-tenant `BrokerCfg`, `PhysicalTenantContext` carries a per-tenant `ExporterRepository`, and each
tenant's partition group runs exporters with that tenant's resolved configuration. The resolution
semantics this ADR decides are therefore **live behavior**, with two rough edges today:

- Exporter entries follow Spring Binder's native map semantics — a tenant that touches
  `data.exporters.<id>.*` gets a **fresh** entry built from only its own keys (root `className` and
  untouched args are dropped), so a per-tenant declaration must restate the *complete* entry
  (`PhysicalTenantExporterConfigIT` pins exactly this).
- Every root exporter is present in every tenant's resolved `Camunda` and **runs on every tenant's
  partitions with root args** unless fully redeclared — the isolation hazard described below is no
  longer latent.

One wiring gap remains: the initial dynamic cluster configuration — which decides which exporter
ids are *enabled* on a partition — is still generated from the root `BrokerCfg` only
([#56652](https://github.com/camunda/camunda/issues/56652), coordinated with the Distributed
Systems dynamic-cluster-configuration effort). A tenant-only exporter is resolved but does not run
yet, and — critically for this ADR — an *enabled* id with **no descriptor** in a partition's
repository is instantiated as a `BlockingExporter`, which blocks exporting (and log compaction) on
that partition. This constrains sequencing (§6).

**Why the merge question is hard.** An exporter's `args` is a raw `Map<String, Object>` — there is
no typed POJO at the configuration layer, so `data.exporters` cannot join the typed-POJO overlay
engine of ADR-0007 §2. A first attempt
(PR [#55157](https://github.com/camunda/camunda/pull/55157)) implemented a *generic* recursive
deep-merge in `configuration/`. It was abandoned before merging: a key-agnostic merge must
canonicalize keys (lowercase, strip dashes) so that `indexPrefix` / `index-prefix` collapse before
merging, but for **custom exporters we do not own the config model** — `args` may contain nested
maps whose keys are *user data* that must not be rewritten. Distinguishing a config-property sub-map
from a user-data sub-map requires the exporter's config `Class`; the broker's
`ExporterConfiguration.normalizeKeys` (in `zeebe/broker`, `…/exporter/context/`) is safe precisely
because it is **type-aware** — it introspects the config class via Jackson and normalizes only
property keys, preserving the keys of `Map`-typed fields. The configuration layer has no config
`Class` for a generic exporter, so it can never merge arbitrary args safely.

**Why the inheritance question is a safety question.** A tenant's secondary storage is
isolation-validated (ADR-0007 §4), but a *generic* exporter's target (an Elasticsearch URL and index
prefix in its `args`) is opaque to that validation. If every tenant silently inherited every root
exporter, a root ES exporter would export **every tenant's records to one shared target** — an
isolation breach no existing check catches. The document-store catalog already answered the same
question with an explicit per-tenant `assigned` list
([ADR-0007](0007-physical-tenant-configuration-resolution-and-validation.md) §4, issue #54366), and
OIDC providers likewise ([ADR-0004](0004-per-physical-tenant-provider-selection-via-assigned.md)).

Two exporters are special. `camundaexporter` and `rdbms` are **autoconfigured**: they are mandatory
once the corresponding secondary storage is configured, their config is derived downstream
(`BrokerBasedPropertiesOverride.populateCamundaExporter` / `populateRdbmsExporter`) from
`camunda.data.secondary-storage.*` — which is already per-tenant overridable field-by-field and
isolation-validated — and their `className` is legitimately absent from the config tree. Note that
the *autoconfigured* instance is special, not the class: a **second, explicitly declared**
CamundaExporter (the multi-region duplication setup) is an ordinary catalog entry, because its
target args are exactly as isolation-sensitive as an ES exporter's.

## Decision

### 1. Explicit assignment: `data.exporters-assigned`

Root-declared **generic** exporters form a catalog. A tenant runs a catalog exporter only if it
lists the id in `camunda.physical-tenants.<id>.data.exporters-assigned`:

```yaml
camunda:
  data:
    exporters:
      elasticsearch:                 # catalog entry
        className: io.camunda.zeebe.exporter.ElasticsearchExporter
        args:
          url: http://localhost:9200
          index:
            prefix: root
          bulk:
            size: 1000
            delay: 5
  physical-tenants:
    tenanta:
      data:
        exporters-assigned: [elasticsearch]
        exporters:
          elasticsearch:
            args:
              index:
                prefix: tenant-a     # everything else inherited from root
```

- **Placement.** The key sits next to `data.exporters` and fits the existing per-tenant config
  model: like the document/OIDC `assigned`, it is bound from the environment per tenant, never a
  field on the config POJO, and it exists **only** under `camunda.physical-tenants.<id>.*`; a
  root-level `camunda.data.exporters-assigned` is rejected at startup.
- **Mandatory-explicit, and a complete manifest.** `exporters-assigned` lists **every** generic
  exporter that runs for the tenant: the catalog entries it takes from root **and** the exporters
  it declares itself — consistent with the document-store `assigned`, which selects across root
  and tenant-declared stores alike. Whenever a generic exporter could apply to the tenant (the
  catalog is non-empty, or the tenant declares generic exporters of its own), every explicitly
  declared tenant **must** declare the key. An **empty list is valid** and means "no generic
  exporters" — unlike document stores, zero generic exporters is a legitimate deployment (the
  autoconfigured exporters still run). This is the deliberate delta from the document rule
  ("non-empty required"): for exporters, both silent outcomes of an *absent* key are bad in
  different ways (silently inheriting breaches isolation; silently inheriting nothing loses export
  data), so the operator must say which one they mean. The synthesized `default` tenant is exempt
  (it *is* the root and keeps the full catalog); an explicitly declared `default` is validated like
  any other tenant.
- **Exemption by autoconfigured id.** Exactly the ids `camundaexporter` and `rdbms` are outside the
  catalog: always present for every tenant, never listed in `exporters-assigned` (listing them is an
  error, with a message explaining they are always on). This holds even when the operator writes
  args-tuning for them under root `data.exporters` — the entry stays exempt. Any other explicitly
  declared entry, whatever its class, is catalog.
- **Narrowing.** A tenant's resolved `data.exporters` contains the exempt autoconfigured entries
  plus exactly the assigned entries (catalog and tenant-private alike). Unassigned catalog entries
  are removed from the tenant's resolved map.

### 2. What ends up in a tenant's resolved `data.exporters`, per exporter id

For every exporter id, the resolver answers one question: **does this tenant get an entry for the
id, and if so with which `className` and which `args`?** The answer depends on three yes/no inputs:

1. Does **root** declare the id (is it a *catalog* entry)?
2. Does the tenant list the id in its **`exporters-assigned`**?
3. Does the tenant **declare config** for the id (under
   `physical-tenants.<tenant>.data.exporters.<id>.*`)?

| Root declares it | Tenant assigns it | Tenant configures it | Tenant's resolved entry |
|---|---|---|---|
| yes | yes | no | The root entry, unchanged. |
| yes | yes | args only — class **has** a merger (§3) | Root's `className`/`jarPath`; args = root args and tenant args **deep-merged** (tenant wins where both set a key). |
| yes | yes | args only — class has **no** merger | Root's `className`/`jarPath`; the tenant's args exactly as declared (root args are not merged in). |
| yes | yes | a `className`/`jarPath` **different** from root's | **Boot error.** Assigning an id means "run root's exporter"; changing its class means you do not want root's exporter — use a new, tenant-private id instead. Restating the *same* value is allowed. |
| yes | no | yes | **Boot error.** Contradictory config: the tenant configures an exporter it did not assign. |
| yes | no | no | No entry — this exporter does not exist for this tenant. |
| no | yes | yes | The tenant's entry exactly as declared (a *tenant-private* exporter). Its validity (class loadability, args correctness) stays the broker's authority, as today. At runtime it does not run until [#56652](https://github.com/camunda/camunda/issues/56652) makes the enable state per tenant. |
| no | no | yes | **Boot error** — the same rule as for catalog ids: config for a generic exporter that is not in the tenant's manifest is contradictory. (Consistent with document stores, where a tenant-declared store must also be listed in `document.assigned`.) |

An id in `exporters-assigned` must exist somewhere — in the catalog or in the tenant's own
declarations; anything else is a boot error. The autoconfigured `camundaexporter` / `rdbms` sit
outside the table entirely: always present for every tenant, their config derived from the tenant's
(already per-tenant-resolved) secondary-storage properties; a tenant that declares args-tuning for
one of them gets that declaration as-is.

**Unassigning is not a parking mechanism.** Removing an id from `exporters-assigned` while keeping
its configuration is rejected (the boot-error rows above), not treated as "configured but
inactive". Temporarily deactivating an exporter without touching its configuration is a *runtime*
operation — the exporter-disable management endpoint on the dynamic cluster configuration, whose
physical-tenant-level support arrives with the per-tenant enable state
([#56652](https://github.com/camunda/camunda/issues/56652)) — and unlike a config edit it
preserves the exporter's state and position. Keeping the two mechanisms apart keeps
`exporters-assigned` a true statement of what runs, with no silently-inert configuration drifting
out of date.

Two properties follow from the boot-error rows: a resolved entry can never end up with
`className: null` (inheriting `className`/`jarPath` from root is always safe, because changing them
is rejected), and a merger only ever merges two arg maps that belong to **one** exporter class.
Note also that nothing is ever *deleted* from root config: `exporters-assigned` narrows what a
tenant sees (consistent with ADR-0007: "the merge never deletes; `assigned` lists narrow").

### 3. Opt-in type-aware merge: the `ExporterConfigMerger` SPI

The deep merge the abandoned PR tried to do generically is instead **opt-in per exporter class**,
implemented where the config `Class` is reachable:

```java
// zeebe/exporter-api
public interface ExporterConfigMerger {
  boolean supports(String className);
  Map<String, Object> merge(Map<String, Object> rootArgs, Map<String, Object> tenantArgs);
}
```

- **Discovery.** Implementations are discovered via `ServiceLoader` at configuration-resolution
  time. The distribution bundles the ES/OS exporter jars on the boot classpath, so their mergers are
  found; a custom exporter loaded from `jarPath` is *not* on the boot classpath at resolution time,
  so custom exporters fall out of merge scope automatically — no special-casing. (A custom exporter
  deployed on the boot classpath *may* ship its own merger; that is additive and safe, since its
  author owns its config model.)
- **Matching.** `supports(className)` is matched against the **root** catalog entry's `className`
  (unambiguous, since divergence is a boot error). Two discovered mergers claiming the same class
  name fail startup. Merge failures are wrapped by the resolver with the exporter id and tenant id.
- **Implementations: Elasticsearch, OpenSearch, and CamundaExporter.** One merger per exporter
  module, next to its config class — for `io.camunda.zeebe.exporter.ElasticsearchExporter`,
  `io.camunda.zeebe.exporter.opensearch.OpensearchExporter`, and
  `io.camunda.exporter.CamundaExporter` (config class `io.camunda.exporter.config.ExporterConfiguration`).
  Each is a few lines delegating to the shared helper (§4) with its own configuration class. The
  CamundaExporter merger applies to *explicitly declared* catalog entries only — the multi-region
  duplication setup, where a second CamundaExporter is declared under `data.exporters` — since the
  autoconfigured `camundaexporter` entry is exempt and derives its config from secondary-storage
  properties (§1).
- **Cross-tenant target validation is not the merger's job.** Merging args and identifying an
  exporter's *target location* (url, index prefix, …) are independent capabilities with different
  consumers: the merge runs per tenant during resolution; isolation validation compares *across*
  tenants afterwards. When generic-exporter target isolation is implemented, it should be a
  **sibling SPI** discovered the same way (e.g. `supports(className)` + `location(args)`), feeding a
  `CrossTenantValidation` the way `StorageIdentity` does today — not an extra obligation forced onto
  every merger. That follow-up is out of scope here; the direction is recorded so the SPI surface
  stays consistent when it lands.

### 4. One source of normalization: a shared type-aware helper

**Who uses the normalization.** Two callers:

1. **The broker, today, on every exporter instantiation.** `ExporterConfiguration.fromArgs`
   (`zeebe/broker`, `…/exporter/context/`) already walks the exporter's config class via Jackson
   introspection, normalizes *property* keys so relaxed spellings collapse
   (`index-prefix` / `INDEX_PREFIX` / `indexPrefix`) while **preserving** the keys of `Map`-typed
   fields (user data), and then binds the map into the typed config. This is existing behavior, not
   something new this ADR introduces.
2. **The mergers, before deep-merging.** Root and tenant may spell the same property differently,
   so the merger must normalize both maps *the same way the broker will* before merging — and only
   class-aware walking knows which keys are properties (normalize, merge) and which are user data
   (leave untouched).

If each merger re-implemented this, normalization semantics would exist in four places (broker, ES,
OS, CamundaExporter) and drift — the risk flagged in the #55157 review ("have normalisation come
from one place"). So the machinery is extracted into **one shared static helper** that the broker
**delegates** to (a separate structural `refactor:` commit, no behavior change). Merge semantics
inside the helper: property keys are normalized against the class; nested POJO properties recurse;
scalars and lists replace; **`Map`-typed properties replace wholesale** (their content is user data
the merge does not interpret — the same philosophy that put custom exporters out of scope).

**Where it lives — the SPI and the helper are decided separately:**

- The `ExporterConfigMerger` **interface** goes in `zeebe/exporter-api`: it is part of the exporter
  *contract*, dependency-free, and additive (revapi-checked).
- The **helper** brings a `jackson-databind` dependency, and `exporter-api` is the *public* exporter
  SDK — hosting the helper there means external exporter authors get Jackson on their compile path
  and the helper's semantics are frozen under API-compatibility guarantees. The alternative is a
  small **internal support module** (e.g. `zeebe-exporter-config-support`) depended on by the broker
  and the three merger modules, keeping the public SDK dependency-free at the cost of one more
  Maven module. (`exporter-common` was evaluated and rejected: neither the ES/OS exporters nor the
  broker depend on it today, and it drags a heavy transitive tree — search-domain,
  security-protocol, bpmn-model.) **Recommendation: the internal support module** — the public-API
  cost is permanent while the extra-module cost is one-time. Final call with the reviewers of this
  ADR.

### 5. Where it runs: a dedicated resolver step, not a `MapOverlaySpec`

The logic runs as a dedicated step in `PhysicalTenantResolver.of()` right after
`PhysicalTenantMapOverlays.apply(...)`, following the established one-helper-per-overlay-concern
shape (`PhysicalTenantDocumentConfigurations`, `PhysicalTenantAuthenticationProviderConfigurations`).
It sources the root catalog from the resolver's root `Camunda` parameter (authoritative, pre-overlay)
and the tenant's declared entries from a targeted re-bind of
`physical-tenants.<id>.data.exporters`, then recomputes the tenant's `data.exporters` per §1/§2.
Validation (root-level key rejected; mandatory-explicit; unknown/blank/exempt ids in the list;
configured-but-unassigned; className/jarPath divergence) fails fast with
`UnifiedConfigurationException`, sibling to the existing per-PT validations of ADR-0007 §4.

`data.exporters` is deliberately **not** registered in the typed-POJO overlay engine: the engine's
fixed semantic (seed the touched entry from root, rebind) would grant partial field inheritance to
*every* exporter including custom ones, and Spring's rebind merges the nested `args` map only at
top-level keys without normalization — neither whole-entry-replace nor a correct deep merge. The
map-policy coverage guard keeps `data.exporters` in its explicit allowlist, with the comment pointing
at this step.

### 6. Sequencing: merge now, assignment with #56652

The two halves of this ADR have different runtime prerequisites and land in two steps:

**Step 1 — merge (§2 combination semantics, §3 SPI, §4 helper): now.** With the #57202 wiring in
place, the merge is immediately effective end-to-end and strictly improves the live behavior: the
headline use case of #55155 (inherit root's ES exporter, override one arg) starts working, and the
className/jarPath divergence boot error replaces today's silent construction of a broken fresh
entry. Nothing in step 1 changes *which* exporters run where.

**Step 2 — assignment (§1 `exporters-assigned`: mandatory-explicit validation, narrowing, and the
configured-but-unassigned error): together with
[#56652](https://github.com/camunda/camunda/issues/56652).** Narrowing removes unassigned catalog
entries from a tenant's resolved map — but as long as the enable state is generated from the root
`BrokerCfg` only, those ids remain *enabled* on the tenant's partitions, and an enabled id without
a descriptor becomes a `BlockingExporter` that stalls exporting and log compaction. Narrowing
before #56652 would therefore convert an isolation hazard into an availability incident. Until
step 2 lands, the inherit-all behavior (a root exporter runs for every tenant with root args unless
overridden) is an **accepted, documented interim state** — it is today's live behavior, not a
regression introduced by this ADR.

### 7. Testing

- `configuration/` cannot see the real mergers (it does not depend on the ES/OS modules), so
  resolver tests cover the full decision table against a **test-only merger** registered via
  `src/test/resources/META-INF/services`.
- The shared helper's home module unit-tests the type-aware merge (Map-field key preservation,
  relaxed-key collapse, recursion, list-replace); the broker's existing `ExporterConfiguration`
  tests pin the delegation refactor.
- The ES/OS/CamundaExporter modules test their mergers against their real config classes.
- End-to-end: **extend `PhysicalTenantExporterConfigIT`** (added by #57202 in
  `zeebe/qa/integration-tests`) with a merge scenario — a root exporter whose class has a merger,
  partially overridden by a tenant, asserting the tenant's partitions run it with the merged args.
  This exercises the real `ServiceLoader` discovery on a real broker classpath, replacing the
  dist-scope smoke test that was planned while the resolved exporters were unconsumed. Assignment
  scenarios (narrowing, boot errors) join the same IT in step 2, next to its currently-disabled
  tenant-only-exporter case.

## Consequences

### Positive

- The generic-exporter isolation gap is closed at the same place as document stores and OIDC
  providers: nothing root-declared runs for a tenant implicitly, and a reviewer reads a tenant's
  export surface off one key. The three `assigned` surfaces are now consistent.
- The headline use case of #55155 works: share the tuning, override the target —
  `exporters-assigned: [elasticsearch]` plus a one-line `index.prefix` override.
- Merging is exactly as safe as it can be: performed only where the config class is known, never on
  models we do not own. The `className: null` pitfall of the native binder is structurally
  impossible for assigned ids.
- Normalization semantics have a single owner; broker and merge can no longer drift apart.
- The SPI is additive: any further merger — e.g. for a custom exporter deployed on the boot
  classpath — needs no framework change.

### Negative / trade-offs

- **Configuration bar rises again**: with a non-empty catalog, every tenant must declare
  `exporters-assigned` — one more mandatory key on top of ADR-0007's list (storage namespace,
  `document.assigned`, `providers.assigned`, `security.initialization`). Intended, and to be
  documented together with those.
- `exporter-api` — a revapi-guarded public SDK module — gains one new public interface. The
  type-aware helper's home is the one point left open in §4 (internal support module recommended vs
  hosting it in `exporter-api`); either way normalization has a single owner.
- Assignment does **not by itself** guarantee target isolation: a tenant that assigns the root ES
  exporter without overriding its target args still shares the root target. Extending cross-tenant
  isolation validation to generic exporter targets (via the sibling target-locator SPI sketched in
  §3) is a possible follow-up, not covered here.
- Merged entries carry normalized (lowercased, dash-stripped) arg keys while untouched entries keep
  the operator's spelling. Cosmetic: the broker re-normalizes idempotently when instantiating the
  exporter config.
- For assigned entries **without** a merger, tenant args replace root args wholesale — partial
  inheritance is simply not offered for classes we cannot introspect. Operators of custom exporters
  restate the full `args` per tenant.
- An explicit empty list (`exporters-assigned: []`) must bind distinguishably from an absent key
  through Spring's `Binder` — empty-collection binding has been fragile before (the empty-YAML-map
  fixes). To be verified first in implementation; the fallback is additionally accepting `""`
  (Spring's empty-string → empty-list conversion) and documenting it.
- **Assignment is gated on #56652** (§6): until the enable state is per tenant, the inherit-all
  interim stands — every root exporter runs for every tenant with root args unless overridden. The
  merge (step 1) softens this (overriding the target is now a one-liner) but does not close it;
  operators running physical tenants before step 2 must override generic-exporter targets per
  tenant themselves.
- Physical-tenant deployments configured **before** step 2 lands will fail at boot when it does
  (the mandatory `exporters-assigned` key is missing). This is a deliberate loud migration — the
  key encodes an isolation decision that was previously implicit — and lands together with the
  feature-flagged per-tenant enable state, to be called out in its release notes.

## Considered alternatives

1. **Generic recursive deep-merge in `configuration/`** (PR #55157). Rejected — the reason this ADR
   exists: without the config class, key canonicalization cannot distinguish property keys from
   user-data keys in custom-exporter args, so the merge silently rewrites data it must not touch.
   Type-awareness is not available at the configuration layer; it is available next to each config
   class, hence the SPI.
2. **Inherit-all (no `assigned`), merge as the only feature** — the original issue proposal.
   Rejected: a root generic exporter would silently export every tenant's records to one shared
   target, and no validation covers generic exporter args. The document/OIDC precedent already
   establishes explicit selection as the model for "root declares, tenant opts in".
3. **Optional `assigned`, absent = inherit none.** Safer than inherit-all and simpler than
   mandatory-explicit, but an operator who forgets the key gets a tenant that silently exports
   nothing — silent data loss instead of a silent breach. Mandatory-explicit makes both failure
   modes impossible at the cost of one required key, and matches the document rule.
4. **Implicit assignment (configuring an id implies assigning it).** Rejected: `exporters-assigned`
   stops being the single source of truth for what runs where; a tenant's isolation surface can no
   longer be read off one key.
5. **Exemption by className instead of by autoconfigured id.** Rejected: a second, explicitly
   declared CamundaExporter (multi-region) would bypass assignment and silently duplicate every
   tenant into one shared second-region target — exactly the risk `assigned` exists to prevent.
   Also className is legitimately absent for the autoconfigured entries, making the check awkward.
6. **Registering `data.exporters` in the typed-POJO overlay engine** (ADR-0007 §2) with a merge
   hook. Rejected: the engine's seed-from-root semantics would grant partial inheritance to custom
   exporters (contradicting whole-entry ownership), and the nested raw `args` map still cannot be
   deep-merged by Spring's binder — the engine solves a different problem (typed POJO entries).
7. **Whole-entry replace on className divergence instead of a boot error.** Permissive, but it
   contradicts the meaning of assignment and reintroduces same-id-different-class across tenants —
   a trap for the id-keyed exporter state downstream.
8. **Helper duplicated per impl, with no shared home at all.** Rejected: four copies of
   normalization semantics (broker, ES, OS, CamundaExporter) — precisely the drift the #55157
   review flagged.
9. **SPI and helper both in `exporter-api`.** One home, no new module — but the public exporter SDK
   gains a `jackson-databind` dependency and the helper is frozen under API-compatibility
   guarantees, for a utility external exporter authors were never meant to call. Kept as the
   fallback if the internal support module (§4) is judged not worth a new Maven module.
10. **Running the merge at the broker exporter-load seam instead of the configuration layer.**
    Rejected for now: the resolver is the single place per-tenant config is computed (ADR-0007 §1),
    and consumers must be able to read resolved exporters like any other property. If a concrete
    custom-exporter inheritance requirement ever appears (the config class *is* reachable at broker
    load time), the SPI can be additionally honored at that seam — additive, not a rewrite.
11. **Allowing configured-but-unassigned entries as "parked" configuration** (keep an exporter's
    config while temporarily removing it from `exporters-assigned`, to re-assign it later).
    Rejected: the resolver cannot distinguish deliberate parking from a forgotten assignment, so
    the safe reading is "contradiction — fail fast"; and silently-inert configuration drifts out of
    date until the day it is re-activated. Temporary deactivation is the job of the runtime
    exporter-disable management endpoint (per-tenant with #56652), which also preserves the
    exporter's state and position — something a config-level park/unpark cannot do.

## References

- [#55155](https://github.com/camunda/camunda/issues/55155) — per-tenant exporter config overlay;
  this ADR. Parent: [#52680](https://github.com/camunda/camunda/issues/52680) (epic
  [#52027](https://github.com/camunda/camunda/issues/52027)).
- PR [#55157](https://github.com/camunda/camunda/pull/55157) — abandoned generic deep-merge
  (closed unmerged; superseded by this ADR).
- PR [#57202](https://github.com/camunda/camunda/pull/57202) (fixes
  [#56517](https://github.com/camunda/camunda/issues/56517)) — per-tenant `BrokerCfg` +
  `ExporterRepository` wiring; makes the semantics decided here live behavior, and adds
  `PhysicalTenantExporterConfigIT`.
- [ADR-0007](0007-physical-tenant-configuration-resolution-and-validation.md) — resolution model,
  overlay engine, validation split; §3 deferred exporters to this ADR.
- [ADR-0004](0004-per-physical-tenant-provider-selection-via-assigned.md) — the `assigned`
  precedent for OIDC providers; document-store `assigned` per #54366.
- [#56652](https://github.com/camunda/camunda/issues/56652) — per-tenant exporter enable state in
  the dynamic cluster configuration (coordinated with the Distributed Systems
  dynamic-cluster-configuration effort); gates step 2 (§6).
