# Broker health

This describes how a Zeebe broker tracks its own health, how that health is aggregated into a tree,
and how that tree is exported as metrics. It also explains broker *readiness*, which is related but
deliberately separate from the health tree.

## Two interfaces

Health is built on two interfaces in `io.camunda.zeebe.util.health`:

- **`HealthMonitorable`** — something that *has* a health report (`getHealthReport()`) and can notify
  listeners when it changes (`addFailureListener` / `removeFailureListener`). Both leaves (e.g. the
  `StreamProcessor`) and aggregators implement this.
- **`HealthMonitor extends HealthMonitorable`** — something that *aggregates* the health of children
  it monitors. `CriticalComponentsHealthMonitor` (CCHM) is the only implementer.

Everything else in the tree — `RaftPartition`, `RaftContext`, `StreamProcessor`, `ExporterDirector`,
and so on — implements only `HealthMonitorable`. These are the leaves: they report their own health
but do not aggregate a sub-tree.

## CriticalComponentsHealthMonitor

`CriticalComponentsHealthMonitor` (in `zeebe/scheduler`) is the building block of the tree. It is
**healthy only if all of its registered components are healthy**.

It tracks health two ways:

- **Failure listeners (push):** when a registered component's health changes, it informs the monitor,
  which recomputes its own health and informs *its* listeners upwards.
- **Periodic probing (pull):** every `HEALTH_MONITORING_PERIOD` (60s) the monitor re-reads the health
  report of each registered component, to catch changes that were not pushed.

A component can also be registered as *expected but not yet present* via `monitorComponent(name)`,
which records an `unknown` placeholder. This keeps the monitor from reporting healthy before a
component it is waiting for has actually been registered.

## The health-aggregation tree

The aggregation tree is what actually computes broker health. It is assembled from nested CCHMs:

```
BrokerHealthCheckService
  └─ CCHM "Broker-<id>"                              the broker-level aggregator
       ├─ ZeebePartition (wrapper)                   one per partition, registered flat
       │    └─ CCHM "Partition-<tenant>-<n>"         the partition's own aggregator
       │         ├─ RaftPartition                    always present
       │         ├─ ZeebePartitionHealth             always present
       │         ├─ StreamProcessor                  registered per role transition
       │         ├─ ExporterDirector                 registered per role transition
       │         ├─ SnapshotDirector                 registered per role transition
       │         └─ MigrationSnapshotDirector        registered per role transition
       └─ ... one ZeebePartition per partition
```

The broker-level CCHM (in `BrokerHealthCheckService`) aggregates the `ZeebePartition` actors. Every
partition the broker runs — across **all physical tenants** — is registered flat on this single
level via `registerMonitoredPartition`.

Each `ZeebePartition` is only a thin wrapper for health purposes: its `getHealthReport()` delegates
to its **own** CCHM (`componentHealthMonitor`, created in the `ZeebePartition` constructor). That
inner CCHM aggregates the real partition sub-components. `RaftPartition` and `ZeebePartitionHealth`
are registered once when the partition actor starts; the remaining components (`StreamProcessor`,
`ExporterDirector`, `SnapshotDirector`, `MigrationSnapshotDirector`) are registered and removed by
the partition transition steps as the partition changes Raft role, so the set of leaves changes over
the partition's lifetime.

A consequence of this design is that the `ZeebePartition` wrapper and its inner CCHM **share the same
component name** — both are `Partition-<tenant>-<n>`. They are two different objects occupying the
same logical position in the tree: the wrapper is what the broker monitor sees, the inner CCHM is
what aggregates the children.

### Why component names carry the tenant

The broker CCHM keeps its components in a single flat `Map<String, …>` keyed by component name.
Partition numbers are **only unique within a physical tenant** — each tenant is its own partition
group, so `Partition-1` exists once per tenant. To avoid two tenants' partitions colliding on the
same map key, `ZeebePartition.componentName(PartitionId)` qualifies the name with the tenant
(partition group): `Partition-<tenant>-<n>`. The tenant prefix is therefore a property of the flat
map, not a structural level in the tree.

## How the tree is exported as metrics

Health computation is separate from health *export*. Export is done through the
`ComponentTreeListener` interface; the implementation is `HealthTreeMetrics`, which emits a
`zeebe_broker_health_nodes` gauge per node (`1` healthy, `-1` unhealthy, `-2` dead, `0` unknown).

A CCHM notifies its `ComponentTreeListener` when:

- it is constructed (it registers *itself* as a node, with its parent), and
- a component is registered or removed (it registers/unregisters that component as a node).

Each gauge is tagged with `id` (the component name) and `path` (the slash-joined chain of parents,
e.g. `Broker-0/Partition-default-1`). The path is reconstructed from the child→parent relationships
the listener has been told about.

There are **two** `HealthTreeMetrics` instances, bound to different meter registries:

| Instance       | Created in                                  | Meter registry            | Partition tags                          |
|----------------|---------------------------------------------|---------------------------|-----------------------------------------|
| Broker-level   | `Broker`                                    | global system registry    | `physicalTenant=none, partition=none`   |
| Per-partition  | `PartitionStartupAndTransitionContextImpl`  | partition-scoped registry | `physicalTenant=<tenant>, partition=<n>`|

The per-partition registry already carries `physicalTenant` and `partition` as common tags (set in
`MetricsStep`), so nodes registered through the per-partition listener are automatically attributed
to the right partition and tenant. The broker-level registry has no partition, so its listener uses
the `none/none` placeholder tags.

### Double emission of the partition node

Because the `ZeebePartition` wrapper and its inner CCHM share a name *and* use different listeners,
the partition node is emitted **twice**:

- the broker CCHM registers the `ZeebePartition` wrapper through the **broker-level** listener →
  `zeebe_broker_health_nodes{id="Partition-<tenant>-<n>", physicalTenant="none", partition="none"}`
- the inner CCHM registers itself through the **per-partition** listener →
  `zeebe_broker_health_nodes{id="Partition-<tenant>-<n>", physicalTenant="<tenant>", partition="<n>"}`

Both have the same `id` and `path`, differing only in the partition tags. The sub-components below
the partition are only registered through the per-partition listener, so they are emitted once, with
the correct tenant and partition tags.

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
