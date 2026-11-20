# Connect the Hub client to partition 1 with a leader-bound receiver and a response-based result path

**DRI**: Christian Thiel

**Status**: Proposed (8.11)

**Purpose**: Defines where the Hub client runs in the broker, how it learns the policy state of the
engine, how it hands change sets to the engine, and how Hub learns whether an update was applied,
failed or was aborted.

**Audience**: Zeebe engineers working on the broker lifecycle or the policy update processors;
engineers building the Hub client; and AI agents reasoning about how Hub and the engine talk to each
other.

## Context

[ADR 0014](0014-811-hub-managed-identity-policy-updates.md) defines how partition 1 of a physical
tenant receives a policy update in chunks, applies it, and commits it on all partitions. It leaves
open the component that connects Hub to the engine. That component must:

- run once per physical tenant, on the broker node that leads partition 1, and move with that
  leadership;
- know the policy version, the Hub sequence and the status of the last update (`PENDING`,
  `APPLYING`, `COMMITTED`) that the engine stores in RocksDB;
- poll Hub from the stored sequence, and pause the polling while an update is open;
- report to Hub whether an update was applied, failed or was aborted.

The engine's processors have no REST client and should not get one. Side effects of a processor run
only on the leader after the commit, are not run again during reprocessing, and are not retried. A
report sent only from a side effect is lost if the leader fails before the call to Hub succeeds.

Two components are needed:

- **`PolicyInPort`**: takes a `PolicyChangeSet` (DTO still to be defined), splits it into chunks
  (usually one), and sends them to partition 1.
- **`HubUpdateReceiver`** (working name): owns the Hub client, reads the policy state, forwards
  change sets to the `PolicyInPort`, pauses and resumes the polling, and reports results to Hub.

## Decisions

**D1. One `HubUpdateReceiver` per physical tenant, bound to the leadership of partition 1.**
- The receiver implements `PartitionListener` and ignores every partition except partition 1. It
starts in `onBecomingLeader`, and stops in `onBecomingFollower` and `onBecomingInactive`.
- It is registered per physical tenant, like the job stream error handler in
`PartitionManager.createPartitionManager`. A listener shared by all tenants only gets a bare
partition number and cannot tell the tenants apart.
- A new broker startup step (`AbstractBrokerStartupStep`) creates the receivers before the
`PartitionManagerStep`, as the `JobStreamServiceStep` does for job streams.
- The partition notifies its listeners only after the leader transition is complete, so the state of
partition 1 is open by then.
- The receiver works on its own actor and never blocks the leader transition of the partition. Its
`onBecomingLeader` future always completes successfully, because a failed listener future fails
the leader transition of the partition.

**D2. The engine publishes the policy state of partition 1 through the gossip, and the receiver
reacts to it.**
- RocksDB is the engine's internal command state, not a read model. No component outside the stream
processor reads it for this feature.
- The engine gets a `PolicyStateNotifier` interface, built like `ProbeNotifier`. The broker
implements it as `GossipPolicyStateNotifier`, and `ZeebePartitionFactory` passes it to the engine.
- On partition 1, the policy processors publish the new state (committed policy, Hub sequence,
status of the open update) after a command changed it. The state is read while the command is
processed, and published in a side effect, so only after the commit and only on the leader:
- `APPEND_CHUNK`, after the chunk is buffered (`PENDING` or `APPLYING`) or the update failed;
- `COMMIT_VERSION`, after the version is committed.
- A side effect does not run during replay. So a lifecycle listener publishes the state once
partition 1 has recovered as the new leader, and withdraws it when the stream processor closes or
fails.
- `GossipPolicyStateNotifier` writes the state as a small JSON value into a property of the local
member, `policyState:<physicalTenantId>`, as `TopologyManagerImpl` publishes the partition roles.
SWIM detects the change on its next gossip round (250 ms by default), posts `METADATA_CHANGED`
to the local listeners, and spreads it to the other members.
- The receiver registers as a `ClusterMembershipEventListener`. It runs on the leader of partition
1 (D1), so it reads the property of the local member: once on start, and again on every
`METADATA_CHANGED` event of the local member. The events of other members are ignored.
- The gossiped state can be behind the log. It is used to read start values and to wait for
`COMMITTED`, never to decide something that must be exact. Those decisions are made by the engine
while it processes a command (D4).
- While an update is open, the receiver waits for the next change of the gossiped state, and polls
Hub again only once the update is `COMMITTED`.

