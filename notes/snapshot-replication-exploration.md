# Snapshot replication — code validation & improvement options

**Branch:** `claude/handover-problem-exploration-w7dy39` · **Date:** 2026-07-01
**Context:** Follow-up to the chunk-sizing handover (model + `tcp-transfer-explorer.jsx`) and
PR [#56383](https://github.com/camunda/camunda/pull/56383) (default `snapshotChunkSize` 1 GiB → 1 MiB).
Goal: validate the model's assumptions against the code, then reconsider the approach — what are
elegant, targeted changes (no raft-layer redesign) that significantly improve snapshot replication?

---

## 1. Headline findings

1. **The first chunk of every replication is unbounded.** The leader never applies its own
   configured `snapshotChunkSize`; the chunk reader starts at `Long.MAX_VALUE` and is only capped
   by the *follower's* `preferredChunkSize` carried on each response — i.e. from chunk 2 onward.
   The first chunk is always an entire file (lexicographically first = usually an SST).
   **PR #56383 as-is does not prevent the multi-region livelock it targets**: a first SST too big
   for the 2.5 s timeout livelocks forever, and retries never shrink it.
2. **The follower fsyncs every chunk** (`channel.force(true)`), inside the window the raft thread
   is blocked. This is pure overhead: pending snapshots are discarded on crash/restart anyway, so
   per-chunk durability buys nothing. At 1 MiB chunks this multiplies fsync count ~8× vs
   per-file transfers — the main regression risk of the new default.
3. **Timeout → retry same chunk** (as the model assumed, and covered by tests). But **any other
   transport failure → restart the whole snapshot from chunk 0**, even though the follower still
   has all received state and same-chunk retry would be safe. One connection reset at 95% of a
   10 GiB transfer re-sends ~10 GiB.
4. **Chunk size is receiver-driven and per-response** — the auto-tuning hook already exists in the
   protocol (`InstallResponse.preferredChunkSize`, applied via `reader.setMaximumChunkSize`).
5. **Pipelining needs no wire-format change.** Chunks flow over one TCP connection in order; the
   follower's strict sequence check passes for pipelined in-order sends. The single-in-flight
   limitation is purely leader-side bookkeeping (`RaftMemberContext.installing`).
6. Observability is nearly zero: two gauges (in-progress count, last duration), follower-side only.

## 2. Model validation (handover §4), with citations

### §4.1 Retry semantics on chunk failure

- **Timeout → retry the same chunk.** `LeaderAppender.handleInstallResponseFailure`
  (`zeebe/atomix/cluster/src/main/java/io/atomix/raft/roles/LeaderAppender.java:473-488`): if the
  error is a `TimeoutException` the member's `nextSnapshotChunkId` is kept; `buildInstallRequest`
  seeks back to it (`LeaderAppender.java:396-405`). Tested:
  `RaftSnapshotReplicationFailureHandlingTest#shouldNotRestartFromFirstChunkWhenInstallRequestTimesOut`.
- **Non-timeout transport failure → restart from chunk 0** (`LeaderAppender.java:481-484` resets
  `nextSnapshotIndex`/`nextSnapshotChunkId`).
- **ERROR response → restart from chunk 0** (`handleInstallResponseError`,
  `LeaderAppender.java:521-533`). Tested:
  `...#shouldRestartSnapshotReplicationIfFollowerRejectedRequest`.
- **Retry cadence:** nothing re-sends immediately on failure; the next heartbeat tick
  (250 ms) re-enters `appendEntries` → `tryToReplicate`. Each failed attempt wastes
  `X + up to heartbeatInterval`. After 5 consecutive failures (`MIN_BACKOFF_FAILURE_COUNT`,
  `LeaderAppender.java:67`), the leader sends only empty appends until one succeeds
  (`LeaderAppender.java:775-779`), then resumes installs.
- Repeated failures also feed the leader's step-down check (`failAttempt`,
  `LeaderAppender.java:635-668`).
- Each retry re-reads and re-checksums the chunk from disk on the raft thread.
- Follower keeps `pendingSnapshot` across leader-side failures; it only aborts on out-of-order
  chunk id, mismatched snapshot id, rejection cases, or role transitions
  (`PassiveRole.java:437-464`, `555-570`). Duplicate of the previous chunk id → idempotent OK
  (`PassiveRole.java:438-446`).

### §4.2 Chunk I/O on the raft thread

