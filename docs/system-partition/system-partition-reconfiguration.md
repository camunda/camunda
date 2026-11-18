# System partition reconfiguration

Status: draft, revision 7 (after review rounds 1 to 3, the PoC, and the change to leader-driven membership). Issue: [camunda/pdp-3723-dual-region-failover-detection#42](https://github.com/camunda/pdp-3723-dual-region-failover-detection/issues/42).

## 1. Summary

The system partition is a Raft partition. Each broker of the cluster has a replica of it. Only some brokers are voters. The other brokers are passive members. A passive member replicates only committed entries. It does not vote and it does not count toward the quorum.

This document tells how the cluster sets and changes the voters and the passive members of the system partition.

The design has these main rules:

1. The Raft configuration of the system partition is the only record of its members and their types. The dynamic configuration (`ClusterConfiguration`) does not record them.
2. The voter policy sets the number of voters for each zone and the order of the zones. It comes from the static configuration. The system partition state stores it as a record, not the Raft configuration. An operation can change it with a normal write to the system partition. Each broker keeps a local copy for its election priority at restart.
3. At the first start, all brokers calculate the same initial configuration from the voter policy. They bootstrap only when all brokers of the static configuration agree that no system partition exists. When its initial entry commits, the first leader appends a configuration entry before any other entry. Thus, a configuration index above 0 shows that the system partition exists.
4. At a restart, a broker starts the system partition with the Raft configuration that it stored on its disk.
5. The leader of the system partition makes all normal changes of the members. It monitors the cluster members and changes the Raft configuration to agree with them. It adds an authorized broker (`JOINING`, `ACTIVE`, or the target of a pending `MemberJoin`) as a passive member. It removes a broker that leaves. It promotes caught-up passive members into empty voter slots, and it demotes extra voters. The joining or leaving broker does not change the Raft configuration.
6. A force-remove plan changes the system partition first. It uses a forced Raft change only if the remaining voters are not a quorum. A forced change does not need a leader. Before a forced change, the executor checks that the members that answered are a blocking set of each voter set. If they are not, a committed entry can be lost, and the operator must accept this explicitly (`acceptPossibleLoss`).
7. When the system partition has no quorum and the failed brokers must stay cluster members, the operator uses "force system partition voters". It sets new voters and makes all other members passive. It removes no member. The completeness check of rule 6 also applies.
8. A broker without system partition data starts its replica without a configuration, with a new incarnation. It publishes the incarnation in its membership properties and waits until the leader adds it. It does not vote, and it accepts only a configuration in which it is a passive member with its own incarnation.
9. A voter that lost its disk comes back with a new incarnation. The leader replaces it as it replaces a leaving voter. The broker becomes a voter again only after it has caught up.
10. Each removal and each forced change puts the brokers on a fence list. A fence entry has the broker id and the incarnation of the broker. A fenced broker must delete its data and start again with a new incarnation. Fence entries are never cleared.
11. The dynamic configuration starts after the system partition. The start of the system partition does not read it. Only the leader reads the cluster members, at runtime, after its dynamic configuration is initialized. Later, the leader reads them from the system partition state.

Revision 6 adds the results of the proof of concept (PoC). Section 9 tells what the PoC shows and what it does not cover.

Revision 7 moves the normal changes of the members from the joining or leaving broker to the leader (D2). It also moves the voter policy from the Raft configuration to the system partition state (D18). Raft then stores only what it enforces itself: member types, incarnations and the fence list. A joining broker cannot read the dynamic configuration when the dynamic configuration is in the system partition, because its replica has not caught up. The PoC implements revision 6. Section 9.7 lists what revision 7 changes for it.

Revision 7 also takes these changes from the review of an alternative design (`combined-design.md`, C1 to C10, in the worktree `system-partition-config-review`): the authorization of pending `MemberJoin` targets (5.5), recovery from a joint configuration and the completeness check before a forced change (5.6.3), the replacement of an incarnation only when the old one is silent and the promotion with the lag threshold (5.6.4), the completion of a wait only on a committed, non-joint configuration (5.6.2), and the validation, activation, disaster-recovery bootstrap and status of 5.11. Section 5.12 proposes the management API, and 5.4.5 tells a new cluster from an existing cluster.

## 2. Context and constraints

These decisions are already made. This design does not change them, except where Q2 says so.

|             Source              |                                                                                                                                                                                                    Decision                                                                                                                                                                                                     |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Decisions, Nov 10, 2026         | Each broker has a replica of the system partition. The configured voters make the quorum. The other brokers are non-voting members. A broker reads committed entries from its local replica. The system partition of a broker is not accessible until it has caught up with the leader's commits.                                                                                                               |
| Decisions, Oct 23, 2026 (#21)   | When the control region fails, the cluster does not move the control vote to a data region. If the control region and one data region fail, recovery is manual: fence the failed regions, force-reconfigure the system partition to the surviving voters, then the cluster fails over through the normal path. The control region comes back as a non-voting member. It becomes a voter after it has caught up. |
| Decisions, Oct 22, 2026 (#7)    | Brokers of a lost region that start again do not join the cluster until the cluster adds the region back.                                                                                                                                                                                                                                                                                                       |
| Decisions, Oct 26, 2026         | No failover on partial region failures. The rolling conversion from dual-region to 2.5 regions is in scope for 8.11.                                                                                                                                                                                                                                                                                            |
| Decisions, Oct 29, 2026 (#20)   | Only zone-aware deployments are supported for automatic failover: two data zones of equal size and one control zone.                                                                                                                                                                                                                                                                                            |
| Issue #42                       | The system partition will later hold the dynamic configuration. Its leader will be the configuration coordinator. Thus, the system partition must not depend on the dynamic configuration to keep its own Raft members.                                                                                                                                                                                         |
| Decided Nov 17, 2026 (#186)     | A broker's restart must not depend on the availability of the system partition leader.                                                                                                                                                                                                                                                                                                                          |
| Design constraint, Nov 17, 2026 | The dynamic configuration starts after the system partition. Thus, the start of the system partition cannot read the dynamic configuration.                                                                                                                                                                                                                                                                     |

Thus, the system partition must be able to change its Raft configuration when the dynamic configuration cannot change. This is necessary for the forced recovery in #21.

## 3. Terms

|        Term        |                                                                                                                   Meaning                                                                                                                   |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Voter              | A member of type `ACTIVE` in the Raft configuration. It votes and it counts toward the quorum.                                                                                                                                              |
| Passive member     | A member of type `PASSIVE`. The leader sends it only committed entries and snapshots. It does not start elections.                                                                                                                          |
| Member             | A voter or a passive member.                                                                                                                                                                                                                |
| Incarnation        | A 64-bit id of a member in the Raft configuration. The members of the initial configuration have incarnation 0. A broker that starts without data generates a new random incarnation and publishes it in its membership properties (5.4.4). |
| Authorized broker | A broker that is `JOINING` or `ACTIVE` in the dynamic configuration, or the target of a `MemberJoin` in the pending plan, and that is not `LEAVING`, `LEFT`, or the target of a pending `MemberLeave` or `MemberRemove` (5.5). |
| Blocking set | A set `R` of members is a blocking set of a voter set `V` if `R` has at least `n - q + 1` voters of `V`. `n` is the number of voters of `V`, and `q = floor(n/2) + 1` is its quorum. Each quorum of `V` then has a member of `R` (5.6.3). |
| Membership version | The version of the dynamic configuration that the leader used for its last change of the members. The Raft configuration stores it (5.5).                                                                                                   |
| Voter policy       | The number of voters for each zone, and the order of the zones for the election priority. The system partition state stores it (5.1).                                                                                                       |
| Voter slot         | One voter position in a zone. A zone with a voter count of 2 has 2 slots.                                                                                                                                                                   |
| Cluster member     | A broker that the dynamic configuration shows as `JOINING`, `ACTIVE` or `LEAVING`.                                                                                                                                                          |
| Fence list         | A list of entries in the Raft configuration (R8). Each entry has a broker id and an incarnation. The entries are brokers that were removed, and brokers that a forced change made passive.                                                  |
| Most recent log    | The log with the highest last term. If the last terms are equal, the log with the highest last index.                                                                                                                                       |
| Control zone       | The first zone of the voter policy. It has the highest election priority. A dual-region policy has no control zone. Its first zone is a data zone.                                                                                          |
| Fenced notice      | The committed configuration that the leader sends to a fenced member, in a `ConfigureRequest` that is marked as committed (5.6.6).                                                                                                          |

## 4. What the code does today

The facts below are from `main` at `004b815e921`.

### 4.1 Raft

Raft paths are relative to `zeebe/atomix/cluster/src/main/java/io/atomix/raft`.

- Raft supports non-voting members. `RaftMember.Type` has `INACTIVE`, `PASSIVE`, `PROMOTABLE` and `ACTIVE`. The quorum, the votes and the commit index use only `ACTIVE` members (`RaftClusterContext.getQuorumFor`, `getVoteQuorum`).
- The leader sends only committed entries to a `PASSIVE` member (`RaftMemberContext`: `PASSIVE -> log.openCommittedReader()`). A passive member also receives snapshots (`PassiveRole.onInstall`).
- A passive member has no election timer. But `anoint()` does not check the member type (`ReconfigurationHelper`), thus it can make a passive member a candidate.
- A passive member gives its vote if the candidate's log is up to date (`PassiveRole.handleVote`). A vote does not check if the candidate is a member. An empty log gives any vote (`PassiveRole.isLogUpToDate`). Candidates ask only `ACTIVE` members.
- The data-loss check is off for passive members, and it cannot detect an empty disk.
- A member that must truncate below its commit index fails (`RaftLog.deleteAfter`).
- Raft stores its configuration in the `.conf` file of the partition (`MetaStore.storeConfiguration`). It stores the member types and the `force` flag. It writes the file when a configuration commits, and at once for a forced or initial configuration.
- At a restart, `RaftClusterContext.bootstrap` loads the stored configuration, then newer configuration entries from the log. It uses the given member list only when no configuration exists. Then `createInitialConfig` makes **all** members `ACTIVE`.
- A new leader appends only an initial entry (`InitialEntry`, `LeaderRole`). It does not append a configuration entry. Thus, a partition that never changed its members keeps configuration index 0.
- `RaftPartition.bootstrap` does nothing if the local broker is not in the given member list. `RaftPartition.join` always creates a new server. It does not close a server that runs.
- Each change uses joint consensus, also a change of type only. The leader accepts only one change at a time.
- `LeaderRole.onReconfigure` rejects a request if its configuration index is **lower** than the current index, or if its term is not the current configuration term. An index of 0, or a higher index, passes the index check. If the members and types are already the target, it returns OK (`equalMembership`).
- The leader promotes a member to `ACTIVE` only if the member has caught up (`matchIndex > 0`, and `matchIndex >= commitIndex` or a lag below `promotionLagThreshold`). The check does not look at when the member last answered. Production promotes data partition members with `PartitionPromoteOperation` (`PartitionManagerImpl.promote`, `RaftPartitionServer.promoteMember`).
- `LeaderRole.onJoin` of a member that is already in the configuration with the same or a higher type writes no new configuration.
- After a removal commits, the leader stops sending to the removed member. The removed member never gets the newer configuration.
- `ReconfigurationHelper.forceConfigure(Map<MemberId, Type>)` accepts member types. A retry with the same member ids is accepted. A retry with different member ids is rejected. `ForceConfigureQuorum` needs an answer from **each** member of the new configuration, and accepts 0 failures.
- After a forced change, the next leader appends a normal configuration. Then the `force` flag is cleared, and `AbstractRole.shouldAcceptRequest` accepts requests from all brokers again.
- Election priority is not in the Raft configuration. `RaftPartitionServer` reads it from `PartitionMetadata` at each start. `reconfigurePriority` changes the node priority at runtime. The target priority cannot change at runtime (`PriorityElectionTimer`).
- Raft has no listener for configuration changes. `RaftPartition.members()` returns only ids. During joint consensus, `getMembers()` returns the old and the new members together.
- The leader sends entries directly to each member. The work of the leader increases with the number of members.

### 4.2 Broker and dynamic configuration

Broker paths are relative to `zeebe/broker/src/main/java/io/camunda/zeebe/broker`. Dynamic configuration paths are relative to `zeebe/dynamic-config/impl/src/main/java/io/camunda/zeebe/dynamic/config/impl`.

- The data partitions start from the `ClusterConfiguration` (`PartitionManagerImpl.start`). After the first start, Raft uses its stored configuration.
- `Partition.forceReconfigure` sets each member to `ACTIVE`.
- The member operations are `MemberJoin`, `MemberLeave` and `MemberRemove`. The joining or leaving broker applies `MemberJoin` and `MemberLeave`. The coordinator applies `MemberRemove`, with `MemberLeaveApplier`.
- `MemberJoinApplier` applies again on each retry and after each restart while the broker is `JOINING`.
- Global operations run one at a time. A failed operation is retried after 10 seconds to 1 minute. An operation that never completes is not stopped.
- The global appliers are registered before the configuration is initialized, in `ClusterConfigurationManagerStep`. This is before the partition start steps.
- A scale-down adds its `MemberLeave` operations after the partition phases. A scale-up adds `MemberJoin` before them.
- A force-remove plan (`ForceScaleDownRequestTransformer`) first force-reconfigures each data partition. Then it applies one `MemberRemove` for each removed broker. "Remove zone" with `force=true` uses the same transformer. `RemoveZoneTransformer` selects the coordinator as the lowest member outside the removed zone. This can be a broker that is down.
- `MemberId.ID_COMPARATOR` sorts by node index, then by zone. The zone of a broker is part of its `MemberId` (`<zone>_<nodeIdx>`).
- On `main`, a zone has no "control" role, and `numberOfReplicas` must be 1 or more. The earlier failover PoC adds `ZoneRole.CONTROL`. (This is not the PoC of section 9.)
- Zone priorities are in `ZoneSpec` and can change at runtime (`UpdateZonePrioritiesTransformer`).
- At start-up, the `FileInitializer` loads the stored dynamic configuration first. A broker that was down while it was removed thus sees itself as a member until it receives a newer configuration from other brokers.
- With dynamic node ids, each lease creates a new versioned data directory. The data partitions copy their data from the previous version (`VersionedNodeIdBasedDataDirectoryProvider`).

## 5. Design

### 5.1 Voter policy

**Properties.**

- Zone-aware cluster: each zone has a property `system-partition-voters`. Example: `zone-a: 2`, `zone-b: 2`, `control: 1`.
- Cluster that is not zone-aware: one property `camunda.cluster.system-partition.voters`.

**Default values.**

- Data zone: the `numberOfReplicas` of the zone.
- Control zone: 1. This needs the control role of the earlier failover PoC (`ZoneRole.CONTROL`, 4.2).
- Cluster that is not zone-aware: the largest odd number that is not more than the replication factor and not more than the cluster size.

**Validation at start-up.**

- Each zone: `0 <= voters <= numberOfBrokers`.
- The total number of voters must be 1 or more.
- If automatic failover is enabled: the control zone must have voters, and the two data zones must have the same number of voters.
- If the total is even, the broker logs a warning.

**Storage.** The system partition state stores the policy as one record. The Raft configuration does not have it. The policy has the voter count of each zone and the order of the zones. Its version is the log index of the record. The order comes from the zone priorities of the static configuration at the bootstrap. Later changes of the zone priorities in the dynamic configuration do not change it.

**Policy cache.** Raft starts elections before the broker has applied the system partition state. Thus, each replica, also a passive one, writes the policy and its version to a cache file in the system partition directory when it applies a policy record. The write is atomic. The cache is not part of Raft. It is a hint for the election priority only (5.3). A missing or old cache does not affect safety.

**Control zone and zones.** The control zone is the first zone of the policy. The policy has no other field for it. The broker that makes a policy (the bootstrap, or "update system partition voter policy") puts the control zone first, then the data zones in the order of their priority. A dual-region policy starts with the data zone of the highest priority. The zone of a member is the zone of its `MemberId`. A member without a zone is in the zone `""` (empty). A zone that is not in the policy has 0 slots.

**Initial policy.** The initial configuration (index 0) is calculated from the policy of the static configuration (5.4.1). The probe compares the hash of the static policies (5.4.2). After its configuration entry of R14 is committed, the first leader writes the static policy as the first record of the system partition state. If a leader finds no policy record in the state, it writes the record. The state machine ignores a second initial record. The leader does not change the members until it has applied a policy record (5.5).

**Policy at start-up.** The broker uses the policy of the cache. If no cache exists, for example at the first start or at a start without data, it uses the static policy. When the broker has applied the state, it uses the policy of the state. If the static policy and the policy of the state are different, the broker logs a warning and uses the policy of the state.

**Change.** The operation "update system partition voter policy" writes a new policy record to the system partition, as other system partition writes do. It needs a quorum. When the leader has applied the record, it promotes passive members into new slots and demotes extra voters (5.6.4). Each replica updates its cache and its priority when it applies the record. Use cases:

- Rolling conversion from dual-region to 2.5 regions (decision of Oct 26): add the control zone, then set `control: 1`.
- Add a zone.

### 5.2 Voter selection

`selectVoters` is a pure function:

```
selectVoters(configuration, excluded) -> targetConfiguration
```

- `configuration` must be a committed configuration that is not in joint consensus. The function uses only its new members and the voter policy of the state. If a joint change is in progress, the caller waits.
- `excluded` are brokers that are not `JOINING` or `ACTIVE` in the dynamic configuration, and the targets of pending `MemberLeave` and `MemberRemove` operations. They are never selected as new voters. The leader calculates this set from its dynamic configuration, only after it is initialized.

For each zone:

1. Keep the current voters of the zone.
2. If the zone has more voters than slots, the voters with the highest node index are extra. The leader demotes them (5.6.4).
3. If the zone has fewer voters than slots, the slots stay empty. The leader fills them with caught-up passive members (5.6.4).

Members of a zone that is not in the policy are always passive, because the zone has 0 slots. The liveness of a broker never changes the voters (D6).

The leader rejects a promotion if the member did not answer within the election timeout (R9). Thus, a broker that is down is never promoted.

### 5.3 Election priority

The broker calculates the priority from the Raft configuration and the voter policy (5.1). Raft does not store the priority or the policy.

1. Sort the voters by the zone order of the policy. The control zone is the first zone (5.1), thus control-zone voters come first. Voters of a zone that is not in the policy come last. Then sort by node index, low to high.
2. The priority of a voter is `max(1, policyTotal - position)`. `policyTotal` is the total number of voters in the policy. `position` starts at 0. If each zone has no more voters than slots, each voter has a different priority. If a zone has more voters than slots, the last voters get priority 1. Thus, each voter keeps a positive priority and can start an election.
3. The target priority is `policyTotal`. It changes only when the policy changes (R12).
4. A passive member has priority 0.

The broker calculates the priority at start-up from its stored configuration and the policy cache. It calculates it again on each new configuration (R10) and on each new policy record, and calls `reconfigurePriority` if the value changed.

The priority election does not take the leadership from a leader that runs. A voter with a higher priority becomes the leader only after the current leader steps down. Thus, when a voter with a higher priority than the current leader has caught up, the broker starts the existing leadership balancing, or a leadership transfer to the voter with the highest priority. A transfer goes only to a voter (R6). Example: `C1` comes back after the control zone failed (5.10.2).

### 5.4 Start of the system partition

The system partition starts in its own start step. It is not part of a tenant group. The step runs **before** the dynamic configuration starts. Thus, the step uses only local data, the static configuration and the probe. It does not know if the broker is a cluster member. The leader decides that at runtime (5.5).

The start step does not block the start of the broker. The broker becomes ready without the system partition (decision #186). The system partition is not accessible until it has caught up (decision of Nov 10). Thus, a slow probe or a missing leader does not stop a rolling restart or an ordered pod start.

The broker uses the first row of this table that applies:

| # |                                                   Condition                                                   |                                                                                                                   Action                                                                                                                    |
|---|---------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | The broker has a stored Raft configuration (`.conf`) for the system partition.                                | Start with the stored configuration (`bootstrap`). The member ids and types for `bootstrap` come from the stored configuration, not from the static configuration. A broker that a scale-up added has no entry in the static configuration. |
| 2 | No stored Raft configuration, and one probe answer has a configuration index above 0.                         | Delete local system partition data, if any. Start without data (5.4.3).                                                                                                                                                                     |
| 3 | No stored Raft configuration, and all static brokers answer "no data" or index 0, with the same hash (5.4.2). | Bootstrap with the initial configuration (5.4.1).                                                                                                                                                                                           |

If no row applies, the probe retries (5.4.2).

A voter that was removed, and starts with old data, uses row 1. It sends polls and votes. The members reject them, because its id and incarnation are on the fence list (5.6.6). When it gets a `FENCED` rejection or the fenced notice of the leader, it deletes its data and starts without data (5.4.3). The leader does not add it until the broker is `JOINING` or `ACTIVE` again.

A passive member that was removed, and starts with old data, also uses row 1. It sends no requests, thus no member rejects it. It is never accessible, because no leader sends it entries. When the cluster adds it again, the leader sends it the fenced notice (5.6.1).

#### 5.4.1 Initial configuration

Each broker of the static cluster calculates the same configuration at index 0:

- For each zone, the members with the lowest node indexes fill the voter slots. They are `ACTIVE`.
- All other static brokers are `PASSIVE`.
- Each member has incarnation 0.
- The configuration does not have the policy. The policy is the first record of the state (5.1).

This needs a Raft change: bootstrap with member types and incarnation 0 (R1).

#### 5.4.2 Probe

The probe finds out if the system partition already exists. The broker asks each broker of the static configuration and each broker in its cluster view. Each answer has:

- "no data", or the stored configuration index and term;
- a hash of the voter policy and of the initial configuration;
- the origin of the broker: `NEW` or `EXISTING` (5.4.5), and if it stored an activation configuration (5.11.2).

The first leader of the system partition appends a configuration entry when its initial entry is committed. It does this before it appends any other entry and before it announces its leadership (R14, D19). The entry has the members of the initial configuration. Thus, a configuration index above 0 shows that the system partition exists.

The broker decides:

- If one answer has an index above 0: use row 2. The hash is not checked. A broker added by a scale-up, or with a changed static configuration, can start.
- If one answer has an activation configuration: wait. Use row 2 when an answer has an index above 0.
- If one answer, or the broker itself, has the origin `EXISTING`, and no answer has an index above 0: the cluster existed before the system partition. Do not use row 3. Wait for the activation (5.11.2).
- If **all** brokers of the static configuration answered, all answers are "no data" or index 0, and all origins are `NEW`:
  - If a hash is different from its own: stop the system partition with an error. Two brokers with different initial configurations could elect two leaders in one term.
  - Else: use row 3. All brokers calculate the same initial configuration. Before the first configuration entry commits, no entry with data is committed. Thus, a broker that bootstraps late, or after a disk loss, loses nothing.
- Else: retry. There is no timeout fallback.

For disaster recovery, an operator can send a one-time request that allows row 3 without all answers (5.11.3). It is not a start property.

#### 5.4.3 Start without data

A broker without data can be a voter in the current configuration. Its log is empty, and it has no record of its last vote. If it votes, the cluster can lose committed entries. Thus, a broker without data never votes until the leader has made it a voter again (R16):

1. The broker generates a random 64-bit incarnation. The incarnation is never 0 and never `Long.MIN_VALUE`: 0 is the incarnation of the members of the initial configuration, and the log format reads `Long.MIN_VALUE` as 0. The broker stores the incarnation on its disk.
2. The broker publishes the incarnation in its membership properties (5.4.4).
3. The broker starts the Raft server without a configuration. The server is `INACTIVE`. It does not grant votes or polls, and it does not start an election. It rejects appends and snapshots.
4. The server accepts a `ConfigureRequest` only if the configuration has the broker as `PASSIVE` with its own incarnation. It rejects each other configuration, also one in which the broker is a voter with an earlier incarnation. The rejection has the incarnation of the broker.
5. When it accepts a configuration, the server stores it and becomes `PASSIVE`. It then catches up as a passive member. At a restart, it uses row 1 with this stored configuration. Thus, it is still passive.

The broker becomes a voter only when the leader promotes it. The leader promotes only a member that has caught up (5.6.4, R9). Thus, a broker without data never votes with an empty log. No marker on the disk and no probe term are necessary.

A configuration from a deposed leader is safe. It can only make the broker passive. The broker then receives the committed configuration from the current leader.

If the system partition has no leader, the broker waits. It counts as a broker that is down. A forced change of 5.6.3 or 5.6.5 treats it like that.

A broker at the first start of the cluster (row 3) does not use this procedure. Else no leader could be elected.

#### 5.4.4 Incarnation of a broker

The leader must know the incarnation of a broker before it adds the broker. Each broker publishes the incarnation of its system partition replica as a property of its membership (gossip). The property is not part of the dynamic configuration. Gossip properties can change at runtime (`SwimMembershipProtocol` posts `METADATA_CHANGED`).

- A broker that uses row 1 publishes the incarnation of its stored configuration.
- A broker that starts without data publishes its new incarnation (5.4.3).
- A broker that deletes its data because it is fenced publishes its new incarnation (5.6.6).

A broker of an older version publishes no incarnation. The leader does not add or change a broker without an incarnation (Q12).

#### 5.4.5 New or existing cluster

A cluster that ran an older version has no system partition. Its brokers find "no data" in the probe, as the brokers of a new cluster do. But its current members can be different from the static configuration. Thus, it must not bootstrap from the static configuration (row 3). It needs the activation of 5.11.2.

The start step cannot read the dynamic configuration. But it can see which files are on the local disk. Thus, each broker records its **origin** once, in a marker file in the system partition directory:

1. At start, if the system partition directory has no origin marker, the start step looks at the data directory before any other step of this start writes to it.
2. If the data directory has the stored dynamic configuration (`PersistedCurrentClusterConfiguration.TOPOLOGY_FILE_NAME`) or a data partition directory, the origin is `EXISTING`. The broker ran before, with a version without the system partition.
3. Else, the origin is `NEW`.
4. The start step writes the marker before it returns, thus before the dynamic configuration starts and writes its file. Later starts read the marker. They do not look at the data directory again.

Thus:

- At the first start of a new cluster, each broker has the origin `NEW`. A broker that restarts before the bootstrap is complete keeps `NEW`, because the marker was written before its dynamic configuration file.
- After a rolling upgrade, each broker of the existing cluster has the origin `EXISTING`. No broker uses row 3. The coordinator starts the activation when all brokers run the new version (5.11.2).
- A broker that a scale-up adds to an existing cluster has the origin `NEW`. But other brokers answer `EXISTING`, an activation configuration or an index above 0. Thus, it does not use row 3.
- A broker of an older version does not answer the probe. Thus, row 3 is not possible during a rolling upgrade, because it needs all answers.

A wrong `EXISTING` is safe: the activation calculates the initial configuration from the current members, and that also works for a new cluster. A wrong `NEW` is not safe. It can occur only if every broker of an existing cluster lost its data directory. Then the cluster is new, because all its data is lost.

With dynamic node ids, the marker is copied with the system partition data to the new version directory (5.9).

Later, when the dynamic configuration is in the system partition, a new cluster still uses row 3, because no other copy of the dynamic configuration exists before the system partition. The origin rule does not change.

### 5.5 The leader changes the members

The leader of the system partition makes all normal changes of the members. No other broker changes the Raft configuration with a normal change. The forced changes of 5.6.3 and 5.6.5 are the only exception, because they are necessary when there is no leader.

**Input.** The leader compares four sources:

- its committed Raft configuration, when no joint change is in progress;
- the cluster members of its dynamic configuration: their states and the pending `MemberJoin`, `MemberLeave` and `MemberRemove` operations of the current plan. Later, when the dynamic configuration is in the system partition, the leader reads them from the system partition state;
- the incarnations that the brokers publish (5.4.4), and the replication state of each member (match index, last answer);
- the voter policy of the system partition state (5.1).

The leader does nothing while its dynamic configuration (later: its system partition state) is not initialized, and until it has applied the state up to the commit index of its first entry and a policy record exists. The Raft configuration stores the membership version: the version of the dynamic configuration that the leader used for the last change. The leader does nothing while its own version is lower than the membership version. Thus, a new leader with an older gossip view does not undo a change of the previous leader.

**When the leader checks.** On each new committed Raft configuration, on each change of its dynamic configuration, on each change of a published incarnation, when a passive member has caught up, and on a timer.

**Authorization.** A broker is **authorized** if:

- it is `JOINING` or `ACTIVE` in the dynamic configuration, or it is the target of a `MemberJoin` in the pending plan; and
- it is not `LEAVING` or `LEFT`, and it is not the target of a pending `MemberLeave` or `MemberRemove`. These conditions have priority.

A pending `MemberJoin` authorizes the broker before `MemberJoinApplier` sets it to `JOINING`. This is necessary when the dynamic configuration is in the system partition. Then the new broker can read the plan and run `MemberJoin` only after the leader has added it and it has caught up (D21).

A `LEFT` broker stays in the dynamic configuration only after a cancelled plan. A completed plan removes it (`GlobalConfiguration.completeGraphChangeIfDrained`). `MemberJoinApplier.init` rejects a `LEFT` broker today (Q6). Thus, the priority of `LEFT` over a pending `MemberJoin` blocks nothing that works today.

**Rules.** The leader takes the first rule that applies. It makes one change at a time. Each change uses joint consensus.

| # | Condition | Change |
|---|-----------|--------|
| 1 | A member is `LEAVING` or `LEFT`, is the target of a pending `MemberRemove`, or is neither in the dynamic configuration nor the target of a pending `MemberJoin`. | Remove it and fence it. If it is a voter, promote a replacement in the same change (D9). All such members go in one change. If the member is the leader itself, the leader first transfers its leadership (R6). |
| 2 | A member publishes a different incarnation than the one in the configuration, the new incarnation is not fenced, and the incarnation of the configuration did not answer within the election timeout. | Replace the member: remove and fence the old incarnation, and add the new incarnation as `PASSIVE`. If the old one is a voter, promote a replacement in the same change (D9). |
| 3 | A broker is authorized, is not in the configuration, and publishes an incarnation that is not fenced. | Add it as `PASSIVE`. |
| 4 | A zone has more voters than slots. | Demote one extra voter (5.6.4). |
| 5 | A zone has fewer voters than slots, and a passive member of the zone is authorized, is not fenced, has caught up, and answered within the election timeout. | Promote one passive member (5.6.4). |

The target of a pending `MemberLeave` is not authorized. Thus, the leader does not add or promote it. But rule 1 removes it only when it is `LEAVING`. A leaving voter thus keeps its vote while its data partitions move, and a cancelled scale-down does not remove and fence a broker that stays.

Rule 2 needs a silent old incarnation. Responses carry the incarnation of the sender (R8, R17), thus the leader knows when the old incarnation answered last. A broker that lost its disk is replaced after at most one election timeout. A second process with the same broker id and a new incarnation does not replace a healthy member that still answers. The leader logs a warning. A fenced incarnation is silent, because the leader ignores its responses (5.6.6).

An authorized broker that is not in the configuration, and that publishes a fenced incarnation, has old data. The leader sends it the fenced notice (5.6.6). It does not add it.

The leader never changes the members because a broker is down (D6). A broker that is down keeps its type.

**A new leader.** The leader keeps no state outside the Raft configuration. A new leader first ends a joint change that is in progress. Then it applies the rules again. Thus, a change that the earlier leader did not complete is completed by the new leader. The configuration that ends a joint phase fences each member that is in the old members but not in the new members (R18). Thus, a new leader that ends the joint phase of a removal also adds the fence entry.

**Fence entries.** The leader puts a removed member on the fence list in the configuration that ends the joint phase, not in the joint configuration. In the joint configuration, a leaving voter still counts for the quorum of the old voters. Thus, it must still get entries, and its answers must count.

**Incarnations in the quorum.** A response counts for a member only if its incarnation is the incarnation of that member in the configuration (R17). In the joint phase of rule 2, the old incarnation is a voter of the old configuration. The broker answers with its new incarnation. Thus, its answers do not count for the old voter. The joint change needs a quorum of the old voters without the broker. This is correct: the broker lost the entries of the old voter.

**Configuration of the leader.** The leader writes the full target configuration: the typed members, the incarnations, the fence list and the membership version. Raft rejects a configuration in which a fenced member is a voter. The membership component, not Raft, checks the policy: it never writes a configuration that adds voters to a zone beyond its slots. A zone that is not in the policy has 0 slots. A member without a zone is in the zone `""` (5.1).

**Requests from other brokers.** One request goes to the leader. A member that is not the leader forwards them. If it knows no leader, it answers `NO_LEADER`, and the sender retries (R3):

- A read of the leader's current configuration: the typed members, the incarnations, the fence list, the membership version, the index, the term and if a joint change is in progress (R10). The appliers of 5.6 use it to wait for a change of the leader.

A broker reads its local configuration with the same data (R10).

### 5.6 Flows

#### 5.6.1 Add a broker (`MemberJoin`)

The new broker applies `MemberJoin`. When it runs, the system partition step has already run (5.4). The applier does not change the system partition. It completes as today.

The leader sees the broker as the target of the pending `MemberJoin`, or as `JOINING`. Thus, the broker is authorized (5.5). Then:

1. If the broker is not in the configuration and publishes an incarnation that is not fenced, the leader adds it as `PASSIVE` (5.5, rule 3). A broker that started without data accepts the configuration and catches up (5.4.3).
2. If the broker publishes a fenced incarnation, it has old data from a time before it was removed. The leader sends it the fenced notice. The broker deletes its data and starts without data with a new incarnation (5.6.6). Then step 1 applies.
3. If the broker is already a member with its published incarnation, nothing changes. For example, an earlier leader added it already.
4. Later, when a slot of its zone is empty and the broker has caught up, the leader promotes it (5.6.4).

`MemberJoin` does not wait for the leader. The global operations run one at a time. A plan must not wait for a catch-up or a promotion. The excluded set (5.2) and I1 stay true, because the leader adds only an authorized broker.

When the dynamic configuration is in the system partition, the new broker cannot read the plan before it is a member. The leader adds it, because it is the target of the pending `MemberJoin`. The broker catches up, reads the plan and runs `MemberJoinApplier`. If the plan is cancelled before that, the broker is no longer authorized, and rule 1 removes and fences it.

Scale-up of a zone uses this flow. The new brokers are passive unless their zone has an empty slot.

#### 5.6.2 Remove a broker (`MemberLeave`)

The leaving broker applies `MemberLeave`. The dynamic configuration shows it as `LEAVING`. Then:

1. The leader sees the broker as `LEAVING` and removes it (5.5, rule 1).
   - If the broker is passive, the leader removes it in one change.
   - If the broker is a voter, the leader finds a replacement: a passive member of the same zone that has caught up, answered recently, and is not excluded (5.2). It sends **one** change that removes the leaving broker and promotes the replacement. Joint consensus keeps the old and the new quorum. If no replacement is possible, the leader removes the broker only. The slot stays empty until the leader promotes a passive member later (5.6.4).
   - If the broker is the leader, it first transfers its leadership to a different voter. The new leader removes the broker.
2. The removal puts the broker on the fence list with its incarnation, in the configuration that ends the joint phase (5.5).
3. The applier of `MemberLeave` waits until the leader reports a **committed, non-joint** configuration that does not have the broker. An appended configuration, or a joint configuration, is not a completion. The broker learns the result from the fenced notice, or from a read of the leader's configuration (5.5). The wait has a limit. When the limit ends, the result is unknown: the applier fails, and the dynamic configuration retries it. The applier never undoes a change. A retry first reads the leader's configuration. If the broker is not in it, the applier completes at once.

Thus, the broker leaves the system partition before the dynamic configuration marks it `LEFT` (I1).

**No answer.** When the removal commits, the leaving broker is fenced. The leader sends it the fenced notice, and the broker stops its system partition (5.6.6). Thus, the applier cannot read its local replica after that. It reads the leader's configuration from a different member.

`MemberRemove` uses `MemberLeaveApplier` too. The coordinator applies it for a broker that is down. The leader sees the broker as the target of a pending `MemberRemove` and removes it (5.5, rule 1). The applier waits for a committed, non-joint configuration without the broker, as above. Usually phase 0 of the force-remove plan did the removal already (5.6.3).

#### 5.6.3 Force-remove brokers or a zone

"Force-remove brokers" and "remove zone" with `force=true` use `ForceScaleDownRequestTransformer`. The automatic region failover also uses this plan. The plan gets a new first phase:

```
Phase 0  GlobalPhase [SystemPartitionRemoveMembers(executor, removedBrokers, acceptPossibleLoss)]   <- new
Phase 1  PartitionGroupPhase (sequential) [PartitionForceReconfigure ...]        (today)
Phase 2  GlobalPhase [MemberRemove(coordinator, removedBroker) ...]              (today)
```

The executor is a surviving broker. The request must select a coordinator that is not removed **and** that is reachable. Today `RemoveZoneTransformer` can select a control broker that is down. This must change (5.9).

The operation tells the executor to change the Raft configuration. It does not write the members into the `ClusterConfiguration`.

**Step 1: collect.** The executor asks all reachable members that are not removed for their configuration, last log term, last log index, commit index, incarnation and the policy that they applied (or their cache) with its version. It uses the configuration with the highest index and term (`K`), and the policy with the highest version. If no member has a policy, it uses its static policy. `S(K)` are the voter sets of `K`: one set, or two sets (old and new) if `K` is joint. `R` are the members that answered with the incarnation that `K` has for them. A broker that answers with a different incarnation lost its log, and it is not in `R`.

**Step 2: is a forced change in progress?** If that configuration is forced, the removed brokers are not in it, and its fence list has each removed broker with its incarnation, a forced change was already made by an earlier try. A forced configuration that still has the removed brokers is not an earlier try. For example, "force system partition voters" keeps the down brokers as fenced passive members (5.6.5). The executor sends this configuration again with `forceConfigure`, unchanged. The step completes when each voter of it has answered.

**Step 3: decide.** The executor counts the voters that are not removed:

- **Normal change**, if for **each** set `V` of `S(K)`, the voters of `V` that are not removed are a quorum of `V`: the leader removes the brokers. It sees them as the targets of the pending `MemberRemove` operations of the plan, and it removes all of them in **one** change and puts them on the fence list (5.5, rule 1). The executor waits until the removed brokers are not in the leader's configuration. The leader fills empty slots later (5.6.4).
- **Forced change**, else. This happens in a dual-region cluster without a control zone.
  0. **Completeness check.** If `R` is a blocking set of each set of `S(K)`, the forced change keeps each committed entry (proof in D20). Else the operation fails with `METADATA_COMPLETENESS_UNKNOWN` and lists the voters that did not answer. The coordinator runs the same check when it makes the plan, thus the request usually fails at once (5.12.2). The operator can bring back one more voter with its disk and try again, or send the request again with `acceptPossibleLoss = true`.
  1. The voters of the forced configuration are the reachable members, voters or passive, that have the most recent log (D11). Usually these are the surviving voters.
  2. Each zone gets at most the number of voters that its slots in the policy allow. The members with the lowest node index come first.
  3. If no member with the most recent log is in a zone of the policy, take one of them anyway. The leader demotes it later, when a member of a policy zone is promoted (5.6.4).
  4. All other remaining members are passive.
  5. The removed brokers go on the fence list with their incarnations (R8).
  6. Each voter of **each** set of `S(K)` that is not removed, but that is not a voter of the forced configuration, also goes on the fence list with its incarnation. This is a voter that does not have the most recent log, or that did not answer in step 1. The forced configuration gets to it only with a best-effort send. Thus, it can keep an old configuration in which it is a voter. 5.6.5 fences the down brokers for the same reason. The fenced voter stays a member. It deletes its data and starts again with a new incarnation (5.6.6). The leader replaces its incarnation, because it is `JOINING` or `ACTIVE` (5.6.4).
  7. Call `forceConfigure` with the full target configuration. The executor builds it from the configuration of step 1, not from its own configuration, which can be older: the members with their types and incarnations, and the fence list with the new entries. The executor sends it to all members that answered in step 1, with a best-effort wait. Only the voters of the new configuration must answer (R2). A member that applies a forced configuration adds its entries to its own fence list. It never removes an entry.
  8. After the new leader is elected, it restores the policy with promotions and demotions (5.6.4).

**Recovery record.** When the system partition has a leader again, the executor writes a recovery record to the system partition state: the index and term of the forced configuration, the fenced brokers, the result of the completeness check, and `acceptPossibleLoss`. A retry writes the same record, and the state machine ignores a second copy. Consumers of the state can thus see that entries can be missing (Q9). The forced configuration itself does not have these fields.

The automatic failover never makes a forced change (D13). Thus, it never needs `acceptPossibleLoss`.

**When the normal change cannot commit.** The rule expects that the remaining voters are alive. If one is also down, the change cannot commit. The step retries, and the plan does not continue. The operator must then use the operation of 5.6.5.

**Why phase 0 comes first.** Later, the plan is stored in the system partition. Then the plan can continue only if the system partition works.

#### 5.6.4 Promotion, demotion and replacement by the leader

These rules keep the configuration in line with the policy (5.5, rules 2, 4 and 5). Each change of rules 4 and 5 changes only one broker.

**Promotion.** The leader promotes a passive member if all these conditions are true:

1. The dynamic configuration of the leader is initialized, and its version is not lower than the membership version (5.5).
2. The zone of the member has fewer voters than slots in the committed configuration.
3. The member is not excluded (5.2) and not fenced (no fence entry matches its id and its incarnation).
4. The member has caught up: its match index is above 0, and it is not lower than the commit index of the leader or lower by less than `promotionLagThreshold` (R9). A passive member gets only committed entries, thus under load its match index is always a little lower than the commit index. The lag changes only how fast the joint entry commits, not safety.
5. The member answered within the election timeout (R9).

If more than one member can be promoted, the leader promotes the member with the lowest node index. When the leader applies a new policy record, it checks again.

**Demotion of extra voters.** If a zone has more voters than slots, the leader demotes the extra voter with the highest node index. It demotes one voter in each change. This can demote a voter that is down. That is safe, because the quorum gets smaller. A voter of a zone that is not in the policy is also extra. The leader demotes it only after a member of a policy zone is promoted. Thus, the number of voters does not go down to a number that tolerates fewer failures.

**Replacement of an incarnation.** A broker that lost its disk, or that deleted its data because it is fenced, publishes a new incarnation (5.4.4). If the configuration has the broker with a different incarnation, and that incarnation did not answer within the election timeout, the leader replaces it (5.5, rule 2). If the old incarnation is a voter, the leader promotes a caught-up passive member of the same zone in the same change, as for a leaving voter (D9). Else the slot stays empty, and the leader promotes the broker itself when it has caught up.

**Repair after a cancelled plan.** A plan can be cancelled after the system partition change, for example after phase 0 or after the removal in `MemberLeave`. If the broker is still authorized, the leader adds it again when it publishes a new incarnation (5.5, rule 3). The broker deletes its data when it gets the fenced notice (5.6.6).

#### 5.6.5 Force system partition voters

This is a new operation. It is for the case in which the system partition has no quorum, and the failed brokers must stay cluster members. Example: in a 2.5-region cluster, the control zone and zone A are down (#21).

Input: the brokers that are down (`downBrokers`).

1. The operator verifies that the down brokers are down and fences them.
2. The operator calls the operation on a surviving broker.
3. The broker collects as in 5.6.3, step 1. If the configuration is forced, and each down broker is fenced with its incarnation and is not a voter, it is a forced change of an earlier try. The broker sends it again. The operation rejects the request if a voter of a set of `S(K)` did not answer and is not in `downBrokers`.
4. The completeness check of 5.6.3, step 3, item 0 applies. In the case of #21 (control zone and one data zone down), it fails, because the surviving voters are not a blocking set. Thus, the operator must send `acceptPossibleLoss = true`, or bring back a failed voter with its disk.
5. The forced configuration:
   - The voters are the reachable members that are not down and that have the most recent log (D11). Each zone gets at most the number of voters that its slots allow. The rules of 5.6.3, step 3, items 2 and 3 apply.
   - All other members, the down brokers included, stay members and become `PASSIVE`.
   - The down brokers go on the fence list with their incarnations (R8).
6. Call `forceConfigure` with the full target configuration, as in 5.6.3, step 3, item 7. The broker sends it to all members that answered, with a best-effort wait. Only the voters of the new configuration must answer (R2).
7. The system partition has a quorum again. The executor writes the recovery record (5.6.3). The leader restores the policy for the surviving zones with promotions and demotions (5.6.4).
8. The failure detector works again. If all brokers of the failed data zone are down, the automatic failover removes the zone with a normal change (5.6.3). It records the failover state and the failback reference, as #21 expects.
9. If one broker of the failed data zone is still alive, the automatic failover does not start (no failover on partial failures, Oct 26). The operator then force-removes the zone.

The operation does not change the dynamic configuration or the data partitions. Each broker stays a cluster member. Thus, invariant I1 stays true.

This operation is not a plan of the dynamic configuration. It never waits in a queue behind a plan. A plan can be stuck because the system partition has no leader, for example a scale-down in `MemberLeave`. Only one plan runs at a time. Thus, the operator must cancel the stuck plan before the automatic failover can run.

#### 5.6.6 Fenced brokers

A broker on the fence list can have an old configuration in which it is a voter. Its log can have entries that the survivors do not have. Thus (R8):

- A fence entry has a broker id and an incarnation. Raft requests (poll, vote, append, configure, and the requests of R3) and their responses carry the incarnation of the sender.
- A member rejects a request only if the sender id and the sender incarnation match a fence entry in its configuration. It rejects the request before a role handles it. Thus, it does not update its term from such a request.
- The error of the rejection depends on the view of the member. If the fence entry is committed in its own view, the error is `FENCED`. Else the error is `ILLEGAL_MEMBER_STATE`, and the sender ignores it. An entry is committed in the view of a member if the configuration is forced, if the commit index of the member is at the configuration index or higher, or if the stored (committed) configuration of the member has the entry.
- A response from a fenced member counts as a response from a member that is not reachable. Thus, the receiver also ignores the term in the response. One wrapper of the outbound protocol does this for all requests.
- The leader does not replicate entries to a fenced member. It sends the fenced notice: the committed configuration in a `ConfigureRequest` that is marked as committed. The leader sends the notice at most once per heartbeat interval to:
  - a member of the configuration that is fenced, when the configuration that fences it is committed;
  - a broker that is not in the configuration and publishes a fenced incarnation (5.4.4). This is a removed broker with old data.
- The leader rejects a request from a fenced sender.
- A broker acts on a fence entry only if the incarnation of the entry is its own current incarnation. In addition, one of these must be true: the entry is in a configuration that the broker knows as committed (also its stored configuration at a restart), a member sent a `FENCED` rejection of a request that the broker sent for itself, or the leader sent the fenced notice. A member that forwards a request of a fenced broker only passes the rejection back to that broker.
- Then Raft makes the member inactive and calls the fenced listener. The broker closes the system partition (R11) and deletes its data. It starts without data, with a new incarnation (5.4.3).
- A new incarnation matches no fence entry. The leader adds it only if the broker is `JOINING` or `ACTIVE` (5.5).
- Fence entries are never cleared. A broker that starts again without data has a new incarnation. Thus, old entries never match it. The number of entries is not more than the number of removals, replacements and forced changes (D17).

The fence list has these kinds of entries:

- Brokers that a removal took out of the system partition (`MemberLeave`, `MemberRemove`, phase 0). They usually are not cluster members any more. The leader does not add them until the cluster adds them again.
- Old incarnations that the leader replaced (5.6.4).
- Brokers that "force system partition voters" made passive, and voters that a forced change of phase 0 made passive (5.6.3). They are still cluster members. Thus, the leader replaces their incarnation when they start again without data.

A removed passive member sends no requests. Thus, no member rejects it. It is never accessible. When the cluster adds it again, the leader sends it the fenced notice (5.6.1).

A broker that deletes its data loses its local view. Its system partition is not accessible until it has caught up again (decision of Nov 10). Local readers do not see the state go backwards.

The fence list protects the survivors. It does not protect against an isolated group of fenced brokers that cannot reach any survivor. Such a group can be a quorum of the old configuration. Thus, the operator must keep the failed brokers down until they can reach the survivors (Q1).

#### 5.6.7 Restart of one broker

The broker uses row 1, with the member ids and types of its stored configuration. Raft loads its stored configuration. The broker publishes the incarnation of the stored configuration. It calculates its priority from that configuration and the policy cache (5.3).

#### 5.6.8 Restart of the cluster

All brokers restart at the same time. Each broker uses row 1 with its stored configuration. The dynamic configuration starts after it:

- The voters elect a leader. The voter with the highest priority usually wins. The priorities come from the stored configurations, thus they are available at once.
- Passive members start in the passive role. They do not start elections.
- A broker that missed the last change has an older configuration. If it is passive in that configuration, it does no harm. The leader sends it the current configuration. If it is a voter in the older configuration but not in the current one, it cannot win an election, because the current voters have a more recent log.
- A fenced or removed voter that starts with old data sends polls. The members reject them (5.6.6). When it gets a `FENCED` rejection or the fenced notice of the leader, the broker deletes its data and starts without data (5.4.3).
- A removed passive member that starts with old data sends no requests. No member rejects it. It is never accessible. If the cluster adds it again, the leader sends it the fenced notice (5.6.1).
- The leader makes no change of the members until its dynamic configuration is initialized and is not older than the membership version (5.5). Until then, a broker that started without data waits.
- A broker without a stored configuration uses the probe (rows 2 and 3).

#### 5.6.9 Disk loss

A broker that lost its disk and is still a cluster member uses row 2. It starts without data, with a new incarnation, and publishes it (5.4.3). It does not vote.

- The leader sees a new incarnation for a member and replaces it (5.6.4). The old incarnation goes on the fence list.
- If the old incarnation was a voter, the leader promotes a caught-up passive member of the same zone in the same change. If no such member exists, the slot stays empty until the broker has caught up. Then the leader promotes it.
- If the old incarnation was passive, the broker catches up as a passive member.
- While the old incarnation is a voter, it counts as a voter that is down. The answers of the new incarnation do not count for it (R17).

#### 5.6.10 Zone changes

- **Add a zone.** `AddZone` adds brokers with `MemberJoin`. They are passive, because the zone is not in the policy. Then "update system partition voter policy" gives the zone its slots.
- **Rolling conversion from dual-region to 2.5 regions.** Add the control zone, then update the policy with `control: 1`. The leader promotes the control broker when it has caught up.
- **Zone priorities.** A change in the dynamic configuration does not change the priority of the system partition. The zone order is in the policy of the system partition state.
- **Zone migration** (ids without a zone to zone ids). `selectVoters` must move at most one vote in each change. A voter without a zone is demoted only in the same change that promotes a voter with a zone. The details need a separate design (Q7).

### 5.7 Invariants

- **I1. System partition members are authorized brokers, or targets of a pending `MemberLeave` until they are `LEAVING`, except while the leader removes a member.** The leader adds only an authorized broker. `MemberLeave`, `MemberRemove` and phase 0 of a force-remove plan wait for a committed, non-joint configuration without the broker, or phase 0 removes it with a forced change, before the dynamic configuration marks it `LEFT`. A cancelled plan makes a pending `MemberJoin` target unauthorized, and rule 1 removes it. "Force system partition voters" removes no member.
- **I2. The Raft configuration is the only record of the members and their types. The system partition state is the only record of the voter policy.** The policy cache is a local copy for the election priority only.
- **I3. Only these change the voters:** the leader (5.5), phase 0 of a force-remove plan when it makes a forced change and "force system partition voters". Liveness does not change the voters.
- **I4. A broker without data does not vote** until the leader promotes it after it has caught up.
- **I5. A fenced broker takes part only after it deleted its data and started again with a new incarnation.**
- **I6. A fence entry matches only the incarnation that it fenced.** An old entry never rejects a broker that started again.

### 5.8 Changes to Raft

|       #       |                                                                                                                                                                                                                                                                                                                                                                          Change                                                                                                                                                                                                                                                                                                                                                                          |                                                                 Why                                                                 |
|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| R1            | Bootstrap with member types and incarnation 0 for each member. `createInitialConfig` uses the given types.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | Today the initial configuration makes all members `ACTIVE`.                                                                         |
| R2            | `forceConfigure` waits only for the `ACTIVE` members of the new configuration. The member set of `ForceConfigureQuorum` has no passive members. It must work when the caller is passive. The caller sends the change to all reachable members, with a best-effort wait.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | Today one passive member that is down stops the recovery.                                                                           |
| R3            | Changed in revision 7. A request to the leader that reads the leader's configuration. A member forwards it to the leader. A member that knows no leader answers `NO_LEADER`, and the sender retries. There is no API that lets a different broker send a full target configuration. A policy change is a write to the system partition state, not a Raft request.                                                                                                                                                                                                                                                                                                                                                                                        | Only the leader changes the members (5.5). The appliers of 5.6 need the read.                                                       |
| R4            | Tests for the promotion from `PASSIVE` to `ACTIVE`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Production promotes only from `PROMOTABLE` today.                                                                                   |
| R5 (optional) | Send new committed entries to passive members when the commit index moves. Not done in the PoC.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Today passive members get them only on the next heartbeat.                                                                          |
| R6            | `anoint()` and leadership transfer reject a member that is not `ACTIVE`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Today a passive member can become a candidate through `anoint()`.                                                                   |
| R7            | Removed in revision 7. The empty-start guard with `probeTerm` and the marker is replaced by R16.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | A broker without data is never a voter until the leader promotes it (5.4.3).                                                        |
| R8            | Fence list of (broker id, incarnation) in the configuration (5.6.6). Part of membership equality. Requests and responses carry the sender incarnation. Members reject votes, polls, appends and the requests of R3 from a fenced sender before a role handles them: `FENCED` if the entry is committed in their own view, else `ILLEGAL_MEMBER_STATE`, which the sender ignores. A response from a fenced member counts as from a member that is not reachable. The leader does not replicate to a fenced member. It sends the committed configuration as the fenced notice, at most once per heartbeat interval, also to a non-member that publishes a fenced incarnation. A broker acts only on a committed entry, a `FENCED` rejection or the notice. | Today the `force` flag protects only until the next normal configuration.                                                           |
| R9 | The promotion check also requires an answer from the member within the election timeout. It keeps the existing lag threshold (`promotionLagThreshold`). Only for the system partition (R20). | Today a passive member that is down can pass the check. A passive member always lags a little under load (5.6.4). |
| R10           | A listener for configuration changes, and a read of the typed configuration with incarnations, fence list, membership version, index, term and joint state. The leader exposes the match index and the time of the last answer of each member and incarnation.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | The priority, `selectVoters` and the rules of the leader (5.5) need them.                                                           |
| R11           | `RaftPartition.join` fails while a server exists that was not closed. The same holds for the start without data (R16).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Today it creates a second server on the same directory.                                                                             |
| R12           | The target priority can change at runtime.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | The policy can change (5.1).                                                                                                        |
| R13           | Removed in revision 7. The join admission hook and the errors `NOT_A_MEMBER` and `NOT_READY` are not necessary.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | The leader decides which brokers to add (5.5).                                                                                      |
| R14           | When its initial entry is committed, the first leader appends a configuration entry. It does this before it appends any other entry and before it announces its leadership. Only for the system partition (R20).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Today a partition that never changed its members keeps configuration index 0. The probe cannot see that the partition exists (D19). |
| R15           | Changed in revision 7. Each member has an incarnation in the configuration. The leader writes it. A change can replace the incarnation of a member: the old incarnation leaves, the new one is added as `PASSIVE`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Fence entries must match only one life of a broker (D17).                                                                           |
| R16           | New in revision 7. Start the server without a configuration, as `INACTIVE`, with an incarnation. The server accepts a `ConfigureRequest` only if the configuration has it as `PASSIVE` with its own incarnation. Then it stores the configuration and becomes `PASSIVE`. It rejects each other configuration with its incarnation in the response.                                                                                                                                                                                                                                                                                                                                                                                                       | Today a server must bootstrap or join. A broker without data must not vote (5.4.3).                                                 |
| R17           | New in revision 7. A response counts for a member, also for the quorum and the match index, only if its incarnation is the incarnation of that member in the configuration.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | In the joint phase of a replacement, the new incarnation must not count for the old voter (5.5).                                    |
| R18           | New in revision 7. The configuration that ends a joint phase fences each member that is in the old members but not in the new members, with its incarnation. Only for the system partition (R20).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | A new leader that ends the joint phase of an earlier leader then also adds the fence entry (Q13).                                   |
| R19           | New in revision 7. The configuration has a membership version. Raft stores it and does not interpret it.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | A new leader with an older view of the cluster members does not undo a change (5.5).                                                |
| R20           | New in revision 7. A Raft partition option `systemPartition`. R9, R14 and R18 apply only when it is set. The voter policy is not in Raft. Raft has no check of zones or slots.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | Data partitions keep their behavior. The rules of zones belong to the membership component (D18).                                   |

The configuration format gets new fields (incarnations, fence list, membership version). The voter policy is not in the format (D18). The format version of the configuration entry and of the `.conf` file (`raft-entry-schema.xml`) goes from 4 to 5. Data with version 4 reads as incarnation 0 for each member, an empty fence list and membership version 0. Only the system partition writes incarnations other than 0, fence entries and a membership version. Data partitions write version 5 with these empty values. Their behavior does not change: without the option `systemPartition` (R20), the first leader appends no configuration entry, the promotion check does not require a recent answer (R9), and the end of a joint phase fences no member (R18).

### 5.9 Changes to the broker and the dynamic configuration

- A start step for the system partition, with the start table of 5.4, the probe and the origin marker (5.4.5). It runs before the dynamic configuration starts. It does not block the readiness of the broker. Row 1 uses the member ids and types of the stored configuration. Row 2 starts without data (R16). The step keeps the incarnation on the disk and publishes it as a membership property (5.4.4).
- A fenced listener. It closes the system partition, deletes its data and starts without data with a new incarnation (5.6.6).
- A membership component on the leader of the system partition (5.5). It runs only while the local replica is the leader. It reads the dynamic configuration, the published incarnations and the replication state, and applies the rules of 5.5 and 5.6.4. Later, it reads the cluster members from the system partition state.
- Leadership balancing for the system partition. When a voter with a higher priority than the leader has caught up, the broker starts the leadership balancing or a transfer to the voter with the highest priority (5.3).
- `SystemPartitionMembership`: `selectVoters`, the priority, the rules of 5.5 and 5.6.4 with the zone check, and the forced changes of 5.6.3 and 5.6.5, as pure functions. The policy is an input, not a part of the Raft configuration.
- `MemberJoinApplier` does not change the system partition. `MemberLeaveApplier` (also for `MemberRemove`) waits until the broker is not in the leader's configuration (5.6.2). Each wait has a limit.
- A new global operation `SystemPartitionRemoveMembers(executor, removedBrokers, acceptPossibleLoss)`. `ForceScaleDownRequestTransformer` adds it as phase 0. In a normal change, it waits for the leader. In a forced change, it calls `forceConfigure` (5.6.3).
- The force-remove request and `RemoveZoneTransformer` select a coordinator that is not removed and that is reachable.
- New management operations: "force system partition voters" (5.6.5), which is not a plan, and "update system partition voter policy" (5.1), which writes a policy record to the system partition.
- A policy record in the system partition state, and the policy cache file of each replica (5.1). The first leader writes the initial record.
- The voter policy properties and the control zone role (5.1).
- With dynamic node ids, the system partition data is copied to the new version directory, as for data partitions. The lease gives exclusive use.
- Purge and restore do not change the system partition members.
- The completeness check, `acceptPossibleLoss` and the recovery record in phase 0 and in "force system partition voters" (5.6.3).
- Plan validation, the activation of an existing cluster, the disaster-recovery bootstrap request and the status read (5.11).

### 5.10 Deployment examples

`A1` is broker 1 of zone A. `C` is the control zone. `{...}` is a Raft configuration. `V` is a voter, `P` is a passive member. `fence: [...]` is the fence list. `B1#5` is broker `B1` with incarnation 5. Without `#`, the incarnation is 0.

#### 5.10.1 Dual-region without a control region

**Setup.** Zones A and B with 4 brokers each. Policy `A: 2`, `B: 2`.

**Bootstrap.** All 8 brokers probe. When all 8 answer "no data", each bootstraps:

```
{A1 V, A2 V, B1 V, B2 V, A3 P, A4 P, B3 P, B4 P}     quorum: 3 of 4
```

The first leader appends a configuration entry with the same members. The configuration index is now above 0. Then it writes the policy record.

**One broker fails.** If it is a voter, 3 of 4 voters are alive. Nothing changes.

**Zone B fails.** 2 of 4 voters are alive. No quorum. Automatic failover is not available in this setup (#20). The data partitions also have no quorum. Recovery:

1. The operator verifies that zone B is down and fences it.
2. The operator sends "remove zone B" with `force=true`.
3. Phase 0: `A1, A2` are not a quorum of 4. Thus, a forced change. `A1` and `A2` answered. They are a blocking set of `{A1, A2, B1, B2}` (2 of 4, quorum 3). Thus, the change is complete, and no `acceptPossibleLoss` is necessary. `A1` and `A2` have the most recent log:

   ```
   {A1 V, A2 V, A3 P, A4 P}   fence: [B1, B2, B3, B4]     quorum: 2 of 2
   ```
4. Phases 1 and 2 change the data partitions and the dynamic configuration.

The system partition tolerates no failure now. This is the same as the data partitions, which have only the 2 replicas in zone A.

**Only one member has the most recent log.** Example: `A1` was cut off, and entries committed on `A2`, `B1` and `B2`. `A3` received them as a passive member. Then `A2` failed together with zone B. `A1` can be reached again. Of the reachable members, only `A3` has the most recent log. `R` has only one voter (`A1`), and it is not a blocking set (1 of 4, 2 necessary). Thus, the operator must send `acceptPossibleLoss = true`. The forced configuration is:

```
{A3 V, A1 P, A2 P, A4 P}   fence: [A1, A2, B1, B2, B3, B4]     quorum: 1 of 1
```

`A1` and `A2` were voters, but they are not voters of the forced configuration. Thus, they go on the fence list too (5.6.3, step 3, item 6). `A1` gets the fenced notice. It deletes its data and starts without data with a new incarnation. When `A2` comes back with its old data, it is a voter in its old configuration and sends polls. The members reject them, and the leader sends `A2` the fenced notice. `A2` deletes its data and starts without data with a new incarnation. Both are `ACTIVE` cluster members, thus the leader replaces their incarnations (5.6.4). When `A1` has caught up, the leader promotes it, and zone A has 2 voters again:

```
{A3 V, A1#3 P, A2#6 P, A4 P}  ->  {A3 V, A1#3 V, A2#6 P, A4 P}   fence: [A1, A2, B1, B2, B3, B4]
```

If `A1` to `A4` all had the most recent log, the policy `A: 2` limits the voters to `A1` and `A2`.

**Restart of the cluster after the forced change.** `A1` and `A2` load the forced configuration with the fence list. They elect a leader with 2 of 2 votes. `A3` and `A4` start as passive members. `B1` and `B2` start with old data. They were voters, thus they send polls. `A1` and `A2` reject them (fence list), and the leader sends the fenced notice. They delete their data and start without data. The leader does not add them, because they are `LEFT`. `B3` and `B4` were passive. They send no requests and are never accessible.

**Failback.** The operator adds `B1` to `B4` again. Each one is now `JOINING`. `B3` and `B4` still have old data and publish a fenced incarnation. The leader sends them the fenced notice, and they delete their data. Each broker starts without data with a new incarnation, and the leader adds it as `P`. The old fence entries stay, but they do not match the new incarnations. The leader fills the 2 slots of zone B when the brokers have caught up:

```
{A1 V, A2 V, A3 P, A4 P, B1#5 P, B2#8 P, B3#2 P, B4#6 P}   fence: [B1, B2, B3, B4]
  ->  {..., B1#5 V, ...}  ->  {A1 V, A2 V, B1#5 V, B2#8 V, ...}
```

**Network partition between the zones.** Neither side has a quorum (2 of 4). A forced change on one side is safe for the system partition, because the other side cannot get a quorum of the old configuration. The fencing is still necessary for the data partitions.

#### 5.10.2 Two and a half regions (with a control region)

**Setup.** Zones A and B with 3 brokers each, control zone C with 1 broker. Policy `A: 2`, `B: 2`, `C: 1`.

**Bootstrap.**

```
{C1 V, A1 V, A2 V, B1 V, B2 V, A3 P, B3 P}     quorum: 3 of 5
```

The first leader appends a configuration entry with the same members, then the policy record. `C1` has the highest priority (5), thus it usually wins the first election. The priorities are `C1` 5, `A1` 4, `A2` 3, `B1` 2, `B2` 1. `A3` and `B3` have 0.

**Zone A fails: automatic failover.** 3 of 5 voters are alive (`B1, B2, C1`). The failure detector commits the failover decision. The failover runs the force-remove plan for zone A. Phase 0: `B1, B2, C1` are a quorum of 5. Thus, a **normal** change:

```
{C1 V, B1 V, B2 V, B3 P}   fence: [A1, A2, A3]     quorum: 2 of 3
```

No forced change is necessary. The system partition still tolerates one failure. The slots of zone A stay empty.

**Zone A comes back before the failback.** `A1` and `A2` have 2 of 5 old votes, thus they cannot elect a leader. They send polls. The survivors reject them (fence list), and the leader sends the fenced notice. They delete their data and start without data. The leader does not add them, because they are `LEFT`. `A3` was passive. It sends no requests and is never accessible.

**Failback.** The failback adds `A1` to `A3` again. They are now `JOINING`. `A3` still has old data and publishes a fenced incarnation. The leader sends it the fenced notice, and it deletes its data. Each broker starts without data with a new incarnation, and the leader adds it as `P`. The old fence entries stay. The leader fills the slots of zone A when the brokers have caught up:

```
{C1 V, B1 V, B2 V, B3 P, A1#4 P, A2#7 P, A3#3 P}  ->  ...  ->  {C1 V, A1#4 V, A2#7 V, B1 V, B2 V, A3#3 P, B3 P}
```

**The control zone fails.** 4 of 5 voters are alive. The cluster does nothing (#21). The leader moves to the data voter with the next priority. When `C1` comes back with its disk, it catches up as a voter. The priority election does not take the leadership from the leader that runs (5.3). Thus, `C1` is the leader again only after the current leader steps down: when `C1` has caught up, the broker starts the leadership balancing. If `C1` comes back with an empty disk, it starts without data with a new incarnation. It does not vote. The leader replaces its incarnation. The control zone has no other broker, thus the slot stays empty until `C1` has caught up. Then the leader promotes it (5.6.9).

**The control zone and zone A fail.** 2 of 5 voters are alive. No quorum. Processing stops (#21).

- **Preferred:** bring `C1` back with its disk. The system partition has a quorum again, and the automatic failover removes zone A as above.
- **If `C1` cannot come back:**
  1. The operator verifies that zone A and `C1` are down and fences them.
  2. The operator calls "force system partition voters" with `downBrokers = A1, A2, A3, C1` and `acceptPossibleLoss = true`. `B1` and `B2` are not a blocking set of 5 voters (3 necessary). Entries that only `C1, A1, A2` had can be lost. `B1` and `B2` have the most recent log. `B3` can have it too, but the policy `B: 2` limits the voters to `B1` and `B2`:

     ```
     {B1 V, B2 V, A1 P, A2 P, A3 P, B3 P, C1 P}   fence: [A1, A2, A3, C1]     quorum: 2 of 2
     ```
  3. The system partition has a quorum. The failure detector sees that zone A failed. The automatic failover removes zone A with a normal change. The fence entries stay:

     ```
     {B1 V, B2 V, B3 P, C1 P}   fence: [A1, A2, A3, C1]
     ```

     It records the failover state and the failback reference.

  4. Automatic failover is not available now, because the control zone has no voter (#21).

**Restart of the cluster after the forced change.** `B1` and `B2` load the stored configuration with the fence list and elect a leader. `B3` starts as passive. `C1`, `A1` and `A2` were voters. If they start with old data, they send polls and are rejected (fence list). They delete their data and start without data. The leader does not add the brokers of zone A, because they are `LEFT`. `C1` is still `ACTIVE`, thus the leader replaces its incarnation (5.6.4). `A3` was passive. It sends no requests and is never accessible.

**Return of the control zone.** `C1` starts. If it has old data, it starts with row 1. The survivors reject it, because `C1` with incarnation 0 is on the fence list. The leader sends the fenced notice. `C1` deletes its data and starts without data with a new incarnation. `C1` is still `ACTIVE` in the dynamic configuration, thus the leader replaces its incarnation. The old entry for `C1` stays, but it does not match the new incarnation. When `C1` has caught up, the leader promotes it:

```
{B1 V, B2 V, B3 P, C1#9 P}  fence: [A1, A2, A3, C1]  ->  {C1#9 V, B1 V, B2 V, B3 P}  fence: [A1, A2, A3, C1]
```

`C1` gets the highest priority (5) again. It is the leader only after the leadership balancing (5.3).

Automatic failover is available again. Then the failback adds zone A. `A1` to `A3` start without data with new incarnations, and the leader adds them. Their old fence entries stay. The failback is manual here, because #21 counts the forced change as a configuration change.

**Remaining danger.** `A1, A2` and `C1` have 3 of 5 old votes. If they start with old data, and they cannot reach `B1` or `B2`, they can elect a leader among themselves. The fence list does not help then. Thus, the operator must keep them down until the network to zone B works (Q1).

### 5.11 Validation, activation and status

#### 5.11.1 Plan validation

When the coordinator makes a scale-down plan, it rejects the plan with `INSUFFICIENT_VOTER_CAPACITY` if a zone then has fewer brokers than slots in the voter policy. The operator first lowers the count of that zone with "update system partition voter policy", then sends the scale-down again. This closes Q8.

The check does not apply to "force-remove brokers", to "remove zone" and to the automatic failover. After a failover, the slots of the lost zone stay empty on purpose (D3).

The plan preview shows, for each joint phase of the system partition, the voter sets, the quorum of each set, and the number of brokers and zones that can fail.

#### 5.11.2 Activation of an existing cluster

A cluster that runs an older version has no system partition. The probe of 5.4.2 finds "no data" on all brokers, but the static configuration can be different from the current members. Thus, an existing cluster uses an activation instead of row 3:

1. All brokers run the new version. The cluster version allows the system partition (Q12). The probe shows the origin `EXISTING` (5.4.5), and no answer has an index above 0.
2. The coordinator stops new plans and waits until no plan is pending.
3. The coordinator calculates the initial configuration with the rules of 5.4.1 from the **current** members, not from the static brokers.
4. The coordinator sends the initial configuration and its hash to each member. Each member stores it and bootstraps with it (row 1 at each later start). All voters of the initial configuration must acknowledge. A passive member that does not acknowledge starts later with row 2.
5. The coordinator waits until the configuration index is above 0 (R14). Then it allows plans again.

A broker that stored the activation configuration answers the probe with "activation" and the hash. A broker without data that gets such an answer never uses row 3. It waits until an answer has a configuration index above 0, and then uses row 2. A retry of the activation sends the same configuration.

The origin marker of 5.4.5 tells the start step that the cluster existed before the system partition.

#### 5.11.3 Disaster-recovery bootstrap

Row 3 without all probe answers needs a one-time request to a broker. The request has the expected hash of the initial configuration. The broker accepts it only if it has no stored configuration, no probe answer has a configuration index above 0, and the hash is the same as its own. The broker does not store the request. A later start without data uses the normal probe.

#### 5.11.4 Status and errors

The status read returns:

- the voter sets of the configuration, and the two sets of a joint phase if one is in progress;
- the quorum of each set, and the number of brokers and zones that can fail;
- the voter policy and its version;
- the fence list and the last recovery record (5.6.3).

New errors: `NO_LEADER` (R3), `INSUFFICIENT_VOTER_CAPACITY` (5.11.1), `METADATA_COMPLETENESS_UNKNOWN` (5.6.3). Each error names the blocked operation and the necessary action.

5.12 gives the endpoints. They need approval before implementation (Q20).

### 5.12 Management API

This section is a proposal for review. The new endpoints are on the REST API of the gateway, under `/cluster/v2`, as `/cluster/v2/status` and `/cluster/v2/topology` today. The existing management API on the actuator (`/actuator/cluster`, `zeebe/dynamic-config/openapi/src/main/resources/api/cluster-api.yaml`) stays supported, and this design changes it where necessary.

#### 5.12.1 New endpoints

| Method and path | Purpose | Body and parameters | Responses |
|---|---|---|---|
| `GET /cluster/v2/system-partition` | Status of the system partition (5.11.4). | — | `200`: leader; configuration index and term; joint phase (yes or no); the voter sets (one set, or old and new) with the quorum of each set; the number of brokers and zones that can fail; for each member: id, zone, type, incarnation, lag to the commit index, time of the last answer, authorized (yes or no); voter policy and version; fence list; last recovery record; activation state (`NOT_ACTIVATED`, `ACTIVATING`, `ACTIVE`). `503 NO_LEADER` returns the local view of the broker that answered, marked as not current. |
| `PUT /cluster/v2/system-partition/voter-policy` | "Update system partition voter policy" (5.1). | Body: `{ "zones": { "<zoneId>": <voters> }, "zoneOrder": ["<zoneId>", ...] }`. Query: `dryRun`. | `202`: the new policy and its version, and the promotions and demotions that follow. `400`: invalid policy (5.1 validation). `409`: a change of the policy or of the members is in progress. `503 NO_LEADER`. |
| `POST /cluster/v2/system-partition/force-voters` | "Force system partition voters" (5.6.5). | Body: `{ "downBrokers": ["<brokerId>", ...], "acceptPossibleLoss": false }`. Query: `dryRun`. | `200`: the forced configuration, the fenced brokers, the completeness result. `400`: a voter did not answer and is not in `downBrokers`. `409 SYSTEM_PARTITION_HAS_QUORUM`: a normal change is possible. `409 METADATA_COMPLETENESS_UNKNOWN`: the check of 5.6.3 failed; the body lists the voter sets, the members that answered and the members that are missing. |
| `POST /cluster/v2/system-partition/activation` | Start or retry the activation of an existing cluster at once (5.11.2). The coordinator also starts it automatically. | — | `202`: activation started; the body has the initial configuration and its hash. `409`: a plan is pending, a broker runs an older version, or the system partition already exists. |
| `POST /cluster/v2/system-partition/bootstrap` | Disaster-recovery bootstrap of one broker (5.11.3). | Body: `{ "brokerId": "<brokerId>", "expectedHash": "<hash>" }`. | `200`: the broker bootstraps. `409`: the broker has a stored configuration, a probe answer has an index above 0, or the hash is different. |

The gateway sends each request to the system partition leader, or to the given broker for `bootstrap`. "Force voters" and "bootstrap" do not need a leader. The gateway sends them to a broker that can reach the members (5.6.5).

#### 5.12.2 Changes to the existing actuator endpoints

| Endpoint | Change |
|---|---|
| `DELETE /actuator/cluster/brokers/{brokerId}?force=true`, `POST /actuator/cluster/brokers?force=true`, `DELETE /actuator/cluster/zones/{zoneId}?force=true` | New query parameter `acceptPossibleLoss` (default `false`). When the coordinator makes the plan, it runs step 1 and the completeness check of 5.6.3. If phase 0 needs a forced change and the check fails, the request fails with `409 METADATA_COMPLETENESS_UNKNOWN`, unless `acceptPossibleLoss = true`. The plan stores the value in the operation `SystemPartitionRemoveMembers(executor, removedBrokers, acceptPossibleLoss)`. Phase 0 checks again when it runs. If the check fails then, the operation fails and is retried; the operator can cancel the plan and send it again with the flag. |
| `POST /actuator/cluster/brokers` (without `force`), `DELETE /actuator/cluster/brokers/{brokerId}` (without `force`) | New error `400 INSUFFICIENT_VOTER_CAPACITY` when a zone then has fewer brokers than slots (5.11.1). The body names the zone, its brokers and its slots. |
| All plan requests with `dryRun=true` | `PlannedOperationsResponse` gets a section `systemPartition`: normal or forced change, the completeness result, the target voter sets of each joint phase, the quorum of each set, and the number of brokers and zones that can fail. |
| `GET /actuator/cluster/changes`, `GET /actuator/cluster/changes/{changeId}` and the plans in `GET /actuator/cluster` | New operation type `SYSTEM_PARTITION_REMOVE_MEMBERS` with `executor`, `removedBrokers` and `acceptPossibleLoss`. |
| Error bodies (`components.yaml`) | A new field `code` with the new error codes (`NO_LEADER`, `INSUFFICIENT_VOTER_CAPACITY`, `METADATA_COMPLETENESS_UNKNOWN`, `SYSTEM_PARTITION_HAS_QUORUM`). Each error names the blocked operation and the necessary action. |

`GET /cluster/v2/status` gets a field `systemPartition` with a short health summary: quorum (yes or no), leader and activation state. `GET /cluster/v2/topology` does not change.

The automatic failover uses the force-remove plan internally. It never sets `acceptPossibleLoss` (D13).

## 6. Decisions

### D1. The Raft configuration is the only record of the system partition members

**Decision.** The members and their types are only in the Raft configuration.

**Why.** The system partition will later hold the dynamic configuration. If the dynamic configuration stored the system partition members, each would need the other to change. The forced recovery must work when the system partition has no quorum. Raft already stores its configuration safely and recovers it at restart.

**Alternatives that were rejected:**

- *Store the members in `ClusterConfiguration`, as for data partitions.* This makes a circular dependency. It also gives two records that can be different.
- *Store the voters as a key in the system partition state.* This gives two records. A key can only change with a quorum, thus it does not help the forced recovery.

### D2. The leader monitors the cluster members and changes the system partition members

**Decision.** The leader of the system partition makes all normal changes of the members (5.5). It monitors the cluster members, the published incarnations and the replication state. It adds, removes, replaces, promotes and demotes members. The joining or leaving broker does not change the Raft configuration. `MemberLeave`, `MemberRemove` and phase 0 wait for the leader. Only the forced changes (phase 0 without a quorum, "force system partition voters") do not use the leader.

**Why.**

- Later, the dynamic configuration is in the system partition. A joining broker then cannot read it, because its replica has not caught up. It cannot even read the `MemberJoin` operation that tells it to join. The leader always has the current configuration.
- Later, the leader of the system partition is the configuration coordinator. The rules of 5.5 then run where the cluster members change. The leader authorizes the target of a pending `MemberJoin` (D21). Thus, the new broker does not need the dynamic configuration before it is a member.
- The leader knows the replication state of each member. Thus, it can see when a passive member has caught up and when a broker answers.
- One actor changes the members. No exact index check between brokers is necessary, and no broker other than the leader sends a target configuration.
- A new leader applies the rules again from the Raft configuration. Thus, a change that the earlier leader did not complete is completed.
- The joining broker needs no dynamic configuration. It needs only its incarnation (5.4.4).

**Alternatives that were rejected:**

- *The joining or leaving broker changes the Raft configuration, and each broker keeps its own membership correct: self-promotion, self-join, a join admission hook on the leader (revision 6).* The joining broker needs its local dynamic configuration to join and to promote itself. When the dynamic configuration is in the system partition, the joining broker cannot read it.
- *New operations in the dynamic configuration, for example `SystemPartitionAddMember` and `SystemPartitionRemoveMember`, that a plan adds after `MemberJoin` and before `MemberLeave`.* The plan gives a clear order, and the progress is visible in the plan. But:
  - Each operation has one executor (`memberId`). The leader of the system partition can change, thus it cannot be the executor until it is the coordinator. The coordinator would have to send the change to the leader.
  - Some changes have no operation: the promotion after a passive member has caught up, a new policy, a broker with a new incarnation after a disk loss, and the return of a member that "force system partition voters" made passive. The leader must handle these anyway.
  - A plan must not wait for a catch-up, because global operations run one at a time.
  - Thus, the leader would have two inputs for the same work. The leader-driven design has one input.
- *Only explicit operations (draft 1).* Some brokers never get an operation: the control broker after "force system partition voters", or a broker after a cancelled plan.

### D3. The voter policy is a count for each zone

**Decision.** Each zone has a voter count. An empty slot is not filled from a different zone. The policy comes from the static configuration. The system partition state stores it (D18). An operation can change it with a write to the system partition.

**Why.**

- After a failover, the cluster has the voters of the remaining zones (for example 2 + 1). The control zone keeps its vote, as #21 requires.
- At failback, the returning brokers fill the slots of their zone. No special failback logic is necessary.
- No vote moves between zones. Thus, one data zone never gets the majority.
- The system partition state gives all brokers the same policy and the same zone order.
- The rolling conversion to 2.5 regions needs a change of the policy at runtime.

**Alternatives that were rejected:**

- *One total count, round-robin across zones, as in the earlier failover PoC.* After a failover the leader fills the missing slots from the remaining zone, and at failback it must demote them again.
- *One total count at bootstrap, then replace a voter only in its zone.* The slots of a zone are lost after the zone is empty.
- *Use only the static configuration at runtime.* Brokers with different static values calculate different targets.
- *Store the policy in the dynamic configuration (`ZoneSpec`).* This makes a new dependency on the dynamic configuration (D1).
- *A fixed policy (revision 2).* It blocks the rolling conversion to 2.5 regions.
- *The policy in the Raft configuration (revisions 5 to 6).* See D18.

### D4. The leader calculates the target configuration

**Decision.** The leader calculates and writes the target configuration of each normal change (5.5). Other brokers send only a read of the configuration to the leader (R3). A policy change is a write to the system partition state (D18).

**Why.** The leader has the committed configuration and the replication state. A change of the leader cannot conflict with a change of a different broker. Thus, no exact index check and no rejection with the current configuration are necessary.

**Alternative that was rejected:**

- *The broker that runs the operation calculates the target and sends it with the configuration index and term. The leader checks them exactly and returns its current configuration in a rejection (revision 6).* It needs a remote API for full target configurations and the forwarding of requests by non-members (Q11 of revision 6). It is not necessary when the leader makes the changes (D2).

### D5. Non-voters are `PASSIVE`, not `PROMOTABLE`

**Decision.** A non-voter is `PASSIVE`.

**Why.** A passive member gets only committed entries. The decision of Nov 10 says that a broker reads committed entries locally.

**Alternative that was rejected:**

- *`PROMOTABLE` for all non-voters.* The promotion is a little faster, but code that reads the local replica must filter entries that are not committed.

### D6. The liveness of a broker does not change the voters

**Decision.** Only the rules in I3 change the voters.

**Why.** The system partition detects failures and decides the failover. If it also replaced voters that are down, it could move votes to one side of a network partition. #21 decided that the cluster takes no automatic action when the control region fails.

**Alternative that was rejected:**

- *Replace a voter that is down for a configured time.* See above. Liveness is used only to reject the promotion of a broker that is down (R9). That never removes a voter.

### D7. Bootstrap with typed members

**Decision.** At the first start, all static brokers bootstrap with the same typed configuration (R1).

**Why.** All brokers start with the same configuration at index 0. A non-voter does not need a leader to start.

**Alternatives that were rejected:**

- *The initial voters bootstrap, and the other brokers join as `PASSIVE`.* The first start of a non-voter needs a leader. A non-voter that restarts must use `bootstrap` with itself in the member list, else `RaftPartition.bootstrap` does nothing.
- *Bootstrap with all brokers as voters.* The quorum is then a majority of all brokers.

### D8. A broker without data probes all static brokers before it bootstraps

**Decision.** A broker without a stored configuration bootstraps only if all static brokers answer with "no data" or index 0, with the same hash. If one answer has an index above 0, it joins. The first leader appends a configuration entry before it commits any other entry (R14, D19). Thus, an index above 0 shows that the system partition exists.

**Why.** After a change, the initial configuration is out of date. If some brokers with empty disks bootstrapped it, they could be a quorum of the initial configuration and start a second system partition. A broker that bootstraps a little later than the others gets index 0 answers and makes the same configuration. Before the first configuration entry commits, no entry with data is committed. Thus, the late broker loses nothing. The hash finds brokers with different initial configurations. It is not checked for a join, so that scale-up and a changed static configuration work.

**Alternatives that were rejected:**

- *Always bootstrap from the static configuration.* See above.
- *Always join.* At the first start no leader exists.
- *Bootstrap after a timeout if a quorum of the initial voters answered "no".* After a disk loss of several brokers, they can be that quorum.
- *Read the dynamic configuration to find out if the cluster is new.* This makes a new dependency on the dynamic configuration (D1).
- *Probe for a Raft term or a log above 0, instead of a configuration index above 0.* See D19.

### D9. A leaving voter is replaced in one joint change

**Decision.** When a voter leaves, or the leader replaces its incarnation, the leader sends one change that removes the voter and promotes a replacement of the same zone. If no replacement is possible, it removes the voter only. The leader removes all brokers of a force-remove plan in one change (phase 0).

**Why.** Joint consensus keeps the quorum of the old voters and of the new voters during the change. The number of voters in a zone does not go above the policy. If the replacement came first in a separate change, zone A could have 3 of 6 voters for some time. If zone A then failed, the system partition would have no quorum.

**Alternatives that were rejected:**

- *Promote first, then remove (draft 1).* See above.
- *Remove first, then promote.* The zone has one voter less for some time.
- *Remove the brokers of a force-remove one by one.* Each removal is a joint change with no spare voter. Between the removals, a fill step could promote a broker that is down.

### D10. The election priority comes from the Raft configuration and the voter policy

**Decision.** Each broker calculates its priority from the Raft configuration and the voter policy. At start-up, the policy comes from the local policy cache (5.1). The target priority is the total of the policy. The priority of a voter is at least 1. The zone order is frozen in the policy. The priority election does not take the leadership from a leader that runs. The broker starts the leadership balancing when a voter with a higher priority has caught up (5.3).

**Why.** The voters change at runtime. A target from the policy is the same on all brokers. If the target were the current number of voters, brokers that did not restart would keep an old target, and several voters would start elections at the same time. The zone priorities of the dynamic configuration can change at runtime, thus brokers could calculate different priorities. The policy cache is on the local disk. Thus, each broker has its priority at a restart, also without a leader and before it has applied the state.

### D11. The voters of a forced configuration are the members with the most recent log

**Decision.** Before a forced change, the broker collects the logs of the reachable members. Only the members with the most recent log become voters. Each zone gets at most the number of voters that its slots allow. If no member with the most recent log is in a zone of the policy, one of them becomes a voter anyway. In phase 0, each voter that the forced change demotes goes on the fence list (5.6.3). The leader restores the policy later with promotions and demotions (5.6.4).

**Why.** The member with the most recent log has all entries that any reachable member knows as committed (Raft log matching and leader completeness). If a member with an older log were also a voter, it could win an election without the most recent member. Then a passive member that had more entries would have to truncate below its commit index. Raft cannot do that, and local readers already saw the entries. The slot limit keeps the number of voters in a zone inside the policy, as the leader check of R3 expects.

**Alternatives that were rejected:**

- *The surviving voters, plus a passive member with a more recent log (revision 2).* The surviving voters can still elect a leader without that passive member.
- *Always the surviving voters.* A passive member with more entries fails when it must truncate them.

### D12. Fence list

**Decision.** A removal and a forced change put the brokers on a fence list in the configuration, with their incarnations (R8). Members reject requests from a sender whose id and incarnation match an entry. A fenced broker deletes its data and starts again without data and with a new incarnation (5.4.3). Fence entries are never cleared (D17).

**Why.** A failed broker has an old configuration in which it can be a voter. After the next normal configuration, the `force` flag no longer protects the survivors. A fenced broker could then send vote requests with a higher term and make the leader step down. If it became leader, the survivors would have to truncate committed entries.

**Alternatives that were rejected:**

- *Only a runbook step that deletes the data (draft 1).* An operator can forget it. The survivors have no protection.
- *A configuration epoch: a broker must know the current epoch before it can vote.* Each voter must learn the epoch, which is the same work as the fence list. The fence list also tells exactly which brokers must delete their data.
- *Check the dynamic configuration at the start.* It starts after the system partition (D16).

### D13. The force-remove plans change the system partition first

**Decision.** Force-remove plans get phase 0. It selects a forced change only if the voters that are not removed are not a quorum.

**Why.**

- The operator sends one request in a dual-region cluster without a control zone. The order is always correct.
- A forced change is used only when a normal change can never commit. In a 2.5-region cluster, the removal of one data zone is always a normal change.
- A retry finds a forced change in progress and sends it again. Thus, two tries do not make two different forced configurations.

**Alternatives that were rejected:**

- *A separate runbook command for every forced change.* In a dual-region cluster, the operator must call two commands in the correct order.
- *Always use a forced change.* It loses the safety of joint consensus. The automatic failover must not make forced changes.
- *Decide on the measured liveness of the voters.* Two tries could make different decisions.
- *Put the step after the data partitions.* Later the plan is stored in the system partition.

### D14. "Force system partition voters" removes no member

**Decision.** When the system partition has no quorum and the failed brokers must stay cluster members, a separate operation makes the surviving members with the most recent log the only voters. It makes all other voters passive and puts the failed brokers on the fence list. It removes no member. It is not a plan of the dynamic configuration.

**Why.** #21 expects this order: force the system partition to the surviving voters, then fail over through the normal path. The normal path records the failover state and the failback reference. Because no member is removed, I1 stays true. A plan cannot run while a stuck plan runs. Thus, the operation must not wait behind a plan.

**Alternative that was rejected:**

- *One force-remove request for zone A and the control zone (draft 1).* It bypasses the failover path. Two separate "remove zone" requests block: the first one sees a quorum on paper, but a voter in it is down.

### D15. A broker without data does not vote until the leader promotes it

**Decision.** A broker without data starts without a configuration and with a new incarnation (R16, 5.4.3). It accepts only a configuration in which it is passive with its own incarnation. The leader replaces an old incarnation of the broker with the new one as a passive member. The broker becomes a voter only when the leader promotes it after it has caught up.

**Why.** A voter with an empty disk gives any vote, and it lost its record of its last vote. Example: entry E is committed on `A1, A2, C1`. `C1` loses its disk and comes back. Then zone A fails. `B2` does not have E. `C1` and `B1` vote for `B2`, and E is lost. With this decision, `C1` is passive until it has E. While the old incarnation of `C1` is a voter, it counts as a voter that is down (R17).

The leader makes the changes of the members (D2). Thus, a replacement needs no extra mechanism. No marker on the disk and no probe term are necessary. A configuration from a deposed leader can only make the broker passive.

**Alternatives that were rejected:**

- *Accept the limit, as for data partitions (draft 1).* The system partition holds the failover decisions.
- *Empty-start guard: the broker keeps its voter type, but does not vote until its commit index reaches the commit index of a leader with a term of `probeTerm` or more (revisions 3 to 6).* It needs a marker on the disk, a probe of a quorum of the voters and the join of the broker. The broker cannot join when the dynamic configuration is in the system partition (D2). Its advantage: the broker stays a voter, and a quorum without it is not necessary for the change. But a broker under the guard does not vote either. Thus, the availability is almost the same.
- *Release the guard at the commit index of any leader (revision 2).* A deposed leader on a minority side can release it too early.

### D16. The start does not read the dynamic configuration; only the leader reads it

**Decision.** The start of the system partition uses only local data, the static configuration and the probe. A broker without data starts without a configuration and waits for the leader (5.4.3). Only the leader reads the cluster members, at runtime (5.5). It adds only a broker that is `JOINING` or `ACTIVE` and not the target of a pending removal. Each removal puts the broker on the fence list, so that a removed broker with old data is rejected.

**Why.** The dynamic configuration starts after the system partition. Thus, a starting broker cannot know if it is a cluster member. The leader reads its dynamic configuration after it is initialized. The membership version keeps a leader with an older view from undoing a change (R19). A `LEAVING` broker, or the target of a pending removal, will soon not be a cluster member. If the leader added it, I1 could become false. Later, the cluster members are in the system partition state, and the leader reads them there. Thus, the design does not change when the dynamic configuration moves into the system partition.

**Alternatives that were rejected:**

- *Check the dynamic configuration at the start (revision 3, rows 1 and 2).* It is not available at that time. The stored file is also old for a broker that was down during its removal.
- *Let the system partition start wait for the dynamic configuration.* The dynamic configuration will later depend on the system partition. This makes a circular wait.
- *The broker asks to join, and the leader admits it if the broker is a cluster member (revision 6).* The joining broker read its local dynamic configuration before it asked, and sent its version. That is not possible when the dynamic configuration is in the system partition (D2).
- *Leader stickiness in Raft: ignore vote requests while a leader is known.* It stops a removed broker from making the leader step down. But the removed broker still runs with old data and is never told to delete it. It can still be a quorum of an old configuration with other removed brokers. The fence list covers both. Leader stickiness is a good general change for Raft, but it is not enough alone.

### D17. Fence entries have an incarnation and are never cleared

**Decision.** Each member has an incarnation (R15). A fence entry is a broker id and an incarnation. A member rejects a request only if both match. A broker acts on an entry only if the incarnation is its own, and the entry is committed, a member answered `FENCED`, or the leader sent the fenced notice. A member answers `FENCED` only if the entry is committed in its own view. Fence entries are never cleared.

**Why.** A broker that starts again without data has a new incarnation. Thus, its old entries never match it, also on a member with an old stored configuration. The number of entries is not more than the number of removals, replacements and forced changes. An uncommitted entry can disappear. Thus, a broker must not delete its data because of it. For the same reason, a member that has the entry only in a configuration that is not committed answers `ILLEGAL_MEMBER_STATE`, and the sender ignores it.

**Alternatives that were rejected:**

- *Clear the entry when the broker joins again (revision 4).* A member can be down during the rejoin and keep an old stored configuration with the entry. It then rejects the appends of the rejoined broker. The rejoined broker deletes its data, also when it is the legitimate leader.
- *Clear the entry when the dynamic configuration no longer knows the broker (Q10 of revision 4).* It is not necessary, because the number of entries is limited. It also makes a new dependency on the dynamic configuration.

### D18. The voter policy is a record in the system partition state, not in the Raft configuration

**Decision.** The system partition state stores the voter policy as a record (5.1). The Raft configuration has only what Raft enforces: member types, incarnations, the fence list and the membership version. Each replica keeps a local cache of the last policy that it applied, for the election priority at restart. The membership component checks the zones and the slots. Raft has no logic for zones (R20).

**Why.**

- Raft stays a general protocol. The policy is a rule of the system partition, not of consensus. Data partitions do not need it.
- In revision 7, only the leader changes the members (D2). A new leader applies all committed entries before it acts. Thus, it always has the current policy. No broker can act on an old policy, as self-promotion could in revision 4.
- A policy change is a normal write. It is ordered with the other writes of the system partition.
- The election priority must be available before the state is applied. The local cache gives it. The priority is only a hint: a wrong value delays an election, but it does not affect safety.
- A forced change needs the slot limits without a leader. Each reachable member reports the policy that it applied. The executor uses the policy with the highest version. A policy that is too old changes only the number of voters in a zone, not safety.

**Alternatives that were rejected:**

- *The policy in the Raft configuration (revisions 5 to 6).* Each broker has the policy at restart without a cache. But Raft must store, serialize and check a rule of zones that only the system partition uses. In revision 6, this was necessary because each broker promoted itself and the leader checked each change. In revision 7, the leader alone changes the members.
- *Only the static configuration.* Brokers with different static values calculate different targets, and the policy cannot change at runtime (D3).

### D19. The first leader appends a configuration entry

**Decision.** When its initial entry is committed, the first leader of the system partition appends a configuration entry. It does this before it appends any other entry and before it announces its leadership (R14). The probe uses a configuration index above 0 to find out that the system partition exists.

**Why.** Today the first leader appends only an initial entry. A cluster that never changed its members keeps configuration index 0. The probe then cannot see that the system partition exists. With the new entry, nothing with data commits before the index is above 0. Thus, a late broker that bootstraps at index 0 loses nothing.

The PoC showed why the leader waits until the initial entry is committed. A follower can reset its log on the first append from the leader. Then it loads its stored configuration again and loses a configuration that it received before that append. Its stored configuration has index 0, and the leader does not send the configuration again to a member that reports index 0. Thus, the follower would keep index 0. In addition, the PoC does not load the stored configuration again if the reset deletes no entries.

**Alternative that was rejected:**

- *Probe for a Raft term above 0 or a log above 0.* At a first start, brokers that already tried elections have a term above 0. A late initial voter then starts without data in a partition that has no leader. It does not vote (5.4.3). If the quorum needs its vote, no leader is elected. This is a deadlock.

### D20. A forced change checks completeness with a blocking set

**Decision.** Before a forced change, the executor checks that the members that answered (`R`, with the incarnation of `K`) are a blocking set of each voter set of `K` (5.6.3). If the check fails, the operator must send `acceptPossibleLoss = true`. The executor writes a recovery record after the change.

**Why.** A forced change can lose committed entries. Without the check, a complete case (dual-region, both survivors answer) and a lossy case (2.5 regions, control zone and one data zone lost) look the same to the operator. The commit index alone cannot prove completeness: an entry can be committed while no survivor knows that it is committed.

**Argument.** Let `e` be a committed entry.

1. `e` is on a quorum of at least one set of `S(K)`:
   - `e` committed while `K` was in effect: on a quorum of each set of `K`.
   - `e` committed before `K`, and `K` is joint with the old set `V_o`: the configuration before `K` had `V_o`, thus `e` is on a quorum of `V_o`.
   - `e` committed before `K`, and `K` is the final entry of a normal change with the set `V_n`: the joint entry before `K` committed on a quorum of `V_n`, and `e` is before it in the log (log matching).
   - `e` committed before `K`, and `K` is forced: each voter of `K` had the most recent log of the earlier recovery (D11). If that recovery passed the check, the log had `e`. Else the operator already accepted the loss.
   - `K` is the initial configuration: no entry is before it.
2. A configuration after `K` that the executor did not see cannot have committed an entry. Its commit needs a quorum of a set of `S(K)`. That quorum has a member of `R`, which would have reported the later configuration.
3. `R` is a blocking set of the set of step 1. Thus, a member of `R` has `e`.
4. The most recent log `L` in `R` has each committed entry that a member of `R` has (leader completeness and log matching). The voters of the forced configuration have `L` (D11). Thus, they all have `e`.

A broker that answers with a different incarnation lost its log. It does not count in `R`.

**Alternatives that were rejected:**

- *No check; accept the loss in all cases (revisions 1 to 7 before the review).* The operator cannot see a possible loss.
- *A new recovery epoch from a snapshot, with an offline tool and a recovery hold (alternative design B).* It gives the same evidence. But it needs a new Raft group install and tools that do not exist, and it changes decision #21, which asks for a forced change in place.

### D21. The leader authorizes the target of a pending `MemberJoin`

**Decision.** A broker is authorized if it is `JOINING` or `ACTIVE`, or the target of a pending `MemberJoin`, and it is not `LEAVING`, `LEFT` or the target of a pending removal (5.5).

**Why.** When the dynamic configuration is in the system partition, the new broker can run `MemberJoinApplier.init` only after it is a member and has caught up. If only `JOINING` brokers were authorized, the broker would never be added. The plan already authorizes the broker. The rule is the same in both stages; only the source of the plan changes. A cancelled plan removes the authorization, and rule 1 removes the broker.

**Alternatives that were rejected:**

- *Only `JOINING` or `ACTIVE` (revision 7 before the review).* It deadlocks when the dynamic configuration is in the system partition.
- *Explicit plan operations that add and remove system partition replicas (alternative design B).* See D2.
- *Remove the target of a pending `MemberLeave` at once.* A scale-down plan moves the data partitions before `MemberLeave`. The voter would lose its vote for that time, and a cancelled plan would fence a broker that stays. Rule 1 removes it when it is `LEAVING`.

## 7. Risks and open questions

- **Q1. Isolated fenced brokers.** The fence list protects the survivors. A group of fenced brokers that cannot reach a survivor can still be a quorum of the old configuration. The operator must keep them down until the network works. Is a stronger protection necessary?
- **Q2. Product confirmation.** Phase 0 of the force-remove plans does the forced change in a dual-region cluster without a control zone. "Force system partition voters" and "update system partition voter policy" are new management operations. #21 planned a runbook step in v1. Product must confirm.
- **Q3. Load on the leader.** The leader sends heartbeats and entries to each broker. It also runs the rules of 5.5. For clusters with tens of brokers this is acceptable.
- **Q4. Upgrade and mixed versions.** In an existing cluster, the probe finds no data on all brokers. Older brokers cannot answer the probe. The static configuration can be different from the current cluster members. The initial configuration must use the current cluster members. This needs a separate design. Resolved in revision 7: the origin marker (5.4.5) and the activation (5.11.2).
- **Q5. Dependency on the dynamic configuration.** The start does not use it. Only the leader reads it, at runtime (5.5). `MemberJoin` and `MemberLeave` are its operations. `MemberLeave`, `MemberRemove` and phase 0 wait for the leader. When the dynamic configuration moves into the system partition, the leader reads the cluster members from the system partition state.
- **Q6. Cancel blocks failback.** After `CancelChange`, members stay in the configuration as `LEFT`. Then `MemberJoinApplier.init` rejects them. This is an existing problem, but it is on the failback path.
- **Q7. Zone migration.** 5.6.10 gives only the rule. It needs a separate design.
- **Q8. Voters after a scale-down.** Resolved in revision 7. A scale-down that leaves a zone with fewer brokers than slots is rejected (5.11.1).
- **Q9. Lost and resurrected entries.** A forced change can lose system partition entries that were committed only on the fenced brokers. Also, an entry that was never committed can become committed after a forced change. For example, a deposed leader appended the entry. Consumers of the system partition state must accept this, as they must for data partitions. Revision 7 makes a possible loss explicit (D20), and the recovery record shows it in the state (5.6.3).
- **Q10. Size of the fence list.** Resolved in revision 5. Fence entries are never cleared (D17). Their number is not more than the number of removals, replacements and forced changes.
- **Q11. Forwarding by non-members.** Resolved in revision 7. Only the requests of R3 go to the leader. The appliers that send them run on members, or read from a different member (5.6.2).
- **Q12. Mixed versions in the cluster.** The new Raft error type `FENCED`, the new management errors (5.11.4), the new fields of the Raft requests and the incarnation property of the membership are not known to older brokers. Thus, every broker must run the new version before the system partition uses them. Gate the system partition behind the cluster version.
- **Q13. Leader change in the joint phase of a removal.** Proposed solution in revision 7: the configuration that ends a joint phase fences each member that is in the old members but not in the new members (R18). This needs a test in the PoC.
- **Q14. Stale view of the leader.** Today the leader reads the cluster members from gossip. Its view can be older than the view of the broker that runs a plan. The membership version (R19) keeps a new leader from undoing a change. A stale view only delays a change: the leader adds only `JOINING` or `ACTIVE` brokers, it removes only brokers that leave or are removed, and it never acts on liveness. The appliers that wait for the leader have a limit and are retried. Is the delay acceptable for the failover? The same delay applies to a pending `MemberJoin` in the gossip view (D21). It ends when the dynamic configuration is in the system partition.
- **Q15. Replacement in the joint phase.** In a replacement (5.5, rule 2), one member id is in the old members and in the new members with different incarnations. Raft keeps one member context for each member id. R17 must count the answers for the correct incarnation. The PoC must show that this is possible without a larger change of `RaftClusterContext`.
- **Q16. Leader leaves.** The leader transfers its leadership before it removes itself (5.6.2). If no other voter has caught up, the transfer fails and the removal waits. Is a removal of the leader by itself (Raft steps down after the commit) a better fallback?
- **Q17. State machine of the system partition.** The policy record needs the state machine of the system partition and its local reads (decision of Nov 10). The design of that state machine is not part of this document. The policy cache must be written by that state machine when it applies the record, also on passive replicas.
- **Q18. `acceptPossibleLoss` and #21.** Decided on Nov 18, 2026: the explicit flag applies also to "force system partition voters". In the case of #21, the operation fails without `acceptPossibleLoss = true`, unless a failed voter comes back with its disk. Product must still confirm the operator procedure (Q2).
- **Q19. Recovery record.** Is the recovery record enough, or must the failover logic stop until an operator confirms? This depends on what the failover state machine does with a lost decision. A recovery hold is not part of stage 1.
- **Q20. Public API.** 5.12 proposes the new endpoints under `/cluster/v2` and the changes to the actuator endpoints. Decided on Nov 18, 2026: propose them in this design; review and approval follow.

## 8. Tests

- Raft: bootstrap with typed members and incarnation 0. Restart keeps the types. A passive member never becomes a candidate, also with `anoint()`.
- Raft: the first leader appends a configuration entry when its initial entry is committed, before any other entry and before it announces its leadership. After that, the configuration index is above 0.
- Raft: promotion from `PASSIVE` to `ACTIVE`. A member that did not answer within the election timeout is not promoted. A change that adds a voter to a zone beyond its slots is rejected. A demotion in a zone with too many voters is accepted. A target with a fenced voter is rejected.
- Raft: a forced change completes when a passive member is down. Only the members with the most recent log become voters, and not more than the slots of each zone. The change goes to all members that answered.
- Raft: start without data (R16). The server does not vote or poll. It rejects a configuration in which it is a voter or has a different incarnation. It accepts a configuration in which it is passive with its own incarnation, stores it and is passive after a restart.
- Raft: replacement of an incarnation. In the joint phase, the answers of the new incarnation do not count for the old voter (R17). The old incarnation is fenced. The leader promotes the new incarnation only after it has caught up.
- Raft: the end of a joint phase fences the removed members, also when a new leader ends it (R18).
- Raft: fence list. Members reject a fenced (id, incarnation) without a term update, also in responses. A member answers `FENCED` only for a fence entry that is committed in its view, else `ILLEGAL_MEMBER_STATE`. The leader sends the fenced notice at most once per heartbeat interval and no entries, also to a non-member that publishes a fenced incarnation. The fenced broker deletes its data and starts again with a new incarnation. The old entry stays and does not reject it. A member with an old stored configuration accepts the appends of the broker with the new incarnation. An uncommitted entry without a leader notice does not make a broker delete its data.
- Raft: storage. Decode real version-4 bytes of a configuration entry and of a `.conf` file. The result has incarnation 0 for each member, an empty fence list and membership version 0.
- Unit: priorities. A zone with more voters than slots gives each voter a priority of 1 or more.
- Unit: the rules of the leader (5.5) for each case: a new `JOINING` broker, a broker without an incarnation, a broker with a fenced incarnation, `LEAVING`, `LEFT`, a target of a pending removal, a new incarnation of a voter and of a passive member, the leader leaves, an empty slot, extra voters, a zone that is not in the policy, a dynamic configuration that is not initialized, a version below the membership version.
- Unit: phase 0 forced change. A voter that is not removed and is not a voter of the forced configuration goes on the fence list.
- Unit: probe decisions: all "no data", index 0 answers, one index above 0, a different hash at bootstrap, a different hash for row 2, missing answers.
- Integration (2 + 2 + 1 voters on 3 + 3 + 1 brokers): add a broker, remove a voter, remove the leader, failover of a zone, failback, restart of each broker, restart of all brokers, disk loss of a voter and of a passive member, cancelled plan after phase 0, `MemberRemove` in phase 2, a leader change during a removal.
- Integration: "force system partition voters" with the control zone and zone A down, then the automatic failover, a restart of all brokers, then the return of `C1`. `C1` is the leader again after the leadership balancing.
- Integration: `MemberLeave` of a voter. The leaving broker is fenced before it reads the result, and the applier completes after it reads the leader's configuration from a different member.
- Integration (4 + 4 brokers without a control zone): force-remove of zone B, a restart of all brokers, then failback.
- Integration: rolling conversion from dual-region to 2.5 regions with "update system partition voter policy". Each replica updates its cache and priority when it applies the record.
- Unit and integration: the policy cache. A restart of all brokers uses the cached priorities before the state is applied. A missing cache falls back to the static policy. The first leader writes the initial policy record once, also after a leader change. A forced change uses the policy with the highest version from the reports.
- Integration: restart of all brokers with dynamic node ids.

- Unit: authorization. Rule 3 adds a pending `MemberJoin` target that is not `JOINING`. Rule 1 removes it when the plan is cancelled. A pending `MemberLeave` target is not added or promoted, and it is removed only when it is `LEAVING`.
- Integration (simulation of stage 2): the dynamic configuration is only in the system partition state. A new broker is added, catches up, reads the plan and completes `MemberJoin`.
- Unit: phase 0 with a joint configuration. A normal change needs a quorum in both sets. The forced change fences the voters of both sets that are not voters of the forced configuration.
- Unit: the blocking-set check for the examples of 5.10. A request without `acceptPossibleLoss` fails with `METADATA_COMPLETENESS_UNKNOWN` when the check fails. A broker that answers with a new incarnation does not count.
- Raft: a forced change after a passed check keeps an entry that is committed but that no survivor knows as committed.
- Unit: rule 2 does not replace an incarnation that answered within the election timeout.
- Raft: a passive member is promoted under constant load with the lag threshold.
- Integration: an applier that times out during a joint phase does not complete. Its retry completes after the final configuration commits.
- Unit: a scale-down that leaves a zone with fewer brokers than slots is rejected. A force-remove and a remove zone are not rejected.
- Unit: origin marker. A data directory with the stored dynamic configuration or a data partition gives `EXISTING`. An empty one gives `NEW`. A restart reads the marker and does not change it. The probe does not use row 3 when one origin is `EXISTING`.
- Integration: rolling upgrade of an existing cluster. No broker bootstraps from the static configuration. The activation creates the system partition from the current members.
- API: each endpoint of 5.12.1 and each change of 5.12.2, with the errors. A force-remove without `acceptPossibleLoss` fails at the request when the check fails. A dry run shows the system partition section.
- Integration: activation of an existing cluster, with a passive member that does not acknowledge at first and starts later with row 2.

## 9. Proof of concept

The PoC changes only Raft, in `zeebe/atomix/cluster`. The rules of 5.2 to 5.6 are pure functions in the package `io.atomix.raft.partition.systempartition`:

- `SystemPartitionMembership`: the policy, the initial configuration, `selectVoters`, the priorities, self-promotion, the leader's demotion and the change of `MemberLeave`.
- `SystemPartitionRecovery`: phase 0 of a force-remove plan (5.6.3) and "force system partition voters" (5.6.5).

The PoC does not change the broker or the dynamic configuration.

### 9.1 Commits

|    Commit     |                                           Header                                            |
|---------------|---------------------------------------------------------------------------------------------|
| `c4f52beaee5` | feat: let raft bootstrap with typed members                                                 |
| `7c5d403d4c4` | feat: let a member request a full typed raft configuration at an exact index                |
| `c7fd1f4549d` | feat: let raft force a configuration without its passive members                            |
| `2b777a2962b` | feat: fence raft members by incarnation in the configuration                                |
| `58da28c5ee9` | feat: let the raft leader refuse joins of members that the cluster does not admit           |
| `93a144fc07f` | fix: keep a configuration received before the first append on an empty log                  |
| `2eab594eba7` | feat: let the raft configuration limit voters per zone with a voter policy                  |
| `73fa36e419a` | feat: keep a raft member that joined with an empty log from voting until caught up          |
| `ad5f9296a73` | feat: keep raft leadership on voters and expose the typed configuration                     |
| `617364524c1` | docs: describe how the system partition keeps its voters and passive members                |
| `1264eb94e2d` | feat: decide system partition voters, priorities and recoveries from the raft configuration |
| `a0df80a847d` | fix: reject a raft partition join while its server still runs                               |
| `b612fac81c8` | test: run the system partition deployment examples end to end on raft                       |
| `05335a14f27` | docs: update the system partition design with the proof of concept results                  |
| `de200f500f0` | fix: keep a voter without data from voting after it restarts before catching up             |
| `f1b5919a641` | test: register the fenced listener before member 3 is fenced                                |
| `6281f25afee` | fix: keep a member healthy when it forwards a request of a fenced member                    |
| `0bac99da62c` | fix: let a leaving voter count for the old quorum until the joint phase ends                |
| `1b852525813` | fix: force the configuration of the highest report, not the executor's lagging one          |
| `d5418f7eddf` | fix: remove brokers in phase 0 that "force voters" kept as passive members                  |
| `6f66dcadcfc` | test: cover the reset of an empty log on the first append                                   |
| `c7ba8fefce0` | fix: keep the promotion check of data partitions as it was before R9                        |
| `778a7ab26db` | test: check the join after a closed server without an NPE message of a mock                 |
| `93e542cfece` | fix: never give a member without data an incarnation that reads as 0                        |
| `6e3ed92b98f` | fix: let later versions add fields to fence entries and voter policies on the wire          |
| `dc88417acdc` | refactor: send through a plain fencing decorator and read the fence list safely             |
| `9ed0a7f0c51` | refactor: check whether a fence entry is committed without reading the disk                 |
| `ecbf08dfb45` | test: read configurations that a broker wrote with schema version 4                         |

### 9.2 What the PoC validates

`SystemPartitionScenarioTest` runs the examples of 5.10 end to end on in-process Raft servers. The test helper `SystemPartitionCluster` does the work of the broker: the start table, the background join of a fenced broker, the priorities, `MemberLeave`, phase 0 and "force system partition voters". A small stand-in of the dynamic configuration supplies the admission hook and the excluded set. Each test checks the members, types, incarnations, fence list and policy at each step. It also checks the quorum, and that committed entries stay readable on the survivors.

|                                                  Scenario                                                   |                         Test (`SystemPartitionScenarioTest`)                         |
|-------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| 5.10.2 Bootstrap, priorities, restart of all brokers                                                        | `TwoAndAHalfRegions.shouldBootstrapWithControlLeaderAndKeepConfigurationOverRestart` |
| 5.6.1, 5.6.2 Scale-up of zone B, `MemberLeave` of the voter `B1` in one joint change                        | `TwoAndAHalfRegions.shouldAddBrokerAsPassiveAndSwapLeavingVoterInOneJointChange`     |
| 5.10.2 Zone A fails (normal change), zone A comes back, failback                                            | `TwoAndAHalfRegions.shouldFailOverZoneWithNormalChangeAndFailBackBySelfPromotion`    |
| 5.10.2 The control zone fails, `C1` comes back                                                              | `TwoAndAHalfRegions.shouldNotChangeConfigurationWhenControlZoneFails`                |
| 5.10.2 The control zone and zone A fail, "force system partition voters", failover, restart, return of `C1` | `TwoAndAHalfRegions.shouldForceVotersWhenControlZoneAndZoneFail`                     |
| 5.6.9 Disk loss of a voter (empty-start guard)                                                              | `TwoAndAHalfRegions.shouldKeepVoterWithLostDiskFromVotingUntilCaughtUp`              |
| 5.6.9 Disk loss of a passive member                                                                         | `TwoAndAHalfRegions.shouldKeepPassiveMemberWithLostDiskPassive`                      |
| 5.10.1 Zone B fails (forced change), restart of the cluster, failback                                       | `DualRegion.shouldForceZoneAWhenZoneBFailsAndFailBack`                               |
| 5.10.1 Only one member has the most recent log                                                              | `DualRegion.shouldForceOnlyMemberWithMostRecentLogAndRestorePolicyBySelfPromotion`   |
| 5.6.3 A lagging passive executor forces the configuration of the highest report                             | `DualRegion.shouldForceConfigurationOfHighestReportFromLaggingPassiveExecutor`       |

The Raft changes and the rules have their own tests:

- `SystemPartitionRaftTest`: typed bootstrap (R1), `reconfigure` with an exact index (R3, R4), `forceConfigure` without passive members (R2), fencing (R8, R15), the join admission hook (R13), the voter policy in the configuration (R14, the zone check of 5.5), the empty-start guard (R7), voters and the configuration view (R6, R9, R10).
- `SystemPartitionMembershipTest`: the policy, `selectVoters`, the priorities, self-promotion, the demotion and the change of `MemberLeave`.
- `SystemPartitionRecoveryTest`: phase 0 (`RemoveMembers`) and "force system partition voters" (`ForceVoters`).
- `RaftPartitionJoinTest`: R11.
- `RaftNamespacesTest` (serialization of the new request fields; fence entries and the policy are serialized with field names), `RaftEntrySBESerializerTest` and `MetaStoreTest` (format version 5, the empty-start marker), `ConfigurationVersion4CompatibilityTest` (real version-4 bytes), `PriorityElectionTimerTest` (R12), `JoinOptionsTest` (incarnations), `PassiveRoleResetLogTest` (a configuration that a member with an empty log receives before the first append).

Test results after the review fixes (module `zeebe/atomix/cluster`): `SystemPartitionRaftTest` 36, `SystemPartitionScenarioTest` 10, `SystemPartitionMembershipTest` 37, `SystemPartitionRecoveryTest` 15, `RaftPartitionJoinTest` 3, `MetaStoreTest` 21, `RaftEntrySBESerializerTest` 12, `RaftClusterContextTest` 37 (171 tests). The slow suites `ReconfigurationTest` and `RaftTest` run 59 tests (1 skipped). All 552 tests of the module pass (1 skipped).

Status of the Raft changes (5.8):

- Done: R1, R2, R4, R6, R7, R8, R9, R11, R12, R13, R14, R15.
- Done in part: R3 (no forwarding by non-members, Q11), R10 (a passive member does not yet learn the commit index of the leader).
- Not done: R5 (optional).

### 9.3 What the PoC changed in the design

The PoC deviated from revision 5 in these points. Revision 6 adopts them:

- The first leader appends its configuration entry when its initial entry is committed, before any other entry and before it announces its leadership (5.4.2, R14, D19).
- A member answers `FENCED` only if the fence entry is committed in its own view. Else it answers `ILLEGAL_MEMBER_STATE`, and the sender ignores it (5.6.6, R8).
- The fenced notice is the committed configuration in a `ConfigureRequest` that is marked as committed, at most once per heartbeat interval. The leader does not replicate entries to a fenced member (5.6.6).
- A response from a fenced member counts as a response from a member that is not reachable (5.6.6).
- A join with a new incarnation keeps the type of the member. The leader rejects a target in which a fenced member is a voter (5.4.4, 5.5, R15).
- A zone that is not in the policy has 0 slots. A member without a zone is in the zone `""`. The leader rejects only a change that adds voters to a zone over its limit (5.1, 5.5).
- The control zone is the first zone of the policy (5.1).
- The configuration format version goes from 4 to 5 (5.8).
- The join refusals are the new retriable errors `NOT_A_MEMBER` and `NOT_READY` (5.4.4, R13).
- A non-member does not forward a request (5.5, R3, Q11).

The scenario tests found these gaps in revision 5. Revision 6 closes them:

- The priority election does not take the leadership from a leader that runs. The broker must start the leadership balancing (5.3, 5.9, 5.10.2). The PoC test makes the leader step down.
- A forced change of phase 0 also fences the voters that it demotes (5.6.3, 5.10.1). The PoC code does not do this yet. In `shouldForceOnlyMemberWithMostRecentLogAndRestorePolicyBySelfPromotion`, `A2` is not fenced and becomes passive when it comes back.
- Row 1 uses the member ids and types of the stored configuration, not the static configuration (5.4, 5.6.7). The test helper does this.
- The priority of a voter is at least 1 (5.3). `SystemPartitionMembership.priorities` does this.
- Self-promotion needs the commit index of the leader. R10 now includes it (5.6.4). The PoC test reads it from the leader directly.
- `MemberLeave` waits for the answer with a timeout and then checks the leader's configuration again (5.6.2). The test helper does this.

### 9.4 Review of the PoC

A review of the PoC found these problems. The `fix` commits after `05335a14f27` correct them. Each fix has a test that fails without the fix:

- Raft kept the empty-start guard only in memory. A voter that restarted before it caught up could vote. Raft now keeps the marker with `probeTerm` in the partition directory (5.4.3, R7).
- A member that forwarded a request of a fenced broker fenced itself when the leader answered `FENCED` (5.6.6).
- The leader added the fence entry of a leaving voter in the joint configuration. The leaving voter then did not count for the old quorum, and a leaving leader fenced itself before it ended the joint phase (5.6.2, Q13).
- A recovery used the incarnations, fence list and policy of the executor's own configuration, and the receivers replaced their fence list. The target now comes from the configuration of step 1. The receivers merge the fence lists. The leader keeps its incarnations in a `reconfigure` (5.5, 5.6.3).
- Phase 0 resent the forced configuration of "force system partition voters" instead of a removal (5.6.3, step 2).
- The answer check of R9 also applied to data partitions. It now applies only with a voter policy.
- An incarnation could be 0 or `Long.MIN_VALUE` (5.4.3). Kryo serialized fence entries and the policy by position, thus no field could be added later.

### 9.5 What the PoC does not cover

- The start step of the broker, with the probe and the empty-start marker on disk (5.4).
- The appliers of the dynamic configuration (`MemberJoin`, `MemberLeave`, `MemberRemove`) and the phase 0 operation `SystemPartitionRemoveMembers` (5.6, 5.9).
- The management operations "force system partition voters" and "update system partition voter policy". The PoC has only their decisions and Raft calls.
- R5.
- R2 waits only for the voters of the new configuration. The review asked to also wait, best effort, for the old voters that answered step 1 of 5.6.3. The PoC sends them the forced configuration, but does not wait for them. This needs a second, optional ack set in the `forceConfigure` API.
- Forwarding by non-members (Q11).

### 9.6 How to run the tests

In the repository root, after `./mvnw install -pl zeebe/atomix/cluster -am -Dquickly -T1C`:

```bash
./mvnw verify -pl zeebe/atomix/cluster -Dtest='SystemPartition*,RaftPartitionJoinTest,PassiveRoleResetLogTest,ConfigurationVersion4CompatibilityTest' -DskipTests=false -DskipITs -Dquickly
```

### 9.7 What revision 7 changes for the PoC

The PoC implements revision 6. Revision 7 changes these parts. They are not in the PoC yet:

- R16, the start without data, replaces R7, the empty-start guard, and the join with `JoinOptions`.
- R13, the join admission hook, and the errors `NOT_A_MEMBER` and `NOT_READY` are not necessary.
- R3 changes: the request with a full target configuration and an exact index becomes a leader-internal change. Only the policy change and the read of the configuration stay as requests.
- New: R17 (answers count only for the matching incarnation), R18 (the end of a joint phase fences removed members), R19 (membership version) and R20 (the option `systemPartition`).
- The voter policy goes out of the Raft configuration, the format and `LeaderRole` (the zone check). It becomes a state record with a local cache (D18). The PoC has no state machine for the system partition. The test helper can supply the policy directly.
- The rules of 5.5 and 5.6.4 replace self-promotion, self-join and the change of `MemberLeave` in `SystemPartitionMembership`. The test helper `SystemPartitionCluster` must run them on the leader.
- From the review: authorization of pending `MemberJoin` targets, rule 2 with a silent incarnation, the lag threshold in the promotion, the joint configuration and the completeness check in `SystemPartitionRecovery`, and the completion on a committed, non-joint configuration.
- The scenario tests of 9.2 stay valid as scenarios. Their steps change: brokers start without data instead of a join, and the leader promotes instead of self-promotion.

