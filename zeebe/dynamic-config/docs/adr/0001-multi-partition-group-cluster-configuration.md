# Extend cluster configuration to support multiple partition groups

**DRI**: Deepthi Devaki Akkoorath

**Status**: Accepted

**Purpose**: Defines the data model, merge semantics, migration strategy, and phase-advancement
model for extending dynamic cluster configuration from a single Raft partition group to multiple
named groups.

**Audience**: Contributors working on `zeebe/dynamic-config`, cluster topology, and
physical tenant support.

## Context

The existing `ClusterConfiguration` type supports exactly one Raft partition group (the `default`
group) and conflates two distinct concerns: broker lifecycle state
(JOINING → ACTIVE → LEAVING → LEFT, managed cluster-wide) and partition assignment state (which
partitions a broker hosts in a specific Raft group). With multiple partition groups this conflation
breaks: a broker may be in the cluster but not yet assigned to any group, making it invisible in a
per-group config; and integer partition keys collide across groups (`default` partition 1 and
`groupA` partition 1 are different Raft replicas with the same key).

Physical tenant isolation requires each tenant to own a dedicated Raft partition group with
independent partition lifecycle, exporter state, routing state, and change plans. Scaling or
reconfiguring one tenant must not block another.

## Decision

**D1. Separate broker lifecycle state from partition assignment state using dedicated types.**
Introduce `GlobalConfiguration` — which tracks every broker in the cluster with its lifecycle
state and all cluster-level config, with no partition assignment — and
`PartitionGroupConfiguration` — which tracks per-group partition assignment and Raft replica
state, with no lifecycle state. Both are held in a new top-level `CurrentClusterConfiguration`
wrapper alongside an optional `PhasedChangePlan`. The existing `ClusterConfiguration` type is
retained for backward compatibility with REST-layer consumers during the migration period.

**D2. All partition groups are symmetric; `GlobalConfiguration` is the single authority for
cluster membership.**
Every broker is always visible in `GlobalConfiguration` regardless of which partition groups it is
assigned to. No partition group plays a special membership role. This ensures that a broker joining
the cluster before partition assignment, or completing a leave, is never invisible to coordinators,
REST handlers, or initializers.

**D3. Merge semantics with config-version-based fast path per sub-type.**
`GlobalConfiguration` and `PartitionGroupConfiguration` each carry a `version` incremented only
at plan start and plan end. If two copies have different versions, the higher version wins
wholesale. If equal, members are merged field-by-field using per-member versions. The top-level
`CurrentClusterConfiguration.version` is always `INITIAL_VERSION` and is not used in merge
decisions — it is reserved for a potential future root-level fast path.

**D4. Dual-write gossip for rolling-restart safety, without a feature gate.**
In 8.10, upgraded brokers populate both `GossipState.clusterTopology` (field 1, legacy) and
`GossipState.currentClusterConfiguration` (field 2, new) on every gossip send. Old brokers (8.9) read
field 1 only and continue to receive live updates from upgraded nodes. New brokers check field 2
first; if absent they migrate field 1 via `ofDefault()`. No feature gate is needed on the gossip
path itself.

**D5. Persistence header version discriminator for transparent first-boot migration.**
The existing `PersistedClusterConfiguration` header version field discriminates the body format:
version 1 = legacy `ClusterTopology` body; version 2 = `CurrentClusterConfiguration` body. On
first boot after an upgrade the broker reads the v1 body, migrates it via `ofDefault()`, and
writes back v2. No operator action or separate migration step is required.

**D6. Pre-computed `PhasedChangePlan` for cluster-spanning operations.**
Operations that span cluster membership and partition groups are sequenced in a `PhasedChangePlan`
with all phases pre-computed at plan creation. Each phase is either a `GlobalPhase`
(operations written to `GlobalConfiguration.pendingChanges`) or a `PartitionGroupParallelPhase`
(operations written atomically to the named groups' `pendingChanges`). Phases are read-only
templates; activating a phase copies its operations into sub-config `pendingChanges`. This enables
coordinator restart recovery without re-deriving the plan from observed state, and ensures phase
activation is deterministic and idempotent.

**D7. The coordinator is the sole actor responsible for phase advancement.**
Phase transitions — detecting that the active phase is complete and activating the next phase —
are performed exclusively by the coordinator (the ACTIVE broker with the lowest member ID). This
preserves the existing coordinator-driven execution model. As a result, the coordinator is a
liveness dependency for phase transitions: a coordinator failure mid-plan stalls phase advancement
until a new coordinator is elected and detects its role. Eliminating this dependency by allowing
any ACTIVE broker to advance phases (since all phase operations are pre-stored) is deferred as a
follow-up improvement.

## Alternatives considered

- **Option A' (wrapper with `default` group as membership authority).** No dedicated
  `GlobalConfiguration`; the `default` partition group's config holds all cluster members.
  Rejected because every consumer that needs "all cluster members" must know to look in the
  `default` group, leaking special-casing into coordinators, REST handlers, and initializers, and
  breaking if a broker is not assigned to `default`.

- **Option B (additive `partitionGroups` field in existing `ClusterConfiguration`).** Keep
  `ClusterConfiguration` unchanged for the default group; add an optional `partitionGroups` map
  for non-default groups. Rejected because it cannot represent different partition counts for the
  default group cleanly and requires a later migration to Option A.

- **Option C (compound partition key `(groupId, partitionId)` throughout).** Replace
  `int partitionId` with a compound key in the data model. Rejected because it cannot provide
  independent per-group change plans: operations for different groups are interleaved in one global
  queue, and a stuck operation in one group blocks all groups.

- **Option D (fully federated: one `ClusterConfigurationManager` per partition group).**
  Independent manager, gossip topic, and persistence file per group. Rejected because it multiplies
  actor/thread overhead by the number of partition groups, introduces bootstrap ordering
  constraints, and its aggregated state converges to Option A's data model.

## Consequences

- Per-partition-group change plans, routing state, and exporter state are fully independent;
  concurrent single-group operations (exporter changes, per-group reconfiguration) require no
  global coordination.
- All brokers are always visible via `GlobalConfiguration` regardless of group assignment; broker
  join/leave operations and per-group partition operations are cleanly separated by type.
- Wide blast radius: every consumer of `ClusterConfigurationService` must be updated or shielded
  behind a compatibility accessor during the migration.
- Rolling restart safety is provided without a feature gate, but upgraded brokers must dual-write
  gossip throughout the upgrade window.
- The coordinator is a liveness dependency for phase transitions in cluster-spanning operations;
  coordinator failure mid-plan stalls phase advancement until a new coordinator detects its role.
- The existing `ClusterConfiguration` type is retained for backward compatibility during migration
  and removed only after all consumers are updated.

## Source

- [Solution proposal (internal)](https://docs.google.com/document/d/1za8mqxQVL37VSbM2myi3zBr-CbkPU91yDTiuWlfXMQg/edit?usp=sharing)
- [Issue #56018](https://github.com/camunda/camunda/issues/56018)

