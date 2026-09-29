# Object-Store Log Sharing — Retiring the Event Bridge

Status: design discussion record (2026-07-24). Nothing here is built; this documents a full-day
architecture discussion so it is not lost. Companion to `lake-greenfield-architecture.md` (whose
C1/C4 contracts this amends — see §13) and grounded in a code audit of the Zeebe continuous-backup
feature (§5.3).

**Verdict in one paragraph.** The event bridge should be retired as the egress/transport tier.
Each cluster continuously ships its own log — once — into a per-cluster object-store prefix that is
simultaneously the backup, the point-in-time-recovery source, and the egress API. Consumers read
the bucket directly; there is no broker, no egress service, and no server in any read path. The
engine's commit path is untouched: Zeebe acks on Raft (milliseconds); shipping runs asynchronously
behind durability. Consumer freshness is 2–4 s via a polled chunk tier; sub-second consumption is a
named, deferred option (a stateless gateway tail-stream API), to be added only when a consumer
actually needs < 2 s.

---

## 1. Why: what the event bridge charges vs. what it provides

Count the copies. EB story: every byte exists ~8× (3× cluster Raft log, 3× EB block storage, 1× EB
long-term tiering to object store, 1× derived analytics tables). Proposed story: 5× (3× Raft, 1×
archive — already paid for as backup, 1× derived tables). The three deleted copies are the
expensive ones: replicated block storage (~3–4× the $/GB of S3), inter-AZ replication traffic on
every byte (the dominant line item in broker TCO analyses), and a peak-sized broker fleet burning
24/7 through idle nights. For a SaaS with thousands of mostly-idle clusters the broker tier forces
a bad choice (fleet-per-cluster vs. multi-tenant broker with isolation machinery); a per-cluster
bucket prefix has native isolation and costs ≈ storage when idle.

What EB actually provided, and each item's replacement:

| Broker job | Replacement |
|---|---|
| Durable replicated transport | The object store (11 nines), already paid for as backup |
| Retention decoupled from the source | Bucket lifecycle rules; cold-tiering makes *long* retention cheap |
| Fan-out with per-consumer positions | Immutable objects; every reader keeps its own position |
| Consumer-group coordination | Never a platform concern — each consumer coordinates in its own store (§7) |
| Sub-second tail latency | Genuinely lost on this path; deferred tail API (§6) when a consumer needs it |

The batching-vs-freshness trade-off is universal for any *copy* of a stream — what differs is the
constants. A broker append over a persistent connection group-commits at millisecond floors with no
per-request price; an S3 PUT has ~30–100 ms latency and per-request pricing, bottoming out around
1–2 s economically. EB occupied the middle rung: more copies and machinery than a zero-copy tail
read, for a latency floor nobody was consuming. The rung has no tenant.

## 2. The shape

```
 CLUSTER (per cluster, ×N)                          CONTROL PLANE (a role, §6)
 ┌───────────────────────────────────────┐          ┌────────────────────────────┐
 │ partitions · Raft log (commit = Raft) │ register │ registry: cluster → bucket,│
 │   └─ ARCHIVER  per partition, leader- │ ───────► │ prefix, creds, versions    │
 │      side, journal-level, async       │          └──────────┬─────────────────┘
 │      (NOT an exporter)                │                     │ discover + vend creds
 │      trims local log only below X     │                     ▼
 └───────────────┬───────────────────────┘          CONSUMERS (each self-coordinated)
                 │ chunks (1–2s) · segments on roll   analytics lake · webapp ingestion ·
                 ▼ manifest CAS                       warehouse export · integrations
 OBJECT STORE  s3://…/cluster-a/           ◄───────  poll chunks seq+1 (live, 2–4s)
   manifest.json          (topology)       ◄───────  ranged GETs + prefetch (history)
   partition-1/manifest.json (X, F, epoch)
   partition-1/chunks/…    partition-1/segments/…
```

Prime rules (everything else follows):

