# Describe restored tenant histories with immutable manifests

**DRI**: Event Plane and cluster configuration maintainers

**Status**: Proposed (8.11)

**Purpose**: Define backup provenance and the publication boundary for tenant history changes.

**Audience**: Backup, restore, cluster configuration and Event Plane maintainers.

## Context

Restores reuse journal indexes and record positions. Consumers need the shared prefix between their
previous history and the restored one. A backup can come from an older history than the one being
replaced. The Event Plane must remain outside backup, restore and publishing's critical path.

## Decision

**D1. A history is an inherited prefix plus an append-only suffix.**
A manifest identifies one physical tenant history by UUID and records every restored partition's
source history, checkpoint index and checkpoint position. It is immutable and survives data retention.

**D2. Restore preparation precedes history activation and processing.**
One coordinator owns the operation identity and fixed timestamp. All partitions remain stopped until
inherited journal publication and durable installation of the shared history have completed. An
ordinary object PUT then publishes the complete manifest. There are no conditional writes.

**D3. Current-history selection is best effort.**
The Event Plane selects the greatest fixed timestamp and canonical UUID for the tenant. Clock errors
may select an older history indefinitely. Ancestry and transition reporting remain exact; ordinary
uploads never affect selection. Consumers may catch up independently.

**D4. Backups preserve provenance; legacy ancestry is explicit.**
A backup records its source history. Restore reports that identity and the actual journal checkpoint.
A legacy backup remains restorable, but cannot activate a history claiming known ancestry without an
explicit migration policy.

## Alternatives considered

- **Stamp every Raft entry.** Adds journal-format work to answer ancestry that restore already knows.
- **Globally ordered activation.** Requires coordination beyond the accepted best-effort selection
  contract; fixed timestamps keep the restore metadata small.

## Consequences

- Normal publishing starts after the prepared checkpoint and needs no ancestry map or per-entry stamp.
- The binary segment format, archive preparation and replicated upload floor remain separate work.
- Native backup success alone is not proof of archive publication. Runtime activation stays unwired
  until preparation has a real implementation.
- Shared segments require backup-aware retention and rollup; history selection does not authorize deletion.

## Source

- [Event Plane design draft](https://github.com/camunda/event-plane/pull/88).
- [Manifest contract](https://github.com/camunda/event-plane/blob/1706be87f575173c10e5071e2a67ae2ee5f4ab52/docs/14-history-manifests.md)
  and [JSON schema](https://github.com/camunda/event-plane/blob/1706be87f575173c10e5071e2a67ae2ee5f4ab52/api/src/main/resources/history/history-manifest-v1.schema.json).
- [Implementation boundary](../../../zeebe/backup/docs/history-manifests.md).

