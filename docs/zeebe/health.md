# Broker health

This describes how a Zeebe broker tracks its own health, how that health is aggregated into a tree,
and how that tree is exported as metrics. It also explains broker *readiness*, which is related but
deliberately separate from the health tree.

## Three types

Health is built on three small types in `io.camunda.zeebe.util.health`:

- **`HealthMonitorable`** — something that *has* a health report (`getHealthReport()`) and can notify
  listeners when it changes (`addFailureListener` / `removeFailureListener`). Both leaves (e.g. the
  `StreamProcessor`) and aggregators implement this.
- **`HealthMonitor extends HealthMonitorable`** — something that *aggregates* the health of children
  it monitors. `CriticalComponentsHealthMonitor` (CCHM) is the only implementer.
- **`HealthTreeListener`** — observes structural changes of the tree (a node added or removed, with
  the node's `HealthNodePosition`) so that a projection of the tree — the `zeebe_broker_health_nodes`
  metric — can be derived from it. `HealthTreeMetrics` is the only implementer.

Everything else in the tree — `RaftPartition`, `StreamProcessor`, `ExporterDirector`, and so on —
implements only `HealthMonitorable`. These are the leaves: they report their own health but do not
aggregate a sub-tree.

## CriticalComponentsHealthMonitor

`CriticalComponentsHealthMonitor` (in `zeebe/scheduler`) is the building block of the tree. It is
**healthy only if all of its registered components are healthy**, and it is a *pure aggregator*: it
owns no metric state.

It tracks health two ways:

- **Failure listeners (push):** when a registered component's health changes, it informs the monitor,
  which recomputes its own health and informs *its* listeners upwards.
- **Periodic probing (pull):** every `HEALTH_MONITORING_PERIOD` (60s) the monitor re-reads the health
  report of each registered component, to catch changes that were not pushed.

A component can also be registered as *expected but not yet present* via `monitorComponent(name)`,
which records an `unknown` placeholder. This keeps the monitor from reporting healthy before a
component it is waiting for has actually been registered.

Each monitor carries an immutable `HealthNodePosition` (its `name`, full `path`, and the
`physicalTenant` / `partition` it is attributed to). When its set of children changes, it *announces*
the change — `onNodeRegistered(child, position)` / `onNodeRemoved(child)` — to a single
`HealthTreeListener`. It does not build paths, track relationships, or know anything about metrics;
that is the listener's job.

## The health-aggregation tree

The aggregation tree mirrors the domain: it is assembled from nested CCHMs, one interior level per
structural level of the system (broker, physical tenant, partition), with a partition's own
components as leaves.

```
BrokerHealthCheckService
  └─ CCHM  Broker-<id>                          the broker-level aggregator
       └─ CCHM  Tenant-<tenant>                 one per expected physical tenant
            └─ CCHM  Partition-<n>              the partition's own aggregator
                 ├─ RaftPartition               always present
                 ├─ DiskSpace                   always present (disk-space availability)
                 ├─ PartitionTransition         always present (transition lifecycle + dead latch)
                 ├─ StreamProcessor             registered per role transition
                 ├─ ExporterDirector            registered per role transition
                 ├─ SnapshotDirector            registered per role transition
                 └─ MigrationSnapshotDirector   registered per role transition
```

The broker-level CCHM (in `BrokerHealthCheckService`) aggregates one **tenant** CCHM per physical
tenant the broker is configured to run. Tenant nodes are created eagerly for every expected tenant
(the set is known from configuration at construction), so the tree is complete and predictable and an
empty-but-expected tenant is legitimately not-yet-healthy. Tenant CCHMs run on the broker health
actor.

Each tenant CCHM aggregates its **partition** CCHMs. A partition contributes **exactly one** node:
`ZeebePartition` owns a single partition CCHM and registers it under its tenant node (via
`BrokerHealthCheckService#registerMonitoredPartition`); `ZeebePartition` is not itself a tree node.
`RaftPartition`, `DiskSpace` and `PartitionTransition` are registered once when the partition actor
starts; the remaining components (`StreamProcessor`, `ExporterDirector`, `SnapshotDirector`,
`MigrationSnapshotDirector`) are registered and removed by the partition transition steps as the
partition changes Raft role, so the set of leaves changes over the partition's lifetime.

Health propagates upwards across actors by the same failure-listener mechanism at every level:
leaf → partition → tenant → broker.

### A partition's own health is explicit leaves

A partition's own health factors are first-class leaf nodes, not a bundled "intrinsic health"
component:

- **`DiskSpace`** reflects disk-space availability and starts healthy (available until a disk-usage
  monitor says otherwise).
- **`PartitionTransition`** reflects the role-transition lifecycle as a single concern — whether
  services are installed, the live `partitionTransition.getHealthIssue()`, and the sticky `dead`
  latch from an unrecoverable failure — and starts unhealthy ("services not installed"). "Once dead,
  stays dead" is a property of this one leaf; a `DEAD` leaf makes the partition CCHM (and the tree
  above it) report `DEAD` under the worst-wins aggregation.

