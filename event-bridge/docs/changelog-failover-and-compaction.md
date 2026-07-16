# State changelogs, log compaction, and the failover story

- Status: design document (not an ADR — the decisions live in
  [event-bridge ADR 0001](adr/0001-compacted-topics-and-log-compaction.md) and
  [streaming ADRs 0005–0009](../event-bridge-streaming/docs/adr/); this document explains the
  whole machine in one place, honestly, including what is deliberately not built).
- Snapshot date: 2026-07-16. The "Status ledger" section at the end says exactly what is merged,
  what sits on branches, and what is still missing.
- Audience: someone who wants to understand how a stream-processing shard's state survives
  crashes, moves between machines in about one commit interval, and why the broker can promise
  all of that with a bounded amount of disk.

## 1. The problem

A shard folds records from a source partition into local state (RocksDB). Two things can happen
to it:

- **Crash/restart on the same machine** — it must come back without losing acknowledged work.
- **The machine dies** — another member must take over the partition, fast, and with the same
  state.

The naive answer — "replay the source from the beginning" — is unsound here, not merely slow:
source topics are retention-bounded (old records are deleted regardless of consumer progress),
while some state has unbounded age. A process-instance scope opened months ago is still live
state; the records that built it are long gone. So state must be recoverable from something
whose lifetime matches the *state's* lifetime, not the source's retention window.

The answer built in this initiative: every shard continuously replicates its state changes into
a **changelog** — a partition of a **compacted topic** — and the broker's compaction keeps that
changelog's size proportional to the *live keyspace*, not to history. Failover then means
"apply bytes from the changelog", never "re-execute history".

```
                       ┌──────────────────────────────────────────────┐
   source partition ──►│ ACTIVE SHARD: fold → local RocksDB           │
   (retention-bounded) │        every commit = a CUT                  │
                       └───────────────┬──────────────────────────────┘
                                       │ cut delta + marker
                                       ▼
                       ┌──────────────────────────────────────────────┐
                       │ CHANGELOG partition (compacted topic)        │
                       │ bounded by keyspace, lives forever           │
                       └───────┬──────────────────────┬───────────────┘
                               │ tail + apply bytes   │ read from start
                               ▼                      ▼
                        WARM STANDBY            COLD REBUILD
                        (~1 cut behind)         (O(keyspace), not O(history))
```

## 2. The foundation: every commit is a cut

Everything rests on one property of the streaming runtime (ADRs 0005/0007/0008): **all durable
effects of a shard happen at one atomic barrier — the cut.** Between barriers, the fold mutates
state freely and *nothing leaves the shard*. At the barrier:

```
   fold ... fold ──╢ BARRIER at source offset X ╟── fold continues ...
                   ║
                   ║ ① FREEZE: detach "everything that changed since the last
                   ║    completed cut" as one immutable object — the delta
                   ▼
        ② publish outputs (facts, serving rows)
        ③ produce the delta to the changelog + one MARKER record  ── wait for ack
        ④ ONE local RocksDB transaction: { delta, X, changelog position P }
        ⑤ commit the source offset X to the coordinator
```

Because the five steps are strictly ordered, every crash window has a known, benign outcome:

```
   crash after…      what the world looks like              what happens on recovery
   ───────────────   ────────────────────────────────────   ─────────────────────────────
   ① freeze          nothing durable anywhere               replay from the last cut —
                                                            as if the cut never started
   ③ changelog ack   changelog has the delta+marker;        rerun the cut: SAME keys
     (before ④)      local store still at the OLD cut       re-produced → compaction's
                                                            last-write-wins absorbs the
                                                            duplicates
   ④ local tx        state, X and P all agree locally;      resume; the offset commit is
     (before ⑤)      coordinator has the old offset         re-driven — marker ≥ group
                                                            offset is the invariant readers
                                                            lean on (§3)
   ⑤ offset commit   everything agrees everywhere           normal restart
```

No window requires repair, coordination, or a wipe — each one lands on "rerun or resume".

Three details carry the whole design:

**The delta is exact.** A cell created and deleted between two cuts never appears (absorption);
an unchanged cell is never re-written; a delete is only emitted for a cell some *completed* cut
actually wrote. These sound like optimizations; they are correctness features of the changelog —
without them it would carry phantom tombstones and full-image re-writes (see §4).

