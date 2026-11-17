# System partition reconfiguration

Status: draft, revision 6 (after review rounds 1 to 3 and the PoC). Issue: [camunda/pdp-3723-dual-region-failover-detection#42](https://github.com/camunda/pdp-3723-dual-region-failover-detection/issues/42).

## 1. Summary

The system partition is a Raft partition. Each broker of the cluster has a replica of it. Only some brokers are voters. The other brokers are passive members. A passive member replicates only committed entries. It does not vote and it does not count toward the quorum.

This document tells how the cluster sets and changes the voters and the passive members of the system partition.

The design has these main rules:

1. The Raft configuration of the system partition is the only record of its members and their types. The dynamic configuration (`ClusterConfiguration`) does not record them.
2. The voter policy sets the number of voters for each zone and the order of the zones. It comes from the static configuration. The Raft configuration of the system partition stores it. An operation can change it with a Raft configuration change.
3. At the first start, all brokers calculate the same initial configuration from the voter policy. They bootstrap only when all brokers of the static configuration agree that no system partition exists. When its initial entry commits, the first leader appends a configuration entry before any other entry. Thus, a configuration index above 0 shows that the system partition exists.
4. At a restart, a broker starts the system partition with the Raft configuration that it stored on its disk.
5. Add a broker and remove a broker change the system partition. They call Raft directly. They do not write the system partition members into the dynamic configuration.
6. A force-remove plan changes the system partition first. It uses a forced Raft change only if the remaining voters are not a quorum.
7. When the system partition has no quorum and the failed brokers must stay cluster members, the operator uses "force system partition voters". It sets new voters and makes all other members passive. It removes no member.
8. Each broker keeps its own membership correct: a passive broker promotes itself into an empty voter slot, and a broker that is not a member asks to join. The leader admits a join only from a broker that is `JOINING` or `ACTIVE` and that is not the target of a pending removal. The leader demotes extra voters.
9. A broker that joins without data does not vote until it has caught up with the current leader.
10. Each removal and each forced change puts the brokers on a fence list. A fence entry has the broker id and the incarnation of the broker. A fenced broker must delete its data and join again with a new incarnation. Fence entries are never cleared.
11. The dynamic configuration starts after the system partition. The start of the system partition does not read it. The leader admission, the excluded set and self-promotion read it at runtime, after it is initialized.

Revision 6 adds the results of the proof of concept (PoC). Section 9 tells what the PoC shows and what it does not cover.

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

|      Term       |                                                                                          Meaning                                                                                           |
|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Voter           | A member of type `ACTIVE` in the Raft configuration. It votes and it counts toward the quorum.                                                                                             |
| Passive member  | A member of type `PASSIVE`. The leader sends it only committed entries and snapshots. It does not start elections.                                                                         |
| Member          | A voter or a passive member.                                                                                                                                                               |
| Incarnation     | A 64-bit id of a member in the Raft configuration. The members of the initial configuration have incarnation 0. A broker that starts without data generates a new random incarnation.      |
| Voter policy    | The number of voters for each zone, and the order of the zones for the election priority. The Raft configuration stores it.                                                                |
| Voter slot      | One voter position in a zone. A zone with a voter count of 2 has 2 slots.                                                                                                                  |
| Cluster member  | A broker that the dynamic configuration shows as `JOINING`, `ACTIVE` or `LEAVING`.                                                                                                         |
| Fence list      | A list of entries in the Raft configuration (R8). Each entry has a broker id and an incarnation. The entries are brokers that were removed, and brokers that a forced change made passive. |
| Most recent log | The log with the highest last term. If the last terms are equal, the log with the highest last index.                                                                                      |
| Control zone    | The first zone of the voter policy. It has the highest election priority. A dual-region policy has no control zone. Its first zone is a data zone.                                         |
| Fenced notice   | The committed configuration that the leader sends to a fenced member, in a `ConfigureRequest` that is marked as committed (5.6.6).                                                         |

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

**Storage.** The Raft configuration of the system partition stores the policy, as it stores the fence list. The policy has the voter count of each zone and the order of the zones. It is not a separate record in the system partition state. The order comes from the zone priorities of the static configuration at the bootstrap. Later changes of the zone priorities in the dynamic configuration do not change it.

**Control zone and zones.** The control zone is the first zone of the policy. The policy has no other field for it. The broker that makes a policy (the bootstrap, or "update system partition voter policy") puts the control zone first, then the data zones in the order of their priority. A dual-region policy starts with the data zone of the highest priority. The zone of a member is the zone of its `MemberId`. A member without a zone is in the zone `""` (empty). A zone that is not in the policy has 0 slots.

The initial configuration (index 0) has the policy from the static configuration (5.4.1). The configuration entry of the first leader has the same policy (5.4.2). At a restart, each broker reads the policy from its stored configuration. Thus, the priorities are available at once, also without a leader. At start-up, each broker compares its static policy with the policy in its stored configuration. If they are different, the broker logs a warning and uses the policy in the configuration.

**Change.** The operation "update system partition voter policy" is a Raft configuration change. It uses `reconfigure` (5.5, R3) and needs a quorum. After the new configuration commits, self-promotion fills new slots and the leader demotes extra voters (5.6.4). Use cases:

- Rolling conversion from dual-region to 2.5 regions (decision of Oct 26): add the control zone, then set `control: 1`.
- Add a zone.

### 5.2 Voter selection

`selectVoters` is a pure function:

```
selectVoters(configuration, excluded) -> targetConfiguration
```

- `configuration` must be a committed configuration that is not in joint consensus. The function uses only its new members and its voter policy. If a joint change is in progress, the caller waits.
- `excluded` are brokers that are not `JOINING` or `ACTIVE` in the dynamic configuration, and the targets of pending `MemberLeave` and `MemberRemove` operations. They are never selected as new voters. The caller reads the dynamic configuration only after it is initialized.

For each zone:

1. Keep the current voters of the zone.
2. If the zone has more voters than slots, the voters with the highest node index are extra. The leader demotes them (5.6.4).
3. If the zone has fewer voters than slots, the slots stay empty. Self-promotion fills them (5.6.4).

Members of a zone that is not in the policy are always passive, because the zone has 0 slots. The liveness of a broker never changes the voters (D6).

The leader rejects a promotion if the member did not answer within the election timeout (R9). Thus, a broker that is down is never promoted.

### 5.3 Election priority

The broker calculates the priority from the Raft configuration and its voter policy. Raft does not store the priority.

1. Sort the voters by the zone order of the policy. The control zone is the first zone (5.1), thus control-zone voters come first. Voters of a zone that is not in the policy come last. Then sort by node index, low to high.
2. The priority of a voter is `max(1, policyTotal - position)`. `policyTotal` is the total number of voters in the policy. `position` starts at 0. If each zone has no more voters than slots, each voter has a different priority. If a zone has more voters than slots, the last voters get priority 1. Thus, each voter keeps a positive priority and can start an election.
3. The target priority is `policyTotal`. It changes only when the policy changes (R12).
4. A passive member has priority 0.

The broker calculates the priority at start-up from its stored configuration. It calculates it again on each new configuration (R10), and calls `reconfigurePriority` if the value changed. A new policy comes in a new configuration.

The priority election does not take the leadership from a leader that runs. A voter with a higher priority becomes the leader only after the current leader steps down. Thus, when a voter with a higher priority than the current leader has caught up, the broker starts the existing leadership balancing, or a leadership transfer to the voter with the highest priority. A transfer goes only to a voter (R6). Example: `C1` comes back after the control zone failed (5.10.2).

### 5.4 Start of the system partition

The system partition starts in its own start step. It is not part of a tenant group. The step runs **before** the dynamic configuration starts. Thus, the step uses only local data, the static configuration and the probe. It does not know if the broker is a cluster member. The leader checks that when the broker asks to join (5.4.4).

The start step does not block the start of the broker. The broker becomes ready without the system partition (decision #186). The system partition is not accessible until it has caught up (decision of Nov 10). Thus, a slow probe or a missing leader does not stop a rolling restart or an ordered pod start.

The broker uses the first row of this table that applies:

| # |                                                   Condition                                                   |                                                                                                                                                                           Action                                                                                                                                                                            |
|---|---------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | The broker has a stored Raft configuration (`.conf`) for the system partition.                                | Start with the stored configuration (`bootstrap`). The member ids and types for `bootstrap` come from the stored configuration, not from the static configuration. A broker that a scale-up added has no entry in the static configuration. If the empty-start marker exists, join instead, with the incarnation and the `probeTerm` of the marker (5.4.3). |
| 2 | No stored Raft configuration, and one probe answer has a configuration index above 0.                         | Delete local system partition data, if any. Ask to join as `PASSIVE` with a new incarnation and the empty-start guard (5.4.3). The leader admits the join only if the broker is `JOINING` or `ACTIVE` (5.4.4).                                                                                                                                              |
| 3 | No stored Raft configuration, and all static brokers answer "no data" or index 0, with the same hash (5.4.2). | Bootstrap with the initial configuration (5.4.1). No empty-start guard.                                                                                                                                                                                                                                                                                     |

If no row applies, the probe retries (5.4.2).

A voter that was removed, and starts with old data, uses row 1. It sends polls and votes. The members reject them, because its id and incarnation are on the fence list (5.6.6). When it gets a `FENCED` rejection or the fenced notice of the leader, it deletes its data and asks to join. The leader refuses the join until the broker is `JOINING` or `ACTIVE` again.

A passive member that was removed, and starts with old data, also uses row 1. It sends no requests, thus no member rejects it. It is never accessible, because no leader sends it entries. Step 3 of `MemberJoin` deletes its data when the cluster adds it again (5.6.1).

#### 5.4.1 Initial configuration

Each broker of the static cluster calculates the same configuration at index 0:

- For each zone, the members with the lowest node indexes fill the voter slots. They are `ACTIVE`.
- All other static brokers are `PASSIVE`.
- Each member has incarnation 0.
- The configuration has the voter policy of the static configuration (5.1).

This needs a Raft change: bootstrap with member types, the policy and incarnation 0 (R1).

#### 5.4.2 Probe

The probe finds out if the system partition already exists. The broker asks each broker of the static configuration and each broker in its cluster view. Each answer has:

- "no data", or the stored configuration index and term, and the current Raft term;
- a hash of the voter policy and of the initial configuration.

The first leader of the system partition appends a configuration entry when its initial entry is committed. It does this before it appends any other entry and before it announces its leadership (R14, D19). The entry has the members and the voter policy of the initial configuration. Thus, a configuration index above 0 shows that the system partition exists.

The broker decides:

- If one answer has an index above 0: use row 2. The hash is not checked. A broker added by a scale-up, or with a changed static configuration, can join.
- If **all** brokers of the static configuration answered, and all answers are "no data" or index 0:
  - If a hash is different from its own: stop the system partition with an error. Two brokers with different initial configurations could elect two leaders in one term.
  - Else: use row 3. All brokers calculate the same initial configuration. Before the first configuration entry commits, no entry with data is committed. Thus, a broker that bootstraps late, or after a disk loss, loses nothing.
- Else: retry. There is no timeout fallback.

An operator flag allows row 3 without all answers. It is for disaster recovery only.

#### 5.4.3 Empty-start guard

A broker that joins without data (row 2) can be a voter in the current configuration. Its log is empty, and it has no record of its last vote. If it votes, the cluster can lose committed entries.

The guard (R7):

1. Before the broker asks to join, it generates a random 64-bit incarnation. It writes an "empty start" marker on its disk and stores the incarnation with it.
2. The broker repeats the probe until it has answers from a quorum of the voters of the highest configuration that it found. `probeTerm` is the highest Raft term that these voters reported. The broker does not use terms from other brokers. It stores `probeTerm` with the marker.
3. The broker joins with `JoinOptions`: the incarnation, the version of its dynamic configuration (5.4.4) and `probeTerm`. Raft activates the guard. While the guard is active, the broker does not grant votes or polls, and it does not start an election.
4. Raft releases the guard when its commit index reaches the commit index of an append from a leader with a term of `probeTerm` or more. Then the broker removes the marker.

A leader with a lower term can be a deposed leader on a minority side. Its commit index can be too low.

Raft keeps the guard only in memory. The broker keeps the marker, the incarnation and `probeTerm` on its disk. At each start and at each join while the marker exists, the broker joins with the stored incarnation and `probeTerm` (row 1 of 5.4). Thus, a restart does not release the guard.

After "force system partition voters", the highest configuration has fewer voters. Thus, a broker that lost its disk can complete the probe with the answers of the surviving voters.

If the system partition needs the vote of this broker for a quorum, it cannot elect a leader until a different voter comes back. This is correct: no safe election is possible.

A broker at the first start of the cluster (row 3) has no guard. Else no leader could be elected.

#### 5.4.4 Join admission

A broker without system partition data asks to join in a background loop. This happens after row 2, after a broker deleted its data because it is fenced (5.6.6), and during `MemberJoin` (5.6.1).

The broker:

1. Waits until its local dynamic configuration is initialized.
2. Does not ask to join while its local dynamic configuration shows it as `LEAVING`, `LEFT`, or as the target of a pending removal.
3. Joins with `JoinOptions`: its incarnation, the version of its local dynamic configuration that shows it as `JOINING` or `ACTIVE`, and `probeTerm` of the empty-start guard (5.4.3).
4. Retries with a backoff until the leader admits it. If a join try reaches its deadline, the loop starts a new try.

The leader checks the request with the admission hook (R13):

1. If its dynamic configuration is not initialized, it refuses with `NOT_READY`.
2. If the version of its dynamic configuration is lower than the version in the request, it refuses with `NOT_READY`.
3. If the broker is not `JOINING` or `ACTIVE` in its dynamic configuration, it refuses with `NOT_A_MEMBER`. `LEAVING` and `LEFT` are refused.
4. If the broker is the target of a pending `MemberLeave` or `MemberRemove`, it refuses with `NOT_A_MEMBER`.
5. If the sender id and incarnation match a fence entry, it rejects the request (5.6.6).

`NOT_A_MEMBER` and `NOT_READY` are two new Raft errors. Both are retriable, and `ReconfigurationHelper.join` retries them. The hook checks each change that adds a member: a join, a join with a new incarnation of a member, and a `reconfigure` (5.5). The leader reads its dynamic configuration at runtime. Later, when the dynamic configuration moves into the system partition, the leader reads the cluster members from the system partition state (Q5).

A join retry with the same incarnation is a no-op. If the broker is already a member with a different incarnation, the leader writes a new configuration with the new incarnation (R15). The member keeps its type. Thus, a voter that lost its disk stays a voter. Its empty-start guard keeps it from voting until it has caught up (5.4.3). Old fence entries of the broker stay. They do not match the new incarnation (5.6.6).

### 5.5 How a change gets to Raft

The broker that runs an operation calculates the target configuration. It sends the full configuration to the leader:

```
RaftPartition.reconfigure(Map<MemberId, Type> members, Collection<FenceEntry> fenced, VoterPolicy policy, long configurationIndex, long configurationTerm)
```

`fenced` are the entries that the change adds to the fence list. The leader never removes an entry. `policy` is null if the change does not change the policy. The request sets only the types. Each member keeps the incarnation that it has in the current configuration. Only a join changes an incarnation (5.4.4).

Rules for this new API (R3):

- The configuration index must be the same as the leader's index.
- The leader rejects a new member that is `ACTIVE`. A new member must first be `PASSIVE`.
- The admission hook (R13) checks each member that the change adds (5.4.4).
- The leader rejects a change from a fenced sender (R8).
- The leader rejects a target configuration in which a fenced member (id and incarnation) is a voter. The leader does not replicate to a fenced member. Thus, such a voter would stop each commit.
- The leader rejects a change that adds voters to a zone, if the zone then has more voters than the target policy allows. A zone that is not in the policy has 0 slots. A member without a zone is in the zone `""` (5.1). The leader accepts a change that does not add voters to a zone, also if the zone has too many voters. Thus, the leader can demote extra voters one at a time, and a policy change does not have to correct all zones at once.
- A rejection contains the leader's current configuration (index, term, typed members, incarnations, fence list, policy). The broker calculates the target again from it.
- If the configuration is already the target, the leader returns OK and makes no change (`equalMembership`, which must also compare the incarnations, the fence list and the policy).
- A member that is not the leader forwards the request to the leader. If it knows no leader, it answers `NO_LEADER`, and the sender retries.
- A broker that is not a member does not forward a request, and it cannot send `reconfigure` itself. A broker that is not a member only joins (5.4.4). Usually, the broker that runs an operation is a member. Q11 asks if forwarding by non-members is necessary.

"Update system partition voter policy" uses this API. It sends the current members with the new policy.

A removal is complete when the broker is not in the leader's configuration.

The broker reads its local configuration with a new API. It returns the typed new members, the incarnations, the fence list, the policy, the index, the term and if a joint change is in progress (R10).

The broker that runs an operation retries a rejection in the applier, with a bounded wait. If the wait ends, the operation fails, and the dynamic configuration retries it later.

### 5.6 Flows

#### 5.6.1 Add a broker (`MemberJoin`)

The new broker applies `MemberJoin`. The applier can run more than once. When it runs, the system partition step has already run (5.4).

1. Get the leader's current configuration (5.5).
2. If this broker is in it with its current incarnation, and no fence entry matches it, continue at step 5. The broker already joined, maybe in an earlier try.
3. Else, if the system partition runs with old data, close it and delete the data (R11). The data is from a time before the broker was removed. This also cleans up a removed passive member (5.4).
4. Start the background join as `PASSIVE` with a new incarnation and the empty-start guard (5.4.4). The dynamic configuration now shows the broker as `JOINING`. If the leader's view is older, the leader answers `NOT_READY` and the join retries.
5. `MemberJoin` completes after the broker has asked to join. It does not wait for the admission or for a promotion. The background join finishes the join.

The global operations run one at a time. Thus, `MemberJoin` must not wait for the admission or a promotion. Self-promotion fills an empty slot later. The excluded set (5.2) and I1 stay true, because the leader admits only a broker that is `JOINING` or `ACTIVE` (5.4.4).

Scale-up of a zone uses this flow. The new brokers are passive unless their zone has an empty slot.

#### 5.6.2 Remove a broker (`MemberLeave`)

The leaving broker applies `MemberLeave`:

1. Get the leader's current configuration (5.5). If this broker is not in it, `MemberLeave` completes.
2. If the broker is passive, remove it in one change.
3. If the broker is a voter, find a replacement: a passive member of the same zone that has caught up, answered recently, and is not excluded (5.2). Send **one** change that removes the leaving broker and promotes the replacement. Joint consensus keeps the old and the new quorum.
4. If no replacement is possible within the timeout, remove the broker without a replacement. The slot stays empty until self-promotion fills it.

Each removal also puts the broker on the fence list with its incarnation. If a removed voter starts again later with its old data, the members reject its requests (5.6.6). A removed passive member sends no requests (5.4).

**No answer to the removal.** When the removal commits, the leaving broker is fenced. The leader sends it the fenced notice, and the broker stops its system partition (5.6.6). This can happen before the answer to its request arrives. Thus, the applier waits for the answer with a timeout. When the timeout ends, the applier starts again at step 1 and gets the leader's configuration again. If the broker is not in it, `MemberLeave` completes.

`MemberRemove` uses `MemberLeaveApplier` too. If the member is not the local broker, the applier checks the system partition again. Usually phase 0 of the force-remove plan did the removal already (5.6.3). If the broker is still a member, the applier removes it in one change and puts it on the fence list.

#### 5.6.3 Force-remove brokers or a zone

"Force-remove brokers" and "remove zone" with `force=true` use `ForceScaleDownRequestTransformer`. The automatic region failover also uses this plan. The plan gets a new first phase:

```
Phase 0  GlobalPhase [SystemPartitionRemoveMembers(executor, removedBrokers)]   <- new
Phase 1  PartitionGroupPhase (sequential) [PartitionForceReconfigure ...]        (today)
Phase 2  GlobalPhase [MemberRemove(coordinator, removedBroker) ...]              (today)
```

The executor is a surviving broker. The request must select a coordinator that is not removed **and** that is reachable. Today `RemoveZoneTransformer` can select a control broker that is down. This must change (5.9).

The operation tells the executor to change the Raft configuration. It does not write the members into the `ClusterConfiguration`.

**Step 1: collect.** The executor asks all reachable members that are not removed for their configuration, last log term, last log index and commit index. It uses the configuration with the highest index and term.

**Step 2: is a forced change in progress?** If that configuration is forced and its fence list has all removed brokers, a forced change was already made by an earlier try. The executor sends this configuration again with `forceConfigure`, unchanged. The step completes when each voter of it has answered.

**Step 3: decide.** The executor counts the voters that are not removed:

- **Normal change**, if they are a quorum of the current voters: remove all removed brokers in **one** change, and put them on the fence list. Self-promotion fills empty slots later.
- **Forced change**, if they are not a quorum. This happens in a dual-region cluster without a control zone.
  1. The voters of the forced configuration are the reachable members, voters or passive, that have the most recent log (D11). Usually these are the surviving voters.
  2. Each zone gets at most the number of voters that its slots in the policy allow. The members with the lowest node index come first.
  3. If no member with the most recent log is in a zone of the policy, take one of them anyway. The leader demotes it later, when a member of a policy zone is promoted (5.6.4).
  4. All other remaining members are passive.
  5. The removed brokers go on the fence list with their incarnations (R8).
  6. Each voter of the current configuration that is not removed, but that is not a voter of the forced configuration, also goes on the fence list with its incarnation. This is a voter that does not have the most recent log, or that did not answer in step 1. The forced configuration gets to it only with a best-effort send. Thus, it can keep an old configuration in which it is a voter. 5.6.5 fences the down brokers for the same reason. The fenced voter stays a member. It deletes its data and joins again with a new incarnation (5.6.6). The leader admits it, because it is `JOINING` or `ACTIVE`.
  7. Call `forceConfigure`. The executor sends it to all members that answered in step 1, with a best-effort wait. Only the voters of the new configuration must answer (R2).
  8. After the new leader is elected, self-promotion and the leader's demotion restore the policy (5.6.4).

The forced change can lose entries that were committed only on the removed brokers. The design accepts this. It is the same for data partitions today.

**When the normal change cannot commit.** The rule expects that the remaining voters are alive. If one is also down, the change cannot commit. The step retries, and the plan does not continue. The operator must then use the operation of 5.6.5.

**Why phase 0 comes first.** Later, the plan is stored in the system partition. Then the plan can continue only if the system partition works.

#### 5.6.4 Own membership: self-promotion, self-join and the leader's demotion

These rules keep the configuration in line with the policy. Each rule changes only one broker in each change. All use 5.5, thus the exact index check prevents two brokers from filling the same slot.

**Self-promotion.** A passive broker checks at start-up, on each new configuration and when it has caught up. A new policy comes in a new configuration. The conditions are:

1. Its local dynamic configuration is initialized.
2. Its zone has fewer voters than slots in the committed configuration.
3. It is not excluded (5.2) and not fenced (no fence entry matches its id and its incarnation).
4. It has no empty-start marker.
5. Its local commit index is not lower than the leader's commit index. The broker learns the leader's commit index from the append requests and heartbeats that it receives (R10).

If all conditions are true, it sends a change that promotes itself. A broker with a lower node index waits a shorter time before it sends. The leader rejects a promotion that puts more voters in a zone than the policy allows (5.5).

**Self-join.** A broker that is not a member of the system partition, or that is fenced (5.6.6), deletes its system partition data and asks to join (5.4.4). It does not ask while its local dynamic configuration shows it as `LEAVING`, `LEFT`, or as the target of a pending removal. The leader admits it only if it is `JOINING` or `ACTIVE`. This repairs the case in which a plan was cancelled after the system partition step (for example after phase 0, or after the removal in `MemberLeave`). If the broker is still `JOINING` or `ACTIVE`, the leader admits it.

**Demotion of extra voters.** The leader checks each new configuration. If a zone has more voters than slots, the leader demotes the extra voter with the highest node index. It demotes one voter in each change. This can demote a voter that is down. That is safe, because the quorum gets smaller. A voter of a zone that is not in the policy is also extra. The leader demotes it only after a member of a policy zone is promoted. The leader check of 5.5 accepts each demotion, also when a zone has too many voters.

#### 5.6.5 Force system partition voters

This is a new operation. It is for the case in which the system partition has no quorum, and the failed brokers must stay cluster members. Example: in a 2.5-region cluster, the control zone and zone A are down (#21).

Input: the brokers that are down (`downBrokers`).

1. The operator verifies that the down brokers are down and fences them.
2. The operator calls the operation on a surviving broker.
3. The broker collects as in 5.6.3, step 1, and checks for a forced change in progress as in step 2.
4. The forced configuration:
   - The voters are the reachable members that are not down and that have the most recent log (D11). Each zone gets at most the number of voters that its slots allow. The rules of 5.6.3, step 3, items 2 and 3 apply.
   - All other members, the down brokers included, stay members and become `PASSIVE`.
   - The down brokers go on the fence list with their incarnations (R8).
5. Call `forceConfigure`. The broker sends it to all members that answered, with a best-effort wait. Only the voters of the new configuration must answer (R2).
6. The system partition has a quorum again. Self-promotion and the leader's demotion restore the policy for the surviving zones.
7. The failure detector works again. If all brokers of the failed data zone are down, the automatic failover removes the zone with a normal change (5.6.3). It records the failover state and the failback reference, as #21 expects.
8. If one broker of the failed data zone is still alive, the automatic failover does not start (no failover on partial failures, Oct 26). The operator then force-removes the zone.

The operation does not change the dynamic configuration or the data partitions. Each broker stays a cluster member. Thus, invariant I1 stays true.

This operation is not a plan of the dynamic configuration. It never waits in a queue behind a plan. A plan can be stuck because the system partition has no leader, for example a scale-down in `MemberLeave`. Only one plan runs at a time. Thus, the operator must cancel the stuck plan before the automatic failover can run.

#### 5.6.6 Fenced brokers

A broker on the fence list can have an old configuration in which it is a voter. Its log can have entries that the survivors do not have. Thus (R8):

- A fence entry has a broker id and an incarnation. Raft requests (poll, vote, append, configure, join, reconfigure) carry the incarnation of the sender.
- A member rejects a request only if the sender id and the sender incarnation match a fence entry in its configuration. It rejects the request before a role handles it. Thus, it does not update its term from such a request.
- The error of the rejection depends on the view of the member. If the fence entry is committed in its own view, the error is `FENCED`. Else the error is `ILLEGAL_MEMBER_STATE`, and the sender ignores it. An entry is committed in the view of a member if the configuration is forced, if the commit index of the member is at the configuration index or higher, or if the stored (committed) configuration of the member has the entry.
- A response from a fenced member counts as a response from a member that is not reachable. Thus, the receiver also ignores the term in the response. One wrapper of the outbound protocol does this for all requests.
- The leader does not replicate entries to a fenced member. When the configuration that fences the member is committed, the leader sends it the fenced notice: the committed configuration in a `ConfigureRequest` that is marked as committed. The leader sends the notice at most once per heartbeat interval. The leader rejects a join or a `reconfigure` from a fenced sender.
- A broker acts on a fence entry only if the incarnation of the entry is its own current incarnation. In addition, one of these must be true: the entry is in a configuration that the broker knows as committed (also its stored configuration at a restart), a member sent a `FENCED` rejection, or the leader sent the fenced notice.
- Then Raft makes the member inactive and calls the fenced listener. The broker closes the system partition (R11) and deletes its data. It asks to join with a new incarnation, an empty log and the empty-start guard (5.4.4).
- A join with a new incarnation matches no fence entry. The leader admits it only if the broker is `JOINING` or `ACTIVE`.
- Fence entries are never cleared. A broker that joins again has a new incarnation. Thus, old entries never match it. The number of entries is not more than the number of removals and forced changes (D17).

The fence list has two kinds of entries:

- Brokers that a removal took out of the system partition (`MemberLeave`, phase 0). They usually are not cluster members any more. Their join is refused until the cluster adds them again.
- Brokers that "force system partition voters" made passive, and voters that a forced change of phase 0 made passive (5.6.3). They are still cluster members, thus their join is admitted.

A removed passive member sends no requests. Thus, no member rejects it. It is never accessible. Step 3 of `MemberJoin` deletes its data (5.6.1).

A broker that deletes its data loses its local view. Its system partition is not accessible until it has caught up again (decision of Nov 10). Local readers do not see the state go backwards.

The fence list protects the survivors. It does not protect against an isolated group of fenced brokers that cannot reach any survivor. Such a group can be a quorum of the old configuration. Thus, the operator must keep the failed brokers down until they can reach the survivors (Q1).

#### 5.6.7 Restart of one broker

The broker uses row 1, with the member ids and types of its stored configuration. Raft loads its stored configuration. The broker calculates its priority from that configuration and its voter policy.

#### 5.6.8 Restart of the cluster

All brokers restart at the same time. Each broker uses row 1 with its stored configuration. The dynamic configuration starts after it:

- The voters elect a leader. The voter with the highest priority usually wins. The priorities come from the stored configurations, thus they are available at once.
- Passive members start in the passive role. They do not start elections.
- A broker that missed the last change has an older configuration. If it is passive in that configuration, it does no harm. The leader sends it the current configuration. If it is a voter in the older configuration but not in the current one, it cannot win an election, because the current voters have a more recent log.
- A fenced or removed voter that starts with old data sends polls. The members reject them (5.6.6). When it gets a `FENCED` rejection or the fenced notice of the leader, the broker deletes its data. It asks to join when its local dynamic configuration allows it (5.4.4). The leader answers `NOT_READY` until its own dynamic configuration is initialized.
- A removed passive member that starts with old data sends no requests. No member rejects it. It is never accessible. `MemberJoin` deletes its data if the cluster adds it again (5.6.1).
- Self-promotion waits until the local dynamic configuration is initialized (5.6.4).
- A broker without a stored configuration uses the probe (rows 2 and 3).

#### 5.6.9 Disk loss

A broker that lost its disk and is still a cluster member uses row 2. It asks to join as `PASSIVE`, with a new incarnation and the empty-start guard. The leader admits it, because it is `JOINING` or `ACTIVE`. The leader writes the new incarnation into the configuration (R15).

- If it is still a voter in the configuration, the leader does not demote it. The join with the new incarnation keeps its type (5.4.4). It gets the current configuration and is a voter again. It does not vote until it has caught up with the current leader.
- If it is passive, it catches up as a passive member.

#### 5.6.10 Zone changes

- **Add a zone.** `AddZone` adds brokers with `MemberJoin`. They are passive, because the zone is not in the policy. Then "update system partition voter policy" gives the zone its slots.
- **Rolling conversion from dual-region to 2.5 regions.** Add the control zone, then update the policy with `control: 1`. Self-promotion promotes the control broker.
- **Zone priorities.** A change in the dynamic configuration does not change the priority of the system partition. The zone order is in the policy of the Raft configuration.
- **Zone migration** (ids without a zone to zone ids). `selectVoters` must move at most one vote in each change. A voter without a zone is demoted only in the same change that promotes a voter with a zone. The details need a separate design (Q7).

### 5.7 Invariants

- **I1. System partition members are a subset of the cluster members.** `MemberLeave` and phase 0 of a force-remove plan remove a broker from the system partition before the dynamic configuration marks it `LEFT`. The phase 2 `MemberRemove` checks the system partition again. The leader admits a new member only if it is `JOINING` or `ACTIVE` and not the target of a pending removal. "Force system partition voters" removes no member.
- **I2. The Raft configuration is the only record of the members, their types and the voter policy.**
- **I3. Only these change the voters:** `MemberLeave`, phase 0 of a force-remove plan, the recheck of `MemberRemove`, "force system partition voters", self-promotion and the leader's demotion of extra voters. Liveness does not change the voters.
- **I4. A broker without data does not vote** until it has caught up with the current leader.
- **I5. A fenced broker takes part only after it deleted its data and joined again with a new incarnation.**
- **I6. A fence entry matches only the incarnation that it fenced.** An old entry never rejects a broker that joined again.

### 5.8 Changes to Raft

|       #       |                                                                                                                                                                                                                                                                                                                                                  Change                                                                                                                                                                                                                                                                                                                                                   |                                                                    Why                                                                     |
|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| R1            | Bootstrap with member types, the voter policy and incarnation 0 for each member. `createInitialConfig` uses the given types.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Today the initial configuration makes all members `ACTIVE`.                                                                                |
| R2            | `forceConfigure` waits only for the `ACTIVE` members of the new configuration. The member set of `ForceConfigureQuorum` has no passive members. It must work when the caller is passive. The caller sends the change to all reachable members, with a best-effort wait.                                                                                                                                                                                                                                                                                                                                                                                                                                   | Today one passive member that is down stops the recovery.                                                                                  |
| R3            | `reconfigure(Map<MemberId, Type>, fenced, policy, index, term)` on `RaftPartition`. Exact index match. Reject a new member that is `ACTIVE`. Reject a target in which a fenced member is a voter. Return the current configuration in a rejection. Forward to the leader. A member that knows no leader answers `NO_LEADER`, and the sender retries. A broker that is not a member does not forward (Q11). The admission hook (R13) checks each added member. The leader rejects a change that adds voters to a zone beyond its slots (5.5).                                                                                                                                                              | Today the API changes only the local member's type, and the index check accepts a higher index.                                            |
| R4            | Tests for the promotion from `PASSIVE` to `ACTIVE`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Production promotes only from `PROMOTABLE` today.                                                                                          |
| R5 (optional) | Send new committed entries to passive members when the commit index moves. Not done in the PoC.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | Today passive members get them only on the next heartbeat.                                                                                 |
| R6            | `anoint()` and leadership transfer reject a member that is not `ACTIVE`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | Today a passive member can become a candidate through `anoint()`.                                                                          |
| R7            | Empty-start guard with `probeTerm` (5.4.3). Raft keeps the guard in memory. A join takes `JoinOptions`: the incarnation, the version of the dynamic configuration and `probeTerm`. The broker keeps the marker and `probeTerm` on its disk and passes `probeTerm` on each join.                                                                                                                                                                                                                                                                                                                                                                                                                           | Today a voter with an empty log gives any vote, and it lost its record of its last vote.                                                   |
| R8            | Fence list of (broker id, incarnation) in the configuration (5.6.6). Part of membership equality. Requests and responses carry the sender incarnation. Members reject votes, polls, appends, joins and `reconfigure` requests from a fenced sender before a role handles them: `FENCED` if the entry is committed in their own view, else `ILLEGAL_MEMBER_STATE`, which the sender ignores. A response from a fenced member counts as from a member that is not reachable. The leader does not replicate to a fenced member. It sends the committed configuration as the fenced notice, at most once per heartbeat interval. A broker acts only on a committed entry, a `FENCED` rejection or the notice. | Today the `force` flag protects only until the next normal configuration.                                                                  |
| R9            | The promotion check also requires an answer from the member within the election timeout.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | Today a passive member that is down can pass the check.                                                                                    |
| R10           | A listener for configuration changes, and a read of the typed configuration with incarnations, fence list, policy, index, term and joint state. A passive member learns the commit index of the leader, for example from the append or heartbeat requests that it receives.                                                                                                                                                                                                                                                                                                                                                                                                                               | The priority, `selectVoters` and self-promotion (5.6.4) need them.                                                                         |
| R11           | `RaftPartition.join` fails while a server exists that was not closed.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | Today it creates a second server on the same directory.                                                                                    |
| R12           | The target priority can change at runtime.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | The policy can change (5.1).                                                                                                               |
| R13           | A join admission hook on the leader. The broker supplies the check (5.4.4). The refusals are two new Raft errors, `NOT_A_MEMBER` and `NOT_READY`. Both are retriable. `ReconfigurationHelper.join` retries them.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | The start of the system partition cannot read the dynamic configuration (5.4.4).                                                           |
| R14           | When its initial entry is committed, the first leader appends a configuration entry. It does this before it appends any other entry and before it announces its leadership. Only a configuration with a voter policy gets this entry.                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | Today a partition that never changed its members keeps configuration index 0. The probe cannot see that the partition exists (D19).        |
| R15           | Each member has an incarnation in the configuration. A join request carries it. A join retry with the same incarnation is a no-op. `onJoin` writes a new configuration when the incarnation changes, and keeps the type of the existing member.                                                                                                                                                                                                                                                                                                                                                                                                                                                           | Fence entries must match only one life of a broker (D17). Today `onJoin` writes nothing for a member that is already in the configuration. |

The configuration format gets new fields (incarnations, fence list, voter policy). The format version of the configuration entry and of the `.conf` file (`raft-entry-schema.xml`) goes from 4 to 5. Data with version 4 reads as incarnation 0 for each member, an empty fence list and no policy. Only the system partition writes incarnations other than 0, fence entries and a policy. Data partitions write version 5 with these empty values. Their behavior does not change: without a policy, the first leader appends no configuration entry.

### 5.9 Changes to the broker and the dynamic configuration

- A start step for the system partition, with the start table of 5.4 and the probe. It runs before the dynamic configuration starts. It does not block the readiness of the broker. Row 1 uses the member ids and types of the stored configuration. The step keeps the empty-start marker, the incarnation and `probeTerm` on the disk, and passes them on each join (5.4.3).
- A background join loop (5.4.4). It waits for the local dynamic configuration, and it retries after the join deadline.
- A fenced listener. It closes the system partition, deletes its data and starts the background join with a new incarnation (5.6.6).
- Leadership balancing for the system partition. When a voter with a higher priority than the leader has caught up, the broker starts the leadership balancing or a transfer to the voter with the highest priority (5.3).
- A `SystemPartitionMembership` component: `selectVoters`, the priority, the join admission check (on the leader), the rules of 5.6.4 and the flows of 5.6.
- `MemberJoinApplier` and `MemberLeaveApplier` call `SystemPartitionMembership`. Each wait has a limit. `MemberJoin` completes after the broker has asked to join. `MemberLeave` gets the leader's configuration again after a timeout, because the leaving broker can stop before it gets the answer (5.6.2). The `MemberRemove` applier checks the system partition again.
- A new global operation `SystemPartitionRemoveMembers(executor, removedBrokers)`. `ForceScaleDownRequestTransformer` adds it as phase 0.
- The force-remove request and `RemoveZoneTransformer` select a coordinator that is not removed and that is reachable.
- New management operations: "force system partition voters" (5.6.5), which is not a plan, and "update system partition voter policy" (5.1), which is a Raft configuration change.
- The voter policy properties and the control zone role (5.1).
- With dynamic node ids, the system partition data is copied to the new version directory, as for data partitions. The lease gives exclusive use.
- Purge and restore do not change the system partition members.

### 5.10 Deployment examples

`A1` is broker 1 of zone A. `C` is the control zone. `{...}` is a Raft configuration. `V` is a voter, `P` is a passive member. `fence: [...]` is the fence list. `B1#5` is broker `B1` with incarnation 5. Without `#`, the incarnation is 0.

#### 5.10.1 Dual-region without a control region

**Setup.** Zones A and B with 4 brokers each. Policy `A: 2`, `B: 2`.

**Bootstrap.** All 8 brokers probe. When all 8 answer "no data", each bootstraps:

```
{A1 V, A2 V, B1 V, B2 V, A3 P, A4 P, B3 P, B4 P}     quorum: 3 of 4
```

The first leader appends a configuration entry with the same members and policy. The configuration index is now above 0.

**One broker fails.** If it is a voter, 3 of 4 voters are alive. Nothing changes.

**Zone B fails.** 2 of 4 voters are alive. No quorum. Automatic failover is not available in this setup (#20). The data partitions also have no quorum. Recovery:

1. The operator verifies that zone B is down and fences it.
2. The operator sends "remove zone B" with `force=true`.
3. Phase 0: `A1, A2` are not a quorum of 4. Thus, a forced change. `A1` and `A2` have the most recent log:

   ```
   {A1 V, A2 V, A3 P, A4 P}   fence: [B1, B2, B3, B4]     quorum: 2 of 2
   ```
4. Phases 1 and 2 change the data partitions and the dynamic configuration.

The system partition tolerates no failure now. This is the same as the data partitions, which have only the 2 replicas in zone A.

**Only one member has the most recent log.** Example: `A1` was cut off, and entries committed on `A2`, `B1` and `B2`. `A3` received them as a passive member. Then `A2` failed together with zone B. `A1` can be reached again. Of the reachable members, only `A3` has the most recent log. The forced configuration is:

```
{A3 V, A1 P, A2 P, A4 P}   fence: [A1, A2, B1, B2, B3, B4]     quorum: 1 of 1
```

`A1` and `A2` were voters, but they are not voters of the forced configuration. Thus, they go on the fence list too (5.6.3, step 3, item 6). `A1` gets the fenced notice. It deletes its data and joins again with a new incarnation. When `A2` comes back with its old data, it is a voter in its old configuration and sends polls. The members reject them, and the leader sends `A2` the fenced notice. `A2` deletes its data and joins again with a new incarnation. Both are `ACTIVE` cluster members, thus the leader admits them. When `A1` has caught up, it promotes itself, and zone A has 2 voters again:

```
{A3 V, A1#3 P, A2#6 P, A4 P}  ->  {A3 V, A1#3 V, A2#6 P, A4 P}   fence: [A1, A2, B1, B2, B3, B4]
```

If `A1` to `A4` all had the most recent log, the policy `A: 2` limits the voters to `A1` and `A2`.

**Restart of the cluster after the forced change.** `A1` and `A2` load the forced configuration with the fence list. They elect a leader with 2 of 2 votes. `A3` and `A4` start as passive members. `B1` and `B2` start with old data. They were voters, thus they send polls. `A1` and `A2` reject them (fence list), and the leader sends the fenced notice. They delete their data and ask to join. The leader refuses, because they are `LEFT`. `B3` and `B4` were passive. They send no requests and are never accessible.

**Failback.** The operator adds `B1` to `B4` again. Each one is now `JOINING`. `MemberJoin` deletes old data, if any. Each broker asks to join as `P` with an empty log and a new incarnation. The leader admits it. The old fence entries stay, but they do not match the new incarnations. Self-promotion fills the 2 slots of zone B:

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

The first leader appends a configuration entry with the same members and policy. `C1` has the highest priority (5), thus it usually wins the first election. The priorities are `C1` 5, `A1` 4, `A2` 3, `B1` 2, `B2` 1. `A3` and `B3` have 0.

**Zone A fails: automatic failover.** 3 of 5 voters are alive (`B1, B2, C1`). The failure detector commits the failover decision. The failover runs the force-remove plan for zone A. Phase 0: `B1, B2, C1` are a quorum of 5. Thus, a **normal** change:

```
{C1 V, B1 V, B2 V, B3 P}   fence: [A1, A2, A3]     quorum: 2 of 3
```

No forced change is necessary. The system partition still tolerates one failure. The slots of zone A stay empty.

**Zone A comes back before the failback.** `A1` and `A2` have 2 of 5 old votes, thus they cannot elect a leader. They send polls. The survivors reject them (fence list), and the leader sends the fenced notice. They delete their data and ask to join. The leader refuses, because they are `LEFT`. `A3` was passive. It sends no requests and is never accessible.

**Failback.** The failback adds `A1` to `A3` again. They are now `JOINING`. `MemberJoin` deletes old data, if any. They join as `P` with an empty log and new incarnations, and the leader admits them. The old fence entries stay. Self-promotion fills the slots of zone A:

```
{C1 V, B1 V, B2 V, B3 P, A1#4 P, A2#7 P, A3#3 P}  ->  ...  ->  {C1 V, A1#4 V, A2#7 V, B1 V, B2 V, A3#3 P, B3 P}
```

**The control zone fails.** 4 of 5 voters are alive. The cluster does nothing (#21). The leader moves to the data voter with the next priority. When `C1` comes back with its disk, it catches up as a voter. The priority election does not take the leadership from the leader that runs (5.3). Thus, `C1` is the leader again only after the current leader steps down: when `C1` has caught up, the broker starts the leadership balancing. If `C1` comes back with an empty disk, it joins with a new incarnation and the empty-start guard. It does not vote until it has caught up with the current leader.

**The control zone and zone A fail.** 2 of 5 voters are alive. No quorum. Processing stops (#21).

- **Preferred:** bring `C1` back with its disk. The system partition has a quorum again, and the automatic failover removes zone A as above.
- **If `C1` cannot come back:**
  1. The operator verifies that zone A and `C1` are down and fences them.
  2. The operator calls "force system partition voters" with `downBrokers = A1, A2, A3, C1`. `B1` and `B2` have the most recent log. `B3` can have it too, but the policy `B: 2` limits the voters to `B1` and `B2`:

     ```
     {B1 V, B2 V, A1 P, A2 P, A3 P, B3 P, C1 P}   fence: [A1, A2, A3, C1]     quorum: 2 of 2
     ```
  3. The system partition has a quorum. The failure detector sees that zone A failed. The automatic failover removes zone A with a normal change. The fence entries stay:

     ```
     {B1 V, B2 V, B3 P, C1 P}   fence: [A1, A2, A3, C1]
     ```

     It records the failover state and the failback reference.

  4. Automatic failover is not available now, because the control zone has no voter (#21).

**Restart of the cluster after the forced change.** `B1` and `B2` load the stored configuration with the fence list and elect a leader. `B3` starts as passive. `C1`, `A1` and `A2` were voters. If they start with old data, they send polls and are rejected (fence list). They delete their data and ask to join. The leader refuses zone A, because it is `LEFT`. `A3` was passive. It sends no requests and is never accessible.

**Return of the control zone.** `C1` starts. If it has old data, it starts with row 1. The survivors reject it, because `C1` with incarnation 0 is on the fence list. The leader sends the fenced notice. `C1` deletes its data and asks to join with an empty log and a new incarnation. `C1` is still `ACTIVE` in the dynamic configuration, thus the leader admits it. The old entry for `C1` stays, but it does not match the new incarnation. When `C1` has caught up, it promotes itself:

```
{B1 V, B2 V, B3 P, C1#9 P}  fence: [A1, A2, A3, C1]  ->  {C1#9 V, B1 V, B2 V, B3 P}  fence: [A1, A2, A3, C1]
```

`C1` gets the highest priority (5) again. It is the leader only after the leadership balancing (5.3).

Automatic failover is available again. Then the failback adds zone A. `A1` to `A3` join with an empty log and new incarnations. Their old fence entries stay. The failback is manual here, because #21 counts the forced change as a configuration change.

**Remaining danger.** `A1, A2` and `C1` have 3 of 5 old votes. If they start with old data, and they cannot reach `B1` or `B2`, they can elect a leader among themselves. The fence list does not help then. Thus, the operator must keep them down until the network to zone B works (Q1).

## 6. Decisions

### D1. The Raft configuration is the only record of the system partition members

**Decision.** The members and their types are only in the Raft configuration.

**Why.** The system partition will later hold the dynamic configuration. If the dynamic configuration stored the system partition members, each would need the other to change. The forced recovery must work when the system partition has no quorum. Raft already stores its configuration safely and recovers it at restart.

**Alternatives that were rejected:**

- *Store the members in `ClusterConfiguration`, as for data partitions.* This makes a circular dependency. It also gives two records that can be different.
- *Store the voters as a key in the system partition state.* This gives two records. A key can only change with a quorum, thus it does not help the forced recovery.

### D2. Member operations call Raft directly; each broker keeps only its own membership correct

**Decision.** `MemberJoin` and `MemberLeave` change the system partition before they complete. Force-remove plans use phase 0. None of them record the result in the dynamic configuration. In addition, each broker repairs its own membership (self-promotion, self-join), and the leader demotes extra voters (5.6.4).

**Why.** The operations give a clear order: a leaving voter is replaced in the same change in which it leaves, and a broker is a system partition member only while it is a cluster member. The own-membership rules repair the cases that the operations cannot reach: a broker that never gets `MemberJoin`, a cancelled plan, a new policy. Each rule changes only one broker, and the exact index check orders them.

**Alternatives that were rejected:**

- *Only a reconciler on the system partition leader that compares the Raft configuration with the cluster member list.* The leader cannot know when a passive broker has caught up, or when a leaving broker is about to stop. The operations give that order.
- *Only explicit operations (draft 1).* Some brokers never get an operation: the control broker after "force system partition voters", or a broker after a cancelled plan.

### D3. The voter policy is a count for each zone, in the Raft configuration

**Decision.** Each zone has a voter count. An empty slot is not filled from a different zone. The policy comes from the static configuration. The Raft configuration of the system partition stores it (D18). An operation can change it with a Raft configuration change.

**Why.**

- After a failover, the cluster has the voters of the remaining zones (for example 2 + 1). The control zone keeps its vote, as #21 requires.
- At failback, the returning brokers fill the slots of their zone. No special failback logic is necessary.
- No vote moves between zones. Thus, one data zone never gets the majority.
- The Raft configuration gives all brokers the same policy and the same zone order.
- The rolling conversion to 2.5 regions needs a change of the policy at runtime.

**Alternatives that were rejected:**

- *One total count, round-robin across zones, as in the earlier failover PoC.* After a failover the leader fills the missing slots from the remaining zone, and at failback it must demote them again.
- *One total count at bootstrap, then replace a voter only in its zone.* The slots of a zone are lost after the zone is empty.
- *Use only the static configuration at runtime.* Brokers with different static values calculate different targets.
- *Store the policy in the dynamic configuration (`ZoneSpec`).* This makes a new dependency on the dynamic configuration (D1).
- *A fixed policy (revision 2).* It blocks the rolling conversion to 2.5 regions.
- *A separate policy record in the system partition state (revision 4).* See D18.

### D4. The broker that runs the operation calculates and sends the configuration

**Decision.** The broker calculates the target. It sends it with the configuration index and term. The leader checks them exactly and returns its current configuration in a rejection.

**Why.** Each member has a configuration. A rejection gives the current configuration also to a broker that is not a member or that has an old one. No leader-side service is necessary.

**Alternative that was rejected:**

- *A service on the leader that receives "add", "remove" and "replace" requests.* This needs a new protocol and leader discovery. It gives no more safety.

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

**Decision.** `MemberLeave` of a voter sends one change that removes the voter and promotes a replacement of the same zone. If no replacement is possible, it removes the voter only. Phase 0 removes all removed brokers in one change.

**Why.** Joint consensus keeps the quorum of the old voters and of the new voters during the change. The number of voters in a zone does not go above the policy. If the replacement came first in a separate change, zone A could have 3 of 6 voters for some time. If zone A then failed, the system partition would have no quorum.

**Alternatives that were rejected:**

- *Promote first, then remove (draft 1).* See above.
- *Remove first, then promote.* The zone has one voter less for some time.
- *Remove the brokers of a force-remove one by one.* Each removal is a joint change with no spare voter. Between the removals, a fill step could promote a broker that is down.

### D10. The election priority comes from the Raft configuration and its voter policy

**Decision.** Each broker calculates its priority from the Raft configuration and the voter policy in it. The target priority is the total of the policy. The priority of a voter is at least 1. The zone order is frozen in the policy. The priority election does not take the leadership from a leader that runs. The broker starts the leadership balancing when a voter with a higher priority has caught up (5.3).

**Why.** The voters change at runtime. A target from the policy is the same on all brokers. If the target were the current number of voters, brokers that did not restart would keep an old target, and several voters would start elections at the same time. The zone priorities of the dynamic configuration can change at runtime, thus brokers could calculate different priorities. The policy is in the stored configuration. Thus, each broker has its priority at a restart, also without a leader.

### D11. The voters of a forced configuration are the members with the most recent log

**Decision.** Before a forced change, the broker collects the logs of the reachable members. Only the members with the most recent log become voters. Each zone gets at most the number of voters that its slots allow. If no member with the most recent log is in a zone of the policy, one of them becomes a voter anyway. In phase 0, each voter that the forced change demotes goes on the fence list (5.6.3). Self-promotion and the leader's demotion restore the policy later.

**Why.** The member with the most recent log has all entries that any reachable member knows as committed (Raft log matching and leader completeness). If a member with an older log were also a voter, it could win an election without the most recent member. Then a passive member that had more entries would have to truncate below its commit index. Raft cannot do that, and local readers already saw the entries. The slot limit keeps the number of voters in a zone inside the policy, as the leader check of R3 expects.

**Alternatives that were rejected:**

- *The surviving voters, plus a passive member with a more recent log (revision 2).* The surviving voters can still elect a leader without that passive member.
- *Always the surviving voters.* A passive member with more entries fails when it must truncate them.

### D12. Fence list

**Decision.** A removal and a forced change put the brokers on a fence list in the configuration, with their incarnations (R8). Members reject requests from a sender whose id and incarnation match an entry. A fenced broker deletes its data and joins again with an empty log and a new incarnation. Fence entries are never cleared (D17).

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

### D15. A broker without data does not vote until it has caught up with the current leader

**Decision.** Empty-start guard (R7, 5.4.3).

**Why.** A voter with an empty disk gives any vote, and it lost its record of its last vote. Example: entry E is committed on `A1, A2, C1`. `C1` loses its disk and comes back. Then zone A fails. `B2` does not have E. `C1` and `B1` vote for `B2`, and E is lost. The guard is released only by a leader with a term of `probeTerm` or more, because a deposed leader can have a commit index that is too low.

**Alternatives that were rejected:**

- *Accept the limit, as for data partitions (draft 1).* The system partition holds the failover decisions.
- *Demote the broker to passive first.* This needs a quorum without the broker. The guard gives the same safety without a configuration change.
- *Release the guard at the commit index of any leader (revision 2).* A deposed leader on a minority side can release it too early.

### D16. The leader admits joins; the start does not read the dynamic configuration

**Decision.** The start of the system partition uses only local data, the static configuration and the probe. A broker without data asks to join. The leader admits the join only if the broker is `JOINING` or `ACTIVE` and not the target of a pending removal (R13). Each removal puts the broker on the fence list, so that a removed broker with old data is rejected.

**Why.** The dynamic configuration starts after the system partition. Thus, a starting broker cannot know if it is a cluster member. The leader reads its dynamic configuration at runtime, after it is initialized. Until then, it answers `NOT_READY`, and the broker retries. The join request has the version of the joiner's dynamic configuration. Thus, a leader with an older view also answers `NOT_READY`. A `LEAVING` broker, or the target of a pending removal, will soon not be a cluster member. If the leader admitted it, I1 could become false. Later, the cluster members are in the system partition state, and the same check reads them there. Thus, the design does not change when the dynamic configuration moves into the system partition.

**Alternatives that were rejected:**

- *Check the dynamic configuration at the start (revision 3, rows 1 and 2).* It is not available at that time. The stored file is also old for a broker that was down during its removal.
- *Let the system partition start wait for the dynamic configuration.* The dynamic configuration will later depend on the system partition. This makes a circular wait.
- *Leader stickiness in Raft: ignore vote requests while a leader is known.* It stops a removed broker from making the leader step down. But the removed broker still runs with old data and is never told to delete it. It can still be a quorum of an old configuration with other removed brokers. The fence list covers both. Leader stickiness is a good general change for Raft, but it is not enough alone.

### D17. Fence entries have an incarnation and are never cleared

**Decision.** Each member has an incarnation (R15). A fence entry is a broker id and an incarnation. A member rejects a request only if both match. A broker acts on an entry only if the incarnation is its own, and the entry is committed, a member answered `FENCED`, or the leader sent the fenced notice. A member answers `FENCED` only if the entry is committed in its own view. Fence entries are never cleared.

**Why.** A broker that joins again has a new incarnation. Thus, its old entries never match it, also on a member with an old stored configuration. The number of entries is not more than the number of removals and forced changes. An uncommitted entry can disappear. Thus, a broker must not delete its data because of it. For the same reason, a member that has the entry only in a configuration that is not committed answers `ILLEGAL_MEMBER_STATE`, and the sender ignores it.

**Alternatives that were rejected:**

- *Clear the entry when the broker joins again (revision 4).* A member can be down during the rejoin and keep an old stored configuration with the entry. It then rejects the appends of the rejoined broker. The rejoined broker deletes its data, also when it is the legitimate leader.
- *Clear the entry when the dynamic configuration no longer knows the broker (Q10 of revision 4).* It is not necessary, because the number of entries is limited. It also makes a new dependency on the dynamic configuration.

### D18. The voter policy is in the Raft configuration, not in a state record

**Decision.** The Raft configuration stores the voter policy, as it stores the fence list. "Update system partition voter policy" is a Raft configuration change through `reconfigure` (R3).

**Why.** Each broker reads the policy from its stored configuration at restart. Thus, the priorities are correct at once, also without a leader. The initial configuration has the policy, thus no broker must wait for a record. A policy change needs a quorum, as a record change does.

**Alternative that was rejected:**

- *A separate policy record in the system partition state (revision 4).* A record is not available until the broker has applied it. At a restart of the whole cluster, all priorities would be 0 until then. A broker with an older record could promote itself, and the leader would then demote it again.

### D19. The first leader appends a configuration entry

**Decision.** When its initial entry is committed, the first leader of the system partition appends a configuration entry. It does this before it appends any other entry and before it announces its leadership (R14). The probe uses a configuration index above 0 to find out that the system partition exists.

**Why.** Today the first leader appends only an initial entry. A cluster that never changed its members keeps configuration index 0. The probe then cannot see that the system partition exists. With the new entry, nothing with data commits before the index is above 0. Thus, a late broker that bootstraps at index 0 loses nothing.

The PoC showed why the leader waits until the initial entry is committed. A follower can reset its log on the first append from the leader. Then it loads its stored configuration again and loses a configuration that it received before that append. Its stored configuration has index 0, and the leader does not send the configuration again to a member that reports index 0. Thus, the follower would keep index 0. In addition, the PoC does not load the stored configuration again if the reset deletes no entries.

**Alternative that was rejected:**

- *Probe for a Raft term above 0 or a log above 0.* At a first start, brokers that already tried elections have a term above 0. A late initial voter then tries to join a partition that has no leader. Its empty-start guard blocks its vote. If the quorum needs its vote, no leader is elected. This is a deadlock.

## 7. Risks and open questions

- **Q1. Isolated fenced brokers.** The fence list protects the survivors. A group of fenced brokers that cannot reach a survivor can still be a quorum of the old configuration. The operator must keep them down until the network works. Is a stronger protection necessary?
- **Q2. Product confirmation.** Phase 0 of the force-remove plans does the forced change in a dual-region cluster without a control zone. "Force system partition voters" and "update system partition voter policy" are new management operations. #21 planned a runbook step in v1. Product must confirm.
- **Q3. Load on the leader.** The leader sends heartbeats and entries to each broker. For clusters with tens of brokers this is acceptable.
- **Q4. Upgrade and mixed versions.** In an existing cluster, the probe finds no data on all brokers. Older brokers cannot answer the probe. The static configuration can be different from the current cluster members. The initial configuration must use the current cluster members. This needs a separate design.
- **Q5. Dependency on the dynamic configuration.** The start does not use it. The join admission on the leader, the excluded set of `selectVoters` and self-promotion read the cluster members from it. `MemberJoin` and `MemberLeave` are its operations. When the dynamic configuration moves into the system partition, the admission check and the excluded set read the system partition state instead.
  - Note: the start does not read the dynamic configuration. The leader admission, the excluded set and self-promotion read it at runtime, after it is initialized.
- **Q10. Size of the fence list.** Resolved in revision 5. Fence entries are never cleared (D17). Their number is not more than the number of removals and forced changes.
- **Q6. Cancel blocks failback.** After `CancelChange`, members stay in the configuration as `LEFT`. Then `MemberJoinApplier.init` rejects them. This is an existing problem, but it is on the failback path.
- **Q7. Zone migration.** 5.6.10 gives only the rule. It needs a separate design.
- **Q8. Voters after a scale-down.** A scale-down below the number of slots of a zone leaves fewer voters in that zone. The validation checks only the policy, not the actual voters.
- **Q9. Lost and resurrected entries.** A forced change can lose system partition entries that were committed only on the fenced brokers. Also, an entry that was never committed can become committed after a forced change. For example, a deposed leader appended the entry. Consumers of the system partition state must accept this, as they must for data partitions.
- **Q11. Forwarding by non-members.** A broker that is not a member does not forward a `reconfigure` request, and it cannot send one itself (5.5). A member that knows no leader answers `NO_LEADER`, and the sender retries. Usually, the broker that runs an operation is a member. Is forwarding by non-members necessary? This is a minor question.

## 8. Tests

- Raft: bootstrap with typed members, the policy and incarnation 0. Restart keeps the types and the policy. A passive member never becomes a candidate, also with `anoint()`.
- Raft: the first leader appends a configuration entry when its initial entry is committed, before any other entry and before it announces its leadership. After that, the configuration index is above 0.
- Raft: promotion from `PASSIVE` to `ACTIVE`. A member that did not answer within the election timeout is not promoted. A change that adds a voter to a zone beyond its slots is rejected. A demotion in a zone with too many voters is accepted. A target with a fenced voter is rejected.
- Raft: a forced change completes when a passive member is down. Only the members with the most recent log become voters, and not more than the slots of each zone. The change goes to all members that answered.
- Raft: `reconfigure` with a different index or term is rejected, and the rejection has the current configuration. A policy change through `reconfigure` commits.
- Raft: empty-start guard. A broker with the marker does not vote. A leader with a term below `probeTerm` does not release it. `probeTerm` uses only the voters of the highest configuration. After "force system partition voters", a broker that lost its disk completes the probe.
- Raft: fence list. Members reject a fenced (id, incarnation) without a term update, also in responses. A member answers `FENCED` only for a fence entry that is committed in its view, else `ILLEGAL_MEMBER_STATE`. The leader sends the fenced notice at most once per heartbeat interval and no entries. The fenced broker deletes its data and joins again with a new incarnation. The old entry stays and does not reject it. A member with an old stored configuration accepts the appends of the rejoined broker. An uncommitted entry without a leader notice does not make a broker delete its data.
- Raft: join. A retry with the same incarnation writes no configuration. A join with a new incarnation writes a new configuration and keeps the type of the member. `RaftPartition.join` fails while a server that was not closed exists.
- Raft: storage. Decode real version-4 bytes of a configuration entry and of a `.conf` file. The result has incarnation 0 for each member, an empty fence list and no policy.
- Unit: priorities. A zone with more voters than slots gives each voter a priority of 1 or more.
- Unit: phase 0 forced change. A voter that is not removed and is not a voter of the forced configuration goes on the fence list.
- Unit: `selectVoters` for each case: empty zone, extra voters, excluded brokers (not `JOINING` or `ACTIVE`, targets of pending removals), a zone that is not in the policy.
- Unit: probe decisions: all "no data", index 0 answers, one index above 0, a different hash at bootstrap, a different hash at a join, missing answers.
- Unit: join admission: `NOT_READY` before the dynamic configuration is initialized and for an older version; `NOT_A_MEMBER` for `LEAVING`, `LEFT` and a target of a pending removal; rejection of a fenced sender; the same checks for a `reconfigure` that adds a member.
- Integration (2 + 2 + 1 voters on 3 + 3 + 1 brokers): add a broker, remove a voter, failover of a zone, failback, restart of each broker, restart of all brokers, disk loss of a voter and of a passive member, cancelled plan after phase 0, recheck of `MemberRemove` in phase 2.
- Integration: "force system partition voters" with the control zone and zone A down, then the automatic failover, a restart of all brokers, then the return of `C1`. `C1` is the leader again after the leadership balancing.
- Integration: `MemberLeave` of a voter. The leaving broker is fenced before the answer arrives, and the applier completes after its timeout.
- Integration (4 + 4 brokers without a control zone): force-remove of zone B, a restart of all brokers, then failback.
- Integration: rolling conversion from dual-region to 2.5 regions with "update system partition voter policy".
- Integration: restart of all brokers with dynamic node ids.

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

The Raft changes and the rules have their own tests:

- `SystemPartitionRaftTest`: typed bootstrap (R1), `reconfigure` with an exact index (R3, R4), `forceConfigure` without passive members (R2), fencing (R8, R15), the join admission hook (R13), the voter policy in the configuration (R14, the zone check of 5.5), the empty-start guard (R7), voters and the configuration view (R6, R9, R10).
- `SystemPartitionMembershipTest`: the policy, `selectVoters`, the priorities, self-promotion, the demotion and the change of `MemberLeave`.
- `SystemPartitionRecoveryTest`: phase 0 (`RemoveMembers`) and "force system partition voters" (`ForceVoters`).
- `RaftPartitionJoinTest`: R11.
- `RaftNamespacesTest` (serialization of the new request fields), `RaftEntrySBESerializerTest` and `MetaStoreTest` (format version 5), `PriorityElectionTimerTest` (R12).

Status of the Raft changes (5.8):

- Done: R1, R2, R4, R6, R8, R9, R11, R12, R13, R14, R15.
- Done in part: R3 (no forwarding by non-members, Q11), R7 (the guard in memory; the marker on disk is a broker change), R10 (a passive member does not yet learn the commit index of the leader).
- Not done: R5 (optional).

### 9.3 What the PoC changed in the design

The PoC deviated from revision 5 in these points. Revision 6 adopts them:

- The first leader appends its configuration entry when its initial entry is committed, before any other entry and before it announces its leadership (5.4.2, R14, D19).
- A member answers `FENCED` only if the fence entry is committed in its own view. Else it answers `ILLEGAL_MEMBER_STATE`, and the sender ignores it (5.6.6, R8).
- The fenced notice is the committed configuration in a `ConfigureRequest` that is marked as committed, at most once per heartbeat interval. The leader does not replicate entries to a fenced member (5.6.6).
- A response from a fenced member counts as a response from a member that is not reachable (5.6.6).
- A join with a new incarnation keeps the type of the member. The leader rejects a target in which a fenced member is a voter (5.4.4, 5.5, R15).
- Raft keeps the empty-start guard in memory. The broker keeps the marker and `probeTerm` on its disk and passes them in `JoinOptions` on each join (5.4.3, R7).
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

### 9.4 What the PoC does not cover

- The start step of the broker, with the probe and the empty-start marker on disk (5.4).
- The appliers of the dynamic configuration (`MemberJoin`, `MemberLeave`, `MemberRemove`) and the phase 0 operation `SystemPartitionRemoveMembers` (5.6, 5.9).
- The management operations "force system partition voters" and "update system partition voter policy". The PoC has only their decisions and Raft calls.
- R5.
- A test that decodes real version-4 bytes (8).
- Forwarding by non-members (Q11).

### 9.5 How to run the tests

In the repository root, after `./mvnw install -pl zeebe/atomix/cluster -am -Dquickly -T1C`:

```bash
./mvnw verify -pl zeebe/atomix/cluster -Dtest='SystemPartition*,RaftPartitionJoinTest' -DskipTests=false -DskipITs -Dquickly
```

