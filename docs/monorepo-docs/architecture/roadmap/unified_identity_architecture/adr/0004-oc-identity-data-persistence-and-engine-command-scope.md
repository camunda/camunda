---
status: Accepted
---

# ADR-0004: Identity data persistence in the Orchestration Cluster

## Status

Accepted

## Context

After the OC Security Gateway Framework receives and applies a policy payload (`POLICY_SNAPSHOT`
or `POLICY_DIFF`), the resulting identity state (tenants, roles, groups, mapping rules,
authorizations — including their `scope_type`/`scope_id`) must be persisted in the OC so it can
be used for two distinct authorization purposes:

- **Command authorization (primary storage — RocksDB).** When a user or worker submits a command
  (e.g. create process instance, complete task), the Security Engine Framework inside the engine
  checks the identity state in primary storage to decide whether the command is authorized. Primary
  storage is the authoritative source for execution-time authorization decisions.
- **Query authorization (secondary storage — ES/OS/RDBMS).** When Operate, Tasklist, or the Admin
  UI queries data (e.g. list process instances, list tasks), the OC Security Gateway Framework
  applies authorization filters against the identity state held in secondary storage. Secondary
  storage is the authoritative source for read/query authorization decisions.

Both storage layers therefore need a consistent and up-to-date view of the identity state.
Neither can be omitted: removing primary storage breaks command authorization in the engine;
removing secondary storage breaks query authorization in the OC layer.

Two persistence paths are possible.

### Option 1 — OC Security Gateway Framework writes directly to secondary storage

The OC SGF writes identity state changes directly to secondary storage (ES/OS/RDBMS) after
applying a received policy payload, bypassing the engine and the exporter entirely.

**Problems identified:**

- **New write path, new consistency risks.** Primary storage (RocksDB, used for engine-level
  authorization) and secondary storage (used for query) would be written by two different paths.
  Keeping them consistent — especially after failures or re-applies — requires additional
  coordination logic.
- **Schema ownership.** The OC SGF would need to own and maintain secondary storage schemas for
  identity entities, duplicating schema management that currently lives in the engine/exporter
  layer.
- **Reset and re-apply.** Applying a `POLICY_SNAPSHOT` again must reset both primary and secondary
  storage to a consistent baseline. With two independent write paths, this is harder to make
  atomic and observable.
- **Secondary storage without primary.** The engine still needs identity state in RocksDB for
  engine-level authorization decisions. This means the OC SGF must also trigger engine commands to
  populate primary storage, resulting in two distinct write paths anyway.

### Option 2 — Route through engine commands and exporter (extend existing flow)

The OC SGF forwards identity state changes as commands to the engine (via `EngineCommandPort`).
The engine's Security Engine Framework processes them and persists the state in primary storage
(RocksDB). The existing exporter then picks up the identity records and writes them to secondary
storage (ES/OS/RDBMS), preserving the full flow as it exists today.

To make this work correctly, the engine commands must carry the full scope metadata
(`scope_type`, `scope_id`), so that:

- The Security Engine Framework can persist scope-aware state in RocksDB.
- The exporter can write scope-aware records to secondary storage.
- Engine-level authorization decisions can apply the correct precedence
  (engine-scoped > tenant-scoped > ALL).

**Consequences:**

- A single consistent write path: primary and secondary storage are both populated via the
  engine/exporter flow, as today.
- The ES/OS/RDBMS schema must be extended to include scope fields on authorization records.
  This schema extension is required regardless of which option is chosen.
- The Security Engine Framework takes ownership of scope-aware persistence and authorization
  evaluation inside the engine.
- The exporter must be extended to handle scoped identity records.
- Reset semantics (re-applying a `POLICY_SNAPSHOT`) follow the same engine command path and can
  be made idempotent at the engine level.

## Decision

Choose **Option 2**.

Identity data persistence in the OC follows the existing engine command + exporter flow:

- The OC SGF forwards identity updates via `EngineCommandPort`.
- The Security Engine Framework persists scope-aware state in primary storage (RocksDB).
- The exporter writes scoped identity records to secondary storage (ES/OS/RDBMS).

Option 1 is rejected due to the additional consistency and operational complexity of maintaining a second direct-write path.

Follow-up design work remains, but does not change the selected direction:

- Exact schema extensions required for ES/OS/RDBMS to store scope metadata on authorization records.
- Whether the originating `PolicyVersion` reference should also be stored in primary storage, or only the effective identity state.
- Reset and re-apply semantics: how a full `POLICY_SNAPSHOT` re-apply atomically resets both primary and secondary storage identity state.

## Options considered

### Option 1 — OC Security Gateway Framework writes directly to secondary storage

- The OC SGF writes identity state changes directly to secondary storage (ES/OS/RDBMS) after applying a received policy payload, bypassing the engine and the exporter.
- This introduces a second write path and raises consistency, schema ownership, and reset/re-apply complexity concerns.
- Not chosen.

### Option 2 — Route through engine commands and exporter (chosen)

- The OC SGF forwards identity state changes as commands to the engine (via `EngineCommandPort`).
- The Security Engine Framework persists scope-aware state in primary storage (RocksDB).
- The exporter writes scoped records to secondary storage (ES/OS/RDBMS), preserving the existing end-to-end flow.
- Chosen for a single consistent write path.
