# Broker health tree: nest physical tenants, project metrics, drop intrinsic health

**DRI**: Lena Schönburg

**Status**: Proposed (8.10)

**Purpose**: Restructure the broker health tree so physical tenants are a real structural level,
metrics are a projection of the tree rather than a parallel graph, and a partition's own health
factors are explicit child nodes instead of a bundled "intrinsic health" object.

**Audience**: Zeebe broker engineers and AI agents working on health monitoring, partition
lifecycle, or the `zeebe_broker_health_nodes` metric.

## Context

The broker tracks its health by aggregating components into a tree: a node is healthy only if all of
its children are healthy, and the tree is exported as the `zeebe_broker_health_nodes` metric so it
can be drawn in Grafana. The full mechanics are described in
[docs/zeebe/health.md](../../../docs/zeebe/health.md); this section reproduces the shape the ADR
changes so the before/after can be compared in one place.

### The current tree

```
BrokerHealthCheckService
  └─ CCHM "Broker-<id>"
       ├─ ZeebePartition (wrapper) "Partition-<tenant>-<n>" ─┐ two objects,
       │    └─ CCHM                "Partition-<tenant>-<n>" ─┘ same name → node emitted twice
       │         ├─ RaftPartition
       │         ├─ StreamProcessor
       │         ├─ ExporterDirector
       │         ├─ SnapshotDirector
       │         ├─ MigrationSnapshotDirector
       │         └─ ZeebePartitionHealth-<n>  ← phantom node: bundles disk + transition + dead
       └─ ... every partition of every tenant on one flat level (no tenant grouping)
```

On top of this, the metric export is a **second graph**: `HealthTreeMetrics` keeps its own
relationship/meter maps in sync with the tree via listener callbacks, and a CCHM emits nodes as a
side effect of aggregating. Because a Micrometer listener's tags are fixed per instance, there are
two exporters bound to two registries — broker-global (`physicalTenant=none`) and partition-scoped
(the real tenant/partition tags). The partition node sits on the boundary between them and is emitted
by both.

### Why this is bad

The specific symptoms above all trace back to four structural mistakes:

- **The export structure is a parallel copy instead of a projection of the source of truth.** The
  metric graph is hand-synchronised with the aggregation tree, so the two can drift, and a node that
  straddles the two exporters (the partition) is reported twice with different tags. Anything derived
  from a model should be *computed from* that model, not maintained alongside it.
- **Identity is a global string, so names carry structural information.** Keying every node by a
  globally-unique `componentName` forces the tenant into the partition's name purely to avoid map
  collisions (`Partition-<tenant>-<n>`). The name ends up encoding *where* a node sits, which is the
  tree's job, not the name's.
- **A node cannot express its own health, only its children's.** A CCHM is healthy iff its children
  are, with no notion of the node itself being unhealthy. To inject a partition's own health (disk,
  transition state, death) it is wrapped in a fake child node, `ZeebePartitionHealth-<n>`, that
  corresponds to no real subsystem and bundles three unrelated facts behind one opaque gauge.
- **The domain hierarchy is not represented structurally.** Physical tenants are a real level of the
  system, but the tree is flat — every tenant's partitions hang directly off the broker — so the
  tenant has to be smuggled into strings rather than being a node you can aggregate and filter on.

### What would be better

The same four points, inverted, are the principles the decision applies:

- **One tree is the source of truth; metrics are a pure projection of it.** Emitting is derived from
  the tree, computed from each node's position, so it cannot drift and cannot double-count.
- **Identity is the object and its position; names are for display only** and need be unique only
  among siblings — so structural facts (tenant, partition number) live in the tree, not in names.
- **A health node is either an aggregator or a leaf, with nothing bundled or hidden.** A node's own
  health factors are themselves first-class leaf nodes, so the tree explains *why* something is
  unhealthy instead of hiding it behind a synthetic component.
- **The tree mirrors the domain.** Physical tenant is a structural level between broker and
  partition, so it can be aggregated, filtered, and reasoned about like any other node.

