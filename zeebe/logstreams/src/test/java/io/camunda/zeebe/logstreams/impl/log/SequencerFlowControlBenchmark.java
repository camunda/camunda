/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.logstreams.impl.LogStreamMetricsImpl;
import io.camunda.zeebe.logstreams.impl.flowcontrol.FlowControl;
import io.camunda.zeebe.logstreams.impl.flowcontrol.RateLimit;
import io.camunda.zeebe.logstreams.impl.flowcontrol.StabilizingAIMDLimit;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorageReader;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.AuxCounters.Type;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 6, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED")
@Threads(8)
@State(Scope.Benchmark)
@SuppressWarnings("NullAway.Init")
public class SequencerFlowControlBenchmark {
  // Per-command processing time to simulate - observed stable at 50µs, oscillating at 500µs
  @Param({"50", "500"})
  private int processingMicros;

  @Param({"200"})
  private int expectedRttMs;

  // A non-whitelisted user command, so it is actually subject to the request limiter
  private final WriteContext userContext =
      WriteContext.userCommand(ProcessInstanceCreationIntent.CREATE);

  private final AtomicLong highestAccepted = new AtomicLong(0);

  private Sequencer sequencer;
  private FlowControl flowControl;
  private StabilizingAIMDLimit aimdLimit;
  private List<LogAppendEntry> userBatch;
  private SimpleMeterRegistry sequencerRegistry;

  private volatile boolean running;
  private Thread processorThread;
  // Models the raft-thread hand-off: append offers here (non-blocking, unbounded), a drain thread
  // consumes — reproducing the enqueue cost and producer/consumer coupling without real work.
  private BlockingQueue<Object> raftQueue;
  private Thread raftDrainThread;

  private int limitMin = Integer.MAX_VALUE;
  private int limitMax = Integer.MIN_VALUE;
  private long limitSum;
  private long limitSamples;

  @Setup(Level.Trial)
  public void setup() {
    aimdLimit =
        StabilizingAIMDLimit.newBuilder()
            .initialLimit(100)
            .minLimit(1)
            .maxLimit(1000)
            .backoffRatio(0.9)
            .expectedRTT(expectedRttMs, TimeUnit.MILLISECONDS)
            .build();
    flowControl =
        new FlowControl(
            new LogStreamMetricsImpl(new SimpleMeterRegistry()),
            aimdLimit,
            RateLimit.disabled(),
            0);
    sequencerRegistry = new SimpleMeterRegistry();
    raftQueue = new LinkedBlockingQueue<>();
    sequencer =
        new Sequencer(
            new EnqueueingLogStorage(raftQueue),
            1L,
            4 * 1024 * 1024,
            InstantSource.system(),
            new SequencerMetrics(sequencerRegistry),
            flowControl);
    userBatch = newSingleEntryBatch();

    running = true;
    raftDrainThread = new Thread(this::raftDrainLoop, "raft-drain");
    raftDrainThread.setDaemon(true);
    raftDrainThread.start();
    processorThread = new Thread(this::processorLoop, "stream-processor");
    processorThread.setDaemon(true);
    processorThread.start();
  }

  @TearDown(Level.Trial)
  public void tearDown() throws InterruptedException {
    running = false;
    processorThread.interrupt();
    processorThread.join(TimeUnit.SECONDS.toMillis(5));
    raftDrainThread.interrupt();
    raftDrainThread.join(TimeUnit.SECONDS.toMillis(5));
    final double mean = limitSamples == 0 ? -1 : (double) limitSum / limitSamples;
    System.out.printf(
        "%n[flow-control] AIMD limit over run: min=%d max=%d mean=%.1f (%d samples)%n",
        limitMin == Integer.MAX_VALUE ? -1 : limitMin,
        limitMax == Integer.MIN_VALUE ? -1 : limitMax,
        mean,
        limitSamples);
    // Real sequencer lock timers under this run (userCommand only — the processor doesn't take the
    // lock here, it just calls onProcessed).
    printLockTimer("zeebe.sequencer.lock.hold.time", "writer");
    printLockTimer("zeebe.sequencer.lock.wait.time", "waiter");
  }