- **R1 — The archive is the contract.** Correctness and full history live in the bucket. Anything
  else (tail API, caches) is an accelerator a consumer can lose safely.
- **R2 — One coordinate.** Chunks, segments, and the broker's local log share the Zeebe log
  position. Crossing any seam is idempotent by position dedup. Cluster id joins the origin
  coordinate tuple (one consumer fans in from N clusters).
- **R3 — One writer per object; one commit point per unit.** Every write is a conditional PUT at a
  deterministic next name. Existence commits a chunk; manifest reference commits a segment; the CAS
  swap commits a manifest. "Died in between" leaves only provably-uncommitted litter.
- **R4 — Trim rule.** The broker deletes local log segments only below the archived boundary X, so
  bucket and local log always overlap; a gap between tiers is structurally impossible. (This gate
  already exists in the continuous-backup code — §5.3.)
- **R5 — Consumer coordination is not a platform concern.** The platform provides order, positions,
  manifests, heartbeats. Groups, leases, HA, scaling belong to each consumer, in its own store.
- **R6 — No service ever carries bulk data.** APIs describe, authorize, and (later) stream the
  tail. The moment archive bytes flow through a service, the broker is growing back.

## 3. The bucket: three layers, one position space

Per partition, the position axis is covered by segments up to the coverage boundary **X**, chunks
from X to the flushed frontier **F**, and nothing above F (broker-local only). The broker's local
log extends from ≤ X to the live head (R4).

| Layer | What | Commit point | Deleted when | Writer |
|---|---|---|---|---|
| chunks | seq-named byte-slices of the *open* journal segment; per partition; adaptive 1–2 s cadence; empty chunk = idle heartbeat | its own PUT (self-contained) | X passes its range (lifecycle) | partition archiver |
| segments | rolled journal files, **byte-identical** (restore-grade) + sparse `.idx` sidecar (position → byte offset; sidecar because the file must stay byte-identical) | referenced by the partition manifest | retention policy (manifest updated first); lifecycle → cold tier | partition archiver |
| partition manifest | segment list + ranges, X, F, writer epoch | the CAS swap itself | old versions after N newer | partition archiver (+ low-rate janitor) |
| cluster manifest | topology only: partitions + birth positions, format versions | CAS swap | — | topology owner (slow path) |

Key consequences:

- **A chunk is literally the next byte-slice of the eventual segment.** "Merging chunks into
  segments" is not a rewrite — the rolled segment supersedes its chunks bit-for-bit. One format
  everywhere; one decoder in the reader.
- **Fast-changing fields live only in single-writer objects.** Frontiers are in partition
  manifests, never the cluster manifest (partition leaders are distributed; a shared frontier
  object would be a multi-writer hotspot). Same reason chunks are per-partition, not per-cluster.
- **Reading at position P, cold: 3 requests** (partition manifest → `.idx` → ranged GET), then
  sequential ranged GETs. Steady-state freshness: `GET seq+1` on the chunk sequence; 404 = caught
  up. No LIST in any hot path; no manifest write per flush.
- Records are framed with per-frame CRCs (journal format) and carry their Raft term; compression,
  if any, must be block-granular so ranged GETs survive.

## 4. Fencing: the one protocol, used everywhere

There are no plain PUTs. Every data write targets the deterministic next name with
create-if-absent (`If-None-Match: *`); every manifest update is a CAS. Consequences:

- Two writers targeting the next slot race on the same key; the store picks exactly one winner.
  Writers are strictly sequential (win slot N before attempting N+1), so a loser is stuck — **a
  failed conditional write is the fencing notification**. It re-reads the manifest, finds a higher
  epoch, halts.
