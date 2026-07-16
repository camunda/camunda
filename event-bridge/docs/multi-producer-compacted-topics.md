# Multi-producer compacted topics: the isolation problem and the design space

- Status: design note — feeds a future producer-fencing ADR; no decision is made here.
- Context: broker-side fencing is currently **deferred** behind the failover milestone
  ([event-bridge-streaming ADR 0009](../event-bridge-streaming/docs/adr/0009-state-changelog-on-compacted-topics.md),
  decision 4 as amended). This note preserves the analysis that deferral was based on, so the
  eventual ADR starts from settled ground instead of re-deriving it.
- Related: [ADR 0001 — compacted topics](adr/0001-compacted-topics-and-log-compaction.md);
  the producer-fencing wire format exists, unmerged, on branch `roman/eb-producer-fencing`
  (commit `ec1ec491cae`).

## The problem

A compacted topic keeps the latest record per key, where "latest" means highest position. With
one producer per partition that promise is unambiguous. With **multiple producers**, two distinct
problems appear, and they must not be conflated:

**Problem 1 — two producers race one key.** P writes `K=7`, Q writes `K=9`; position order
records whose request arrived second, nothing more. Which value *should* win is a question about
what P and Q mean — merge semantics the log does not have and neither producer supplied. There is
no mechanically correct answer beyond last-write-wins, so this is an application-semantics
problem. No broker mechanism in any surveyed system solves it, and none should try.

**Problem 2 — one producer's multi-key update is torn apart.** Real updates touch several keys
that must stay consistent with each other. The producer knows they belong together; interleaving
and crash timing destroy that grouping:

```
P's update:  A=50,  B=150   (invariant: A+B = 200)
Q's update:  A=120, B=80    (invariant holds too)

log:   ── P:A=50 ── Q:A=120 ── Q:B=80 ── P:B=150 ──►
compaction keeps latest-per-key:  A=120 (Q's), B=150 (P's) → A+B = 270
```

The surviving state was written by *no one*. Unlike problem 1, the information needed to prevent
this exists — in the producer — and the infrastructure loses it. Preserving it is squarely an
infrastructure job. Every design below is an answer to problem 2.

### Three independent sources of torn state

| source | fixed by |
|---|---|
| ① two healthy producers writing shared keys, interleaved | not fixable by fencing (both are legitimate) — needs transactions, or key disjointness |
| ② a zombie and its successor — two incarnations of one producer lineage | producer fencing |
| ③ one producer crashing mid-update — half its records published | neither fencing nor transactions-as-such — needs an atomic-apply rule on the read side (or abort markers) |

A complete design must answer all three, and no single mechanism covers more than one.

### The fence has two jobs

Producer fencing is usually described as access control ("the stale writer may not write"). Its
second job is quietly the more important one: **preventing interleaving** between a lineage's two
incarnations. A design that only filters stale *values* but admits interleaved *writes* must
rebuild multi-key atomicity some other way (see option C below for how that fails).

## The decisive axis: shared keys vs. disjoint keys

Whether problem 2's source ① exists at all depends on the topic's key topology:

```
one writer per LOG:                 BookKeeper, HDFS, VSR — succession only
many writers, keys SHARED:         Kafka — needs fencing AND transactions
many writers, keys DISJOINT:       this system — needs fencing only
```

**The projection property.** If every key has exactly one writer, then the shared log, filtered
to any one owner's keys, is indistinguishable from a log that owner had to itself — other
producers' records in between touch other keys and change nothing. Consistency within one
ownership group depends only on that group's own write order, which, with a single live writer,
is always preserved. Franken-states across one owner's keys become impossible *by construction*;
source ① is defined away; sources ② and ③ remain and have cheap answers.

This system's data model already has disjointness natively: record keys carry an ownership prefix
(the analytics shuffle keys by `(datasetId, dimKey)`; a shard's changelog cells, dedup watermarks
and offset-marker all live in its own key set). Disjointness is therefore a **contract to keep**,
not a mechanism to build — but it is load-bearing and invisible: nothing on the wire can express
it, every violating write looks individually legal, and the failure mode is silent franken-state
discovered weeks later. The future ADR must decide how hard to hold it (see open questions).

