# The agent instance is the engine's anchor for one agent run, written by the agent runtime

**DRI**: Nico Korthout-Dittrich

**Status**: Accepted (8.10)

**Purpose**: Defines the agent instance — what one instance spans, who writes it, which parts of its
state the writer owns and which the engine owns, and why an engine-owned agent lifecycle stays open as
a future step.

**Audience**: Zeebe engineers working on agent processing or job processing; connector engineers and
anyone building an agent runtime against the agent instance API; and AI agents reasoning about agent
lifecycle and ownership.

## Context

One logical agent run is not one element instance. The AI agent ad-hoc sub-process pattern re-enters
the same agent as the process loops back, and each re-entry is a new element instance. Agent
execution data therefore needs an anchor of its own: something that outlives an element instance,
that every history item and every metric can hang off, and that Operate can show as one run.

The agent runtime — the Camunda AI Agent Connector, or a user's own job worker — is the only party
that knows what the agent is doing. Under [0010](0010-810-agent-execution-in-engine-records.md) that
data has to reach secondary storage through the engine, so the runtime pushes it in through a command
API. This scopes the work to visibility and leaves how agents actually execute untouched.

What the runtime reports is not durable the moment it arrives. A history item is stored pending,
attributed to the job activation that reported it, and becomes committed or discarded when that job
ends, whether it completes or is destroyed; [0013](0013-810-agent-history-commit-under-job-lease.md)
owns that lifecycle. D6 and D8 below rely on it.

## Decision

**D1. One agent instance spans one logical agent run, across many element instances.**
The instance is created once for the element that hosts the agent and is reused as the process
re-enters that element, accumulating the element instances it has been associated with. Re-entry is
why one run has to outlive one element instance: the agent continues the same conversation from its
context and memory instead of starting a new one. The instance is a separate entity rather than a
field on the element instance, because the relationship is one-to-many and because agent data is not
meaningful for element instances in general. Whether an instance is actually reused is the runtime's
decision, not something the engine enforces: the AI Agent Connector continues with an existing
instance by keeping its agent instance key in the agent context it holds in the agent's memory, so an
agent configured to start from a fresh context has no key to carry forward and creates a new agent
instance instead. What counts as one run follows the runtime's own continuity.

**D2. The agent runtime writes the agent instance through a public command API.**
Creating an instance, moving it through its statuses, reporting metrics, and appending history are all
client commands. The engine does not derive any of it. Under 0010 the runtime already had to get this
data into secondary storage somehow; routing it through the engine's API was the chosen way, and it
keeps the engine out of the business of running agents.

**D3. Client-driven lifecycle today does not foreclose an engine-owned lifecycle later.**
This is a deliberately narrow solution aimed at visibility, not a fork in the road. Nothing here
depends on the runtime remaining the driver: the records, the status model, and the history contract
are all expressed in terms of what happened to the agent, not in terms of who reported it. If the
engine later takes over the agent instance lifecycle outright, that becomes a change of writer, not a
redesign of the data. There was already an execution model and this work left it in place; it only
added visibility. That is what kept the hard problems out of scope — chiefly serving agent history
back to the connector as the context it operates on, which would demand strong consistency rather
than the eventual consistency of secondary-storage reads.

**D4. Creating an agent instance conflicts on existence alone.**
An element instance that already has an agent instance rejects a second create, whatever the request
carries. Comparing payloads to decide whether a create is a benign retry does not work — a prompt
defined in FEEL is re-evaluated on every retry, so identical intent produces a different payload — and
an upsert would silently discard the run that was already recorded.

**D5. The writer owns the active statuses; the engine owns completion; there is no failure status.**
The runtime reports what it is doing, because only it knows. The engine validates that an update names
an active status but does not otherwise interpret which one, so the vocabulary can grow without
changing how the engine behaves. Completion is the exception:
only the engine observes the owning process instance ending or being cancelled, so only the engine can
mark an instance complete, and it does so with an internal command that carries no user to authorize.
There is no failure status — failures surface as incidents on the owning element instance, where the
existing operator tooling already handles them. Leaving the last reported status in place is also
worth more than a failure status would be: knowing what the agent was doing when the incident was
raised is what helps an operator find the root cause and decide how to recover.

