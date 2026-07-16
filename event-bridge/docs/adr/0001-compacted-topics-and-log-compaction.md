# ADR 0001 — Compacted topics: latest-per-key retention by deterministic replica-local cleaning

- Status: Proposed
- Date: 2026-07-15
- Scope: `event-bridge-broker` (data partitions), `event-bridge-protocol` (batch format),
  `event-bridge-cluster-metadata` (topic configuration), fetch path
- Builds on: the journal's index-free fetch scan (sparse in-memory index + frame walk; commit
  `48eb26955ea`) and the marker-snapshot retention mechanism of `LogRetentionCompactor`
- Consumed by: [event-bridge-streaming ADR 0009](../../event-bridge-streaming/docs/adr/0009-state-changelog-on-compacted-topics.md)
  (state changelogs are the first compacted-topic user)

## Context

Every data partition today is a retention-bounded Raft log: `LogRetentionCompactor` keeps the most
recent `maxRecords` and discards everything older by taking an empty *marker snapshot* at the
retention bound — Raft's snapshot machinery then truncates the log and can catch up a lagging
follower via InstallSnapshot. Records older than the window are gone forever, by design.

State-replication topics (streaming ADR 0009) need a different contract: the log must always
contain **the latest record per key**, so a consumer can rebuild a state store by reading it from
the start, at a cost bounded by the live keyspace rather than by history. Deletion must be
expressible (a *tombstone*: a keyed record with an empty value). Meanwhile the existing guarantees
must hold: record positions are stable forever, consumers tailing the head see raw recent history,
replicas never diverge, a crash at any moment loses nothing, and no file is ever unlinked while a
reader still holds it.

Two prior lessons constrain the design. First, the fetch path just eliminated its persisted
per-segment index after we found its content was rebuilt from the log on every load — derived
state on disk is a bug class (stale/absent index files caused a production failover incident), so
compaction must not reintroduce index files. Second, deferred file deletion behind a reader
refcount is mandatory (the zero-copy fetch path once unlinked files still held by in-flight
responses, leaking broker resources).

## Decision

