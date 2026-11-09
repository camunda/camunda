import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.HdrHistogram.Histogram;

/**
 * Broker-like workload, modelled on how a Zeebe broker uses the actor scheduler:
 *
 * <ul>
 *   <li>network threads (platform) submit commands to a command API actor per partition;
 *   <li>the command API actor appends the command to the partition's Raft thread (platform);
 *   <li>the Raft thread batches appends, waits one replication round trip, commits, and wakes the
 *       stream processor and the exporter with actor.run (like LogStreamImpl.onCommit);
 *   <li>the stream processor (CPU actor) processes each committed command, appends follow-up
 *       records and "responds" (end-to-end latency is recorded here);
 *   <li>the exporter (IO actor) exports every committed record and does a blocking flush every N
 *       records (like the Elasticsearch exporter's bulk request);
 *   <li>a snapshot actor (IO) periodically blocks in a native call (like a RocksDB checkpoint);
 *   <li>a few periodic timers per partition.
 * </ul>
 *
 * Blocking calls use ActorControl#runBlocking when the scheduler has it (blockMode=auto), else they
 * block the actor thread as today.
 */
final class BrokerSim {

  static final class Rec {
    final long submittedAt;
    final boolean command;
    long committedAt;

    Rec(final long submittedAt, final boolean command) {
      this.submittedAt = submittedAt;
      this.command = command;
    }
  }

  /** Base actor with access to the protected ActorControl and a blocking helper. */
  abstract static class SimActor extends Actor {
    SimActor(final String name) {
      super(name);
    }

    /** Runs {@code blocking} (offloaded if possible), then {@code then} on this actor. */
    @SuppressWarnings("unchecked")
    void blockThen(final boolean offload, final Runnable blocking, final Runnable then) {
      if (!offload) {
        blocking.run();
        then.run();
        return;
      }
      try {
        final Callable<Object> callable =
            () -> {
              blocking.run();
              return null;
            };
        final ActorFuture<Object> f =
            (ActorFuture<Object>) SchedBench.RUN_BLOCKING.invoke(actor, callable);
        actor.runOnCompletion(f, (v, e) -> then.run());
      } catch (final ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    }
  }

  static final class Partition {
    final int id;
    final ConcurrentLinkedQueue<Rec> appended = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<Rec> forProcessor = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<Rec> forExporter = new ConcurrentLinkedQueue<>();
    final Histogram commandLatency = SchedBench.newHistogram();
    final Histogram exportLatency = SchedBench.newHistogram();
    final AtomicLong responses = new AtomicLong();
    CommandApi api;
    StreamProcessor processor;
    Exporter exporter;
    Thread raft;

    Partition(final int id) {
      this.id = id;
    }

    void append(final Rec rec) {
      appended.add(rec);
      LockSupport.unpark(raft);
    }

    /** Raft thread: batch appends, one replication round trip, commit, notify listeners. */
    void raftLoop(final long replicationNs) {
      final List<Rec> batch = new ArrayList<>();
      while (SchedBench.running) {
        Rec r;
        while ((r = appended.poll()) != null) {
          batch.add(r);
        }
        if (batch.isEmpty()) {
          LockSupport.parkNanos(this, TimeUnit.MILLISECONDS.toNanos(1));
          continue;
        }
        LockSupport.parkNanos(replicationNs); // followers acknowledge
        final long now = System.nanoTime();
        for (final Rec rec : batch) {
          rec.committedAt = now;
          forProcessor.add(rec);
          forExporter.add(rec);
        }
        batch.clear();
        processor.onRecordAvailable();
        exporter.onRecordAvailable();
      }
    }
  }

  static final class CommandApi extends SimActor {
    final Partition p;
    final long workNs;

    CommandApi(final Partition p, final long workNs) {
      super("CommandApi-" + p.id);
      this.p = p;
      this.workNs = workNs;
    }

    void request(final long submittedAt) {
      actor.run(
          () -> {
            SchedBench.busy(workNs);
            p.append(new Rec(submittedAt, true));
          });
    }
  }

