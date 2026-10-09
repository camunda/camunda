# Declare agents in BPMN and derive agent definitions at deploy time

**DRI**: Nico Korthout-Dittrich

**Status**: Accepted (8.10)

**Purpose**: Defines what makes an element an agent — the BPMN marker, the deploy-time record derived
from it, its lifecycle, and why engine logic keys off it rather than off the job type.

**Audience**: Zeebe engineers working on deployment, agent records, or job processing; Modeler and
connector engineers; and AI agents reasoning about how the engine recognises an agent.

## Context

Camunda creates a process definition at deploy time but had no agent-level entity. There was nothing
for an API to return, nothing to aggregate over, and nothing that survived a process version update.
Tools that needed to know which process definitions contain an agent — the agentic control plane
first among them — had no reliable marker to filter on.

The engine's first answer to "is this agentic?" was a prefix match on the job type string, carried
over from 8.9 as an explicit stopgap. A job type is only resolved when a job is created, so it can
never tell a caller at deploy time that a process definition contains an agent — which is exactly
what the agentic control plane needs to know. It fell short at runtime too: an agent driven by a
user-owned job worker runs under its own job type, so it never matched the prefix. Both the job type
and the element template ID are user-editable, so neither can carry engine-internal meaning.

An agent is therefore declared in the model and turned into a real entity at deploy time. That
entity is the deploy-time half of the agent records introduced in
[0010](0010-810-agent-execution-in-engine-records.md).

## Decision

**D1. An element is an agent only if the model marks it with a `zeebe:agentDefinition` extension
element.** The marker sits on the hosting element — a service task or an ad-hoc sub-process — and is
the sole signal. Making the declaration explicit gives modellers a deliberate opt-in and gives the
platform a marker it owns, rather than inferring agenthood from a convention any user can change.

**D2. Deployment derives one agent definition per marked element, bound to that process definition
version.** Agent definitions live inside the `.bpmn` resource, so they are created while transforming
the process, mirroring how a DMN decision is handled inside its DRG: one resource, several
sub-definitions, rather than a resource type of its own. Their lifecycle follows the process
definition — a new version mints new agent definitions, and deleting the process definition deletes
them. The element was already the agent in practice before this decision, because the agent runtime
operates on the job the engine creates for that element; describing the agent in a separate resource
would only decouple the element from the resource that describes it. Nothing about an agent
definition changes independently of the model it came from either, so its own deployment lifecycle
would add a versioning surface with nothing to version.

**D3. `agentType` records what kind of agent this is, from a fixed set the whole product shares.**
The values name the product's own patterns — an AI Agent Sub-process, an AI Agent Task, and an
External Agent, whose loop runs in a harness outside Camunda. The decision is about agent semantics,
not about who operates the runtime. The two native patterns are distinct ways to model an agent, and
that distinction has to hold consistently in the Modeler, the engine, Operate, Optimize, the API, the
agent registry, and the documentation. A fixed set also keeps filtering and grouping in the
agent-definition API predictable, and lets an invalid value be rejected rather than silently produce
an agent that no expected filter finds. Naming external agents explicitly is what lets Camunda
register their instances, history, metrics, and status alongside native ones, instead of leaving an
agent built on LangGraph, Bedrock AgentCore, or custom Python looking like an unclassified service
task. Who owns the agent instance lifecycle is a separate question, planned as an `implementation`
attribute; folding it into `agentType` would blur the two.