**The ordering is the correctness argument.** The changelog is acknowledged strictly *before*
the local transaction, which happens strictly before the source-offset commit. So the changelog
is the **outer truth** and local RocksDB is only a restart accelerator: local state can never
claim durability the changelog does not have. A crash between ③ and ④ leaves duplicate keyed
records in the changelog — harmless, because compaction is last-write-wins and the rerun cut
re-produces the same keys.

**{Δ, X, P} are welded.** State, source position, and changelog position commit in one RocksDB
write batch. After any crash, local state *is* some completed cut — never a fuzzy in-between.
This is why a restart is "open the store, read {X, P}, continue" and never "wipe and rebuild"
(the fate of systems whose local state and progress live in separate places).

The price, stated honestly: output latency is quantized to the cut cadence. Nothing is visible
downstream until the next barrier. For analytics pre-aggregation this is a non-cost; an
application needing per-record millisecond emission would be using the wrong runtime.

## 3. The changelog: state replication, never an event feed

Per cut, the shard appends to its own changelog partition:

```
   ── put(key₁,row) ── put(key₂,row) ── tombstone(key₃) ── MARKER{X} ──
                                                            ▲
      one keyed record per changed cell,                    always the LAST
      one tombstone per (previously persisted,              record of the cut,
      now deleted) cell                                     carries the cut's
                                                            source offset X
```

- **Keys are self-describing.** Each record key is `cfTag(1 byte) + storeKey`, so one changelog
  carries every column family a shard owns (projection rows, open-segment cells, dedup
  watermarks). The marker's reserved key is exactly 4 bytes while every enveloped key is ≥ 5 —
  collision-impossible *by length*, no matter the content.
```
     an enveloped record key:                      the marker key:

     ┌────────┬──────────────────────────┐         ┌────────────────┐
     │ cfTag  │ store key bytes          │         │ 4 reserved     │
     │ 1 byte │ ≥ 4 bytes (every CF's    │         │ bytes          │
     │        │ key type guarantees it)  │         └────────────────┘
     └────────┴──────────────────────────┘
     total: ≥ 5 bytes ────────────────────► always LONGER than the marker's 4:
                                            collision-impossible by length alone,
                                            independent of content
```

- **The marker doubles as the progress report.** Anyone reading the changelog learns, atomically
  with the state, how far the source was processed. There is no separate progress channel to
  race against the state (and the marker is always ≥ the coordinator's committed offset, because
  of the ordering in §2 — so the marker, not the group offset, is the resume authority).
- **An empty cut still writes its marker** if X advanced: a shard that filtered everything for an
  hour must still be resumable at the right offset.
- **The contract: the changelog says *what is*, never *what happened*.** Born-and-died keys are
  invisible; mid-interval values coalesce away. Anything needing business events reads a source
  topic. This is normative — a future consumer treating the changelog as an event feed is a bug
  in that consumer.
- **Halt discipline.** The cut chain ends with the coordinator-fenced offset commit. If that
  commit is *rejected* (the shard was deposed), the shard halts immediately — complete the cut
  locally (changelog and local tx already landed), stop, never retry. This is the interim fence
  (§7) and it is contractual, not incidental.

## 4. Why the exact delta matters: a worked example

Take a cut interval where a process instance scope `S` was created and completed, variable `V`
changed twice, and nothing else happened:

```
   naive changelog (per-put write-through):     our changelog (the cut delta):

   put(S, created)                              put(V, v2)
   put(V, v1)                                   MARKER{X}
   put(S, completed)
   put(V, v2)
   tombstone(S)          ← S never mattered!
   MARKER{X}

   5 records + a tombstone the broker must      1 record. S is absorbed — it
   carry through a grace window, for a key      lived and died between barriers
   that no rebuild will ever need               and never touches the wire
```

Multiply by millions of short-lived scopes under load and the difference is the difference
between a changelog that compaction keeps small and one that feeds the broker's most expensive
path (tombstone grace, §5) with garbage. The three "gap fixes" that made deltas exact
(completed-cut-only deletes, delta-only freeze, moved-only watermarks) are why the sentence
"the changelog record set *is* the cut delta" is literally true in the code — the changelog
records are derived from the same frozen structures the local persist writes, not computed
separately.