- **Leader:** `buildInstallRequest` runs on the raft thread (`appendEntries` asserts
  `raft.checkThread()`); per chunk it opens the file, reads into a fresh direct buffer, and
  computes CRC32C (`FileBasedSnapshotChunkReader.next`,
  `zeebe/snapshot/.../impl/FileBasedSnapshotChunkReader.java:106-129`;
  `SnapshotChunkUtil.createSnapshotChunkFromFileChunk`).
- **Follower:** `onInstall` runs on the raft thread (`PassiveRole.java:126`); the chunk write hops
  to the snapshot-store actor but the raft thread **joins on it**
  (`pendingSnapshot.apply(snapshotChunk).join()`, `PassiveRole.java:215`). Inside that window:
  CRC32C verify, running SFV checksum update, `FileChannel` open, write, **`force(true)` per
  chunk** (`FileBasedReceivedSnapshot.java:93-118`, `176-198`).
- So the model's "both sides blocked ~c/R + P_fixed" holds, with `P_fixed` dominated by the
  follower's per-chunk fsync (ms-scale on cloud disks), plus two file opens and ~3 memory passes
  over the chunk bytes.
- **Election-timer interplay:** the install request resets the follower's election timer only when
  fully received and processed (`FollowerRole.java:86-91`), and the leader sends nothing else to
  that member while one install is in flight (`tryToReplicate`/`canInstall`,
  `LeaderAppender.java:871-880`, `RaftMemberContext.java:215-227`). A chunk whose transfer takes
  longer than the election timeout leaves the receiving follower silent long enough to start
  polling. Effective per-chunk deadline = min(`snapshotRequestTimeout`, `electionTimeout`) —
  both 2.5 s by default.

### §4.3 Chunks never span files — confirmed

- Chunk = `min(maximumChunkSize, remaining bytes of current file)`
  (`FileBasedSnapshotChunkReader.java:112`); chunk id = (fileName, offset)
  (`SnapshotChunkId`, reader `:86`); files ordered lexicographically (`:54-60`); per-file tail
  chunks occur; each small metadata file (`CURRENT`, `MANIFEST-*`, `OPTIONS-*`, metadata) is its
  own chunk.
- **New:** default `maximumChunkSize` is `Long.MAX_VALUE`
  (`FileBasedSnapshot.java:95-100` → reader ctor `:38-40`). The only writer of that limit is the
  response path (`LeaderAppender.java:499-501`). `RaftContext.getSnapshotChunkSize()` is read
  exclusively by `PassiveRole` (`PassiveRole.java:83`) to advertise `preferredChunkSize` — the
  send side never uses its own config. Hence headline finding #1.

### §4.4 Connection per topic — confirmed

- One pooled channel per `(address, messageType)` (`ChannelPool.java:61-137`); the install subject
  is per partition (`RaftMessageContext.java:55`), so the snapshot stream has a dedicated TCP
  connection, idle between replications.
- `TCP_NODELAY=true` and `SO_KEEPALIVE=true` on both sides
  (`NettyMessagingService.java:838-839`, `900-901`) — but kernel-default keepalive (2 h idle)
  won't detect NAT/LB idle-kill in useful time.
- Request timeout is a local scheduler failing the future (`NettyMessagingService.java:302-312`);
  the connection is **not** closed on timeout; late responses are discarded.
- **Dead-channel risk confirmed:** heartbeats/appends use a *different* channel, so a silently
  dead install channel is retried until kernel TCP gives up (~15 min with default `tcp_retries2`)
  before the pool reconnects (`ChannelPool` removes only on close, `:80-88`). Connect timeout 1 s
  (`NettyMessagingService.java:840`).
- Model correction: no TCP handshake per replication (channel reused); the cold-cwnd assumption
  stands via kernel `tcp_slow_start_after_idle`.

### §4.5 Checksums — confirmed cheap

- CRC32C per chunk (`SnapshotChunkUtil.java:29-31`), hardware-accelerated (multiple GB/s):
  computed by the leader at read time (raft thread), re-verified by the follower
  (`FileBasedReceivedSnapshot.java:133-145`), plus a running per-file SFV checksum (`:108`).
  Negligible vs disk/network; the real per-chunk cost is the fsync.

### §4.6 Pipelining feasibility

- Blocked today by (a) leader single-in-flight flag (`RaftMemberContext.java:215-227`),
  (b) follower strict `nextExpected` equality with abort+ERROR on mismatch
  (`PassiveRole.java:449-464`), (c) duplicate detection remembering only the immediately
  preceding chunk id (`:438-446`).
