# Record agent execution in the engine rather than deriving it from connector state

**DRI**: Nico Korthout-Dittrich

**Status**: Accepted (8.10)

**Purpose**: Defines where AI agent execution data lives — that the engine records it, what the engine
stores versus merely passes through, and what it does and does not guarantee about that data.

**Audience**: Zeebe engineers working on agent records, exporters, or secondary storage; connector and
webapp engineers consuming agent data; and AI agents reasoning about where agent execution data comes
from.

## Context

Before this work, everything about a running AI agent lived in an `agentContext` process variable that
the AI Agent Connector wrote on job completion, spilling large conversations into the document store.
The data existed but could not be relied on. The variable is user-editable, so it cannot support an
audit claim. It only changes when a job completes, so a crashed or retried activation leaves nothing
behind. Documents expire on their TTL, so long-term explainability is lost. A connector configured
with a custom memory store exposes nothing at all. And every consumer — Operate, BI, monitoring,
reporting, testing tools — had to parse a connector-owned schema that no platform contract governed.

The user problem was never that the data did not exist; it was that finding and interpreting it was
impractical, and that nothing about it could be trusted. Agent execution is therefore recorded as
first-class engine records, so the record stream is the single pipeline every consumer reads,
independent of how the user configured the agent's memory.

## Decision

**D1. Agent execution is recorded as engine records, and the record stream is the pipeline.**
Agent definitions, agent instances, and agent history are engine record types, exported like every
other record. This is what makes the solution independent of the memory store: whether the connector
keeps its conversation in a variable, a document, or a store of its own, the same records reach
secondary storage, and every downstream consumer reads one schema the platform owns and versions.
Consumers get this for free rather than each building a connector-specific integration.

**D2. The engine records are an additional representation, not the agent's memory.**
`agentContext` keeps its existing role as the connector's runtime state, and the connector keeps
replaying from it. Agent history is the durable record of the context as it was constructed — not a
transcript of model calls, and not a source the agent reads back. No engine or webapp code path reads
history content back into execution. Keeping the two separate is what allowed this work to ship
without changing how any agent actually runs. Nothing forecloses moving the agent's memory into the
engine later, but that would mean storing content in the engine rather than passing it through (D3),
and query APIs that read it back with strong rather than eventual consistency. Both are hard
problems, and neither had to be solved to gain agent visibility, so they were deliberately left out —
see D3 of [0011](0011-810-agent-instance-written-by-agent-runtime.md).

**D3. Content passes through the engine; primary storage keeps only what command processing needs.**
A record carries its content far enough to reach secondary storage through the exporter, and primary
storage retains only the fields a later command has to read — enforced as an allow-list, so a field
added later is excluded unless someone opts it in. Agent message content and tool-call arguments are
arbitrarily large, and keeping large values under a single key in the engine's state store degrades
it. Since this data is write-only from the engine's point of view, storing it in primary state would
buy nothing for the cost.

**D4. Bulk payloads can be kept out of the records entirely, as document references.**
A content block may be a document reference rather than inline text or an object, in which case only
the reference travels through the record and the payload never enters the log at all. This is the
escape valve for genuinely large content, and it keeps the design's throughput cost bounded by the
writer's own choice. It is deliberately not the default: an offloaded payload becomes subject to the
document store's TTL, which is one of the problems this work exists to fix, so the trade-off is the
writer's to make per content block.

**D5. Authorization reuses process-definition permissions; there is no agent resource type.**
Each agent resource reuses the permission of the process resource it derives from, all granted on the
process definition: read-process-definition for an agent definition, read-process-instance for an
agent instance and its history, update-process-instance to write one — the same permissions that
already govern the variables this data replaces. Agent data is therefore no more sensitive than what
the same principal can already read, and a dedicated permission would suggest a protection boundary
that does not exist.

**D6. Retention follows the owning process instance.**
Agent instances and agent history are process-instance-dependent in secondary storage, so the existing
archiver and history-deletion jobs sweep them together with their root process instance, and backup
ordering places them after it. No agent-specific retention period, TTL, or lifecycle policy is
defined; agent indices inherit the platform default like every other process-instance-dependent index.

## Alternatives considered

- **Parse `agentContext` in the Operate front end.** Couples the front end to a connector-owned schema
  and to a variable name the user can reconfigure, inherits the variable's mutability, and still shows
  nothing once a document expires or a custom memory store is used.
- **Resolve the variable and document at read time in the Query API.** Moves the same coupling one
  layer down without removing it. It also loses data structurally: a user who overwrites the variable
  destroys earlier conversation content, and there is no support for custom memory stores.
- **A new exporter that parses `agentContext` into a structured schema.** Solves only document
  retention. Job retries, mutability, custom memory stores, connector coupling, and multi-engine
  deployments all remain unsolved, because the source of truth is still the variable.
- **The connector writes to secondary storage directly.** Bypasses the engine, so the data cannot
  participate in the log's ordering or the platform's multi-region and multi-engine story, and it
  makes the connector responsible for a storage schema it does not own.
- **Operate queries the connector directly.** Requires the connector to run a persistent store and to
  take on authentication and authorization, and gives up cross-process-instance querying entirely —
  agent activity could not be searched across instances.
- **Store references in records and the content elsewhere by default.** Inverts D4's trade-off: it
  bounds record size everywhere but makes every conversation depend on a second store's retention,
  reintroducing the document-TTL problem as the normal case instead of an opt-in.

## Consequences

- Log volume grows with agent conversation content. Primary state does not follow it, because content
  is not retained. D3 and D4 bound the cost, but the engine now carries data whose only purpose is
  observability.
- The platform owns the agent data schema and its compatibility. Connector-side changes no longer
  reach consumers implicitly, and schema evolution becomes a platform concern.
- Agent data reaches every consumer of the record stream — Operate, analytics, and any exporter — with
  no per-consumer integration work.
- Because history is never read back into execution, a writer that reports something other than what
  it actually sent produces a misleading record and the engine will not detect it. Fidelity is the
  writer's contract; see [0012](0012-810-agent-history-commit-under-job-lease.md).
- Query APIs over agent data are eventually consistent, like all secondary-storage reads.
- Agent history has no independent retention control. Operators who need a different retention for
  agent data than for process instance data have no way to express it today.

## Source

- [Document agent instance history as an ADR (camunda/camunda#60743)](https://github.com/camunda/camunda/issues/60743)
- [Explore solution space for AI Agent Visibility & Explainability (camunda/camunda#50339)](https://github.com/camunda/camunda/issues/50339)
- [AI Agent Visibility & Explainability deliverable tracker (camunda/camunda#51801)](https://github.com/camunda/camunda/issues/51801)
- [Real-Time Agent Visibility (product-hub #3462)](https://github.com/camunda/product-hub/issues/3462)
- [Epic AI Agent Visibility & Explainability | Kickoff and Solution](https://docs.google.com/document/d/1iMgG-ESVCuwpr-3vuOdPBthTEyvaGWSGtLy0HY79ywA) (internal)

