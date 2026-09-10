# Commit agent instance history per job activation under the job lease

**DRI**: Nico Korthout-Dittrich

**Status**: Accepted (8.10)

**Purpose**: Defines the agent history commit lifecycle — how items are attributed to one job
activation, when they become durable, what happens to a superseded activation's items, and what the
engine does and does not guarantee about the resulting record.

**Audience**: Zeebe engineers working on agent processing or job processing; engineers building an
agent runtime against the agent instance API; and AI agents reasoning about agent history semantics.

## Context

An agent reports its conversation incrementally, while its job is still running — that is what makes
the visibility near-real-time. But a job that fails or times out is re-activated, and the superseded
worker may still be reporting. Without a way to tell activations apart, an agent whose worker crashed
after reporting three items and was retried leaves six items in secondary storage: three from the dead
activation and three from the live one. The record would assert a conversation that never happened.

[0005](0005-810-job-lease.md) provides the missing identity: an opaque token identifying one activation
of one job. Agent history is its first consumer. This ADR covers what agent history does with it, and
the durability rules that follow; the token's own guarantees stay in 0005, and why agent execution is
recorded in the engine at all stays in [0010](0010-810-agent-execution-in-engine-records.md).

## Decision

**D1. History items ride on the agent instance command that reports them.**
A create or update carries a batch of history items along with the job key and that job's lease token.
Batching them onto a command the runtime already sends also keeps its request count down: a call per
item would be slower and more brittle, giving network failures more chances to interrupt reporting
that the runtime would then have to recover from. A separate command surface for history writes was
considered and does not solve anything the embedded form does not, while introducing a more novel
processing model. Bundling history into job completion instead was rejected outright: it defeats the
incremental-visibility goal and fuses two writes that are deliberately kept apart.

**D2. Items are provisional until the job ends, and commit is the engine's follow-up to that
completion.** An accepted item is stored pending, attributed to the exact job and lease token that
reported it. Configuration items on a create are the exception, applied and committed as the instance
is created so that it starts with usable configuration; everything else a create carries stays pending
like any update's items. When the job completes, the engine issues an internal commit for that job's
current lease token: the items matching it commit, and the rest are discarded in the same pass.
Attribution is therefore per activation rather than per job. A job that ends without completing —
cancelled, terminated with its element, or resolved through a caught error — discards every pending
item for the job key instead, whatever lease reported it, so no item is left without a terminal state.

The lease is opt-in under [0005](0005-810-job-lease.md), so a completion can arrive without one, and
then the commit takes every pending item for the job key. There is no identity to fence with, so the
choice is between committing everything and losing the history, and the engine keeps it. That path is
unfenced by construction: two activations under one job key both commit, which is the corruption the
lease prevents everywhere else. The per-activation guarantee is a guarantee about leased activations.

**D3. Items from a superseded activation are discarded at the next commit, not when the job is
re-activated.** The commit pass commits the items matching the job's current lease and, in the same
pass, discards every remaining pending item for that job under a different lease. Discarding eagerly
on re-activation was the original plan and was dropped: it would have required reworking how a job
activation batch is collected. Deferring costs nothing, because a pending item is not visible as part
of the committed conversation anyway.

**D4. Discarded items are kept, and the committed view is the default read.**
A history search returns committed items unless the caller asks for pending or discarded ones. The
discarded items stay because they carry what a failed or retried activation actually did, which is
what makes them worth having for auditing and cost tracking. The invariant that follows: one
client-supplied item id has at most one committed record and any number of discarded ones, so only the
engine-assigned item key is unique, and callers must filter on commit status rather than assume one
record per id.

**D5. Create rejects a stale lease; update accepts one and stores its items pending.**
The axis is whether the command's effects can be deferred, not strictness. An update's history items
can be deferred: they sit pending under their own lease, and the commit machinery resolves them. Its
metrics are accounted for on arrival, so rejecting the command outright would throw away consumption
that really happened. A create applies and commits its configuration as the instance is created, so
that part has no later step in which a mismatch could be caught, and a stale lease stays a hard
rejection there. The lease is a fencing token, not an authorization check: it decides which items win
at commit time, not whether the command is allowed.