**D6. The instance carries its current configuration, changed through configuration history items.**
Model, provider, system prompt, tools, and limits live on the instance and are updated by history
items that name which of them changed. Storing the current values directly is simpler and cheaper than
reconstructing them at query time from the history, and routing the changes through history items
means the record of what changed and the current value cannot drift apart. History items were chosen
as the carrier for two reasons: the changes are then traceable — what changed, when, and in which
activation — and a change reported by an update follows the same pending, committed, or discarded
path as any other history item, so a change from an activation that goes on to lose does not stick.
The configuration supplied on create is the exception: it is applied and committed as the instance is
created, because there is no earlier state a losing activation could corrupt.

**D7. Metrics are deltas going in and totals coming out, and are never rolled back.**
An update reports what one activation consumed; the engine keeps the running total and reports the
absolute value on the event. Unlike history items and configuration, metrics apply the moment the
command is accepted and survive a discard, because the tokens were spent and the cost was incurred
whether or not the activation went on to win. A record that dropped them would be missing the most
operationally important fact about a failed run.

**D8. One element instance may write to an agent instance at a time.**
A parallel multi-instance agent task can otherwise land two jobs on one agent instance before either
commits. Neither job can see the other's pending items, so both accept the same item and both commit
it — and the duplicate bookkeeping in [0013](0013-810-agent-history-commit-under-job-lease.md) holds
only one entry per item. The active writer is judged by its job, not its element instance, because an
element instance can stay active after its job can no longer write.

## Alternatives considered

- **Expose the agent instance key on the element instance entity.** Rejected because agent data is
  not generic or meaningful for all element instances. It would also fix the model to a shape that D1
  explicitly needs to stay flexible.
- **Derive the current configuration at query time from configuration history items.** More complex
  and less efficient than storing the fields on the instance, and it makes every reader re-implement
  the fold.
- **Upsert or overwrite on create, or introduce a cancel command.** Both were on the table for
  handling a create against an existing instance. Rejected in favour of D4's plain conflict: an upsert
  loses the run already recorded, and a cancel command adds lifecycle surface for a case that a
  rejection already handles.
- **Give the engine the agent lifecycle now.** Rejected as scope, not as direction — see D3. It would
  have meant changing how agents execute and solving strong consistency on the history a connector
  reads back as its context, neither of which the visibility problem needs.

## Consequences

- A writer that crashes after calling the model but before reporting will always be able to lose that
  call's token count. This is inherent to the client-driven shape and is documented rather than fixed.
- The agent instance API is a public contract that any job worker can drive, not only Camunda's
  connector. External agents are ordinary clients of it.
- An agent that is not idle is conceptually a waiting state, but the status model is separate from the
  engine's waiting-state handling. The two features were built at the same time by different teams,
  which is what made integrating them impractical then; nothing prevents it now. It remains open.
- Because there is no failure status, a failed agent shows as its last reported status; operators find
  the failure as an incident on the element instance instead.
- Agent instances and their jobs are always on the same partition, so commit processing reads local
  state with no cross-partition routing.
- Primary state for an agent instance is deleted when its process instance ends; the completion event
  still exports, so secondary storage keeps the final snapshot.

## Source

- [Document agent instance history as an ADR (camunda/camunda#60743)](https://github.com/camunda/camunda/issues/60743)
- [Define Command API spec for agent instances (camunda/camunda#51504)](https://github.com/camunda/camunda/issues/51504)
- [Clean up agent-instance data at end of process instance lifecycle (camunda/camunda#51518)](https://github.com/camunda/camunda/issues/51518)
- [Implement agent instance create and update processors (camunda/camunda#52912)](https://github.com/camunda/camunda/issues/52912)
- [Redesign Agent Instance Command API to batch history writes via PATCH (camunda/camunda#58789)](https://github.com/camunda/camunda/issues/58789)
- [Epic AI Agent Visibility & Explainability | Kickoff and Solution](https://docs.google.com/document/d/1iMgG-ESVCuwpr-3vuOdPBthTEyvaGWSGtLy0HY79ywA) (internal)

