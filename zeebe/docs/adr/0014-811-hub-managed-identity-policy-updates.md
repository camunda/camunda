# Apply Hub policy updates as versioned identity state with a distributed version switch

**DRI**: Christian Thiel

**Status**: Proposed (8.11)

**Purpose**: Defines how the engine receives a policy update from Hub, applies it beyond the record
size limit, distributes it to all partitions and makes it visible all at once, without a migration
of existing identity data.

**Audience**: Zeebe engineers working on identity processing, command distribution or engine state;
engineers building the Hub client; and AI agents reasoning about how identity data reaches the
engine.

## Context

In hub-managed mode, Hub is the source of an OC's identity data: tenants, roles, groups, mapping
rules, memberships and authorizations. Hub sends it as a PolicyChangeSet, the model proposed in
the hub-client: a delta for one or more sequential policy versions, with a fixed apply order.
A change set can be tens of megabytes, while a record and the event batch of one command are each
limited to 4 MB. The update must also become visible all at once: an authorization check must never
see half of a policy version, on any partition. In local mode the OC manages its identity data as
before, and existing clusters hold that data in the unversioned state layout.

The resulting architecture is described
in [Hub-managed identity data](../../../docs/zeebe/hub-identity.md).
In short: partition 1 of each physical tenant receives the change set, splits it into chunks,
buffers them, applies them as ordinary identity commands at the update's policy version, and
finally switches every partition to the new version.

## Decisions

**D1. Partition 1 coordinates every policy update of its physical tenant.**
- The Hub client runs on the leader of partition 1 and hands each change set to a cluster internal
transformer component, which maps the hub DTO to records
- it which rejects the update unless the broker leads partition 1. Policy update records are
processed only on partition 1; the other partitions receive identity commands and the version
switch.
- One coordinator gives the update a single order without cross-partition agreement.

**D2. The broker splits a change set into chunks of whole entities in apply order.**
- the transformer splits each chunk up to a configurable byte budget to avoid the 4MB record size
limit, estimated from the msgpack length plus a margin.
- An entity is never split, single entity is assumed to stay below the 4 MB limit.

**D3. All chunks are buffered before any of them is applied.**
- Chunks of one update are stored in RocksDB on partition 1. The update is only processed when all
chunks are received.

**D4. Use existing identity commands to apply identity updates.**
- A chunk is transformed into the existing tenant, role, group, mapping rule, membership and authorization
commands, written as follow-up commands at the update's policy version.
- Reusing the identity processors reuses their validation, cascades and distribution, so no new identity intents exist.
- The commands are internal, without request metadata, so they bypass the requesting user's authorization check.

**D5. Identity state is versioned, and reads only see the committed version.**
- The identity column families keep multiple versions of an entity, with the policy version as the last
part of the key.
- Deletes are tombstones. Lookup rows that combine several authorizations (an owner's
authorization keys, the permissions per owner and resource type) are copied to the new version
before they change.
- A read returns the latest version that is not newer than `max(current policy version, policyVersion of the command)`. Authorization checks carry no
version, so they see only the committed state

**D6. A dedicated version-switch commits a version on a partition.**
- After the last chunk is applied, partition 1 distributes a committing record to mark the whole update as finished
- After the version is switched, the policy update chunks are also deleted from the local buffer

**D7. The versioned layout of the column families is byte-compatible with the unversioned one at
version 0.**
- with using the policy version as last part of column family keys, the new versioned keys are byte-compatible to the existing non-versioned keys
- no rocksDb data migration should be necessary, and local mode can continue to use the unversioned layout (default version 0)

**D8. The Hub polling sequence is part of the replicated policy state.**
- The policy update record carries the Hub sequence that the change set was polled up to.
- The commit stores it in the `POLICY` state together with `policyId` and `policyVersion`, so the
polling cursor advances in the same step as the version.
- After a restart or leader change, the Hub client resumes polling from the stored sequence.

**D9. Validation happens in the policy update processor before any chunk is applied.**
- Before any chunk is applied, the update processor validates the whole update and only then starts processing.
- If validation fails, the whole update is `ABORTED` and all chunks are removed from the buffer.
- The Hub client receives the failure through `errorMessage` and can retry the update.

**D10. A policy update can be aborted externally while still buffering.**

- If a leader transition happens while the transformer is still sending chunks, the policy update
  may be stuck in the buffer forever because the missing chunks are never sent. The new leader can
  abort that pending update and start a new one.

## Alternatives considered

- **Separate column families for hub-managed mode, selected by a setting.** Leaves the local path
  untouched, but duplicates the identity state classes and makes the state depend on configuration,
  which every broker and every replay must then agree on.
- **Apply chunks as events instead of commands.** Would avoid the follow-up commands, but would
  re-implement the validation, cascades and distribution of the identity processors.
- **Apply chunks as they arrive.** Saves the buffer, but a partially received update would change
  identity data, and an abort could not undo it.

## Consequences

- A policy version becomes visible on each partition with one event, and authorization checks never
  see an update in progress.
- Existing clusters need no migration, and local mode keeps the previous layout and point reads.
- In hub-managed mode, reads seek the latest visible version instead of reading one row.
- Old versions and tombstones accumulate until a cleanup removes them.
- Partitions switch independently, so they can authorize with different versions for a short time.

## Open questions

- **Secondary storage.** The exporters see the identity records of a version as soon as they are
  written, not when the version is committed, so secondary storage shows uncommitted data and
  applies deletes early. The engine does not control secondary storage. Options:
  - RDBMS and Elasticsearch store the policy version with each identity row, and reads filter by the
    committed version, which the exporters take from the commit record.
  - The exporters hold back the identity records of a version until its commit, and write them then.
    That is not atomic for Elasticsearch.
  - Outsourced to https://github.com/camunda/camunda-security-library/issues/795
- **Pausing the polling until a version is applied.** The Hub client must not poll the next change
  set while an update is open, but the client runs outside the stream processor, and the engine
  processes the update asynchronously.
  - https://github.com/camunda/pdp-initiative-ea-identity--access-at-scale/issues/53
- **Hub-client reads current policyVersion and sequenceNo** How can this hub-client component get to
  know this sequence number to receive the correct updates?
  - https://github.com/camunda/pdp-initiative-ea-identity--access-at-scale/issues/52

## Source

- [ADR for Identity Policy fan-out in broker (camunda/camunda#67237)](https://github.com/camunda/camunda/issues/67237)
- [Hub-managed identity data](../../../docs/zeebe/hub-identity.md)
- [Policy change-set model and
  `PolicyProjectionPort` (camunda-security-library ADR-0035, camunda/camunda-security-library#729)](https://github.com/camunda/camunda-security-library/pull/729)