  static final class StreamProcessor extends SimActor {
    final Partition p;
    final long workNs;
    final int followUps;
    final int maxBatch;

    StreamProcessor(final Partition p, final long workNs, final int followUps, final int maxBatch) {
      super("StreamProcessor-" + p.id);
      this.p = p;
      this.workNs = workNs;
      this.followUps = followUps;
      this.maxBatch = maxBatch;
    }

    void onRecordAvailable() {
      actor.run(this::readNext);
    }

    private void readNext() {
      SchedBench.noteThread();
      int processed = 0;
      Rec rec;
      while (processed < maxBatch && (rec = p.forProcessor.poll()) != null) {
        if (!rec.command) {
          continue; // events are only exported here
        }
        SchedBench.busy(workNs);
        for (int i = 0; i < followUps; i++) {
          p.append(new Rec(rec.submittedAt, false));
        }
        if (SchedBench.measuring) {
          p.commandLatency.recordValue(System.nanoTime() - rec.submittedAt);
          p.responses.incrementAndGet();
        }
        processed++;
      }
      if (!p.forProcessor.isEmpty()) {
        actor.submit(this::readNext); // like the processing state machine, yield between batches
      }
    }
  }

  static final class Exporter extends SimActor {
    final Partition p;
    final long workNs;
    final int flushEvery;
    final long flushMs;
    final String flushKind;
    final boolean offload;
    int pending;
    boolean flushing;

    Exporter(
        final Partition p,
        final long workNs,
        final int flushEvery,
        final long flushMs,
        final String flushKind,
        final boolean offload) {
      super("Exporter-" + p.id);
      this.p = p;
      this.workNs = workNs;
      this.flushEvery = flushEvery;
      this.flushMs = flushMs;
      this.flushKind = flushKind;
      this.offload = offload;
    }

    void onRecordAvailable() {
      actor.run(this::export);
    }

    private void export() {
      if (flushing) {
        return; // resumes when the flush completes
      }
      Rec rec;
      while ((rec = p.forExporter.poll()) != null) {
        SchedBench.busy(workNs);
        if (SchedBench.measuring) {
          p.exportLatency.recordValue(System.nanoTime() - rec.committedAt);
        }
        if (++pending >= flushEvery) {
          pending = 0;
          flushing = true;
          blockThen(
              offload,
              () -> SchedBench.block(flushKind, flushMs),
              () -> {
                flushing = false;
                export();
              });
          return;
        }
      }
    }
  }

  static final class Snapshotter extends SimActor {
    final long periodMs;
    final long blockMs;
    final boolean offload;
    boolean inProgress;

    Snapshotter(final int id, final long periodMs, final long blockMs, final boolean offload) {
      super("Snapshot-" + id);
      this.periodMs = periodMs;
      this.blockMs = blockMs;
      this.offload = offload;
    }

    @Override
    protected void onActorStarted() {
      // stagger partitions like real snapshot timers
      actor.schedule(
          ThreadLocalRandom.current().nextLong(periodMs),
          () -> actor.runAtFixedRate(Duration.ofMillis(periodMs), this::snapshot));
    }

    private void snapshot() {
      if (inProgress) {
        return;
      }
      inProgress = true;
      blockThen(offload, () -> SchedBench.block("native", blockMs), () -> inProgress = false);
    }
  }

  static final class Ticker extends SimActor {
    final long periodMs;
    final long workNs;

    Ticker(final String name, final long periodMs, final long workNs) {
      super(name);
      this.periodMs = periodMs;
      this.workNs = workNs;
    }

    @Override
    protected void onActorStarted() {
      actor.runAtFixedRate(Duration.ofMillis(periodMs), () -> SchedBench.busy(workNs));
    }
  }

