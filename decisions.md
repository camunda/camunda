# Agent Instance — Design Decisions

## 1. Metric fields carry deltas on commands, aggregates on events

**Date**: 2026-04-24
**Status**: Accepted
**Context**: The connector needs to report token consumption incrementally (per
LLM call), and the engine must maintain running totals. The BI team will also
consume this data for analysis.

**Decision**: The `inputTokens`, `outputTokens`, and `modelCalls` fields on an
`AgentInstanceRecord` have different semantics depending on the record type:

- **UPDATE command**: fields carry *deltas* (e.g. "this call used 500 input
  tokens").
- **UPDATED event**: fields carry *aggregated totals* after the engine applies
  the delta to its state.

This means the engine processor adds the command's values to the current state
and writes the new totals on the event.

**Rationale**: This design keeps the record schema simple (no separate delta
fields) while still allowing the engine to aggregate. It follows the existing
Zeebe convention where command records and event records share the same value
type but carry different semantic meaning — the processor transforms the
command into the event.

**Trade-off**: Consumers that want to see both incremental updates *and*
aggregates need to look at both the UPDATE command records and the UPDATED
event records. The BI team may want both views (e.g. per-call cost breakdown
vs. total cost per agent run). This is possible because both command and event
records are exported to secondary storage, but it requires the BI team to be
aware of the dual semantics.

**Open question for BI team**: Do they need access to the per-call deltas
(command records), the running totals (event records), or both? If both are
needed, the current design supports it — the BI team queries UPDATE commands
for deltas and UPDATED events for totals. If only totals are needed, they can
ignore command records entirely. Discuss with the BI team to confirm their
requirements before finalizing the exporter configuration.

## 2. Limit fields are creation-time immutable metadata

**Date**: 2026-04-24
**Status**: Accepted
**Context**: The connector sets limits when creating an agent instance. These
do not change over the lifetime of the agent instance.

**Decision**: Three limit fields are included on the record:

- `maxTokens` — combined input + output token limit
- `maxModelCalls` — maximum number of LLM calls
- `maxToolCalls` — maximum number of tool invocations

All default to `-1` (meaning not set), following the codebase convention
where `-1` indicates an optional value that was not configured. They are set
once on the CREATE command and carried unchanged on all subsequent events.
