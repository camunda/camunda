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

**D2. Every declared region is mandatory; there is no "optional region" concept.** A position is
confirmed / the exporter stays unpaused only while *every* declared region independently meets its
own `minReplicas`. A fully-down region pauses exporting
until it recovers or an operator changes config - there is no quorum-across-regions relaxation
(e.g. "2 of 3 regions"). This maximizes durability guarantees at the cost of pausing on a full
region outage, which was an explicit, considered trade-off (see Alternatives).

**D3. The primary's region is resolved dynamically from the connected write instance every check,
never from static config.** A failover can move the primary to a different physical node - and, in
a multi-region topology, potentially a different region - so a static `primaryRegion: us-east`
setting would silently go stale exactly when it matters most. Instead, each provider gains a
`getCurrentReplicaLabel()` read live from the primary's own connection - Postgres native: the
`cluster_name` GUC (a genuine per-instance self-identification setting, distinct from
`application_name`, which is a property of the *connecting* client); MSSQL: the local row's own
`replica_server_name` (`is_local = 1`, the same idiom `getCurrentLogStatus`/`getCurrentDbTime`
already use to mean "whichever replica this connection is local to"); Aurora Global Database: the
`MASTER_SESSION_ID` row's own `server_id`. That label is matched through the exact same region
patterns as replicas, and whichever region it resolves to gets one synthetic, always-best entry
credited to it, so operators size that region's `minReplicas` as the desired *total* healthy node
count (primary + secondaries) rather than secondaries-only - e.g. a 3-node region (1 primary + 2
secondaries) with `minReplicas=2` needs only 1 of its 2 secondaries caught up, wherever the primary
currently happens to be.

A region whose pattern is exactly the catch-all `.*` (see D4) never receives this credit, even if
the primary's label trivially matches it: a catch-all can't meaningfully claim to specifically host
the primary, and crediting it would silently satisfy a bare `minSyncReplicas=1` with the primary
alone and zero real replicas - a correctness bug caught in review before this ADR was finalized.

**D4. There is only ever one quorum code path: a flat `minSyncReplicas` is the degenerate case of a
single region matching every replica, not a separate mode.** `RegionAwareQuorum` always groups
replicas by region and applies the existing per-strategy "sort best-first, take the region's own
`minReplicas`, return the worst of that slice" math independently per region, then takes the worst
result across all of them - there is no `enabled` flag or separate flat branch. The simple
`RdbmsAsyncReplication.minSyncReplicas` convenience (in the `configuration` module) is converted at
the config-mapping boundary into a single synthesized region `{name: "default", pattern: ".*",
minReplicas: N}`; a `null` replica label is treated as the empty string during matching so this
catch-all still counts a replica that reports no label at all, exactly reproducing the old flat
count. This keeps `ExporterConfiguration.ReplicationConfiguration` down to a single `regions` field
- no `enabled` flag either, since region-awareness being "on" is fully implied by that list being
non-empty (always true once converted) - and the two strategies' output is unaffected either way
by construction, not by a separate compatibility branch.

**D5. `minSyncReplicas` and `regions` are mutually exclusive on `RdbmsAsyncReplication`, enforced at
config-mapping time.** Setting both is a configuration error (fails fast with a clear message)
rather than one silently overriding the other - see D4 for how the accepted one is turned into the
single `regions` shape the exporter-side code always deals with.

## Alternatives considered

- **Host/IP-based regex mapping.** Map `client_addr`/hostname patterns to regions instead of an
  application-level label. Rejected: no precedent in this codebase, and IPs are brittle behind
  NAT/cloud load balancers/proxies - the same replica can present different addresses over its
  lifetime.

- **Static `replicaId -> region` list, no regex.** Simplest config shape. Rejected: unusable for
  Postgres, whose `replicaId` (WAL-sender PID) changes on every reconnect.

- **A static `primaryRegion` config field naming which region hosts the primary**, instead of D3's
  live-resolved label. Simpler to implement (no new provider method, no per-vendor SQL). Rejected:
  a failover can move the primary to a different region without any config change, so a static
  setting would silently go stale and either wrongly credit the wrong region or wrongly withhold
  credit from the right one - exactly when a failover is the scenario this feature exists to handle
  correctly.

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
  extra field (`replicaLabel`) regardless of whether the operator declares real regions or leaves it
  at the synthesized flat default.
- The two existing signal strategies depend on a new shared `RegionAwareQuorum`/
  `ReplicaRegionResolver` pair rather than each independently implementing "sort, take top N,
  reduce" - future signal strategies should reuse it too.
- Every `computeConfirmedMarker`/`computePauseLag`/`regionsBelowQuorum` call now also issues one
  extra lightweight query (`getCurrentReplicaLabel()`) to resolve the primary's current region, even
  when no region is eligible for its credit (the flat/synthesized case) - accepted as negligible
  against the existing per-poll-cycle queries (default 15s interval) rather than special-casing it
  away.

## Source

- Discovery discussion and design options for this ADR were captured while scoping the
  `feat/async-repl-region-aware` branch.
