# ADR 0005 — Runtime dataset provisioning and live activation

- Status: Accepted
- Date: 2026-07-06
- Scope: `analytics/analytics-serving` (provisioning service + activation), `analytics/analytics-model`
  (activation gate), `analytics/event-bridge-streaming` (live topology reload),
  `analytics/analytics-pipeline` + `analytics/analytics-webapp` (wiring + REST)
- Builds on: ADR 0001 (declarative datasets — declaration is the source of truth), ADR 0004 (cube
  read path)

## Context

ADR 0001 made the `DatasetDeclaration` the source of truth and built the whole derivation chain —
compiler, stable `aggId` allocation, metadata plane (`DatasetSpecStore` / `MeterIdStore`),
declaration-driven schema provisioning (`DatasetSchemaManager`), the two-stage fill, and the
forward-only **activation vector**. What it did *not* build is the user-facing edge: today the only
datasets that exist are the hard-coded `StandardDatasets`, written once by an idempotent
`bootstrap`, and the running stages call `loadCubes()` exactly once at start-up into an immutable
`ProcessorTopology`. The one wired `POST /api/datasets` endpoint writes an *unrelated* webapp H2
table the pipeline never reads.

So a user cannot define a dataset that actually fills a cube. Closing that needs three things that
are currently missing:

1. **A single admission path** a REST request can call — compile, allocate ids, persist the spec,
   provision the serving schema — that `StandardDatasets.bootstrap` also routes through, so there is
   one way to admit a dataset, not two.
2. **A way for the running pipeline to pick up the new dataset** — Stage 1 needs a `CubeMeterProcessor`
   per new `(cube, meter)` and Stage 2 a merger + `CellApplier` per new `aggId` (unknown `aggId`s are
   silently dropped), and both are built once and frozen in the per-partition `taskFactory`.
3. **A deterministic activation cutover** — the point from which the new cube starts collecting — that
   survives replay and does not require pausing or perfectly sequencing the two stages.

This ADR settles (2) and (3); (1) is a thin service over machinery ADR 0001 already built.

## Decision

### 1. `DatasetProvisioningService` — one admission path

Add a `DatasetProvisioningService` in `analytics-serving` that is the *only* way a dataset is
admitted, live or at bootstrap:

```
provision(DatasetDeclaration decl):
  compile(decl)                          // validate; allocate + persist aggIds (MeterRegistry)
  activationTs = clock.now() + DEBOUNCE  // event-time cutover (see §3)
  registered = registry.admit(decl, Map.of(), activationTs)
  datasetSpecStore.create(registered)    // durable spec
  schemaManager.ensure(compiled)         // serving table/index NOW, not at next boot
  signalReload()                         // §2
  return registered
```

`StandardDatasets.bootstrap` is refactored to call `provision` per declaration (with
`activationTs = 0`, i.e. from the beginning of history — the standard dashboards must cover all
retained data). Idempotency stays where it is: bootstrap is a no-op when the spec store is
non-empty, and a duplicate dataset name is rejected by the store.

### 2. Live per-partition topology hot-reload

The pipeline picks up a new dataset **without an ingestion pause**. A control signal — a monotonic
*dataset-set version* bumped by `provision` and observed by each running stage — makes every
partition actor, **at its next commit boundary** (after produce-before-commit; state + offset
already persisted), rebuild its `Task`'s `ProcessorTopology` from the freshly reloaded cube list,
**reusing the same per-partition RocksDB**. Existing cubes' `aggId`-prefixed column-family state is
untouched; the new cube's families start empty and begin filling. The rebuild is a local,
single-writer operation on the partition actor thread — no lock, no cross-partition coordination.

This is expressed as a capability on the streaming runtime (`event-bridge-streaming`): a `Task` may
be asked to rebuild its topology, and `StreamRuntime` drives the rebuild on the actor between
commits. The analytics stages supply a `taskFactory` that reads the current metadata plane, so a
rebuild reflects the newly-admitted dataset.

### 3. Activation cutover: event-timestamp, debounced (v1)

