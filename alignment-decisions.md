# Agent Instance Record — Alignment Decisions

Decisions made while aligning the engine record schema with the connector
tracing design (camunda/connectors#7029).

## 1. Add `elementId` field to match CreateAgentExecutionRequest

**Date**: 2026-04-24
**Status**: Accepted
**Context**: The connector tracing design's `CreateAgentExecutionRequest`
includes an `elementId` field (the BPMN element ID of the Ad-Hoc Sub-Process).
This is the static model-level identifier, as opposed to `elementInstanceKey`
which is the runtime key. Many other Zeebe records carry `elementId`
(ProcessInstanceRecord, JobRecord, IncidentRecord, etc.).

**Decision**: Add `getElementId()` to `AgentInstanceRecordValue` and
`elementId` (String, default `""`) to `AgentInstanceRecord`. This field is set
once at creation and carried unchanged on subsequent events.

**Rationale**: `elementId` is needed for definition-level aggregation — the
tracing design's metrics at process-definition scope (#26, #33, #35, #36) group
by element ID to compare behavior across instances of the same BPMN element. It
also enables joining agent instances to their BPMN definition elements for
tooling and visualization. Adding it follows the established convention in the
codebase.

## 2. Add `toolCalls` metric field

**Date**: 2026-04-24
**Status**: Accepted
**Context**: The tracing design defines "tool call count" as metric #12:
`SUM(TOOL_CALLS_EMITTED.toolCalls.size())`. The engine record already carries
three aggregate metrics (inputTokens, outputTokens, modelCalls) but was missing
tool calls.

**Decision**: Add `getToolCalls()` to `AgentInstanceRecordValue` and
`toolCalls` (long, default 0) to `AgentInstanceRecord`. Follows the same
delta/aggregate semantics as the other metric fields: UPDATE commands carry
deltas, UPDATED events carry aggregated totals.

**Rationale**: Tool call count is a key operational metric for agent visibility.
The tracing design tracks it at both agent scope (#12) and process-definition
scope (#33). Having it as an aggregate on the engine record alongside the other
metrics keeps the record self-consistent and allows the engine to expose it
without requiring consumers to replay the fine-grained event stream.

## 3. Keep `agentInstanceKey` naming (do not rename to agentExecutionKey)

**Date**: 2026-04-24
**Status**: Accepted
**Context**: The connector tracing design uses `agentExecutionKey` for the
server-assigned identifier. Our engine record uses `agentInstanceKey`. These
refer to the same concept.

**Decision**: Keep `agentInstanceKey` in the engine protocol. The connector
maps between naming conventions at the boundary.

**Rationale**: The Zeebe engine consistently uses "instance" terminology
(processInstanceKey, elementInstanceKey, etc.). Renaming to "execution" would
break this convention for one record type. The connector side is free to use its
own terminology. The tracing design already bridges this gap — its
`CreateAgentExecutionRequest` returns an `agentExecutionKey` which is the same
Long value as the engine's `agentInstanceKey`.

## 4. Keep `provider` field naming (do not rename to providerType)

**Date**: 2026-04-24
**Status**: Accepted
**Context**: The tracing design uses `ProviderInfo(String type, String model)`
where `type` holds the provider identifier (e.g., "openai", "anthropic"). Our
record has a field named `provider` that holds the same data.

**Decision**: Keep the field named `provider`. It is more descriptive than the
generic `type` and aligns with other engine records that use descriptive field
names.

## 5. Keep current status enum (IDLE, THINKING, CALLING_TOOL)

**Date**: 2026-04-24
**Status**: Accepted
**Context**: The tracing design derives a richer status model from the event
stream (initializing, discovering, ready/idle, reasoning, calling_tool,
responded, failed). Our engine record has three statuses.

**Decision**: Keep the current enum. The engine status represents the
BPMN-visible operational state. The tracing design explicitly states "Execution
activity is determined by BPMN state, not by tracing events" (design.md section
4). Fine-grained status derivation belongs to the tracing layer, not the engine
record.

**Note**: The `initializing` and `discovering` states in the tracing design
occur inside the connector before the engine's CREATE command is sent, so they
are not applicable to the engine record. The `responded` and `failed` states
map to BPMN lifecycle events (AHSP completion, incidents) rather than the agent
instance's operational status.

## 6. Engine record limits are ahead of the tracing design

**Date**: 2026-04-24
**Status**: Noted
**Context**: Our record has `maxTokens`, `maxModelCalls`, and `maxToolCalls`.
The tracing design's `LimitsInfo` currently only has `maxModelCalls`, with
`maxTokens` noted as planned (design.md section 15.1) and `maxToolCalls` not
mentioned.

**Decision**: No change to the engine record — it already has the right fields.
The connector tracing design will need to expand `LimitsInfo` to carry
`maxTokens` and `maxToolCalls` when creating the execution entity. This is
additive on the connector side and does not require engine changes.

## 7. Concern: tracing event volume vs. RAFT commit cost

**Date**: 2026-04-24
**Status**: Open concern
**Context**: The tracing design's API (`POST /agent-executions/{key}/events`)
is implemented by appending commands to Zeebe. Every command goes through full
RAFT consensus including replication. The design document does not acknowledge
this cost — it treats the backend as an opaque HTTP endpoint with cheap writes
(section 10.2: "Best-effort — failures are logged but do not fail the job",
section 10.3: "stateless transport abstraction").

The design buffers events and flushes them at 3 points per iteration (before
LLM call, after iteration completion, job completion safety net). A typical
2-iteration agent run with 2 tool calls produces roughly 10-12 individual
events across these flush points.

**Concern**: If each flush maps to **one Zeebe command** carrying the event
batch as payload, that's 2-3 additional RAFT commits per iteration — comparable
to what Zeebe already does for the job lifecycle and likely acceptable.

If each event in a flush batch becomes **its own Zeebe command**, the cost
scales with the number of events. A single iteration with 2 tool results
generates 6-7 commands. An agent calling 5 tools in one turn produces ~10
commands for that iteration alone. This is too much additional load on the
partition.

**Recommendation**: The connector team should ensure that each flush maps to
exactly one Zeebe command, with the batch of events carried as payload within
that single command. The design's existing flush-point model (3 flushes per
iteration) is a reasonable upper bound if this batching is enforced. The number
of events within a batch should not affect the number of RAFT commits.
