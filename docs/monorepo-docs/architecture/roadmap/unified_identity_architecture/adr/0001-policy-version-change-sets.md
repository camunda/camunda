---
status: Proposed
---

# ADR-0001: Link PolicyVersion with policy data via versioned change sets

## Status

Proposed

## Context

- Hub propagates policy updates to 1-N existing Orchestration Clusters.
- The primary requirement is current-state correctness, not historical reconstruction.
- New OC joins are expected to be rare and only require the current state.

## Decision (Option 1)

- Use `PolicyVersion` as a delivery-neutral cluster-scoped commit (`version_number`, `base_version`).
- Store snapshot vs diff only on `OutboxEvent.event_type`.
- Keep stable entity IDs.
- Persist full-entity revisions (`EntityRevision`) for each change (including tombstones for deletes).
- Link ordered changes via `PolicyVersionChange`.
- Build `POLICY_DIFF` from the target version's change rows.
- Build `POLICY_SNAPSHOT` from latest non-deleted revisions per entity/scope up to a target version.

## Alternatives Considered

### Option 2 – Event sourcing style (full-resource events)

- Pros: strong audit trail and replay capabilities.
- Cons: higher complexity and operational overhead.
- Not chosen because replay/history is not a core requirement.

### Option 3 – Full materialized snapshot per version

- Pros: simplest read/bootstrap behavior.
- Cons: high write/storage amplification due to full duplication each version, and high network traffic when full resources are repeatedly posted to OCs.
- Not chosen because it is unnecessary for frequent incremental updates.

## Consequences

- Efficient incremental propagation for the common case (existing OCs).
- Idempotent apply behavior with straightforward gap handling.
- No patch/merge ambiguity because each change carries a full resource payload.

