# ECV tracking and commit (POC)

Proof of concept of the design in `pdp-3722-two-phase-upgrade`:
`solutioning/distributed-systems/ecv-tracking-commit/design.md` and `requirements.md`. It runs on
top of the system partition POC (`io.camunda.zeebe.broker.system.syspartition`) and is active only
when the system partition is enabled (`camunda.cluster.system-partition.enabled=true`).

## What it does

| Design section | Where |
|---|---|
| 1 Supported capabilities, gossiped by each broker | `EcvManagement#publishSupported`, `EcvProtocol.SupportedData` |
| 2 Upgrade readiness, a registry per broker, gossiped | `UpgradeReadinessRegistry`, `EcvProtocol.ReadinessData` |
| 3 Each partition leader pulls the decision and writes the commit command | engine: `ClusterCapabilities*` in `zeebe/engine`, broker: `PartitionCapabilitiesRegistry` |
| 4 Progress of a physical tenant | progress query `EcvProtocol.PROGRESS_SUBJECT`, `CommitEvaluator` |
| 5 Commit checks, commit, automatic commit, state read | `EcvManagement`, `CommitEvaluator`, actuator `GET/POST /actuator/ecv` |
| 6 Check before a broker takes part | `EcvManagement#selfCheck`, `PartitionManagerStep` |
| 8 Local view of the enabled capabilities | `LocalEnabledCapabilitiesView`, `EcvProtocol.EnabledData` |
| R29 Initial enabled set | `EcvManagement#initializeEnabledSet` |

The committed ECV is one key of the system partition (`SystemPartitionKeyType.ENABLED_CAPABILITIES`)
with an encoded `CapabilitySet` (a bitmask as a hexadecimal number). Every writer puts a union, so
the set only grows, and a binary that does not know a bit keeps it.

## Choices made for the POC

Open points and contradictions in the documents, and what the POC does:

- **Open point 14, the source of the enabled set for the self-check.** Both the current proposal
  and the idea are implemented. The broker keeps a local view of the committed ECV on disk
  (`<data>/ecv/enabled-capabilities`). At startup it first compares its supported capabilities
  with this view: if the view already has a capability that the broker does not support, the
  broker is refused without the system partition. Otherwise it asks the system partition leader
  and asks again until a leader answers (R24). The leader is the source of truth.
- **A refused broker and the system partition.** A refused broker still runs its replica of the
  system partition and its ECV management (current proposal of section 6). Only the partitions of
  the physical tenants are not started. The partition manager is registered but not started, so
  the rest of the broker (gateway, management API) runs and shows the reason.
- **Open point 13, members of the system partition.** The system partition POC takes its members
  from the static configuration, so the system partition does not depend on the dynamic
  configuration and a new cluster starts.
- **R29, initial enabled set.** When the system partition has no set, the cluster is bootstrapping:
  a new cluster, an upgrade from 8.10 (8.10 has no system partition) or a restore (a backup does
  not include the system partition). The leader writes all capabilities that it supports. When the
  system partition has a set, the leader keeps it. Raft elects only a broker with an up-to-date
  log, so the decision does not depend on the disk of the leader (design section 11, Nov 5, 2026).
- **Requirements open point 7, the base set.** The catalog marks each capability with the minor
  that introduced it. The base set is every capability introduced in 8.11. It is no longer the
  initial set; the POC uses it only in tests.
- **Open point 10, an 8.11 leader of a tenant partition.** The leader writes the commit command only
  if its broker supports the whole view. Otherwise it waits for a leader that does.
- **Open point 11, an 8.11 leader of the system partition.** A request to a broker that is not the
  leader, or not initialized, answers "not the leader"; the requester asks the next member, and
  the API returns `UNAVAILABLE` (HTTP 503) if no leader answered. The implementation can find the leader through the
  gossip of the partition leaders instead, as for the other partitions (see `askLeader`).
- **Contradiction: "the gateway gates on capabilities" (API decision) against "only the engine and
  the data layer check capabilities".** The POC does not gate the gateway.
- **Contradiction: the data layer stops the whole node against "a refused broker keeps running".**
  The POC follows the DS design: only the tenant partitions are refused.
- **The user API.** APIs & Process Automation owns it and it goes into the REST API. The POC uses an
  actuator endpoint on the broker to exercise what DS does after the gateway.
- **Old values in the gossip data (section 1).** The supported set and the readiness carry the
  random id of the broker start that wrote them. The leader ignores a readiness from another start.
  The enabled set has no id: it is committed, so an old value is still valid.
- **Readiness.** The broker registers one component, `tenant-partitions`, which is ready once the
  self-check passed and the partitions were started. Other components register in
  `UpgradeReadinessRegistry`; none do yet. A natural next source is the startup migrations behind
  `/actuator/upgradeReadiness`.
- **Gateways.** A member outside the cluster configuration that gossips its supported capabilities
  counts for the ready capabilities. Standalone gateways do not gossip them yet.

- **Only grows, also between two commits.** A put completes when Raft commits it, before the state
  machine of the leader applies it. The leader therefore uses the union of its replica and its
  local view, which has each decision as soon as its put completed, and every decision is a union
  with that set.

## Known limitations

- A follower that installs a snapshot of the system partition does not notify the watchers of the
  key-value store. Its local view then gets the enabled set through gossip only.
- The progress of a partition is reported when its leader processes the commit command, before the
  event is committed. The state read can show a partition as applied slightly too early.

## Not in the POC

- R4.2 of `dynamic-config.md`: a refused broker still writes the topology file and gossips the
  dynamic configuration.
- Section 7, the enabled set for a new physical tenant: its partitions get the decision through the
  pull of section 3 instead.
- Section 9, restricted requests of the dynamic configuration while capabilities are uncommitted.
- The gateway rejecting new endpoints before the commit.
- Restore (R30) and the refusal of an upgrade from a lower 8.11 patch.