## 5. Log compaction: how the broker keeps the changelog bounded

The changelog would grow forever without help. The broker's **log compaction** (ADR 0001)
continuously rewrites old history down to the latest record per key. The full machinery, honestly:

### 5.1 The shape on disk

```
   changelog partition on the broker:

   ┌─ Raft snapshot ───────────────────────┐  ┌─ live Raft log ────────────────┐
   │ manifest + clean segments             │  │ dirty zone + active head       │
   │ = compacted history: latest-per-key,  │  │ = recent cuts, raw,            │
   │   at their ORIGINAL positions         │  │   not yet swept                │
   └───────────────────────────────────────┘  └────────────────────────────────┘
```

A reader consumes clean set first, then the live log — one seamless position sequence with gaps
where superseded records used to be. Fetching a swept position returns the next surviving record
(*gap-skip*); positions are **never renumbered**, which half this document depends on.

### 5.2 The cleaner

A background actor per replica runs a deterministic pass — a pure function of (committed log,
committed manifest, config), with **no coordination between replicas** (identical inputs,
identical outputs, byte for byte; there is a test that pins this with two independent runs):

1. Pick the cleaner point C = last committed position − a configured minimum lag (tailing
   readers always see raw recent history).
2. One scan of the dirty zone builds a bounded map: 128-bit key hash → latest position.
3. Sweep: copy forward previous clean set + dirty zone, keeping each record only if it *is*
   its key's latest. Survivors are re-wrapped as **single-entry batches** — each batch header
   already carries its own absolute position, so position gaps need no format, reader, or SDK
   change at all.
4. Commit: the new clean set + manifest are persisted **as the partition's Raft snapshot** at C.
   This is the only durability point — a crash anywhere earlier leaves the previous snapshot
   authoritative and the pass just reruns. Raft then truncates the raw log below C and ships the
   snapshot to lagging replicas via its normal install machinery. Nothing was built for any of
   that; it is the retention marker-snapshot mechanism, generalized to a snapshot with content.
   (Corollary worth savoring: **the snapshot doesn't include the changelog — it *is* the
   changelog, compacted.** One artifact, no snapshot-vs-log seam to manage.)
5. Replaced files enter a deferred-delete queue.

```
   one pass, end to end:

        ┌──────┐   pick C     build map      sweep        COMMIT        tidy
   ┌───►│ IDLE │──────────►──────────►──────────►══════════════►──────────►┐
   │    └──────┘  committed  keyHash →     copy-forward  fsync + persist   │
   │              head − lag  latest pos    survivors     as Raft snapshot  │
   │                                                      ▲                │
   └──────────────────────────────────────────────────────┼────────────────┘
                          crash ANYWHERE left of the ═════╝ commit:
                          previous snapshot is still the truth; the
                          half-written files are swept as orphans; rerun
```

### 5.2b A worked example

A changelog partition carrying three keys, before its first compaction pass. `V` is a variable
that changed three times, `S` a scope that was deleted, `W` a dedup watermark:

```
   position:   100      101      102      103      104      105      106      107
             ┌────────┬────────┬────────┬────────┬────────┬────────┬────────┬────────┐
             │ V=1    │ S=open │ W=4200 │ V=2    │ ✝S     │ MARKER │ V=3    │ MARKER │
             │        │        │        │        │(tomb-  │ {4650} │        │ {4710} │
             │        │        │        │        │ stone) │        │        │        │
             └────────┴────────┴────────┴────────┴────────┴────────┴────────┴────────┘

   the cleaner runs with C = 107 (head minus the min-lag window, say):

   ① map:    V → 106     S → 104 (the tombstone IS S's latest)
             W → 102     MARKER-key → 107

   ② sweep — keep a record only if it is its key's latest:

   position:            102               104               106      107
             ┌────────┬────────┬────────┬────────┬────────┬────────┬────────┐
             │  gap   │ W=4200 │  gap   │ ✝S     │  gap   │ V=3    │ MARKER │
             │ (100,  │        │ (103)  │ kept + │ (105)  │        │ {4710} │
             │  101)  │        │        │ GRACE  │        │        │        │
             └────────┴────────┴────────┴────────┴────────┴────────┴────────┘
                        ▲                ▲                          ▲
             positions PRESERVED —    the tombstone survives     only the LATEST
             V=1, S=open, V=2 and     its first sweep (§5.3);    marker survives —
             the old marker leave     a later pass may drop      it's just a keyed
             GAPS, never renumbering  it after grace             record like any other

   ③ a rebuild reading this clean set gets exactly: W=4200, S deleted, V=3,
     resume the source at 4711 — the full correct state, in 4 records instead
     of 8, no matter how many more times V changes later.

   a fetch for position 100 (swept) returns the record at 102 — GAP-SKIP:
   "the next surviving record at or after what you asked for."
```