**v1 ships an event-timestamp activation gate, not the source-coordinate vector.** A
runtime-provisioned dataset freezes a single `activationTimestampMs = now + DEBOUNCE` (e.g. 10s) at
admission, stored durably on the spec. A fact contributes to the cube iff its **event time**
(the immutable Zeebe record timestamp) is at or after that stamp:

```
admits(fact) := fact.eventTime() >= activationTimestampMs   (&& the existing position gate)
```

Why this is correct enough for v1:

- **Replay-deterministic.** Event time is a property of the event and never changes; `activationTs`
  is frozen once at admission and read back verbatim on replay. So membership is a pure function of
  the log — the same guarantee the position vector gives, by a different key.
- **The debounce window removes the cross-stage race for free.** Because no fact is due until
  `now + 10s`, both stages have ample time to observe the version bump and rebuild before any data
  must flow into the new cube — so we do **not** need to sequence "Stage 2 before Stage 1." If the
  reload lands late, the only effect is a few dropped early facts near the boundary, bounded by how
  late the reload is, never silent corruption of committed cells.

Why it is not yet the "proper" design:

- Event time is **not monotonic with source position** — an out-of-order event whose timestamp is
  below the cutover but which is consumed after it is excluded, and (with clock skew across
  exporters) the cutover is fuzzy at the edges. The proper mechanism from ADR 0001 is the
  **immutable per-source-partition activation *position* vector** (`activation[p]` = the source
  high-watermark at admission), which is exact against out-of-order arrival and is the same knob
  backfill lowers. That requires reading current per-partition end-offsets from the client at
  admission; it is deferred.

The code carries an explicit `TODO(analytics)` at the gate and at the provisioning service pointing
here. The position vector field (`activation`, currently `Map.of()`) is kept in the model as the
forward target; v1 simply gates on the timestamp in addition.

**Fill semantics are forward-only.** A new dataset collects facts arriving after its cutover; it is
*not* backfilled from already-consumed history (both stages resume from their committed offset).
Historical backfill — replaying retained source history for the new cube from a chosen floor — is a
separate reprocessing path, deferred (see ADR 0001 Phase 6).

## Rationale

- **One admission path** removes the ADR 0001 hazard of two ways to create a dataset (hard-coded vs.
  runtime) drifting apart; bootstrap and REST become the same call with a different `activationTs`.
- **Hot-reload over process restart** keeps reads and ingestion up; reusing the per-partition RocksDB
  makes adding a cube non-destructive to every existing cube's state and offsets, because state is
  already `aggId`-prefixed (ADR 0001).
- **Timestamp activation + debounce** is the smallest change that is *both* replay-deterministic and
  free of cross-stage reload ordering — the two properties that make live provisioning safe. It trades
  edge-exactness (out-of-order/skew) for a fraction of the implementation of the offset-vector path,
  with a clean upgrade to that path later.

## Consequences

- `RegisteredDataset` gains a frozen `activationTimestampMs`; `admits` takes the fact's event time.
  `DatasetRegistry.admit` and `DatasetSpecStore` serialization carry it (greenfield: a bootstrapped
  store is rebuilt, so no migration of existing spec rows).
- `StandardDatasets.bootstrap` no longer admits directly; it calls the provisioning service.
- `event-bridge-streaming` grows a topology-rebuild capability on `Task`/`StreamRuntime` — a
  shared-library change, kept minimal and generic (rebuild-at-commit, not analytics-specific).
- `AnalyticsPipelineLifecycle` (webapp host) wires the reload signal from the metadata plane to both
  in-JVM stages.
- Edge fuzziness at the activation boundary (out-of-order / clock skew) is an accepted v1 limit,
  removed when the position-vector path lands.

## Revisit triggers

- Demand for exact activation against out-of-order arrival, or for backfill — promote the
  source-coordinate activation vector (ADR 0001 Phase 6) and lower its floor for backfill.
- Stages scaled across multiple JVMs where a single in-process version bump no longer reaches every
  stage owner — needs a metadata-plane-propagated reload signal (a control record), not an in-JVM
  bump.
- A reload that must not drop even boundary facts — sequence Stage 2's rebuild before Stage 1's, or
  park unknown-`aggId` deltas in Stage 2 until its merger exists.
