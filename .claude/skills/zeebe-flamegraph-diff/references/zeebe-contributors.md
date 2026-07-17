# Biggest CPU contributors in a Zeebe broker flamegraph

Reference for interpreting `fg_subsystems.py` output and `fg_diff.py` frame names.
These are the subsystems that dominate a Camunda 8 broker CPU profile under load,
what they mean, the marker frames that identify them, and what a *rise* usually
implies. Percentages are rough steady-state shares seen on the daily ES-exporter
benchmark (gRPC profile, single broker node) — treat as ballpark, not SLO.

All engine work runs on the Zeebe **actor scheduler**, so nearly everything sits
under `io/camunda/zeebe/scheduler/ActorJob.invoke` /
`AtomixThreadFactory.lambda$newThread$0`. Those two are near the root of almost
every stack — high % there is expected and not itself a finding; look deeper.

## Subsystems (as split by `fg_subsystems.py`)

### exporter-es-client  (~15-16%)
Marker frames: `org/apache/http/impl/nio/*` (`produceOutput`, `outputReady`),
`co/elastic/clients/json/ObjectDeserializer.deserialize`,
`io/camunda/zeebe/broker/exporter/stream/*` (`ExporterContainer`,
`ExporterDirector`, `RecordExporter.export`), `sun/nio/ch/SocketChannel*.write`.
- The Elasticsearch/Camunda exporter serializing records and driving the async
  HTTP client to bulk-index into ES. Absent entirely with `exporter: none`.
- **Rises** if ES is slow (client blocks/retries), bulk size/rate grew, or record
  volume per flush increased. Cross-check with ES cluster health metrics.

### processing  (~12-13%)
Marker frames: `ProcessingStateMachine` (`batchProcessing`, `processCommand`),
`TypedRecordProcessor.processRecord`, `processing/bpmn/*`, `EventAppliers.applyState`,
`ResultBuilderBackedEventApplyingStateWriter`.
- The actual command→event engine work: executing BPMN, applying state changes.
  This is the "useful work" bucket — you *want* CPU here.
- **Falling** processing share while total CPU is flat is a red flag: the node is
  doing less real work per core (something else crowded it out).

### replay  (~9-12%)
Marker frames: `ReplayStateMachine` (`tryToReplayBatch`, `lambda$tryToReplayBatch$5`),
`Engine.replay`.
- Rebuilding state from the log. Followers replay continuously; leaders replay on
  transition/restart. High replay on a node = it follows busy partitions or just
  went through a leader change / restart / snapshot install.
- A large `Engine.replay` spike (→10%+ from ~0) usually means the capture caught a
  catch-up burst, not steady state.

### rocksdb-state  (small in `other`/native, but watch the named frames)
Marker frames: `TransactionalColumnFamily` (`.get`, `.forEach`, `.ensureInOpen`,
its `$$Lambda.run`), `ColumnFamilyContext.withPrefixKey`, `ZeebeTransaction`,
native `rocksdb::*` / `librocksdbjni.so`.
- Reading/iterating/writing engine state in RocksDB. Note much RocksDB time is in
  native code and lands in `other` in the subsystem split — the *named* Java
  frames in the diff are the reliable signal.
- **Rises** with larger state (more/longer prefix scans, bigger LSM → more read
  amplification), more key lookups per record, or compaction pressure. A big jump
  in `TransactionalColumnFamily$$Lambda.run` / `forEach` / `withPrefixKey` points
  at state-access cost per record going up.

### journal-flush  (~3-6%)
Marker frames: `io/camunda/zeebe/journal/file/*` (`SegmentedJournal.flush`,
`SegmentedJournalWriter.flush`), `RaftLogFlusher`, `PassiveRole.flush`,
`java/nio/MappedMemoryUtils.force` (msync), `Sequencer.tryWrite`.
- Appending to and fsync-ing the Raft log (durability). `MappedMemoryUtils.force`
  is the mmap msync; its on-CPU cost is kernel dirty-page scan + writeback setup
  (proportional to region scanned and dirty-page count), NOT the disk transfer.
- **CPU-mode caveat:** a *slower disk* does NOT raise this bucket — disk wait is
  off-CPU and invisible to CPU sampling. More flush CPU means more flush *work*:
  more frequent flushes, or a larger mapped region scanned per `msync` (e.g. bigger
  journal segments), or more append contention (`Sequencer.tryWrite` climbing).
  Confirm with `atomix_journal_flush_time_seconds_{count,sum}`: distinguish "more
  flushes" (count up) from "heavier flushes" (avg latency up, p99 flat) from "disk
  stalls" (p99 up) — only the first two show as CPU.

### raft-netty  (~14-15%)
Marker frames: `io/atomix/raft/*` (`ActiveRole.onAppend`, `PassiveRole.appendEntries`,
`RaftContext`, `RaftServerCommunicator.sendAndReceive`), `io/netty/*`
(`SingleThreadIoEventLoop`, `epoll`), `sun/nio/ch/SocketChannel`.
- Raft replication: leaders send AppendEntries, followers receive/ack, all over
  Netty. Scales with write throughput and cluster chatter.

### grpc  (~2%)
Marker frames: `io/grpc/*` (`ServerInterceptors`, `SerializingExecutor`),
`MetricCollectingServerInterceptor`, `NimbusJwtDecoder.createJwt` (auth). REST
equivalent lives under Tomcat/Spring instead.

### gc  (~8-9%)
Marker frames: `G1CMTask::do_marking_step`, `G1*`, `CMTask`, GC thread roots.
- G1 concurrent marking runs on dedicated GC threads and burns CPU **without**
  showing as stop-the-world pause time. So a high `gc` bucket here can coexist with
  a tiny `jvm_gc_pause_seconds` — it indicates allocation pressure / churn, not
  necessarily pause problems. Cross-check `jvm_gc_pause_seconds_*` vs
  `jvm_gc_memory_allocated_bytes_total`.

### scheduler-idle
`ActorThread.waitOnRunnable`, `Unsafe.park` — idle actor/park time. High idle means
the node is NOT CPU-bound on that path; treat as headroom, not work.

## Frames to ignore in diffs (attribution noise, not regressions)

- `thread_native_entry`, `start_thread`, `Thread::call_run`,
  `Executors$RunnableAdapter.call`, `ForkJoinTask*` — generic thread roots; they
  swing when the profiler attributes native/unknown differently between runs.
- `librocksdbjni<random>.so`, `libnetty_*<random>.so` — per-run temp filenames;
  `fg_common.normalize` collapses them but residual +/- on the raw name is noise.
- `$$Lambda.0x…run` — the address differs every JVM run; only meaningful after
  normalization (which `fg_diff.py` does) and only when the *owning class* is a
  real Camunda/engine type.