### 5.3 Tombstones and the log clock

A tombstone ("key deleted") exists for readers who already saw the old value — drop it too early
and a mid-rebuild reader resurrects the key. So tombstones get a grace window with a two-touch
rule: survive the first sweep, die only in a later pass once the grace has elapsed. The subtle
decision is *whose clock measures the grace*: per-replica wall clocks would make replicas'
clean sets diverge and an NTP jump could expire a tombstone early. Instead the cleaner uses the
**log clock** — the running maximum of record timestamps up to C, persisted in the manifest —
so every input to the expiry decision is replicated log content and the whole manifest stays
byte-identical across replicas. An idle partition freezes the log clock and simply *retains*
boundary tombstones until traffic resumes: the safe failure direction. Sizing rule that no
design removes: **grace must exceed the longest plausible rebuild.**

Continuing the example — the life of `✝S` (its record timestamp is `t=900`):

```
   pass 1 (log clock 1000):  ✝S is S's latest → swept into the clean set, KEPT
                             (two-touch: a tombstone never dies on its first sweep)

   pass 2 (log clock 1400):  grace = 600? 1400 − 900 > 600 AND ✝S ≤ previous C
                             → DROPPED. position 104 becomes a gap; S has vanished
                             entirely, as if it never existed — correct, because
                             any reader who could have seen "S=open" started its
                             rebuild more than a grace window ago and is done.

   the counterexample the rule prevents: drop ✝S in pass 1, and a reader that
   already applied "S=open" from position 101 finishes its rebuild believing S
   still exists — a resurrected key, the classic compaction data-loss bug.

   and if a NEW put S=reopened lands at position 300 (t=1500)? The latest-per-key
   rule supersedes the tombstone immediately: ✝S dies at the next pass regardless
   of grace (a newer value exists; nobody needs the delete anymore).
```

### 5.4 Deletion safety

A superseded clean file is unlinked only when: it is not referenced by the newest snapshot, its
replacement is durable, and its reader-lease count is zero — where a lease is acquired
*atomically with opening the file* (an open descriptor keeps bytes readable across unlink on
POSIX), and deletion is a two-step *condemn by rename, then unlink after the count re-check*.
This mirrors the journal's own segment-deletion pattern and exists because the naive
count-then-delete has a provable race (found by adversarial review, fixed before merge). There
are **no index files anywhere** — clean segments self-frame by batch length, the manifest's
first-position list gives O(log n) segment selection, and the live log uses the journal's
in-memory sparse index. Derived state on disk is a bug class this codebase has paid for once
(a persisted fetch index that was rebuilt-from-scratch on every load anyway, and whose absence
on followers caused a real failover incident); it is not welcome back.

```
   the deletion interlock (why no reader ever holds vanished bytes):

   reader thread                              cleaner (trash queue)
   ─────────────                              ─────────────────────
   acquire(file):                             drain():
     count++  ─── ATOMIC, then ───┐             file unreferenced by manifest?
     OPEN the file                │             ① CONDEMN: rename to *-deleted
       ├─ open OK: the open FD    │             ② re-check count
       │  keeps bytes readable    │                ├─ 0 → unlink now
       │  even past a later       │                └─ >0 → the LAST release()
       │  unlink (POSIX)          │                        unlinks it
       └─ open fails (file was
          condemned first):
          release count, RETRY
          against the fresh
          manifest — never an
          error for data that
          exists

   the happens-before chain: a successful open ⇒ the count was visible before
   the condemn's re-check ⇒ the file cannot be unlinked under the reader.
```