Separately — and already correct — **broker readiness** (`BrokerHealthCheckService.isBrokerReady()`)
is independent of the health tree. It is gated on every configured physical tenant having registered
its bootstrap partitions and every such partition having installed, upholding the invariant that
**the broker does not become ready until partitions from all physical tenants have started**. This
ADR does not change readiness.

## Decision

Apply the four principles above. The target tree has every interior node a plain CCHM and every leaf
a plain `HealthMonitorable`:

```
Broker-<id>                         CCHM
  └─ Tenant-<tenant>                CCHM, one per physical tenant
       └─ Partition-<n>             CCHM   (no tenant prefix)
            ├─ RaftPartition        leaf
            ├─ StreamProcessor      leaf   (registered/removed per role transition)
            ├─ ExporterDirector     leaf   (registered/removed per role transition)
            ├─ SnapshotDirector     leaf   (registered/removed per role transition)
            ├─ MigrationSnapshotDirector  leaf (registered/removed per role transition)
            ├─ DiskSpace            leaf   (diskSpaceAvailable)
            └─ PartitionTransition  leaf   (servicesInstalled + getHealthIssue + sticky-dead)
```

**D1. Physical tenant is a structural level.** Insert a tenant-level CCHM between the broker CCHM
and the partition CCHMs. The broker CCHM aggregates tenant CCHMs; each tenant CCHM aggregates its
partition CCHMs. Tenant nodes are created eagerly for every *expected* physical tenant (the set is
known from configuration at construction) and removed only at shutdown, so the tree is complete and
predictable and an empty-but-expected tenant is legitimately not-yet-healthy. Cross-actor upward
propagation already works (partition → broker today); partition → tenant → broker is the same
mechanism with one more hop, with tenant CCHMs running on the broker health actor.

**D2. Node identity is the object and its position, not a global name.** A node's identity is the
object; its path is derived by walking parents; its display name need only be unique **among
siblings**. `Partition-1` under tenant A and under tenant B are distinct nodes by position. Sibling
maps (`Map<childKey, node>`) replace the global string maps. This removes the reason for the tenant
prefix: `ZeebePartition.componentName` reverts to `Partition-<n>`, undoing the qualification added in
commit `b50ce17`.

**D3. Metric emission is a projection of the health tree, owned by a single projector, decoupled
from CCHM.** CCHM no longer calls a `ComponentTreeListener`; it becomes a pure aggregator. A single
projector observes node add/remove on the real tree and emits exactly one `zeebe_broker_health_nodes`
gauge per node, deriving both the `path` and the `physicalTenant`/`partition` tags from the node's
position (its `Tenant` and `Partition` ancestors). Consequences: there is one node object per node,
so double emission is structurally impossible; the two-exporters/two-registries split and the
fixed-`extraTags` hack are gone; and CCHM loses code rather than gaining it.

**D4. A partition contributes exactly one node.** The `ZeebePartition` wrapper/inner-CCHM double
identity is removed. `ZeebePartition` owns and registers a single partition CCHM under its tenant
node; it is no longer itself a tree node, and the `getHealthReport()` delegation hop disappears.

**D5. A partition's own health factors are explicit child leaves; there is no "intrinsic health"
concept.** `ZeebePartitionHealth` is deleted and its signals become ordinary `HealthMonitorable`
leaf children of the partition CCHM, grouped by concern:

- **`DiskSpace`** — reflects `diskSpaceAvailable`. Starts healthy (assume available until told
  otherwise).
- **`PartitionTransition`** — reflects the role-transition lifecycle: `servicesInstalled`, the live
  `partitionTransition.getHealthIssue()`, and the sticky `dead` latch from `onUnrecoverableFailure`
  (these three are one concern). Starts unhealthy ("services not installed").