**D4. Engine-internal agent logic keys off the resolved agent definition, not off the job type or the
element template.** Whether a job is agentic is decided by whether its element has an agent
definition, and that governs agent definition resolution, the agent instance lifecycle, and the
history commit alike. Both alternatives are user-editable strings, so basing behaviour on them makes
correctness depend on values the user may change for unrelated reasons — which is exactly how
external agents came to silently lose their history. That was a bug (camunda/camunda#60554), and
keying off the resolved agent definition is what fixed it. Audit-log attribution is the one exception
and still consults the job type for backward compatibility; see Consequences.

**D5. No agent definition, no agent instance.** Creating an agent instance for an element that carries
no marker is rejected. A loose policy that inferred agenthood from the element type would leave users
no reason to add the marker at all, and the marker is what every other consumer filters on. The
connector learns that an element is an agent from a reserved job header carrying the agent definition
key, so it needs no model access to decide whether to create an instance.

## Alternatives considered

- **Match a prefix on the job type string.** What the engine actually did until 8.10, introduced as a
  provisional measure. It only recognises agents run by a Camunda connector under a known job type,
  it says nothing at deploy time, and the evidence settled it: external agents lost their history
  entirely.
- **Fall back to the element template ID.** Shipped briefly alongside the marker, then dropped for the
  strict policy in D5. It has the same defect as the job type — a user-editable string — and it would
  have required the engine to know about Modeler-owned identifiers.
- **No marker; infer agenthood from the element type.** Rejected: without a marker there is nothing
  to filter process definitions on, and no way to distinguish an agent from any other ad-hoc
  sub-process.
- **A binary `camunda` / `external` `agentType`.** Enough for the engine, and simpler to set by hand,
  but it collapses the AI Agent Sub-process and AI Agent Task patterns into one value and so cannot
  carry the taxonomy the Modeler, Operate, Optimize, and the API all share. `camunda` would also
  collide with the planned `implementation` attribute, which is about who owns the lifecycle rather
  than what kind of agent this is.
- **A separately deployed agent resource.** Never seriously weighed. An agent has no existence apart
  from the element that hosts it, so a standalone resource would need its own binding back to that
  element and its own version alignment with the process.

## Consequences

- Each process definition version mints new agent definition keys, so aggregating one logical agent
  across versions must key on tenant, process definition ID, and element ID — not on the key.
- The marker constrains process instance migration. Migrating an element that has no agent instance
  to a target element that does carry the marker is allowed and creates nothing; the agent instance is
  only ever created by the runtime, the normal way, on a later activation. Migrating an agent instance
  to a target element with no agent definition is rejected, because an agent instance must always
  belong to an agent definition — and that holds even once the element instance that owned it has
  completed. The agent type is immutable across a migration; a type change is rejected. An accepted
  migration re-resolves the agent instance's agent definition against the target process definition.
- Modellers must opt in. An agent that is not marked works exactly as before and is simply invisible.
- Nothing in the BPMN schema ties `agentType` to the element that hosts it, so deployment validates
  the pair: a native value on the wrong element is rejected, and `external` is accepted on either
  native element.
- The job-type prefix heuristic survives in exactly one place, for backward compatibility: it is one
  half of the condition that stamps the agent's element ID onto a completed job for the audit log, so
  that jobs modelled before the marker existed still get attributed. It plays no part in resolving
  agent definitions, creating agent instances, or committing history. Its removal is tracked in
  camunda/camunda#60860.
- The reserved job header is now part of the contract between the engine and any agent runtime.

## Source

- [Document agent instance history as an ADR (camunda/camunda#60743)](https://github.com/camunda/camunda/issues/60743)
- [Engine — handle deploy-time agent definition (camunda/camunda#58976)](https://github.com/camunda/camunda/issues/58976)
- [Model the zeebe:agentDefinition agent-definition marker in zeebe/bpmn-model (camunda/camunda#58975)](https://github.com/camunda/camunda/issues/58975)
- [Create agent definitions at deploy time (camunda/camunda#59028)](https://github.com/camunda/camunda/issues/59028)
- [Reject AgentInstance creation for elements without an agent definition (camunda/camunda#59278)](https://github.com/camunda/camunda/issues/59278)
- [External-agent history never reaches COMMITTED (camunda/camunda#60554)](https://github.com/camunda/camunda/issues/60554)
- [Epic AI Agent Visibility & Explainability | Kickoff and Solution](https://docs.google.com/document/d/1iMgG-ESVCuwpr-3vuOdPBthTEyvaGWSGtLy0HY79ywA) (internal)

