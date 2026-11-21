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
- The receiver works on its own actor and never blocks the leader transition of the partition.

**D2. The receiver reads the policy state through the partition's `QueryService`.**
- `onBecomingLeader` passes the `QueryService` of the partition: a read-only view on its RocksDB,
created when the partition becomes leader and closed when it steps down.
- The `QueryService` gets a `getPolicyState()` method that returns the policy version, the Hub
sequence and the status of the last update.
- A read shows the state after the last processed command. It is used to read start values and to
wait for `COMMITTED`, never to decide something that must be exact. Those decisions are made by
the engine while it processes a command (D4).
- While an update is `APPLYING`, the receiver reads the `QueryService` every few seconds until the
update is `COMMITTED`, and only then polls Hub again.

**D3. The `PolicyInPort` sends the chunks with the `BrokerClient`, one at a time.**
- The request's `partitionGroup` is the physical tenant, so the command reaches partition 1 of that
tenant.
- The receiver waits for the response to each chunk before it sends the next one. The engine sends
a response only after it has processed the command and committed the state. So after the
response to the last chunk, the `QueryService` already shows `APPLYING`.
- Backpressure, a missing partition leader and a timeout are retried. They say nothing about the
update itself.
- A chunk sent again for the same `updateId` and index is accepted, not rejected. After a timeout
the receiver does not know whether the chunk arrived, and a rejection would report a failure that
never happened.

**D4. On every start, the receiver aborts a pending update and lets the engine decide.**
- The receiver sends `ABORT` without first reading the state. The engine processes it in log order:
- a `PENDING` update becomes `ABORTED`, and its buffer is deleted;
- an `APPLYING` update is not aborted, and the receiver waits for `COMMITTED` (D2);
- with no open update, nothing happens, and the receiver polls Hub.
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

- **Gossip of the policy state between brokers.** Eventually consistent, and sent to every node.
  Only the leader of partition 1 needs the state, and that node can read it locally.
- **A `BrokerAdminRequest` to read the policy state.** A network request to a partition on the same
  node. It only helps if the receiver runs on another node. It can still be useful later for
  debugging.
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
- The engine keeps no REST client, and its processors do not change for the Hub connection.
- All communication with Hub stays in the receiver.
- Hub can receive the same report more than once, and must ignore repeats.
- After a failover, a failed update is validated a second time before Hub learns the reason.
- Waiting for `COMMITTED` costs a read of the `QueryService` every few seconds.

## Open questions

- **Where the components live and who creates them.** Options:
  - All in `zeebe/broker`. The broker gets the Hub client dependency and its configuration.
  - A Spring module builds the receiver with the Hub client, URL and credentials from the Spring
    configuration, and gives the broker a factory per physical tenant through the
    `SpringBrokerBridge` (precedent: `getSchemaInitializer(physicalTenantId)`). The broker controls
    the lifecycle.
  - Outside the broker, for example in the gateway. It watches the topology to run where partition
    1 leads, and reads the state with a `BrokerAdminRequest`. It repeats what the
    `PartitionListener` gives for free.
- **Where the Hub URL and credentials are configured.** Depends on the previous question.
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

