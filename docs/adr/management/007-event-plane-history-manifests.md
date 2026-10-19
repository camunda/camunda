# Each life of a physical tenant is a history

**DRI**: Lena Schönburg

**Status**: Proposed (8.11)

**Deciders**
- Lena Schönburg

**Purpose**: Decide what the cluster records so that the Event Plane can tell a physical tenant's
histories apart after a restore or a purge.

**Audience**: Engineers working on backup, restore, purge and the dynamic configuration.

## Context

The Event Plane keeps a copy of every partition's journal in object storage. A restore writes the
same Raft indexes and record positions again, with other records, and its backup can come from an
older history than the one it replaces. Consumers need to know which of the records they read
still count. The Event Plane's side is
[History manifests](https://github.com/camunda/event-plane/blob/9302e3892d5f7d94b016b33821e1322daed29914/docs/14-history-manifests.md),
proposed in [camunda/event-plane#88](https://github.com/camunda/event-plane/pull/88).

## Decision

**D1. Each life of a physical tenant is a history, named by a random id.** A new tenant, a purge
and a restore each start one; leader changes keep it. For each partition, a history takes over
another history's journal up to and including a checkpoint's entry, or nothing.

**D2. Every broker has the history in its dynamic configuration before the tenant's partitions
start again.** The operation makes up the id and its creation time once, and keeps them, so that
retries and every broker use the same. The configuration also holds what the history takes over in
each partition: its manifest. Processing then goes on without waiting for object storage or the
Event Plane; the Event Plane's publishers write the manifest to their store before their first
upload.

**D3. A backup names the history it was taken in, and a restore reports where each partition's
journal ends**: the checkpoint's index and position, and the history of the last backup, which
the journal up to there belongs to. Backups from before histories name none.

**D4. The incarnation number changes before any partition processes the restored data**, where
the history changes, no longer after the restore has finished.

## Alternatives considered

- **Stamp the history on every Raft entry.** Changes the journal format to tell what the restore
  already knows.
- **Wait until object storage has the manifest, and the entries the backup's history hadn't
  uploaded, before processing.** Puts object storage on the restore's critical path. Once backups
  are made of the Event Plane's segments, those entries are in the store anyway.

## Consequences

- Done here: backups carry an optional history id, a restore reports where each partition's journal
  ends, and the incarnation number changes before processing.
- Still to do: keeping the history and its manifest in the configuration, for new tenants, purges,
  online restores and both kinds of offline restore; filling in the history id when taking a
  backup; and the publisher.
- A restore from a backup that names no history needs a rule for what it takes over before the
  Event Plane can follow it.