  private void printLockTimer(final String name, final String tag) {
    for (final var timer : sequencerRegistry.find(name).timers()) {
      final var snapshot = timer.takeSnapshot();
      final var percentiles = new StringBuilder();
      for (final var p : snapshot.percentileValues()) {
        percentiles.append(
            String.format(" p%.0f=%.2fus", p.percentile() * 100, p.value(TimeUnit.MICROSECONDS)));
      }
      System.out.printf(
          "%n[seq-lock] %s{%s=%s} n=%d mean=%.2fus max=%.2fus%s%n",
          name,
          tag,
          timer.getId().getTag(tag),
          snapshot.count(),
          snapshot.mean(TimeUnit.MICROSECONDS),
          timer.max(TimeUnit.MICROSECONDS),
          percentiles);
    }
  }

  @JMHTest("submit")
  void smoke(final JMHTestCase testCase) {
    testCase
        .withOptions(
            options ->
                options
                    .forks(1)
                    .warmupIterations(1)
                    .measurementIterations(1)
                    .param("processingMicros", "500")
                    .param("expectedRttMs", "200"))
        .run();
  }

  @Benchmark
  public void submit(final Counters counters) {
    final var result = sequencer.tryWrite(userContext, userBatch, -1L);
    if (result.isRight()) {
      counters.accepted++;
      highestAccepted.accumulateAndGet(result.get(), Math::max);
    } else {
      counters.rejected++;
      LockSupport.parkNanos(1_000); // model a client backing off before retrying after backpressure
    }
  }

  private void processorLoop() {
    long nextToProcess = 1L;
    while (running) {
      if (nextToProcess > highestAccepted.get()) {
        LockSupport.parkNanos(1_000);
        continue;
      }
      busyWaitMicros(processingMicros);
      flowControl.onProcessed(nextToProcess);
      nextToProcess++;
      sampleLimit();
    }
  }

  private void raftDrainLoop() {
    while (running) {
      try {
        raftQueue.take();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private static void busyWaitMicros(final long micros) {
    final long deadline = System.nanoTime() + micros * 1_000L;
    while (System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
  }

  private void sampleLimit() {
    final int limit = aimdLimit.getLimit();
    limitMin = Math.min(limitMin, limit);
    limitMax = Math.max(limitMax, limit);
    limitSum += limit;
    limitSamples++;
  }

  private static List<LogAppendEntry> newSingleEntryBatch() {
    final var metadata = new RecordMetadata().intent(ProcessInstanceCreationIntent.CREATE);
    return List.of(LogAppendEntry.of(metadata, new UnifiedRecordValue(64)));
  }

  @AuxCounters(Type.OPERATIONS)
  @State(Scope.Thread)
  public static class Counters {
    public long accepted;
    public long rejected;

    @Setup(Level.Iteration)
    public void reset() {
      accepted = 0;
      rejected = 0;
    }
  }

  /** Models LeaderRole.appendEntry: a non-blocking hand-off to the raft thread, no real work. */
  private static final class EnqueueingLogStorage implements LogStorage {
    private static final Object HANDOFF = new Object();
    private final BlockingQueue<Object> raftQueue;

    private EnqueueingLogStorage(final BlockingQueue<Object> raftQueue) {
      this.raftQueue = raftQueue;
    }

    @Override
    public LogStorageReader newReader() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void append(
        final long lowestPosition,
        final long highestPosition,
        final BufferWriter bufferWriter,
        final AppendListener listener) {
      raftQueue.offer(HANDOFF);
    }

    @Override
    public void addCommitListener(final CommitListener listener) {}

    @Override
    public void removeCommitListener(final CommitListener listener) {}
  }
}