## Reference designs in other systems

**BookKeeper — fence the segment, never reuse it.** A ledger has one writer ever; succession
*seals* it (a quorum of storage nodes durably refuses further adds), recovers the last
quorum-committed entry, closes the ledger in the metadata store, and the successor writes to a
brand-new ledger. The "epoch" is the ledger chain itself. Radically single-lineage.

**HDFS lease recovery — central mint, storage-node enforcement.** One writer per file, holding a
lease at the NameNode. Succession bumps a per-block *generation stamp*; DataNodes reject pipeline
writes carrying the old stamp. A single monotonic number per storage unit, minted centrally,
enforced locally.

**Viewstamped Replication / Raft — the epoch is the log's own leadership.** Operations carry the
view/term that produced them; replicas ignore messages from superseded views. No external
producers exist; the writer *is* the primary. (This system's Raft layer already does this
internally; several options below lift the same idea one level up.)

**Kafka — the multi-lineage reference.** The only surveyed system built for many independent
writers per log, and therefore the most instructive reference for where this system is heading.
Its solution has **two separate subsystems**, and the split matters:

1. *Fencing (per-lineage succession).* A coordinator (owning a partition of an internal compacted
   topic) durably mints `(producer id, epoch)` per application-chosen stable identity;
   re-initialization bumps the epoch, instantly staling the previous incarnation. Every record
   batch carries `(pid, epoch)` in its replicated header. Each partition leader keeps an
   in-memory `pid → epoch` map and rejects stale appends. The map survives restarts via periodic
   local snapshot files plus a tail scan; survives elections because followers maintain it
   continuously while replicating (their replication protocol parses batches anyway); and
   survives compaction because the cleaner retains an *empty batch shell* per producer as a
   sentinel carrying the header. Sequences per batch additionally give transport-level
   idempotence (duplicate/gap detection on retries).
2. *Transactions (isolation for shared keys).* Fencing cannot stop two *healthy* producers from
   interleaving, so multi-key atomicity is rebuilt at read time: records append immediately but
   count as pending; commit is two-phase through the coordinator's replicated state machine
   (`PREPARE` is the point of no return; `COMMIT`/`ABORT` *control records* are then written into
   every touched partition); `read_committed` consumers are held back at the *last stable offset*
   (the first offset of the earliest undecided transaction) and filter aborted ranges via a
   per-segment index. Honest costs: a hung transaction head-of-line blocks **all** committed
   traffic behind it until the transaction timeout; there is no cross-partition read atomicity
   (markers land at different moments); and the compaction interplay (marker delete-horizons,
   aborted-record scrubbing, sentinel batches) is notoriously delicate.

The reference lesson: Kafka needed subsystem 2 *because* its keys are shared. A system whose keys
are disjoint gets subsystem 2's effect from the projection property, for free.

## The options

**A. General per-producer epoch fencing** (the direct answer for multi-lineage topics).
Epochs minted by a durable coordinator, carried in the batch header (wire format already built),
enforced by a monotonic `producerKey → epoch` map at the partition leader. Recovery follows the
engine pattern rather than Kafka's follower bookkeeping (the Raft layer here is deliberately
payload-blind): every replica can *replay* committed batch headers to the same map; the leader
additionally raises the map at append time (speculative apply, reconciled by replay); the floor
below the cleaner point is carried either by a small table in the compaction manifest or by
preserving the fencing header through the cleaner's rewrap — one of the open questions.
Solves source ②; per the projection property, that is all a disjoint-keys system needs.