**D6. Duplicates are skipped silently by client-supplied item id; ordering and omission are the
writer's contract.** An item whose id already exists for this agent instance — committed, or pending
under the same lease — is skipped entirely and echoed back marked as a duplicate, applying none of its
effects, which is what makes D7 of [0012](0012-810-agent-instance-written-by-agent-runtime.md)'s delta
metrics safe to retry. A pending item under a *different* lease is never a duplicate; it belongs to an
attempt that may still lose. The engine does not check that a writer sends items in order or sends
them all: a writer that reorders or omits items produces a record that differs from its own view and
gets no error. That was accepted knowingly — the alternatives either could not detect the problem or
would have let the engine rewrite history it had already committed.

**D7. Pending state is transient, and its bookkeeping is cleaned in bounded chunks.**
Committing or discarding an item deletes it from primary storage; the durable copy already reached
secondary storage from the creation event. What outlives it is the per-instance duplicate bookkeeping,
which grows for the life of the agent instance and is cleaned up when it completes — as a
self-rescheduling batch over a fixed chunk size, because an agent instance can accumulate thousands of
items and a single unbounded delete would exceed the record batch limit and block the stream
processor.

## Alternatives considered

- **Identify activations by an activation counter echoed on job completion.** Puts an engine-generated
  identifier in the worker's hands and makes the worker, not the engine, the authority on which
  activation it is. See 0005 for the full comparison; the lease won.
- **Let the lease expire when the job times out.** Every timed-out job would then sit unfenced until
  re-activation, and a benign late completion could no longer commit its work.
- **Have the writer query committed history before resending.** The query API reads secondary storage,
  which the exporter populates asynchronously. That is an unbounded lag, not a narrow race, so the
  answer can never be trusted at the moment it is needed.
- **Replace the whole batch per request, or let a later request rewrite history.** Last-write-wins
  makes intermediate states unobservable, and rewriting requires retroactively discarding items that
  were already committed — a real softening of the audit guarantee this work rests on.
- **Cache idempotency keys in the gateway.** Introduces gateway-side state that conflicts with the log
  being the sole source of truth.
- **Reject a duplicate with a conflict instead of skipping it.** The original design. Replaced by the
  silent skip because a retry after a partial failure is normal and should not force the writer to
  distinguish its own retries from real conflicts. A rejection would also take down the items in the
  same batch that were not duplicates, losing the metrics of tokens that had genuinely been spent.

## Consequences

- A history item that never reaches a terminal state leaks primary storage and shows as pending
  forever. This has happened once during development, when the engine could not recognise an external
  agent's job as agentic — see [0011](0011-810-agent-definition-from-bpmn-marker.md).
- Writers must supply a job key and that job's lease token whenever they attach history. Enforcement
  of that requirement in the validator and clients is still being completed (camunda/camunda#60864);
  until it is, the unleased commit path in D2 stays reachable.
- The committed record is only as faithful as the writer. The engine guarantees that at most one
  activation's items commit, not that those items match what the model actually saw.
- Metrics accumulate once per history item id and are never rolled back, so a re-activation's new
  items add to what the superseded activation already reported. That is intended. The second
  activation spent its own tokens, and the total is meant to account for every token the run used,
  including those of activations whose history was discarded.
- Read-time truncation of large content is not implemented, so a single very large item is returned in
  full (camunda/camunda#54750).
- Agent history is write-only from the engine's perspective; no engine path reads item content back.

## Source

- [Document agent instance history as an ADR (camunda/camunda#60743)](https://github.com/camunda/camunda/issues/60743)
- [Define engine record and intents for agent messages (camunda/camunda#52806)](https://github.com/camunda/camunda/issues/52806)
- [Implement AGENT_HISTORY pending-commit lifecycle (COMMIT/DISCARD via job lease) (camunda/camunda#55033)](https://github.com/camunda/camunda/issues/55033)
- [Trim AGENT_HISTORY COMMITTED/DISCARDED events to identity fields only (camunda/camunda#57037)](https://github.com/camunda/camunda/issues/57037)
- [Idempotent agent history item creation (camunda/camunda#57363)](https://github.com/camunda/camunda/issues/57363)
- [Bound cleanup of committed history-item ids on agent-instance completion (camunda/camunda#61379)](https://github.com/camunda/camunda/issues/61379)
- [Epic AI Agent Visibility & Explainability | Kickoff and Solution](https://docs.google.com/document/d/1iMgG-ESVCuwpr-3vuOdPBthTEyvaGWSGtLy0HY79ywA) (internal)