- But (b) is not actually violated by in-order pipelined sends on one TCP connection — requests
  arrive and are processed in send order. A bounded window is a **leader-only change**; failure
  paths must fall back to sequential/restart (see (c)).

### §7 empirical gaps (still open)

- File-size histogram of a real snapshot dir; chunk read+checksum and write+fsync
  microbenchmarks to pin `R` and `P_fixed` per side. Not done in this session.

## 3. Reconsidered approach — ranked, targeted improvements

Premise check: *"a reasonable chunk size is an easy win"* — yes, but the win saturates fast, and
with the current code two other costs (first-chunk unboundedness, per-chunk fsync) cap or even
invert it. The elegant wins below are mostly small and independently shippable.

| # |                              Change                              |        Effort         |                                    Impact                                    |
|---|------------------------------------------------------------------|-----------------------|------------------------------------------------------------------------------|
| 1 | Metrics for chunk-level behavior                                 | trivial               | prerequisite to validate everything, incl. PR #56383                         |
| 2 | Cap the **first** chunk with the leader's configured size        | ~1 line               | actually fixes the livelock PR #56383 targets                                |
| 3 | fsync per completed file instead of per chunk                    | small, local          | removes the dominant per-chunk cost; makes 1 MiB default safe on fat pipes   |
| 4 | Retry-same-chunk on **all** transport errors (not only timeout)  | ~3 lines              | stops full-restart waste on transient cross-region resets                    |
| 5 | Leader-side bounded pipelining (k≈2–4)                           | moderate, leader-only | removes the N·RTT stop-and-wait tax; no wire change                          |
| 6 | Delta replication: reuse unchanged SST files from local snapshot | larger (design/ADR)   | typically 80–95 % fewer bytes on repeat replications — the biggest lever     |
| 7 | LZ4 wire compression for chunk data                              | moderate              | 2–4× effective bandwidth on thin pipes (L0–L2 SSTs are stored uncompressed)  |
| 8 | Adaptive chunk sizing                                            | moderate+             | probably unnecessary after 2+3+5; hook (`preferredChunkSize`) already exists |

### 3.1 Metrics first

Chunk duration histogram, install timeout/retry counter, bytes-replicated counter, current chunk
size gauge, raft-thread block time per chunk. Today only `SnapshotReplicationMetrics` exists (two
gauges, follower side). This also answers "how do we validate the 1 MiB default" from the PR
thread.

### 3.2 Cap the first chunk

In `buildInstallRequest`, after `persistedSnapshot.newChunkReader()`
(`LeaderAppender.java:380`), call `reader.setMaximumChunkSize(raft.getSnapshotChunkSize())`.
Without this, every replication (and every retry of chunk 1) sends the whole first file. With it,
the leader's config bounds chunk 1 and the follower's preference takes over from chunk 2 — also
giving sane behavior in mixed-config/mixed-version clusters.

### 3.3 fsync per completed file, not per chunk

`FileBasedReceivedSnapshot.writeReceivedSnapshotChunk` calls `channel.force(true)` on every chunk
(`FileBasedReceivedSnapshot.java:190`). Crash-safety does not need it: a pending snapshot is
discarded on restart (store startup keeps only the latest persisted snapshot,
`FileBasedSnapshotStoreImpl.java:123-152`; a snapshot without its checksum file is deleted,
`:167-181`), and a snapshot only becomes "real" via the atomic checksum-file move + directory
flush at persist (`:621-628`). One caveat: `persist()` itself does **not** fsync file contents
today (`FileBasedReceivedSnapshot.persistInternal`), so the change must fsync each file once when
its last chunk arrives (`offset + length == totalFileSize` is known per chunk), or batch-fsync at
persist. Effect at 1 MiB chunks on 8 MiB files: 1 fsync per 8 chunks instead of per chunk, and
the follower's ack latency (which gates the sequential protocol) drops by the fsync time on 7 of
8 chunks.

### 3.4 Retry-same-chunk on all transport errors

`handleInstallResponseFailure` (`LeaderAppender.java:481-484`) resets to chunk 0 for any
non-timeout error, e.g. a connection reset from a transient cross-region blip. The follower's
pending state survives connection loss, duplicate detection makes same-chunk retry idempotent,
and if the follower truly lost state the retried chunk gets an ERROR response and the existing
restart path kicks in. So the reset is unnecessary conservatism: treat all transport failures
like timeouts. Restart-from-scratch then only happens on explicit follower rejection.