**D3. The `PolicyInPort` sends the chunks with the `BrokerClient`, one at a time.**
- The request's `partitionGroup` is the physical tenant, so the command reaches partition 1 of that
tenant.
- The receiver waits for the response to each chunk before it sends the next one. The engine sends
a response only after it has processed the command and committed the state. So after the
response to the last chunk, the published state is `APPLYING` or newer.
- Backpressure, a missing partition leader and a timeout are retried. They say nothing about the
update itself.
- A chunk sent again for the same `updateId` and index is accepted, not rejected. After a timeout
the receiver does not know whether the chunk arrived, and a rejection would report a failure that
never happened.

**D4. On every start, the receiver aborts a pending update and lets the engine decide.**
- The receiver sends `ABORT` without first reading the state. The engine processes it in log order.
- A `PENDING` update becomes `ABORTED`, and its buffer is deleted.
- An `APPLYING` update is not aborted, and the receiver waits for `COMMITTED` (D2).
- With no open update, nothing happens, and the receiver polls Hub.
- The response tells the receiver which case applies. The engine decides, because the last chunk can
be in the log but not yet processed when the receiver starts.

**D5. Hub learns a failure from the response to the command that ended the update.**
- Validation (ADR 0014, D9) runs while the engine processes the last `APPEND_CHUNK`, so its result
is the response to that command.
- A failed update ends with an `ABORTED` event, not a rejection. The event applier deletes the
buffer, and the record carries the `errorMessage`. A rejection cannot change state, so it could not
clean up the buffer.
- The response to the `ABORT` of D4 is also the `ABORTED` event.
- The receiver reports the failure and its reason to Hub.

**D6. Hub learns a success from the stored state, at least once.**
- When the receiver sees `COMMITTED`, it reports the policy version and the sequence to Hub.
- On every start, the receiver reports the stored policy version and sequence to Hub again, as
"applied up to here". This repeats a report that a failover may have lost.
- Hub ignores a report for an update it already knows.

**D7. A failed or aborted update leaves no result in the engine state.**
- If the leader fails after committing `ABORTED` but before the response arrives, the reason is
lost. The stored sequence did not move forward, because only a commit moves it. So the new
receiver polls Hub from the same sequence, sends the same (or a newer) change set again, and the
deterministic validation fails again with the same reason. This time the response arrives.
- The cost is sending the change set a second time, and only after a failover.

**D8. Policy update commands have the same trust model as all broker commands.**
- The commands use the internal cluster transport, like gateway requests to a broker. The cluster
network is trusted, and cluster TLS encrypts the traffic if it is configured.
- No REST or gRPC endpoint maps to a policy update command, so clients cannot send one through a
public API.

## Alternatives considered

- **A `PolicyStateReader` of the partition.** Reads RocksDB on the partition's actor from outside
  the stream processor, through the `PartitionAdminControl`. This treats the engine's command state
  as a read model, which violates CQRS; it also needs a lookup of partition 1 and a read every few
  seconds while an update is open.
- **`ClusterEventService.broadcast` instead of a member property.** A fire-and-forget event: a
  receiver that starts after the event never sees it, and nothing repeats it.
- **The receiver selects the leader of partition 1 from the gossip of all members.** Needs the
  partition roles and terms from `BrokerInfo` to ignore a stale value. The receiver already runs on
  the leader (D1), so the local member's value is enough.
- **A `BrokerAdminRequest` to read the policy state.** A network request to a partition on the same
  node. It only helps if the receiver runs on another node. `BrokerAdminRequest` also lives in
  `zeebe/gateway`, so the broker would need a dependency on the gateway. It can still be useful
  later for debugging.