### Identity is the object and its position

A node's identity is the object and its position in the tree; its display **name need only be unique
among its siblings**. `Partition-1` under tenant A and `Partition-1` under tenant B are distinct nodes
by position, so the name carries no structural information — the partition name is `Partition-<n>`,
not `Partition-<tenant>-<n>`. Each CCHM keeps its children in a sibling-scoped map keyed by name.

## How the tree is exported as metrics

Health computation is separate from health *export*, and export is a **projection** of the tree, not
a parallel graph. The single `HealthTreeListener`, `HealthTreeMetrics`, emits one
`zeebe_broker_health_nodes` gauge per node (`1` healthy, `-1` unhealthy, `-2` dead, `0` unknown).

Whenever a node enters or leaves the tree, the aggregating CCHM calls `onNodeRegistered(node,
position)` / `onNodeRemoved(node)`, and `HealthTreeMetrics` derives every tag from the supplied
`HealthNodePosition`:

- `id` — the node's `name` (e.g. `Broker-0`, `Tenant-default`, `Partition-1`, `StreamProcessor-1`,
  `DiskSpace`),
- `path` — the slash-joined chain of names from the root (e.g.
  `Broker-0/Tenant-default/Partition-1/StreamProcessor-1`),
- `physicalTenant` and `partition` — the tenant/partition the node is attributed to (or `none` for
  the broker- and tenant-level nodes).

Because there is exactly one node object per node and the listener is the single owner of the
projection, the metric cannot drift from the tree and cannot emit a node twice. All gauges share the
single broker meter registry; the tenant/partition attribution is set per gauge from the position, so
there is no per-partition registry or registry-wide common-tag mechanism for these nodes.

## Broker readiness

Readiness is tracked by `BrokerHealthCheckService` and is **independent of the health tree**. A
broker is ready (`isBrokerReady()`) when all of the following hold:

- `brokerStarted` — broker startup completed,
- every configured physical tenant has registered its bootstrap partitions
  (`registeredPhysicalTenants.containsAll(expectedPhysicalTenants)`), and
- every bootstrap partition, across all tenants, has been installed — i.e. became a Raft leader or
  follower (`partitionInstallStatus` contains no `false`).

Requiring *every expected tenant* to register is what upholds the invariant that **the broker does
not become ready until partitions from all physical tenants have started**. A tenant that never
starts contributes no partitions, so without this check an absent tenant could be mistaken for
"nothing to wait for". Readiness flips on any of three events (a partition install, a tenant
registering, broker startup completing), so each of them re-checks and logs the transition once.

`isBrokerHealthy()` combines the two: it is `false` unless the broker is ready, and otherwise
reflects the health-tree status of the broker-level CCHM.