### 3.5 Leader-side bounded pipelining

Replace the boolean `installing` with a small outstanding-request window (k≈2–4):

- Same wire format, same one-connection ordering; the follower already accepts any in-order
  stream (its check is "chunk id equals what the previous chunk announced as next").
- On OK: slide the window. On timeout/ERROR anywhere in the window: drain, fall back to
  sequential retry from the oldest unacked chunk (with 3.4, that's cheap).
- Bounded memory (k·C). Per-request timeout semantics per chunk are unchanged. Raft-thread work
  still serializes per side, so the thread-block ceiling is untouched — but the wire no longer
  idles for `RTT + follower-work` between chunks; leader read of chunk n+1 overlaps follower
  write of chunk n.
- At 1 MiB chunks, 60 ms RTT, 1 GiB snapshot: removes ~`N·RTT` ≈ 1024 × 60 ms ≈ 61 s of pure
  waiting (plus the per-chunk fsync tax if 3.3 isn't done first).

### 3.6 Delta replication (the big one, still bounded)

RocksDB SST files are immutable and keep their names across checkpoints; consecutive snapshots
share most files. The follower being caught up via snapshot usually *has* the previous snapshot.
Per-file CRCs already exist on both sides (the SFV checksum file written with every persisted
snapshot). Sketch:

1. Leader's initial install message carries the file manifest (name, size, per-file CRC — all
   already computed).
2. Follower diffs the manifest against its latest local snapshot, hardlinks/copies matching files
   into the pending directory, and replies with the list of files it actually needs.
3. Leader streams only those files; existing chunk protocol unchanged; final snapshot-level
   checksum verification unchanged.

No raft-role or threading changes; it's a snapshot-store + one-message protocol addition
(`InstallResponse` grew `preferredChunkSize` the same way in #12795). Steady-state lagging
followers typically re-download 80–95 % identical bytes today — this eliminates most of `D`,
which every other cost (timeout exposure, RTT tax, thread block, fsyncs) scales with.

### 3.7 Compression, 3.8 adaptive sizing — keep in the back pocket

LZ4 on chunk data trades cheap CPU for scarce cross-region bandwidth (L0–L2 SSTs are stored
uncompressed per `ZeebeRocksDbFactory`); worth benchmarking after 1–5. Adaptive sizing has an
existing protocol hook but adds control-loop complexity for a parameter that stops being critical
once 3.2/3.3/3.5 land. One safety note if it is ever built: never *shrink* the size of a chunk
that may already have been applied (retry after lost ack) — the follower dedups by chunk id
(file, offset) without length; continuing from `offset + smallerLen` then mismatches the
follower's `nextExpected` and aborts the transfer. Only resize chunks not yet attempted.

## 4. Corrections to feed back into the model / explorer

- Per-file transfer is not just "today's default" — it is the permanent behavior of **chunk 1**
  regardless of config (until 3.2 lands).
- Retry waste per timeout ≈ `X + up to heartbeatInterval` (heartbeat-tick driven), plus a
  5-failure backoff cycle that inserts an empty-append round trip.
- `P_fixed` (follower) ≈ fsync + 2 file opens + ~3 memory passes; leader side has no fsync.
  `R` is asymmetric per side.
- Effective chunk deadline = min(request timeout, election timeout) — equal at defaults.
- No handshake per replication; cwnd idle-reset assumption holds. Dead-channel (NAT) case burns
  repeated timeouts until kernel TCP gives up, not one.
- Non-timeout failure = restart-from-scratch (worse than modeled) — until 3.4 lands.

## 5. Relevance to PR #56383 review thread

- Carlo's "larger chunks amortize protocol overhead" — the dominant per-chunk overhead is the
  follower fsync + one RTT of stop-and-wait, not header bytes. 1 MiB vs 8 MiB is mostly a wash on
  LAN but 8× more fsyncs and 8× more RTT waits; both are fixable (3.3, 3.5) rather than tunable.
- Deepthi's "how do we validate" — 3.1 metrics + the tc-netem benchmark from the handover (§6.5);
  watch chunk-duration histogram vs timeout and replication duration. Watch the **first chunk**
  specifically: with the current code it is a whole file no matter the config.
- The value 1 MiB itself is defensible: ~2 ms raft block per chunk (500 MB/s disk), ~4 % of the
  2.5 s timeout at 100 Mbps — large margins on both ceilings; the RTT tax it worsens is better
  fixed by pipelining than by a bigger chunk.