- **The `QueryService` passed to `onBecomingLeader`.** It is deprecated for removal, its Javadoc
  limits it to the broker's temporary query API, and it reads RocksDB from outside the stream
  processor.
- **A `getPolicyState()` method on `PartitionAdminAccess`.** Reads RocksDB like the
  `PolicyStateReader`, and puts a product feature into the operator API (pause, snapshot, ban
  instance).
- **`TopologyPartitionListener` instead of `PartitionListener`.** Its leader updates come from
  gossip. They arrive later, and two nodes can briefly both believe they lead partition 1.
- **A `PartitionTransitionStep` instead of a listener.** Gets the full transition context, including
  the `ZeebeDb`, but is core partition machinery. A slow or failing Hub start would delay or fail
  the leader transition.
- **Read the state with an `ABORT` or a status command.** A command is an intent to change state,
  not a query. The `ABORT` of D4 stays a real abort and is not used to read the start values.
- **Notify the receiver from a processor side effect only.** Side effects follow processed
  commands, so they cannot provide the state when the receiver starts. D2 adds the publication
  once partition 1 has recovered, and keeps the last value in the member property.
- **The Hub client inside the engine, in the stream processor of partition 1.** Gives exact state
  access, but puts the network calls to Hub and the Hub client dependency into the engine.
- **Report results from a processor side effect only.** Reaches Hub at once, but is lost on a
  failover or a failed call, because side effects are not retried. It can be added later to wake the
  receiver sooner, never as the only report.
- **Report results from an exporter on partition 1.** Exporters resume from their stored position,
  but a failed export is repeated until it succeeds, and the next record waits for every exporter. A
  Hub outage would stop the export to secondary storage on partition 1.
- **Store the result of a failed update in the engine state.** Makes the reason available after a
  failover, but needs extra state and a clean-up. The repeated validation of D7 gives the same
  result.
- **Write the chunks directly to the log stream of partition 1.** Avoids the network, but has no
  response and bypasses the checks of the command API.

## Consequences

- The Hub client starts and stops with the leadership of partition 1, with no own leader election.
- The engine keeps no REST client. Its policy processors publish the state through an interface,
  and know nothing about the gossip or Hub.
- All communication with Hub stays in the receiver.
- Hub can receive the same report more than once, and must ignore repeats.
- After a failover, a failed update is validated a second time before Hub learns the reason.
- No component outside the stream processor reads the engine's RocksDB state for this feature.
- Every member gossips one more small property per physical tenant while it leads partition 1. The
  value is not durable; the engine publishes it again after a restart or a leader change.
- The receiver learns a commit within about one gossip round (250 ms by default), without polling
  the partition.

## Open questions

- **Where the Hub URL and credentials are configured.** The receiver lives in `zeebe/broker`. A
  Spring module gives the broker a `HubClient` factory per physical tenant through the
  `SpringBrokerBridge` (precedent: `getSchemaInitializer(physicalTenantId)`), so the configuration
  stays in Spring.
- **Hub API.** Whether Hub ignores repeated reports, and whether the poll request can carry the
  last applied sequence, so that a success needs no separate call.
- **Failures while applying.** A failure in the `APPLYING` phase is out of scope. The update then
  stays `APPLYING`, and the polling stays paused. At least a metric or log entry for an update that
  is `APPLYING` for too long should make this visible.

## Source

- [Connect OC to hub via hub-client (camunda/camunda#68925)](https://github.com/camunda/camunda/issues/68925)
- [How does the hub-client get to know the current seqNo?
  (pdp-initiative-ea-identity--access-at-scale#52)](https://github.com/camunda/pdp-initiative-ea-identity--access-at-scale/issues/52)
- [Pausing the polling until a version is applied
  (pdp-initiative-ea-identity--access-at-scale#53)](https://github.com/camunda/pdp-initiative-ea-identity--access-at-scale/issues/53)
- [ADR 0014: Hub policy updates as versioned identity state](0014-811-hub-managed-identity-policy-updates.md)
- [Hub-managed identity data](../../../docs/zeebe/hub-identity.md)