Add a per-topic cleanup policy, `DELETE` (today's retention) or `COMPACT`, and implement
compaction as a **deterministic, replica-local background pass whose atomic commit point is the
partition's Raft snapshot**.

1. **Keyed records.** Entry framing already carries an unconditional `keyLength` field
   (`[entryLength][keyLength][key][value]`, `keyLength == 0` meaning no key), inside the existing
   batch CRC. What compaction adds is semantics: a `KEYED` batch attribute declares that keys are
   meaningful for latest-per-key retention, and a keyed entry with an empty value is a tombstone.
   Publishing batches without the `KEYED` attribute to a compacted topic is rejected at
   validation. The producer API gains a keyed publish variant.
2. **Topic flag in the metadata plane.** `TopicMetadata` carries the cleanup policy; it flows
   through the topic record, create/validate/apply, the query service, and declarative topic
   auto-creation.
3. **On-disk layout.** The Raft journal (dirty zone + active head) stays untouched — the cleaner
   only reads it. Compacted history lives in *clean segments* referenced by a *manifest*; manifest
   and clean segments together are the content of the partition's Raft snapshot. Reading order is
   clean set first, then the live log.
4. **Clean segment format: a sequence of single-entry batches.** Batch headers already carry their
   own absolute position, and the reader's seek contract is "first batch containing *or
   following* the position", so position gaps between batches are legal today. Re-wrapping each
   surviving record as its own batch therefore represents arbitrary gaps with **zero changes to
   the wire format, the reader, or the consumer SDK**. The ~44-byte header per surviving record is
   acceptable for coalesced state records; a packed multi-entry format with explicit per-entry
   positions remains available as a later optimization.
5. **No index files, anywhere.** Clean segments are self-framing (hop by `batchLength`); the
   manifest's first-position-per-segment list gives O(log n) segment selection. Consistent with
   the journal's in-memory sparse index: the disk holds only truth, never acceleration structures.
6. **The cleaner pass** runs per replica, on an actor, with no cross-replica coordination:
   1. *Pick C*: the highest sealed journal-segment boundary that preserves a configured minimum
      lag of raw history behind the head (tailing readers always see un-compacted recent records).
      C is computed only from the committed log, which Raft guarantees identical on every replica
      — so every replica picks the same C for the same log state and produces byte-identical
      output. Cleaner-point advancement is **deterministic-local**; no leader announcement, no
      control records.
   2. *Build map*: one sequential read of the dirty zone up to C, filling a bounded map of
      128-bit key hash → latest position (collision probability negligible; on overflow, lower C
      and multi-pass).
   3. *Sweep*: copy forward the previous clean set plus the dirty zone, keeping each record only
      if its position equals the map's latest for its key. Positions are preserved verbatim —
      gaps, never renumbering.
   4. *Commit*: persist the new clean set + manifest as a transient-then-persisted Raft snapshot
      at index C (content hardlinked, cheap). This is the **only durability point**: a crash
      anywhere earlier leaves the previous snapshot authoritative and the pass simply reruns.
   5. *Tidy*: Raft truncates the raw log ≤ C on its own; superseded clean files enter a
      refcounted deferred-delete queue.
7. **Tombstone grace (two-touch).** A tombstone survives its first sweep into the clean set and is
   stamped (per clean segment, in the manifest). Only a later pass, after a configured grace
   window, may drop it — a rebuilding reader that already applied the old value must still see
   the delete.
8. **Deletion safety checklist.** A superseded clean file is unlinked only when all hold: it is
   not referenced by the newest persisted snapshot; its replacement is durable; Raft's snapshot
   has advanced past it; and its zero-copy reader refcount is zero. Never an immediate unlink.
9. **Fetch.** Positions ≤ C resolve against the clean set (manifest lookup, then header hops);
   positions > C flow through the live-log reader unchanged. A swept position returns the next
   record ≥ it (*gap-skip* — the existing seek semantics). The out-of-range error boundary moves
   from the start of the raft log to the start of the clean set.
10. **Follower catch-up and disaster recovery come for free**: a follower behind C receives the
    snapshot (manifest + clean segments) through the existing InstallSnapshot machinery, then
    normal replication; a restarting broker installs the newest persisted snapshot and replays the
    remaining raft log — the same lifecycle `LogRetentionCompactor`'s marker snapshots exercise
    today, with content.

## Consequences

- Storage for a compacted partition is bounded by O(live keyspace + min-lag window + not-yet-swept
  dirty zone) instead of O(history).
- There is no compaction protocol to operate or reason about: no coordinator, no control records,
  no recovery procedure. All durable state is the log and the snapshot; the cleaner is a stateless
  idempotent function of both. Replicas converge because their inputs are identical and the pass
  is deterministic — this must be pinned by a test (two replicas' logs → byte-identical clean
  sets).
- Consumers of a compacted topic beyond the min-lag window observe latest-per-key, not history.
  Compacted topics are therefore suitable for state replication and unsuitable as an event feed —
  the consumer-facing contract is documented in streaming ADR 0009.
- The cleaner adds background I/O proportional to the dirty zone per pass; pass frequency and
  min-lag are per-topic configuration.
- Combining compaction with time/size retention (drop keys not updated within a window) is a
  possible later extension expressible as configuration; noted, not designed.

## Considered and rejected

- **Persisted per-clean-segment index files.** The `.sidx` history: a persisted index is derived
  state that can be stale, absent, or torn relative to its data, and it caused a real failover
  incident before being removed. Self-framing single-entry batches plus the manifest make the
  clean set need no index at all.
- **A packed clean-segment format with explicit per-entry positions.** Smaller on disk, but a new
  format version, new codec, and a consumer-SDK change — deferred as an optimization; the
  single-entry-batch form is wire-compatible today.
- **Leader-coordinated cleaning (leader compacts, ships results, or announces the cleaner point
  via control records).** Adds a protocol and failure modes for zero benefit while the pass is a
  deterministic function of the replicated log. Would only become necessary for dynamically
  changing compaction policy at runtime; revisit then.
- **Renumbering positions during the sweep.** Breaks every stored consumer offset and the
  offset-marker mechanism of ADR 0009. Gaps are cheap; renumbering is unthinkable.
- **Compact-and-delete hybrid retention now.** Real use cases exist (bounding even the keyspace),
  but nothing needs it yet; the layout does not preclude it.

## Follow-up work items

1. Protocol + client: `KEYED` framing, tombstone convention, keyed publish API, validation.
2. Metadata plane: cleanup policy end-to-end (record, requests, appliers, query, auto-create).
3. Broker: manifest + clean-segment writer/reader, cleaner actor, key-hash map, deferred-delete
   queue; partition wiring behind the topic flag.
4. Raft integration: snapshot-content install path on bootstrap and InstallSnapshot; fetch
   composite reader + gap-skip boundary.
5. Tests: latest-per-key after sweep; determinism (two logs → identical clean sets); position
   preservation + gap-skip + out-of-range boundary; tombstone two-touch grace; crash at every
   cleaner phase; deletion checklist under a held zero-copy reader.
