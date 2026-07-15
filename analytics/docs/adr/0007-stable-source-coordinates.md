# ADR 0007 — Stable source coordinates: dedup the Zeebe stream before the fold

- Status: Proposed
- Date: 2026-07-07
- Scope: `event-bridge/event-bridge-zeebe-connector` (payload frame), the `ZeebeRecordExporter`
  (frame producer), `analytics/analytics-engine` + `analytics/analytics-pipeline` (Stage-1
  pre-fold dedup), `event-bridge/event-bridge-streaming` (seal-watermark javadoc only)
- Builds on: the staged-aggregation design (Stage 1 → shuffle → Stage 2), `SegmentDedup`
  (reduce-side effectively-once), the checkpoint-and-failover design (atomic offset+state cut)

## Context

The pipeline consumes Zeebe records from an Event Bridge topic the `ZeebeRecordExporter` produces
with **at-least-once** semantics: on an exporter retry or broker failover, the same Zeebe record
can be appended to the topic **twice, at two different EB offsets**. Every coordinate the pipeline
uses today is an EB coordinate:

- Stage 1 dedups its own *consumption* replays by EB offset (the actor's resume baseline), but a
  duplicate *append* is a different EB record at a different offset — it passes the baseline and is
  **folded twice** into the base projection. A duplicated `ELEMENT_COMPLETED` derives two completion
  facts; every cube-meter counts it twice. This is the root unsoundness — it corrupts results
  before segments, chunks, or the shuffle are even involved.
- `SegmentSealingAggregation.sealCompletedUpTo` carries a TODO flagging the same producer
  duplication at the seal-watermark level ("acceptable only for demonstrating liveness").

The stable identity of a Zeebe record is its origin coordinate **(zeebePartitionId,
zeebePosition)**: positions are strictly monotone per Zeebe partition, and the exporter publishes
each partition's records in position order (a retried batch re-appends records whose positions are
at or below what was already appended). The payload frame today carries the record's timestamp and
key but **not** its position, and the reconstructed record's `partitionId` is overwritten with the
EB partition — so the stable coordinate is not available on the consume side.

## Decision

### 1. Carry the Zeebe origin coordinate in the payload frame

The frame gains the record's original position and partition id (little-endian, after the key):

```
timestamp(8) | key(8) | position(8) | partitionId(4) | metadataLength(4) | metadata[..] | value[..]
```

`ZeebeRecordCodec.serialize` stamps `record.getPosition()` / `record.getPartitionId()`;
`deserialize` reconstructs the record with its **real** Zeebe coordinates instead of the EB
envelope's. The EB coordinates (partition, offset) keep flowing separately through `SourceRecord`
— they are consumption/progress coordinates, not identity. This is a breaking frame change; the
pipeline is pre-GA and topics are re-seedable, so no frame versioning is introduced (the change is
a one-time cut, noted in the demo scripts).

### 2. Dedup before the fold: a per-Zeebe-partition applied-position watermark in Stage-1 state

Stage 1 keeps a high-watermark of the last applied `zeebePosition` per `zeebePartitionId` in a new
small column family in the task's provider. Before folding a record, the projection checks
`position <= watermark(partition)` → skip; after folding, it advances the watermark. The watermark
is persisted inside the existing per-partition atomic cut (state + consumed EB offset + watermark
commit together), so:

- an exporter duplicate (same Zeebe record, later EB offset) arrives at-or-below the watermark and
  is skipped — **exactly-once folding over an at-least-once topic**;
- a consumption replay after a crash resumes from the committed EB offset with the matching
  watermark state — re-folded records are exactly the not-yet-committed ones, as today.

Order guarantee this relies on: per Zeebe partition, records arrive on the EB topic in
non-decreasing position order (single sequential exporter per partition; retries only re-append
already-appended positions). Cross-partition interleaving on a funneled EB partition is fine — the
watermark is per Zeebe partition.

### 3. Segments, chunks, and the seal watermark stay on EB offsets

With duplicates eliminated *before* the fold, EB offsets are sound for everything downstream,
because the EB topic is immutable and replays are deterministic:

- **Facts** keep their EB origin coordinates (`sourcePartition`, `sourcePosition`) — replaying the
  same offsets re-derives byte-identical facts, so the shuffle's origin-coordinate dedup semantics
  are unchanged.
- **Segments** remain EB-offset ranges: a segment's cell set is a pure function of topic content,
  so a crash-replay re-seal re-emits an identical delta, and `SegmentDedup` drops it by
  `(segment, chunk)` as designed.
- **`sealCompletedUpTo`** remains driven by the committed EB offset — it is a pure liveness signal
  ("the source has consumed past this segment"), no longer an identity mechanism. Its TODO is
  replaced by this argument.

## Considered and rejected

- **Zeebe-position segments**: moving segment identity to `(zeebePartition, zeebePosition)` would
  require one open segment per Zeebe partition per meter (positions are only monotone per
  partition), complicating seal/checkpoint/dedup for no gain once the fold is dedup'd — EB offsets
  are already deterministic identity for immutable topic content.
- **Transactional/idempotent producer in the exporter** (exactly-once append): far larger change
  (broker-side producer sessions/epochs), and the consumer-side watermark is needed anyway for
  replay determinism arguments; the watermark is one small CF and one comparison per record.
- **Dedup by record key**: keys are not unique per record (an element instance emits many records)
  and rejections/commands complicate it; position is the log identity.

## Consequences

- One 8-byte compare + rare watermark write per folded record on the hot path; one extra long per
  Zeebe partition in the checkpoint.
- The frame grows by 12 bytes per record.
- Meter totals become correct under exporter retries/failovers — previously every retried batch
  inflated counts.
- `PartitionActor`'s EB-offset baseline stays as the consumption dedup; the watermark is the
  producer dedup. Both are needed; they answer different questions.