- The **epoch is the Raft term**, stamped inside every object (journal entries already carry it;
  chunk/manifest wrappers add the uploader's term). Epochs are non-decreasing along every
  sequence — an auditable invariant.
- Takeover: new leader's archiver CAS-bumps the epoch in the partition manifest, then fences the
  chunk sequence by winning the next slot (retrying forward if the zombie wins a race). Everything
  the zombie durably wrote *before* the fence is honored (it may have been observed); nothing after
  it can exist.
- Objects that died before their commit point are **adopted or swept**: verdict by commit point,
  never by writer death; deletion only after the commit state is *confirmed* (resolve-then-sweep,
  never delete-on-doubt); safe because of R4 (the source still holds anything above X). Deterministic
  names + content mean the successor's colliding PUT usually verifies and adopts rather than
  re-uploads.
- S3 PUT atomicity (single and multipart) means no torn objects exist, ever; strong read-after-write
  (since 2020) means a successful GET returns the complete slot winner.

## 5. Producer side: the archiver

### 5.1 Placement — a journal-level component, not an exporter

The archiver must **not** be an exporter: exporters sit in the stream processor's export path (lag
holds back compaction; work rides the engine's hot loop), see decoded records rather than the log
(segments could never be byte-identical), and put third-party-adjacent code in the engine's failure
domain. The archiver is a leader-side partition component reading **committed journal bytes** below
the engine — a sibling of the backup service (`BackupServiceTransitionStep` is the placement
template).

### 5.2 Behavior

Per partition, on the leader: ship chunks (adaptive cadence — flush on data, slow heartbeat when
idle; empty chunks advance watermarks), roll segments (upload byte-identical file + build `.idx`
during upload), publish the partition manifest (X, F, epoch) via CAS. Liveness *is* the chunk
stream: "no new chunk beyond max-heartbeat" is the takeover suspicion signal, and it is principled —
in this design, being alive means being able to commit to the bucket.

### 5.3 Continuous backup: build on its layers, not its output

A code audit (2026-07-24) of the recently-landed continuous backup feature (`BackupCfg.continuous`,
default false; 8.9-era, actively hardened) concluded: **reuse the plumbing and invariants; the
bucket artifact is the wrong shape for consumption.**

Reusable as-is:

- **The trim gate exists.** With `continuous=true`, `DbPositionSupplier.getHighestBackupPosition()`
  caps compaction at the last confirmed backup position — R4, implemented and integration-tested
  (`ContinuousBackupIT`). The archiver feeds its X into the same seam.
- **Leader-side, log-driven execution**: checkpoint commands on the log (`CREATE` → upload →
  `CONFIRM_BACKUP`), state replicated via Raft, in-progress backups failed on leader change.
- **Raw byte-identical segment upload** (`FileSetManager.saveFile` streams files as-is), the
  `BackupStore` SPI (S3/GCS/Azure/filesystem), and the per-partition `metadata.json` rollup with
  range entries (`BackupMetadataSyncer`) — the ancestor of our partition manifest. Restore is
  already range-aware (`RestorePointResolver` verifies contiguity, stitches multi-backup ranges).

Gaps (each is a planned delta, not a rework of their machinery):

- Backups are **checkpoint-keyed overlapping sets** re-uploading the full segment tail each cycle —
  not a linear, position-named, upload-once sequence. Consumers cannot tail it.
- Trigger is schedule-driven only (CRON/interval via a lowest-member-id cluster singleton); no
  freshness tier.
- **The S3 store uses no conditional writes anywhere** (plain `putObject`; a zombie ex-leader can
  overwrite manifests). Azure/GCS stores already use preconditions — adding `If-None-Match`/ETag
  CAS to the S3 store fixes a latent backup gap and is the foundation of §4.
- No per-file checksums in manifests; no `.idx`; an unimplemented `TODO` for restore-time
  consistency checks.

**The convergence play**: make the linear archive the primary artifact, and backup degenerates to
*(snapshot + archived-segment-range pointer)* — no segment re-upload ever again. This turns the
archiver from an analytics accessory into a platform feature that subsumes backup's most expensive
part. `JournalInfoProvider.getTailSegments` is the segment-collection seam to extend for
incremental semantics.

## 6. Freshness tiers and the deferred tail API

- Engine latency: unchanged (Raft, ms). S3 is never in any ack path.
- Consumer freshness: 2–4 s (chunk cadence + poll). Chunk PUT costs ≈ $4–13/month/partition at
  1–2 s cadence; adaptive cadence makes idle ≈ storage-only. S3 Express One Zone (~10–20 ms PUTs,
  short retention profile) is a drop-in upgrade for the chunk tier if a few-hundred-ms floor is
  ever wanted at request-cost premium.
- Sub-second consumers get a **stateless gateway tail-stream API** — MongoDB change-stream model:
  `stream(partition, fromPosition)`, server keeps no consumer state (the position is the resume
  token), record filter, idle heartbeats, "below local floor → go to the archive". Zero-copy (it
  serves the log the broker already wrote for consensus), hence off the batching-vs-freshness
  curve entirely. **Deferred with an explicit trigger: a consumer needing < 2 s.** R4 guarantees
  the tiers always overlap, so adding it later is purely additive.
- Anything needing sub-second *today* (job push, task notifications) stays in-cluster, as now.

## 7. Control plane: a role, not a required service

All data-plane-critical metadata lives **in the archive**, written by the cluster (manifests are
self-describing). The control plane holds only pointers and permissions:

- **SaaS**: Console keeps the registry (cluster → bucket/prefix) and vends short-lived scoped
  read-only credentials (the Iceberg-REST-catalog / Delta-Sharing pattern: the API grants access;
  bytes flow storage-to-consumer).
- **Self-managed**: the registry is consumer configuration — a static list of (bucket, prefix,
  creds); manifests do the rest. No new required component.

## 8. Consumer side

### 8.1 Runtime library (the read-side twin of the reader contract)

The atom is a stream = (cluster, partition); a consumer's progress is a **position vector** in its
own store. Library layers: discovery (registry + manifests → stream set, including partitions born
later); chunk poller (`seq+1`, 404-loop); segment reader (3-request random access; K-deep prefetch
of 8–16 MB ranges → catch-up is consumer-NIC-bound — S3 is never the reason a consumer lags; the
lifecycle policy must keep the plausible catch-up window in instant-access storage classes);
position-vector checkpointing with effects; lease-based assignment (below) with pluggable lease
stores (JDBC / S3 conditional writes / k8s).

### 8.2 Leases: liveness by peers, safety by the sink

One lease table in the consumer's own database (for the lake: the same Postgres/H2 catalog it
already runs). One row per stream at **cluster granularity**; `leaseCounter` is the fencing token;
every mutation is a CAS conditioned on it. Every worker runs the same loop: heartbeat what I own
(counter++), scan all rows, judge expiry with **my own clock** (remember (counter, whenISawIt);
never compare wall-clock across machines), claim expired rows / steal one-at-a-time toward fair
share. Peers check liveness; a single-worker deployment checks nothing. Kubernetes keeps N workers
alive (outer loop); leases distribute streams among the living (inner loop).

