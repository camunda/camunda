# ADR 0002 — Analytics engine code structure

- Status: Proposed
- Date: 2026-07-04
- Scope: `analytics/analytics-engine`

## Context

`analytics-engine` is small (~1200 LOC) but reads as a "big ball of mud": too many concerns share
one package. In particular `projection/` conflates four distinct jobs — the read-model **state**
(`BaseProjectionStore`, `StateBackedProjectionStore`, `AnalyticsColumnFamilies`,
`PersistedVariable(s)`), the input **record** (`SourceRecord`), the **processors** (the
`AnalyticsFactProjector` registry + the four `*Deriver`s), and a pure **behavior**
(`VariableEnricher`). `AnalyticsColumnFamilies` — the whole stage's physical schema — sits in
`projection/` though it also serves the Stage-1 rollups and the Stage-2 slots. There is no explicit
read/write split on the state, so nothing tells you *who* may mutate the read-model.

We studied three sibling engines that stay legible at far larger sizes — the workflow engine
(`zeebe/engine`) and the two Event-Bridge control-plane engines
(`event-bridge-cluster-metadata`, `event-bridge-consumer-groups`). They share one shape:

- `record/` — record values + intents + a deserializer registry.
- `state/` — split into `immutable/` (read interfaces) and `mutable/` (write interfaces that
  `extend` the read ones), per-entity DB implementations, `appliers/`, and a single flat
  **column-family enum** that is the entire physical schema.
- `processing/` — processors in a fluent registry keyed by `(ValueType, Intent)`, plus **behaviors**
  (pure, reusable decision helpers) and a `Writers` facade.
- The reusable runtime lives in a **separate framework module** (`event-bridge-stream`); the domain
  module is pure wiring.

Their legibility comes from **one file-group = one job**, with the command → event → state dataflow
made the literal shape of the code.

### The one thing we do *not* copy: the applier seam

Those three engines are Raft single-writer **command-log** state machines. Their `TypedEventApplier`
layer — processors *decide* and emit events; logic-free appliers are the *only* state mutators —
exists so that **leader-apply equals follower-replay**: recovery replays the event log through the
same appliers to rebuild RocksDB. That invariant is the entire justification for the seam.

The analytics engine has a different recovery model. It is a Kafka-Streams-style **poll-loop**
processor on `event-bridge-streaming`: the base projection is rebuilt by **re-consuming the source
from the committed offset and re-folding** (changelog-free, start-from-offset — see
`StateBackedProjectionStore` / the segment-safe commit). There is no log of the projection's own
mutations to replay. An applier layer would therefore add ceremony with none of its payoff — a
textbook YAGNI violation.

We keep the *packaging* patterns and drop the applier machinery.

### Framework/domain split — already satisfied

The reference engines prize splitting the reusable runtime (`event-bridge-stream`) from the domain
(`event-bridge-consumer-groups`). We already have this: `event-bridge-streaming` is the framework
(the `Projector`/`Aggregation`/`Task`/`OffsetStore` seams + `StreamRuntime`), and `analytics-engine`
is the domain. The mud is purely the domain module's *internal* packaging.

## Decision

Restructure `analytics-engine` into seams that mirror the reference shape, adapted to our
poll-loop model:

```
io.camunda.analytics
├─ record/     the engine's records: SourceRecord (input) + the inter-stage wire
│              (CellDelta, ShuffleEnvelope, ShuffleEnvelopeCodec). Fact stays in
│              analytics-model as the shared vocabulary (cf. RecordValue in protocol).
├─ state/      the read-model with a read/write split + the physical schema:
│              immutable/ProjectionState (getters),
│              mutable/MutableProjectionState (extends read; the writers),
│              StateBackedProjectionState (the RocksDB impl of both),
│              AnalyticsColumnFamilies (the CF enum), PersistedVariable(s).
├─ projection/ Stage-1 fold, records → facts: AnalyticsFactProjector (the
│              (ValueType → deriver) registry) + derive/ (FactDeriver + the four
│              derivers) + behavior/ (VariableEnricher).
└─ aggregation/ Stage-1 seal: CubeMeterAggregation.
```

Two scope decisions:

1. **Read/write state split, but no applier seam.** Introduce `ProjectionState` (read) /
   `MutableProjectionState` (write) so the compiler enforces who may mutate the read-model;
   derivers keep mutating it directly. No logic-free applier layer, for the recovery-model reason
   above.
2. **Engine internals only, for now.** The Stage-2 merge node and the topology/stage assembly
   currently live in the app (`event-bridge-analytics`). In Kafka Streams these would be domain
   code owned beside the other nodes; pulling them into the engine (so the app becomes a pure
   launcher/IO) is attractive but larger and needs a pass over the app shards. Deferred to a
   follow-up ADR/phase.

## Consequences

- "Who mutates the read-model" becomes compiler-checked — the single highest-value legibility win
  the references offer that actually applies to us.
- The physical schema (`AnalyticsColumnFamilies`) lives with the state it describes.
- `projection/` stops being a grab-bag; each package has one job (documented in `package-info.java`).
- Executed as refactor-only phases, green + one commit each: (1) state seam, (2) record seam,
  (3) processing tidy. No behavior change.
- Topology ownership and a Stage-2-in-engine move remain open, to be decided once the internals are
  clean.

