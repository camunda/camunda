# Region-aware quorum for RDBMS async replication

**DRI**: Christian Thiel

**Status**: Accepted

**Purpose**: Defines how the RDBMS exporter's async-replication quorum becomes optional,
per-region aware, for multi-region database topologies, instead of a single flat replica count.

**Audience**: Contributors working on `zeebe/exporters/rdbms-exporter` and `db/rdbms` replication
monitoring.

## Context

`DefaultReplicationController` acknowledges a flushed exporter position once `minSyncReplicas` -
a flat integer - replicas report a marker at or above it (see `LsnReplicationSignalStrategy` /
`TimeMonitoringReplicationSignalStrategy`). This has no concept of *where* a replica lives. For a
topology of 3 regions x 2 nodes each, a flat count can't express "always keep at least N nodes
healthy per region": `minSyncReplicas=3` tolerates losing a whole region but not a second node
afterward; `minSyncReplicas=4` guarantees one replica per region but tolerates only a single node
loss overall. Neither expresses the actual intent operators have for multi-region deployments.

No vendor this exporter supports (Postgres native streaming replication, MSSQL Always On AG,
Aurora Global Database) exposes a queryable "region" concept on its replication status views. Each
vendor does, however, expose a connection-level label an operator controls when provisioning a
replica: Postgres `application_name`, MSSQL AG `replica_server_name`, Aurora Global Database
`server_id`.

## Decision

**D1. Region membership is resolved from a self-declared replica label via operator-configured
regex, not from host/IP.** Every `ReplicationStatus` gains a `replicaLabel()` - the vendor label
above - distinct from the existing `replicaId()` (which identifies a connection/session, e.g.
Postgres' WAL-sender PID changes on every reconnect, and is unfit for stable region matching).
Region-awareness config declares an ordered list of `{name, pattern, minReplicas}`; a replica's
label is matched against each pattern in order, first match wins. This mirrors the self-declared
`cluster.zone` idiom already used for Zeebe broker zone-awareness
(`ClusterCfg`/`ZoneCfg`/`ZoneAwarePartitionDistributor`) rather than inferring region from
host/IP, which has no precedent in this codebase and is brittle behind NAT/cloud load balancers.

**D2. Every declared region is mandatory; there is no "optional region" concept.** When region
awareness is enabled, a position is confirmed / the exporter stays unpaused only while *every*
declared region independently meets its own `minReplicas`. A fully-down region pauses exporting
until it recovers or an operator changes config - there is no quorum-across-regions relaxation
(e.g. "2 of 3 regions"). This maximizes durability guarantees at the cost of pausing on a full
region outage, which was an explicit, considered trade-off (see Alternatives).

**D3. The primary's region gets automatic credit for the primary itself.** An optional
`primaryRegion` config field names which declared region hosts the primary. That region's quorum
evaluation includes one synthetic, always-best entry representing the primary, so operators size
`minReplicas` as the desired *total* healthy node count for that region (primary + secondaries)
rather than secondaries-only - e.g. a 3-node region (1 primary + 2 secondaries) with
`minReplicas=2` needs only 1 of its 2 secondaries caught up.

**D4. The existing per-strategy "sort best-first, take top N, return the worst of that slice"
quorum math is generalized, not replaced.** Both `LsnReplicationSignalStrategy` and
`TimeMonitoringReplicationSignalStrategy` already rank replicas this way for the flat case. With
region awareness enabled, the same reduction is applied independently per region (using that
region's own `minReplicas` as N), and the overall result is the worst value among all mandatory
regions. This keeps the two strategies' output byte-identical to today when region awareness is
disabled (the default), and reuses one shared implementation (`RegionAwareQuorum`) instead of
duplicating region-grouping logic in each strategy.

**D5. `minSyncReplicas` is ignored, not combined, when region awareness is enabled.** The two
config knobs are mutually exclusive by design to avoid ambiguous semantics; this is documented on
the config rather than enforced by cross-field validation, keeping the config model simple.

## Alternatives considered

- **Host/IP-based regex mapping.** Map `client_addr`/hostname patterns to regions instead of an
  application-level label. Rejected: no precedent in this codebase, and IPs are brittle behind
  NAT/cloud load balancers/proxies - the same replica can present different addresses over its
  lifetime.

- **Static `replicaId -> region` list, no regex.** Simplest config shape. Rejected: unusable for
  Postgres, whose `replicaId` (WAL-sender PID) changes on every reconnect.

- **Optional/best-effort regions (a per-region `required: false` flag, or "N of M regions
  synced").** Would restore tolerance for a full region outage, matching the motivating example
  literally. Rejected in favor of D2 after discussion: the operator preferred maximizing durability
  guarantees (every declared region mandatory) over region-outage tolerance, accepting that a fully
  down region pauses the exporter until an operator intervenes. This can be revisited as a
  follow-up if real deployments need it.

- **Delegate to vendor-native multi-region primitives** (e.g. Aurora Global Database's own
  cross-region durability semantics) instead of a generic controller-level quorum. Rejected:
  inconsistent across vendors, doesn't cover Postgres native streaming replication (the
  default/most-used strategy), and Aurora Global DB doesn't expose a synchronous per-region quorum
  API anyway - it's asynchronous cross-region replication by design, exactly what the existing
  LSN/lag strategies already measure.

## Consequences

- Operators of multi-region RDBMS topologies can express per-region redundancy requirements
  directly, instead of approximating them with a single count that can't represent regional
  distribution.
- A fully-down mandatory region always pauses exporting - this is a deliberate durability-over-
  availability trade-off (see D2); operators who need the opposite trade-off must remove or adjust
  that region's config manually until support for optional regions (if any) is added.
- `ReplicationStatus` implementations, and the Postgres/MSSQL/Aurora mapper queries, carry one
  extra field (`replicaLabel`) whether or not region awareness is used.
- The two existing signal strategies depend on a new shared `RegionAwareQuorum`/
  `ReplicaRegionResolver` pair rather than each independently implementing "sort, take top N,
  reduce" - future signal strategies should reuse it too.

## Source

- Discovery discussion and design options for this ADR were captured while scoping the
  `feat/async-repl-region-aware` branch.
