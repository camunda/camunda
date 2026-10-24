# Apply Hub policy updates as versioned identity state with a distributed version switch

**DRI**: Christian Thiel

**Status**: Proposed (8.10, proof of concept)

**Purpose**: Defines how the engine receives a policy update from Hub, applies it beyond the record
size limit, distributes it to all partitions and makes it visible all at once, without a migration
of existing identity data.

**Audience**: Zeebe engineers working on identity processing, command distribution or engine state;
engineers building the Hub client; and AI agents reasoning about how identity data reaches the
engine.

## Context

In hub-managed mode, Hub is the source of an OC's identity data: tenants, roles, groups, mapping
rules, memberships and authorizations. Hub sends it as a `PolicyChangeSet` from the
camunda-security-library: a `SNAPSHOT` or `DELTA` for one policy version, with a fixed apply order.
A change set can be tens of megabytes, while a record and the event batch of one command are each
limited to 4 MB. The update must also become visible all at once: an authorization check must never
see half of a policy version, on any partition. In local mode the OC manages its identity data as
before, and existing clusters hold that data in the unversioned state layout.

The resulting architecture is described in [Hub-managed identity data](../../../docs/zeebe/hub-identity.md).
In short: partition 1 of each physical tenant receives the change set, splits it into chunks,
buffers them, applies them as ordinary identity commands at the update's policy version, and
finally switches every partition to the new version.

## Decision

**D1. Partition 1 coordinates every policy update of its physical tenant.**
The Hub client runs on the leader of partition 1 and hands each change set to `PolicyUpdateInPort`,
which rejects the update unless the broker leads partition 1. Policy update records are processed
only on partition 1; the other partitions receive identity commands and the version switch. One
coordinator gives the update a single order without cross-partition agreement.

**D2. The broker splits a change set into chunks of whole entities in apply order.**
`PolicyChangeSetSplitter` fills each chunk up to a byte budget (1 MiB), estimated from the msgpack
length plus a margin. An entity is never split, and chunks never mix apply phases, so the chunk
index is also the apply order. A single entity is assumed to stay below the 4 MB limit.

**D3. All chunks are buffered before any of them is applied.**
Chunks are stored in RocksDB on partition 1, keyed by the update's `envelopeId`, and the first
`APPLY` follows the last received chunk. A partially received update therefore never changes
identity data, and chunks may arrive in any order.

**D4. Each chunk is applied by one `APPLY` command, as existing identity commands.**
A chunk becomes the existing tenant, role, group, mapping rule, membership and authorization
commands, written as follow-up commands at the update's policy version. One chunk per command keeps
each command's events within the budget. Reusing the identity processors reuses their validation,
cascades and distribution, so no new identity intents exist. The commands are internal, without
request metadata, so they bypass the requesting user's authorization check.

**D5. The update reaches the other partitions through command distribution on the `IDENTITY` queue.**
The identity processors distribute every command, as they do for the identity APIs. The queue
delivers in order to each partition, which D7 relies on.

**D6. Identity state is versioned, and reads only see the committed version.**
The identity column families keep every version of an entity, with the policy version as the last
part of the key. Deletes are tombstones, and aggregates (an owner's authorization keys, the
permissions) are copied on write. A read returns the latest version that is not newer than
`max(current policy version, policyVersion of the command)`. Authorization checks carry no
version, so they see only the committed state; the update's own commands carry its version, so the
update reads its own writes.

**D7. `VERSION_SWITCHED` commits a version on a partition.**
After the last chunk, partition 1 writes `SWITCH_VERSION`, switches itself (`VERSION_SWITCHED`)
and distributes the command on the `IDENTITY` queue. Because the update's identity commands were
distributed on that queue first, each partition switches only after it applied the whole update. A
partition that is already at that version or newer rejects the switch and still acknowledges it.
Partitions switch independently; the short window in which partitions authorize with different
versions is accepted.

**D8. The versioned layout is byte-compatible with the unversioned one at version 0.**
Keys use `DbVersionedKey`, which writes no suffix for version 0, so a version-0 key equals the key
without a version. New value properties have defaults, and a membership row stores `0xFF` for
`ACTIVE`, the byte `DbNil` wrote before. Existing data reads as version 0 without a migration.

**D9. The mode follows from the data, not from engine configuration.**
Local mode writes at version 0 and never switches; hub-managed mode writes at version 1 or more.
Both run on the same state and processors. Changing the mode of a running physical tenant is not
covered.

**D10. A newer version supersedes an open update.**
Only one update is open at a time. A chunk of a newer version aborts an open older update
(`ABORTED`); a chunk of the current version, an older version, or a version not newer than the open
update is rejected.

## New records and intents

`ValueType.POLICY_UPDATE` with the record value `PolicyUpdateRecordValue`: `envelopeId`, `kind`
(`SNAPSHOT`, `DELTA`), `policyVersion`, `chunkIndex`, `chunkCount`, the upserted entities, the
deletes, and `lastPage`. Memberships use `PolicyMembershipEntryValue`, deletes use
`PolicyDeleteEntryValue`.

|       Intent       |  Kind   |                        Meaning                         |
|--------------------|---------|--------------------------------------------------------|
| `APPEND_CHUNK`     | Command | Delivers one chunk to partition 1.                     |
| `CHUNK_APPENDED`   | Event   | The chunk is buffered.                                 |
| `APPLY`            | Command | Applies the next buffered chunk.                       |
| `APPLIED`          | Event   | The chunk is applied; `lastPage` marks the last one.   |
| `SWITCH_VERSION`   | Command | Switches a partition to the new version; distributed.  |
| `VERSION_SWITCHED` | Event   | The partition's current policy version is set.         |
| `ABORTED`          | Event   | An open update was superseded; its chunks are removed. |

The identity record values (`TenantRecordValue`, `RoleRecordValue`, `GroupRecordValue`,
`MappingRuleRecordValue`, `AuthorizationRecordValue`) gain an optional `policyVersion`, default 0.
New column families: `POLICY_VERSION`, `POLICY_UPDATE_ASSEMBLY`, `POLICY_UPDATE_CHUNK`.

## Alternatives considered

- **Write the version as a plain composite key suffix.** Keys written without a version can then not
  be read, and a version-0 write adds a second row next to the old one, so every identity column
  family would need a migration. D8 avoids that.
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
- Old versions and tombstones accumulate; nothing removes them yet.
- An aborted update that already applied chunks leaves rows that become visible with the next
  switched version.
- A `SNAPSHOT` is applied like a delta; removing entities it no longer lists is not implemented.
- Partitions switch independently, so they can authorize with different versions for a short time.

## Source

- [Hub-managed identity data](../../../docs/zeebe/hub-identity.md)
- [Policy change-set model and `PolicyProjectionPort` (camunda-security-library ADR-0035)](https://github.com/camunda/camunda-security-library/blob/feat/policy-projection-port/docs/adr/0035-policy-change-set-and-projection-port.md)