## 6. The recovery ladder

All recovery is one reader with two rules, started at different positions.

**The two rules:**

```
   MARKER RULE   find the highest marker; apply ONLY records at positions ≤ it.
                 Records beyond the last marker are a torn cut (the writer died
                 mid-publish) and must be invisible — half-applied cells without
                 their dedup watermark would double-merge on replay. Sound across
                 compaction because positions never change.

   CUT-ATOMIC    when tailing live, buffer records until their cut's marker
   APPLY         arrives, then apply the whole cut in ONE local transaction
                 { rows, X, P } — the same welded shape the active writes.
```

**The rungs:**

```
   ① INTACT-DISK RESTART      open local store, read {X, P} — state IS the last
                              completed cut. Resume the source at X+1. No
                              changelog read at all. (milliseconds)

   ② WARM STANDBY (failover)  a standby has been tailing the changelog all
                              along, cut-atomically, ~one cut behind. Promotion:
                              drain to the last marker, hand the SAME open
                              stores to the fold, resume source at X+1.
                              (~one cut interval)

   ③ COLD REBUILD             read the compacted changelog from position 0,
                              apply ≤ the last marker, resume at X+1.
                              (O(live keyspace) — bounded by compaction,
                              independent of history and of source retention)

   ④ (future, designed)       download an uploaded RocksDB snapshot from an
                              object store, then tail the changelog from the
                              snapshot's own P+1. A pure ACCELERATOR for rung ③
                              at very large keyspaces — its failure degrades to
                              rung ③, never to wrongness. Not built; trigger and
                              design are recorded.
```

A worked failover, concretely:

```
   active on member A:  ...cells... MARKER{X=4200} ...cells... MARKER{X=4650} ⚡ dies
                                                               ▲ mid-next-cut: a few
                                                                 cells published, no
                                                                 marker — torn tail

   standby on member B: applied through MARKER{4650}. The torn cells sit in its
                        buffer, never applied (cut-atomic rule).

   coordinator:         B's heartbeats reported readiness (changelog lag ≈ 0);
                        the assignor promotes B — and only B-grade members: a
                        cold member is never promoted; with no ready standby the
                        partition WAITS (correctness over availability).

   B promotes:          drain to the changelog end — last marker still 4650 —
                        discard the torn buffer, flip the same open stores to
                        the fold, resume the SOURCE at 4651, publish cuts.

   the fold on B cannot tell this from an ordinary restart. That is the design's
   central claim, and it is pinned by the keystone test: run a workload through
   an active, apply its changelog into fresh stores, compare BYTE FOR BYTE.
```

Why byte-equivalence is the keystone: in the workflow-engine pattern, leader and follower share
the applier *code path*, which guarantees equal state. Our active never applies its own
changelog (it folds directly; the changelog is derived from the same frozen delta it persists),
so the shared-code guarantee is replaced by a shared-*bytes* guarantee — upheld by construction
(one frozen object feeds both destinations) and enforced by that test. If someone breaks the
mirror, the test — not a customer — finds out.

And note what is *absent* from the whole ladder: no snapshot store as a correctness dependency,
no determinism contract on the fold (standbys apply bytes; they never re-execute logic), no
coupling to source retention (the changelog outlives everything), and no state wipe after
unclean shutdown (the welded {Δ, X, P} makes local state provably consistent).

## 7. What is deliberately NOT built, and what stands guard meanwhile

Honesty section. Each item is a recorded decision, not an oversight.

**Producer fencing is deferred** (ADR 0009 decision 4, as amended). A deposed zombie shard can
still append to the changelog. Interim guards, both contractual: source-offset commits are
coordinator-epoch fenced, and the halt discipline (§3) makes a zombie stop at the end of its
next cut — bounding the blast radius to **one zombie cut per failover**, whose content is a
deterministic fold of source records the successor also folds. The residual risk (a zombie cut
interleaving a concurrent rebuild near the marker) is accepted, bounded, and converging. The
full fencing design — per-producer epochs, engine-pattern replay recovery, and the
key-disjointness contract that makes broker transactions unnecessary — is settled in principle
and parked with seven open questions in
[multi-producer-compacted-topics.md](multi-producer-compacted-topics.md); the wire format
already exists on a parked branch.

