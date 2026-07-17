# Late Business ID assignment: one irreversible forward-only assignment on a running instance

**DRI**: Mustafa Dagher

**Status**: Accepted (8.10)

**Purpose**: Defines how a Business ID may be assigned once to an already-running root process
instance that has none, and what that assignment does — and does not — affect.

**Audience**: Zeebe engine engineers, gateway/API engineers, and AI agents working on Business ID or
process-instance commands.

## Context

A Business ID is a domain-specific identifier assigned to a process instance (e.g. an order number or
case reference). Today it can only be set when the instance is created — from the create command, a
message start, or a call activity ([ADR 0003](0003-810-business-id-call-activity-propagation.md)). An
instance that starts without one can never acquire one, even though the business entity it represents
(an order, a claim) often only becomes known after execution has begun.

The authoritative runtime copy of the Business ID sits on the process-scope element instance in
state. Jobs, user tasks, decision instances, and message subscriptions each snapshot it from that
element instance at their own creation time. A root instance's Business ID is also indexed for
uniqueness in the `PROCESS_INSTANCE_KEY_BY_BUSINESS_ID` column family, inserted on activation and
removed on completion/termination regardless of whether the uniqueness toggle
(`EngineConfiguration#isBusinessIdUniquenessEnabled`, default off) is on.

The outcome: a new engine command assigns the Business ID once to a running root instance that has
none, but only while uniqueness validation is disabled. The update lands on the process-scope
element-instance record, so propagation to future artifacts falls out of the existing
snapshot-at-creation reads; nothing already created is enriched.

## Decision

**D1. A dedicated value type, record, and intents model the command.** Add
`PROCESS_INSTANCE_BUSINESS_ID` with a `ProcessInstanceBusinessIdRecord` carrying the process instance
key, the Business ID, and tenant information, and an intent with an `ASSIGN` command and an
`ASSIGNED` event. This follows the process-instance migration template rather than overloading
`ProcessInstanceIntent`, keeping the command's lifecycle, authorization, and exporter handling
isolated.

**D2. Assignment is single, irreversible, and only to an instance that has none.** The command is
rejected when the instance already carries a Business ID. There is no unset and no reassignment.

**D3. Any instance that already carries a Business ID is rejected, even for the identical value.**
Assigning to an instance that already has a Business ID is rejected regardless of whether the value
matches or differs. An identical re-send is *not* treated as a silent no-op: the engine requires
every accepted command to produce exactly one follow-up record, so acknowledging a retry without an
`ASSIGNED` event would leave the command with no follow-up record on the log. Clients therefore treat
the `INVALID_STATE` rejection of a retry as "already assigned" rather than relying on idempotent
success.

**D4. Only root process instances are ever eligible; call-activity children are never eligible.** The
uniqueness index is a root-instance concern, and children have their own Business ID story via
[ADR 0003](0003-810-business-id-call-activity-propagation.md). Rejecting children is a permanent
design constraint, not an iteration limit: a command targeting a call-activity child is always
rejected.

**D5. The command is only available while Business ID uniqueness is disabled.** It is rejected when
`businessIdUniquenessEnabled` is on, so late assignment never participates in the cross-partition
uniqueness machinery of
[ADR 0001](0001-810-message-correlation-business-id-cross-partition.md) and stays a purely local
operation.

**D6. The value must pass the existing `BusinessIdValidator`.** The assigned value is validated by the
single existing owner of the rules (max length 256). Any extra rule late assignment needs is added
there, not duplicated.

**D7. The `ASSIGNED` applier updates the element-instance record and inserts the uniqueness index.**
The applier sets the Business ID on the process-scope element-instance record and inserts the
`PROCESS_INSTANCE_KEY_BY_BUSINESS_ID` mapping for the root instance. The record update is what makes
forward propagation (D8) work and is also what the existing completion/termination appliers read when
they delete the index entry, preserving the invariant that the index is maintained regardless of the
uniqueness toggle.

**D8. Propagation is forward-only.** Because artifacts snapshot the Business ID from the process-scope
element instance at their creation time, only artifacts created after the `ASSIGNED` event carry the
value: future jobs, user tasks, decision instances, message subscriptions, and call-activity children.
Pre-existing artifacts keep an empty Business ID, and there is no backfill in secondary storage.

**D9. Authorization reuses `UPDATE_PROCESS_INSTANCE` on the process definition.** The command checks
the same permission the migration processor checks, via the same authorization behavior, keeping late
assignment consistent with the closest existing instance-update command.

**D10. Precondition failures map to a fixed rejection contract.**

|                               Failure                               |          Rejection           |
|---------------------------------------------------------------------|------------------------------|
| Process instance does not exist / wrong tenant                      | `NOT_FOUND`                  |
| Target is a call-activity child (D4)                                | `INVALID_STATE`              |
| Business ID uniqueness enabled (D5)                                 | `INVALID_STATE`              |
| Instance already has a Business ID, same or different value (D2/D3) | `INVALID_STATE`              |
| Value fails `BusinessIdValidator` (D6)                              | `INVALID_ARGUMENT`           |
| Caller not authorized (D9)                                          | `UNAUTHORIZED` / `FORBIDDEN` |

**D11. Three API surfaces expose the assignment.** A dedicated
`POST /process-instances/{processInstanceKey}/business-id-assignment` endpoint (action-noun
convention, alongside `…/cancellation`, `…/migration`, `…/modification`), a matching
`AssignProcessInstanceBusinessId` gRPC RPC, and a new optional Business ID field on the `CompleteJob`
command (the gRPC `CompleteJobRequest` and its REST equivalent).

**D12. Assignment via job completion is atomic.** When the `CompleteJob` Business ID field is set, the
same preconditions (D2–D6, D9) are enforced and, on any failure, the entire `COMPLETE` command is
rejected — the job is not completed. A silent partial success, where the job completes but the
Business ID is never assigned, is disallowed. The precondition logic is shared with the standalone
`ASSIGN` command rather than duplicated.

## Alternatives considered

- **New intents on `ProcessInstanceIntent`.** Rejected in favor of a dedicated value type (D1): a
  separate type keeps the command's lifecycle, authorization, and exporter handling isolated and
  mirrors the migration/modification precedent.
- **Non-atomic complete-with-assignment.** Completing the job and failing only the assignment was
  rejected (D12): a job that completes while its Business ID silently fails to assign is a support and
  observability hazard.
- **Retroactive enrichment of existing artifacts.** Rejected (D8): it would require rewriting
  historical records across primary and secondary storage with no clear ownership of the change event,
  and contradicts the snapshot-at-creation model the engine already relies on.

## Consequences

- Forward propagation needs no changes to artifact-creation read paths that already read the Business
  ID from the process-scope element instance; any path reading a copied record value instead must be
  aligned with that lookup.
- A late-assigned root instance populates the uniqueness index, so its completion/termination cleanup
  works unchanged — the observable proof that D7's record update is correct.
- Observers see a mixed instance: artifacts created before the assignment carry no Business ID, those
  created after carry it. This is intended and must be surfaced clearly in user-facing docs.
- Because the feature is gated on uniqueness being disabled (D5), two instances can share a
  late-assigned Business ID; that is an accepted property of the uniqueness-disabled configuration,
  not a regression.

## Source

- [Late Business ID assignment for active process instances](https://github.com/camunda/camunda/issues/51692)
- [PDP epic: Business ID Visibility and Filtering](https://github.com/camunda/product-hub/issues/3436) (internal)