**B. Collapsed per-partition writer generation.** If a partition has exactly one legitimate
writer by construction (true for state changelogs), the map collapses to a single number —
the BookKeeper/HDFS/VSR shape: one generation per partition, bumped on promotion, recovered as a
running max over batch headers. Considerably simpler than A, but it fences *partitions*, not
producers, and is therefore insufficient the moment a topic legitimately hosts several writer
lineages. Recorded because the collapse is the right call for any future topic class that is
single-writer by construction.

**C. Read-side arbitration, no write fence.** Stamp every record with its epoch; let writes land
freely; the cleaner keeps max-by-`(epoch, position)` per key and readers skip records below the
highest epoch seen. Kills stale *values* elegantly with zero broker enforcement — and fails the
fence's second job: a zombie's half-published multi-key update can interleave with the
successor's records, and the reader's epoch filter then applies a *prefix* of it (cells without
their dedup watermark → double-application on replay). Repairing that requires buffering records
until per-update commit points and teaching the cleaner about update boundaries — rebuilding
transactions, poorly. Rejected, with this failure analysis recorded because the option looks
attractive every time someone rediscovers it.

**D. Coordinator-mediated write validation.** Validate every publish against the coordinator's
live epoch state: either a coordination round-trip on the hot append path, or gateway-side
caching — which is unsound precisely here, because a cache-stale window admits exactly the write
that last-write-wins then *keeps*. Rejected.

**E. Transactions (the shared-keys answer).** Commit markers, reader-side held-back offsets,
aborted-record indexes — the full second subsystem, as per the Kafka reference. Buys multi-key
atomicity *between producers sharing keys*. Not needed while the disjointness contract holds;
this is the price tag for ever relaxing it, and should be treated as such in any future
"can two owners share a key?" discussion.

**F. No fence — end-to-end convergence only** (the current interim, ADR 0009 decision 4 as
amended). Source offset commits are already coordinator-epoch fenced, and the cut chain ends
with that commit: with a contractual *halt-the-shard on rejected commit*, a deposed zombie
discovers its deposition at the end of its next cut, bounding the blast radius to **one zombie
cut per failover** — whose content is a deterministic fold of source records the successor also
folds. Bounded and converging, but not airtight (the mixed-marker window during a concurrent
rebuild); acceptable as a consciously recorded interim, not as the end state.

### Source ③ in this system: the marker rule

Independent of fencing, the writer-crash source is closed on the read side: a cut's offset-marker
is the **last** record of the cut, so readers (rebuild and standby alike) apply records only up
to the highest marker — cut-atomic apply. A torn tail beyond the last marker is invisible instead
of half-applied. The rule survives compaction because record positions are preserved forever
(ADR 0001), and it is sound only while each lane has one live writer — which is what fencing
guarantees. Fencing and the marker rule interlock; neither substitutes for the other.

A pleasant corollary of per-lane markers versus Kafka's last-stable-offset: progress is held back
*per lane*, so one slow producer parks only its own unfinished tail and never head-of-line blocks
other producers' committed records.

## Open questions for the fencing ADR

1. **Epoch authority.** Group member epochs cover every producer that exists today (shards). A
   coordinator-minted `InitProducer(producerKey) → epoch` flow in the metadata plane is the named
   path for non-consumer producers — build when one exists.
2. **Producer identity.** What exactly `producerKey` is and its uniqueness scope.
3. **The disjointness contract.** Document-only; or cheap detection (the cleaner already reads
   every header and can flag two producer keys writing one record key); or hard enforcement
   (requires a key→owner registry — transactions-adjacent cost).
4. **Enforcement-state recovery.** Affirm the engine-pattern tracker of option A.
5. **DELETE-policy topics.** Accept that the fence's memory equals retention there, or restrict
   fenced publishing to compacted topics.
6. **Non-goals to pin.** No per-batch sequences (end-to-end dedup exists), no transactions —
   and what would force revisiting each (transport-level idempotence; shared keys).
7. **Epoch survival through compaction.** Manifest table as the snapshot-carried floor (the
   cleaner's rewrap currently strips the fencing header) versus preserving the header through
   rewrap so the clean set is self-carrying.