Division of labor: **leases decide who should work (allowed to be wrong); the idempotent sink
decides what counts (never wrong)**. A GC-paused zombie resumes, its heartbeat CAS fails — the
rejection is the notification — and any overlap window produced duplicated *effort*, never
duplicated *effect*.

Stealing is **cooperative by default**: the stealer sets `releaseRequested`; the owner finishes its
current cut, checkpoints, releases — handoff at a clean cut boundary, zero replay. Forced steal is
the fallback for unresponsive owners.

**Standby/warmth priority (optimization, later)**: claims are handicapped by self-assessed recovery
cost — hot standby claims immediately; a node with an intact local RocksDB or cached checkpoint
waits briefly; cold nodes wait longest. "Warmest state wins" placement with zero coordination —
just different delays in front of the same CAS. Planned drains name the standby as preferred
successor (the common win: invisible deploys). Standbys poll the lease table attentively but do
NOT get a second liveness mechanism — the lease table stays the single ownership truth.

### 8.3 Exactly-once into Iceberg

Iceberg does half the work: data files are invisible until a catalog commit (CAS on
`metadata_location`). The lake's stamped commits do the rest: each commit advances a shard's
monotonic offset sequence; a commit whose from-offset doesn't match the table's stamp is a replay —
skipped, files orphan-swept. Two workers racing a shard therefore cannot duplicate rows.
Precedents: Flink's Iceberg sink (checkpoint-id stamped, single committer), the Kafka Connect
Iceberg sink (coordinator task commits), Delta's `txn(appId, version)`.