  static void run(final ActorScheduler scheduler, final long warmupMs, final long durationMs)
      throws Exception {
    final int partitions = SchedBench.intArg("partitions", 3);
    final double rate = Double.parseDouble(SchedBench.arg("rate", "3000"));
    final long apiNs = SchedBench.intArg("apiWorkNs", 5_000);
    final long spNs = SchedBench.intArg("spWorkNs", 50_000);
    final int followUps = SchedBench.intArg("followUps", 3);
    final long exportNs = SchedBench.intArg("exportWorkNs", 10_000);
    final int flushEvery = SchedBench.intArg("flushEvery", 1000);
    final long flushMs = SchedBench.intArg("flushMs", 20);
    final long replicationUs = SchedBench.intArg("replicationUs", 500);
    final long snapshotPeriodMs = SchedBench.intArg("snapshotPeriodMs", 2000);
    final long snapshotMs = SchedBench.intArg("snapshotMs", 500);
    final int tickers = SchedBench.intArg("tickersPerPartition", 5);
    final String mode = SchedBench.arg("blockMode", "auto");
    final boolean offload =
        mode.equals("offload") || (mode.equals("auto") && SchedBench.RUN_BLOCKING != null);
    SchedBench.OUT.put("block_offloaded", offload);

    final List<Partition> parts = new ArrayList<>();
    for (int i = 0; i < partitions; i++) {
      final Partition p = new Partition(i);
      p.api = new CommandApi(p, apiNs);
      p.processor = new StreamProcessor(p, spNs, followUps, 100);
      p.exporter = new Exporter(p, exportNs, flushEvery, flushMs, "sleep", offload);
      scheduler.submitActor(p.api, SchedulingHints.cpuBound()).join();
      scheduler.submitActor(p.processor, SchedulingHints.cpuBound()).join();
      scheduler.submitActor(p.exporter, SchedulingHints.ioBound()).join();
      scheduler
          .submitActor(new Snapshotter(i, snapshotPeriodMs, snapshotMs, offload), SchedulingHints.ioBound())
          .join();
      for (int t = 0; t < tickers; t++) {
        scheduler
            .submitActor(new Ticker("tick-" + i + "-" + t, 100 + 50L * t, 5_000), SchedulingHints.cpuBound())
            .join();
      }
      p.raft =
          Thread.ofPlatform()
              .daemon()
              .name("raft-partition-" + i)
              .start(() -> p.raftLoop(TimeUnit.MICROSECONDS.toNanos(replicationUs)));
      parts.add(p);
    }

    // network threads: open-loop command submission, spread over partitions
    final AtomicLong sent = new AtomicLong();
    final int clients = SchedBench.intArg("clients", 2);
    for (int c = 0; c < clients; c++) {
      Thread.ofPlatform()
          .daemon()
          .name("bench-client-" + c)
          .start(
              () -> {
                final long interval = (long) (1e9 / (rate / clients));
                final long tick = TimeUnit.MICROSECONDS.toNanos(200);
                long next = System.nanoTime();
                final ThreadLocalRandom rnd = ThreadLocalRandom.current();
                while (SchedBench.running) {
                  final long now = System.nanoTime();
                  while (next <= now) {
                    parts.get(rnd.nextInt(parts.size())).api.request(System.nanoTime());
                    sent.incrementAndGet();
                    next += interval;
                  }
                  LockSupport.parkNanos(Math.min(tick, next - now));
                }
              });
    }

    SchedBench.phase = "warmup";
    Thread.sleep(warmupMs);
    SchedBench.phase = "measure";
    final SchedBench.CpuWindow cpu = new SchedBench.CpuWindow();
    final long sentAtStart = sent.get();
    SchedBench.measuring = true;
    Thread.sleep(durationMs);
    SchedBench.measuring = false;
    cpu.finish();
    final long sentInWindow = sent.get() - sentAtStart;
    SchedBench.running = false;

    final Histogram cmd = SchedBench.newHistogram();
    final Histogram exp = SchedBench.newHistogram();
    long responses = 0;
    for (final Partition p : parts) {
      cmd.add(p.commandLatency);
      exp.add(p.exportLatency);
      responses += p.responses.get();
    }
    SchedBench.OUT.put("throughput_per_s", Math.round(responses * 1000.0 / durationMs));
    SchedBench.OUT.put(
        "done_ratio", SchedBench.round2(responses / (double) Math.max(1, sentInWindow)));
    SchedBench.putLatency("cmd", cmd);
    SchedBench.putLatency("export", exp);
  }
}
