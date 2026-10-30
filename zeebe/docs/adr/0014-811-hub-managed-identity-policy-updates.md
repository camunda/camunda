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
- The identity column families keep every version of an entity, with the policy version as the last
part of the key.
- Deletes are tombstones, and aggregates (an owner's authorization keys, the permissions) are copied on write.
- A read returns the latest version that is not newer than `max(current policy version, policyVersion of the command)`. Authorization checks carry no
version, so they see only the committed state

**D6. A dedicated version-switch commits a version on a partition.**
- After the last chunk, partition 1 distributes a committing record to mark the whole update as finished

**D7. The versioned layout of the column families is byte-compatible with the unversioned one at
version 0.**
- with using the policy version as last part of column family keys, the new versioned keys are byte-compatible to the existing non-versioned keys
- no rocksDb data migration should be necessary, and local mode can continue to use the unversioned layout (default version 0)

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

## Source

- [ADR for Identity Policy fan-out in broker (camunda/camunda#67237)](https://github.com/camunda/camunda/issues/67237)
- [Hub-managed identity data](../../../docs/zeebe/hub-identity.md)
- [Policy change-set model and
  `PolicyProjectionPort` (camunda-security-library ADR-0035, camunda/camunda-security-library#729)](https://github.com/camunda/camunda-security-library/pull/729)