The setters retarget from the deleted class to these leaves; they register in `onActorStarted` and
unregister on close, exactly as `ZeebePartitionHealth` does today, preserving current default states.
CCHM stays a pure aggregator — no subclass, no protected hook, no wrapper. The **dead latch lives on
the `PartitionTransition` leaf**, not on CCHM: a `DEAD` child makes the partition CCHM report `DEAD`
under the existing worst-wins aggregation, which propagates up the tree. ("Once dead, stays dead" is
a property of that one leaf. `handleUnrecoverableFailure` continues to also notify failure listeners
directly and stop the partition; the leaf's dead state merely makes the tree consistent with that.)

**D6. Readiness stays separate from the health tree.** `isBrokerReady()` continues to be derived
from the expected-tenants and partition-install bookkeeping, not from tree health, because a
transiently-unhealthy but installed partition must not un-ready the broker. The "all physical tenants
must have started" invariant is unchanged.

## Alternatives considered

- **Rewrite the metric `path` string to look nested (cosmetic).** Transform `Partition-<tenant>-<n>`
  into `Broker/<tenant>/Partition-<n>` in the exporter only. Rejected: it changes the displayed path
  without changing the structure, leaving the flat map, the double emission, and the phantom health
  node in place.
- **Keep the tenant prefix in the partition name (status quo, `b50ce17`).** Rejected: the prefix is a
  workaround for a global flat map. With sibling-scoped identity (D2) the collision it guards against
  cannot occur, and the name stops carrying structural information.
- **Fold intrinsic health into a CCHM subclass via a protected `combineWithSelf` hook, or into a
  composed `HealthMonitor` wrapping a CCHM.** Both add machinery — a new concept on shared
  infrastructure, or delegation boilerplate plus a wrapper that (without D3) reintroduces the
  double-node. Rejected in favour of D5: explicit child leaves need *no* new concept, keep CCHM a
  pure aggregator, and are strictly more observable (a `DiskSpace` gauge and a `PartitionTransition`
  gauge instead of one opaque `ZeebePartitionHealth` gauge).
- **Implement the partition node as a `HealthMonitor` with no CCHM at all.** Rejected: the partition
  has runtime-changing children that must be aggregated thread-safely (the child map, per-child
  failure subscription, periodic re-probe, and remove-race handling). That machinery *is* CCHM;
  avoiding it means reimplementing the exact races CCHM already handles.
- **Split `servicesInstalled` and `getHealthIssue` into separate leaves.** Rejected as YAGNI: they
  are one lifecycle concern and `getHealthIssue` already originates in the transition. Keep them in
  the single `PartitionTransition` leaf.
- **Fold readiness into the health tree.** Rejected (D6): readiness and health answer different
  questions, and coupling them would let a transient health blip un-ready the broker.
- **A generic N-level tree/graph framework with pluggable export.** Rejected as over-engineering. The
  domain has exactly three interior levels (broker, tenant, partition) plus leaves; hard-code that.

## Consequences

- The tree matches the domain: `Broker → Tenant → Partition → leaves`, with no special-cased nodes
  (interior = CCHM, leaf = `HealthMonitorable`).
- `CriticalComponentsHealthMonitor` gets simpler — it loses all `ComponentTreeListener`/graph-emission
  responsibility and is a pure children-aggregator.
- The `zeebe_broker_health_nodes` metric **contract changes**: each node is emitted once with
  position-derived `physicalTenant`/`partition` tags; the partition `id`/`path` lose the tenant
  prefix; the `ZeebePartitionHealth-<n>` series is replaced by `DiskSpace` and `PartitionTransition`
  series. The Grafana dashboard (`monitor/grafana/zeebe.json`) and any alerts keyed on the old
  `id`/`path` values must be migrated in lockstep.
- Health is more observable: the reason a partition is unhealthy (disk vs transition vs a specific
  subsystem) is visible as distinct named gauges.
- `ZeebePartitionHealth` and the `ZeebePartition` health-wrapper duplication are deleted; a partition
  contributes exactly one node.
- Broker readiness behaviour is unchanged, including the all-physical-tenants-started invariant.

## Implementation notes

The implementation follows D1–D6; this section records how the projection (D3) is realised and two
small refinements made while building it.

- **Nodes carry their position; the projector is fed, it does not walk.** Each node has an immutable
  `HealthNodePosition` (`name`, `path`, `physicalTenant`, `partition`) composed from its parent's
  position (`broker(...).tenant(...).partition(...).child(...)`). `CriticalComponentsHealthMonitor`
  stays a pure aggregator — it owns no metric state, tracks no relationships and builds no
  paths/tags — but it *announces* structural changes (`onNodeRegistered(node, position)` /
  `onNodeRemoved(node)`) to a single `HealthTreeListener`. The projector (`HealthTreeMetrics`) holds
  only a node→meter map and derives every tag from the supplied position. This is the literal D3
  ("metrics are a projection of the tree, owned by a single projector") with one practical change:
  the projector is *told* about nodes rather than walking the tree itself, because the tree spans
  actor boundaries (broker and tenant nodes run on the broker health actor, partition nodes and
  leaves on each partition actor) and a single walker could not read it thread-safely. Because each
  node's position is self-contained, emission has no cross-actor ordering dependency and cannot drift
  or double-count. A leaf's position is composed by its parent monitor (inheriting tenant/partition);
  an interior node carries its own. The old `ComponentTreeListener` (relationship-tracking 4-method
  API plus per-instance `extraTags`) is deleted in favour of `HealthTreeListener` + `HealthNodePosition`.
- **One registry.** All health-node gauges are emitted on the broker (system) meter registry with
  per-gauge tenant/partition tags, so the per-partition `HealthTreeMetrics` instance and the
  registry-wide common-tag mechanism for these nodes are gone (D3's two-exporters/two-registries
  split).
- **`ZeebePartition` is no longer a `HealthMonitorable`.** Per D4 the partition is not a tree node —
  its own monitor is registered under the tenant node instead — so `ZeebePartition` stops implementing
  `HealthMonitorable` and the `getHealthReport()` delegation hop is gone. The broker admin REST
  endpoint (`BrokerAdminServiceImpl#getPartitionHealth`) reads health straight from the partition
  monitor (`getHealthMonitor().getHealthReport()`). `ZeebePartition` keeps `addFailureListener` /
  `removeFailureListener` as plain methods (and remains a `FailureListener` on its monitor) so the
  topology health broadcaster can still observe the partition and is pushed the current state on
  registration.
- **Grafana.** The `zeebe_broker_health_nodes` tag keys (`id`, `path`, `physicalTenant`, `partition`)
  are unchanged; only their values change (nested `path`, no tenant prefix in the partition `id`,
  `DiskSpace`/`PartitionTransition` instead of `ZeebePartitionHealth-<n>`). The single panel that uses
  the metric ("Health status timeline", legend `{{path}}`) keeps working without a query change, so
  `monitor/grafana/zeebe.json` needs no edit.
- **Aggregation is deterministic.** `HealthReport.fromChildrenStatus` now surfaces, among
  equally-bad children, one that carries an issue (then breaks ties by name), so a partition with both
  its `DiskSpace` and `PartitionTransition` leaves unhealthy reports a stable, diagnostic aggregate
  instead of an arbitrary one.
- **Deferred.** Making `PartitionTransitionHealth.getHealthReport()` a pure read (so it is not
  recomputed on the metric scrape thread) is left as a follow-up: the "transition appears blocked"
  signal is time-based (computed on read, discovered by the periodic probe), so removing the
  recompute would require giving the transition a timer that pushes the issue — a behavioural change,
  not a cleanup. The leaf is instead made thread-safe (synchronized).

## Source

- [docs/zeebe/health.md](../../../docs/zeebe/health.md) — current-state description this ADR revises.
- Commit `b50ce17` — introduced the `Partition-<tenant>-<n>` qualification this ADR reverts (D2).

