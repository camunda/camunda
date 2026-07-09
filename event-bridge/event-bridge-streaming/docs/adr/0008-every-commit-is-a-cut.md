# ADR 0008 — Every commit is a cut

- Status: Accepted
- Date: 2026-07-09
- Scope: `event-bridge-streaming` runtime (`Task`/`Stage` SPI, commit protocol, stop path)
- Supersedes: the synchronous `commit(long)`/`checkpoint()` half of the commit contract carried
  forward by [ADR 0005](0005-asynchronous-checkpointing.md) and restated by
  [ADR 0007](0007-single-durability-concept.md) (the freeze/persist/complete cut protocol is kept
  and becomes the only contract)

## Context

After ADR 0007 removed the runtime-managed durability semantics, one dual path remained inside the
single shard concept: `Task.commit(long)` — the synchronous atomic cut, used by the stop path and
by tasks without frozen-cut support — versus `Task.freezeCut(long)`, the asynchronous cut. The
synchronous side kept a whole machine alive on its own:

- the actor's `committing` suspension flag, branched on in every entry point (`onWork`,
  `onPunctuationTick`, `maybeCommit`, `beginCommit`, `finalizeStop`);
- `beginLegacyCommit` / `onCommitted`, the suspend-and-resume round trip through the sink executor;
- the null return from `freezeCut` as the "no frozen-cut support" signal, and the matching refusal
  path in `StreamProcessor` (`Stage.supportsFrozenCheckpoint()` gating a fallback to
  `Stage.checkpoint()`);
- a second commit method on `PartitionCommitter` with its own javadoc'd mode.

Meanwhile the users of that machine disappeared. Every production task supports frozen cuts, and
the analytics stage tasks' `commit(long)` implementations were already, literally, the composition
freeze → publish → persist → complete — the synchronous contract had decayed into a convenience
wrapper around the asynchronous one.

## Decision

Collapse to one commit concept: **every commit is a cut, and the runtime owns the composition.**

- `Task.freezeCut(long)` is *the* commit contract. It never returns `null`: a task with nothing
  durable returns `CommitCut.NONE` (the empty cut — no-op publish/persist/complete), so the
  runtime still drives the lifecycle and the partition's source offset still advances past the
  barrier. `Task.commit(long)` is deleted.
- The runtime drives every cut through the same phases: freeze on the actor thread at the barrier,
  publish → persist on a sink IO thread while the partition keeps folding, completion back on the
  actor thread. "Synchronous" is merely *where* a cut executes, never a different contract: at
  shutdown the final cut runs inline on the actor thread — freeze → publish → persist →
  complete(true), with the source-offset commit joined (shutdown legitimately waits). Inline
  execution trivially satisfies the cut's threading rules: the freezing thread and the persisting
  thread are the same thread, and no folding is concurrent with it.
- `Stage.checkpoint()` and `Stage.supportsFrozenCheckpoint()` are deleted; the frozen trio
  (`freezeCheckpoint` / `persistCheckpoint` / `completeCheckpoint`, default no-ops) is the only
  checkpoint contract. `StreamProcessor.freezeCut` always freezes all stages and returns the cut
  persisting every stage's frozen delta and the barrier's offset in one shard transaction.
- `PartitionActor` loses `committing`, `beginLegacyCommit` and `onCommitted`; `beginCommit` always
  freezes and dispatches the cut to the sink pool. `PartitionCommitter` collapses to the cut
  surface: one `persistCut` returning the chained future — the in-flight path lets it complete in
  the background, the stop path joins it.

## Consequences

- API shrink: `Task` has one commit method, `Stage` one checkpoint contract. The suspension
  machinery and every branch that consulted it are gone; the actor's only commit state is
  `cutInFlight`.
- A task or stage that genuinely cannot detach its delta must now serialize it at freeze time —
  steal-and-replace into immutable data on the actor thread. That is the discipline the cut
  protocol always demanded, not a loss: the suspension "escape hatch" only ever traded it for a
  stalled partition.
- The node-level synchronous checkpoint chain (`Processor.checkpoint()` →
  `ProcessorNode.checkpoint()` → `ProcessorTopology.checkpoint()`) falls with `Stage.checkpoint()`
  — its only caller. State durability of a topology's stores is the owning task's concern: it
  drives their freeze/persist/complete split inside its own cut, exactly as the analytics stage
  tasks already do.
- The inline stop cut records the same freeze and persist timers as an in-flight cut, so every cut
  of a partition appears in one consistent metric stream; a failed final persist is *not* counted
  as a retry, because nothing retries it — replay from the last durable cut covers the remainder
  after restart.

## Alternatives considered

- **Keep the synchronous contract for "simple" tasks.** Rejected: it keeps two commit semantics
  alive — two barrier orderings to reason about, a suspension mode to test — for a user class that
  does not exist. The simple task is better served *within* the single concept: default trio,
  empty cut, offset still advances.