**The promotion lifecycle is not yet wired into the runtime.** The `ChangelogApplier`,
`PartitionRoleController` (role flips over the same open stores), assignor roles, readiness, and
promotion protocol exist and are tested — but today's runtime still tears down and rebuilds
shards on every role change. Wiring role flips through the controller (plus the real-Task
integration and a genuine two-member promotion test) is the follow-up that turns the components
into an actual failover. Until it lands, the milestone is component-complete, not system-complete.

**Cluster-level proofs.** Multi-replica cleaner convergence, InstallSnapshot arrival, and
two-node failover need a real cluster smoke run (scheduled for a quiet-machine window). Unit and
integration tests cover everything below that seam.

**Small parked items**: gateway fast-fail for un-keyed publishes to compacted topics (the
broker-side rejection exists and is authoritative); a client-facing way to set
`standby.replicas` (today only settable at the membership-record level); snapshot-accelerated
cold start (§6 rung ④).

## 8. The invariants — what must never be broken

If a future change violates one of these, some part of this document silently stops being true.

| invariant | what depends on it |
|---|---|
| positions are preserved forever; compaction leaves gaps, never renumbers | the marker rule across compaction, gap-skip fetch, changelog-since-P, offset resume |
| the marker is strictly the last record of every cut | torn-cut detection, cut-atomic apply, resume authority |
| changelog ack strictly before the local commit, before the offset commit | "changelog = outer truth"; marker ≥ group offset; crash-duplicate absorption |
| {Δ, X, P} commit in one local transaction | wipe-free restart; self-describing snapshots (future rung ④) |
| changelog records derive from the same frozen delta the shard persists | byte-equivalence of standbys; delta trueness end-to-end |
| every key has exactly one owning writer (ownership prefix in the key) | cut atomicity without transactions; per-lane progress; the deferred fencing design |
| the cleaner pass is a pure function of replicated data (incl. the log clock) | replica-local compaction without coordination; byte-identical manifests |
| tombstone grace ≥ the longest plausible rebuild + snapshot staleness | no key resurrection for mid-rebuild readers |
| a rejected offset commit halts the shard, no retry | the one-zombie-cut blast radius while fencing is deferred |
| no derived state persisted on disk (no index sidecars) | the "log and manifest are the only truth" recovery story |

## 9. How this sits relative to the neighbors

### 9.1 Kafka Streams

Kafka Streams replicates state to changelogs too, but writes them per-mutation through a
pressure-leaky cache (no cut → no markable consistent instant → no marker), keeps progress in a
separate log, and therefore must rent atomicity from broker transactions for exactly-once —
paying with reader head-of-line blocking and the local-state wipe after unclean shutdowns. Our
cut is the difference: it makes "state as of X" a stampable statement, so one reserved key and
one read rule replace that machinery.

### 9.2 Flink

Flink snapshots RocksDB to object storage under coordinator-driven barriers because its
operator edges are volatile network channels — a consistent recovery point must be global. Its
changelog state backend (FLIP-158) is our design mirrored: there the snapshot is the authority
and a short-term changelog patches its staleness; here the changelog is the authority and
broker compaction *is* the materialization, running server-side, so workers upload nothing.

```
                     FLINK (FLIP-158)              THIS SYSTEM
   authority         materialized snapshot         the compacted changelog
   accelerator       the changelog (short-term)    local RocksDB
   log bounded by    truncation after              compaction, server-side,
                     materialization               forever
   workers upload    changelog drizzle + SSTs      NOTHING — the broker derives
                     (both, to blob storage)       the bounded form from the
                                                   stream itself
```

### 9.3 The shuffle: durable log vs. wire — what each buys and loses

Between our two stages sits a durable topic (the facts topic). Flink would put a direct network
channel there instead. This choice is *the* fork in the road between the two architectures, so
it deserves its own honest treatment.

```
   LOG SHUFFLE (ours):    Stage-1 ──publish──► topic (broker) ──fetch──► Stage-2
                          durable, replicated, positioned, replayable

   WIRE SHUFFLE (Flink):  Stage-1 ══ RAM buffers, credits, TCP ══► Stage-2
                          exists for microseconds, dies with either side
```