**At scale — elected committer + outbox** (adopt on trigger: routine commit-conflict retries or
snapshot churn; not in the PoC): workers stop committing; each sealed cut is written as a
`pending_cuts` row (file list + stamps) in the same DB, **in one transaction with a lease-row
validation** — the outbox insert is a fenced write, so a stale worker is rejected at its next cut,
before any heartbeat. An elected committer (just another lease) drains and group-commits one
catalog commit per table per interval, validating per-shard stamps and epochs inside. Benefits: no
CAS storms, an order of magnitude fewer Iceberg snapshots. Parquet PUTs stay unfenced — below the
commit point, orphan-swept.

### 8.4 Shard-state checkpoints on S3 (Flink, minus what we don't need)

RocksDB shard state is checkpointed to the consumer's own prefix, per shard, no coordination:

1. Fold + cuts continue untouched (cut persists state delta + offset locally, as today).
2. Every M cuts / ~10 min, at a cut boundary: RocksDB `Checkpoint()` — flush + hard-link snapshot,
   milliseconds, no pause — stamped with the cut's position vector.
3. Async: diff vs. last uploaded manifest; upload only **new** SSTs, content-addressed under
   `shared/` (key = hash of the bytes), create-if-absent (upload cost ∝ churn, not state size).
   **Naming caution (load-bearing)**: SSTs are *not* deterministic across workers — checkpoint
   timing and compaction make two workers' files differ even at the same fold position — so state
   objects must never carry meaningful sequential names (e.g. RocksDB file numbers): a dead
   worker's orphan `000042.sst` would permanently block a successor's different-content upload of
   the same name. Hash-derived names make collisions impossible (different content ⇒ different
   key; same key ⇒ same bytes ⇒ exists-is-success), so orphans only ever cost storage until the
   sweep, never progress. Flink's alternative (unique upload IDs + a local→remote handle map in
   the manifest) is equally valid. General rule: slot-named objects are only safe where content is
   a pure function of the name (chunks, segments); state files are not — name them by content or
   randomness. Reuse detection stays local ((fileNo, size) → hash memo; unchanged files are not
   re-hashed or re-shipped).
4. Commit point: seq-named checkpoint manifest (position vector, lease epoch, SST refs, checksums).
   No mutable LATEST pointer — latest = highest-seq valid manifest.

Recovery: claim lease → highest manifest → parallel SST download (same prefetch pipeline) → open →
replay archive from the stamped positions (sink skips committed cuts by stamp) → live.
RTO ≈ download(churn) + replay(≤ interval, at NIC-bound catch-up speed). Local recovery + lease
stickiness: a restart on the same node skips the download.

Flink scorecard — copy: incremental SSTs, async hard-link snapshot, local recovery, file-merging if
small files bite. Skip: barriers/global alignment (shards are independent single-writer folds —
each checkpoints alone); changelog state backend (the source archive IS the changelog; RPO is
always zero; cadence is purely an RTO knob); checkpoint-tied sink transactions (sink is idempotent
per cut, decoupled); savepoints (rebuild-from-log is the migration path for schema/resharding);
JobManager-style ref-counting GC (periodic full re-base bounds chains; manifest-diff sweep,
resolve-then-sweep).