**What the wire buys.** One broker hop removed per shuffle: a replicated append plus a fetch —
milliseconds and broker disk/IO — becomes microseconds of RAM-to-wire. For *deep* topologies
this compounds: a ten-operator Flink job saves nine hops on every record, which is exactly the
cost structure Flink was built for.

**What the wire costs.** Everything the log was silently doing becomes a subsystem to build:

```
   the log's quiet job                    what the wire needs instead
   ─────────────────────────────────     ─────────────────────────────────────────
   the topic IS the address —            a live ROUTING TABLE (shard → member
   producers don't know consumers        host:port), fed by the coordinator,
                                         re-resolved on every rebalance, with a
                                         protocol for records in flight when
                                         ownership moves — plus a member-to-member
                                         TRANSPORT that does not exist today
                                         (members are gateway clients)

   a slow consumer just lags;            CREDIT-BASED FLOW CONTROL, and slowness
   the log absorbs on disk               PROPAGATES: Stage-2 stalls Stage-1's fold,
                                         which stalls source consumption — latency
                                         coupling replaces absorbed lag

   the edge is replayable —              in-flight records die with a crash, so
   each stage recovers ALONE             recovery needs one of two expensive doors:
   from its own {X, P}                     A) GLOBAL barrier checkpoints across both
                                              stages (coordinator, barrier records in
                                              the channels, ALIGNMENT in Stage-2
                                              across its many Stage-1 inputs, and
                                              any failure rewinds EVERYONE — the
                                              full Flink apparatus)
                                           B) SENDER RETENTION: Stage-1 durably keeps
                                              its shuffle output until Stage-2 acks
                                              committing it, and re-sends on recovery
                                              — i.e. re-implementing a durable,
                                              acked, replayable log inside Stage-1's
                                              RocksDB to avoid using the one we run

   the standby story of §6 —             Stage-2 has no replayable source anymore;
   "resume the source at X+1"            promotion must coordinate with Stage-1 to
                                         regenerate the shuffle since the checkpoint
```

**The verdict, as arithmetic.** The apparatus amortizes over hops. Flink's wire shuffle pays
for itself across deep DAGs; our topology is two stages with **one** shuffle, whose consumers
are dashboards that cannot perceive milliseconds. We would buy transport, routing, flow control,
and either global checkpoints or a homegrown log — and give up independent stage recovery,
absorbed backpressure, and the failover design in §6 — to save one hop nothing is waiting for.
The durable-log shuffle is not a compromise here; for shallow topologies over an owned broker it
is the correct design, and the wire is Flink's correct design for the opposite cost structure.
Neither is wrong; they are answers to different shapes.

## 10. Status ledger (2026-07-16, end of session)

Merged to `roman/optimize`:
- journal fetch scan without index files (+ a pre-existing journal-reopen fix)
- exact cut deltas (completed-cut deletes; delta-only freeze)
- ADRs: event-bridge 0001 (compaction, incl. position-based C and log-clock grace);
  streaming 0009 (changelog, incl. failover-first amendment); 0005 amendment notes
- keyed records + tombstones; per-topic cleanup policy (COMPACT/DELETE)
- the compaction library (cleaner, manifest, clean segments, trash queue + leases) — reviewed
  by adversarial crash-hunt, all findings closed
- broker integration: policy→partition wiring, snapshot-as-manifest, composite fetch,
  un-keyed-publish rejection
- the changelog cut stage for Stage 2 and Stage 1 (all column families), marker, key envelope,
  halt discipline

On branches, verified, merge pending:
- `roman/eb-stage1-watermark-delta` — moved-only watermark persistence (one commit)
- `roman/eb-standby-failover` — standby roles/readiness/assignor, `ChangelogApplier`,
  `PartitionRoleController`, keystone byte-equivalence test (PASS). One commit needs correction
  before merge (ADR 0006 text must be the authored original, without the self-granted sign-off).

Parked: producer fencing (branch + design doc + seven questions); snapshot-accelerated cold
start (designed, trigger recorded).

Next: runtime wiring of the promotion lifecycle; then the two-node failover smoke.