Checkpoint fencing is the relaxed variant and that is sufficient: SST uploads are below the commit
point and content-addressed (a zombie cannot overwrite anything with different bytes — the naming
scheme removes the attack surface); the manifest is the usual slot race with epoch monotonicity;
and the backstop is structural — even a zombie checkpoint that landed is a *valid older state*,
costing replay time only, because checkpoints are caches of a fold, never correctness.

### 8.5 Optional changelog for hot standbys (design kept, build deferred)

Warm standbys probably aren't needed: shard failover never breaks serving (reads come from Iceberg
snapshots); it only delays one shard's freshness by the RTO. If they are wanted, prefer **source-fed
passive replicas** first (follow the archive, fold, commit nothing — viable precisely because the
source is universally readable and folds are deterministic; ADR 0006's model). The changelog earns
its place only when fold CPU is expensive or standbys are many. Its content is already designed
(EB-era `changelog-contents.md`): per-cut state-delta objects — cut seq, lease epoch, position
vector, per-slice write batches (puts + tombstones), checksummed — as seq-named S3 objects; the
standby polls `seq+1` and applies. The periodic checkpoint **is** the compacted log (re-base
supersedes deltas; they lifecycle-expire) — the EB-era changelog-compaction problem disappears in
the S3 formulation. Same two-tier + manifest pattern as the log itself, applied to state.

## 9. Precedents (and what each one licenses)

| System | What it proves for us |
|---|---|
| WarpStream / diskless Kafka | Object-store-first streaming works at scale; also the counter-model: leaderless multi-writer forces an external sequencer + agent read path. Our Raft pre-orders per partition → self-describing bucket, no metadata service, no read fleet. Their ~1 s e2e validates our chunk-tier estimate. |
| Elasticsearch Serverless / OpenSearch remote store | A flagship product rebuilt on "translog-as-WAL to object store + segments + stateless readers"; durability ack on the PUT is their latency price — we don't pay it (Raft acks first). |
| Litestream / SlateDB | The WAL-shipping pattern verbatim: seq-numbered small objects, dumb readers polling next, consolidation supersedes, CAS/epoch fencing (SlateDB). |
| Delta Lake `_delta_log` | "Poll seq+1 in a bucket" proven for years as a commit log. |
| Iceberg REST catalog / Delta Sharing | The access split: API for metadata + credential vending; bulk data direct from storage. |
| Flink Iceberg sink / Kafka Connect Iceberg sink | Stamped idempotent commits + single-committer group commit. |
| Kinesis KCL | Consumer-owned lease table with peer liveness; coordination as a client library, not a broker feature. |
| MongoDB change streams | The deferred tail API's model (server-stateless resume tokens) — and its known gap (history bounded by the oplog) is exactly what the archive tier fixes. |

## 10. Outlook (recorded, not proposed): serverless Zeebe

The same bucket layout supports moving the *engine's* commit line from "Raft acked" to "chunk PUT
acked" for clusters that opt in: durability = the chunk PUT, ordering = lease/epoch single writer
(election without replication), recovery = checkpoint + replay, disks fully ephemeral,
scale-to-zero. Costs: every externally visible touchpoint (client acks, job hand-offs,
cross-partition messages) pays the S3 commit latency (~150–250 ms standard; ~20 ms with dual-zone
S3 Express) even with speculative execution — internal processing runs ahead at memory speed and
only *output commit* is gated (Zeebe already has exactly this gating discipline against Raft
commit). RTO becomes an engineered number (incremental checkpoints, warm standbys). Product shape
would be two tiers: serverless (latency-tolerant, most workloads) and provisioned Raft (today's
engine) — the egress design above is the no-regret first step that builds most of the machinery
either way.

## 11. PoC plan (each phase demoable, each with a hard gate)

Three deliverables grown in parallel: `archive-contract` (bucket protocol library: naming,
manifests, epoch stamps, checksums, CAS helpers — no Zeebe deps), `archive-exporter`→rename
`archive-shipper` (the broker-side archiver), `archive-consumer` (the runtime library).

- **P0 — bucket protocol, no Zeebe.** Contract lib on MinIO + real S3; add **S3 conditional-write
  support to the store layer** (also closes backup's zombie-overwrite gap). Gate: fencing race —
  a zombie never lands a post-fence write; epochs monotonic under ~1k randomized races.
- **P1 — hot path.** Leader-side `ArchiverService` as a partition transition step (sibling of
  `BackupServiceTransitionStep`; **no exporter**): ships open-segment byte-slices as chunks,
  adaptive cadence + heartbeats; consumer polls `seq+1`. Gate: p99 freshness ≤ 4 s at load; idle
  cost ≈ heartbeats; broker CPU delta measured.
- **P2 — cold path.** Segment tier on the continuous-backup layers: byte-identical roll (chunks
  superseded bit-for-bit), `.idx` at upload, X/F manifests via the `metadata.json` pattern;
  archive-X feeds `DbPositionSupplier`; chunk expiry; consumer bootstraps cold and crosses the
  seam. Gate: full rebuild from bucket alone; counted zero dupes/loss across the seam.
- **P3 — failure drills.** Archiver kill mid-upload (orphan adopt/sweep), zombie leader race on
  failover, consumer kill + lease takeover, fenced outbox insert (stale worker rejected
  pre-heartbeat), resolve-then-sweep on unknown commits. Gate: full kill matrix, zero loss/dupes at
  the sink; frontier-stall during failover bounded.
- **P4 — scale & economics.** 3 clusters × N partitions into one consumer; park it 1 h, measure
  catch-up (must be consumer-NIC-bound); request/cost telemetry vs. the model (within 2×); decode
  CPU per MB of raw journal known.
- **P5 — the payoff.** The analytics lake consumes the archive via the consumer runtime; the stack
  runs **with the event bridge not deployed**; lake numbers identical to the EB-fed baseline.

Open PoC decisions: exporter-record format vs. raw-journal shipping was already settled (raw
journal; the reader library is the contract and needs an N-2 broker-version tolerance commitment);
remaining: module placement/naming, chunk cadence/size tuning, whether cluster-manifest-free
discovery (static config) suffices for all PoC gates (expected: yes).

## 12. Watch-list (accepted risks and their levers)

- **Freshness floor** 2–4 s is a product decision; the tail API is the pre-designed escape hatch.
- **Reader-library version skew**: archives outlive the writer version; N-2 tolerance must be a
  tested commitment, not an accident.
- **GDPR/retention**: immutable segments mean deletion = retention windows + downstream
  re-materialization; the raw-archive window can be short once consumers have materialized.
- **Small-object pressure**: adaptive cadence + lifecycle deletion; per-broker chunk bundling is
  the fallback if request costs ever bite (at the price of messier discovery).
- **Access scope**: the raw log is all-or-nothing per cluster (no field/tenant filtering at the
  source); acceptable while consumers are per-cluster trusted planes.

## 13. What this changes in existing designs

- `lake-greenfield-architecture.md` **C1**: "the log" becomes the per-cluster archive (the EB
  `zeebe-records` topic is retired as input); rebuild-from-empty-disk = list + read immutable
  objects. **C4**: keep the Task/cut/CommitCut execution model and state SPI; replace the
  consumer-group/rebalance layer with lease-based assignment (§8.2); `OriginGate` and stamped
  commits carry over unchanged; cluster id joins origin coordinates.
- **ADR 0006 (streaming standby tasks)**: reborn as the source-fed passive replica option (§8.5) —
  now trivially feasible because the source is universally readable.
- **ADR 0009 (changelog/standby)**: retired as a broker-topic design; its *content* spec survives
  as the optional S3 delta stream (§8.5); its compaction problem dissolves (checkpoint = the
  compacted log).
- **Event bridge**: retired as the egress tier once P5 passes. EB-internal designs (metadata plane,
  topic scoping, consumer groups) become reasoning records.
